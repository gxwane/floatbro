package io.github.floatbro.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.floatbro.core.common.diagnostics.DiagnosticFailureKind
import io.github.floatbro.core.common.diagnostics.DiagnosticRecord
import io.github.floatbro.core.common.diagnostics.LocalDiagnostics
import io.github.floatbro.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticLogsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val diagnostics = remember { LocalDiagnostics.getInstance(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<DiagnosticRecord>>(emptyList()) }
    var storageBytes by remember { mutableLongStateOf(0L) }
    var selectedRecordForReview by remember { mutableStateOf<DiagnosticRecord?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("ALL") }

    fun refreshData() {
        scope.launch {
            records = diagnostics.readFailures()
            storageBytes = diagnostics.storageBytes()
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    val filteredRecords =
        remember(records, activeFilter) {
            filterRecords(records, activeFilter)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DiagnosticLogsHeader(
                storageBytes = storageBytes,
                canClear = records.isNotEmpty(),
                onClearClick = { showClearConfirmation = true },
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DiagnosticFilterRow(
                    activeFilter = activeFilter,
                    totalCount = records.size,
                    onFilterChange = { activeFilter = it },
                )
                HorizontalDivider(color = Slate200)
                DiagnosticLogsList(
                    records = filteredRecords,
                    onRecordClick = { selectedRecordForReview = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    if (showClearConfirmation) {
        DiagnosticClearConfirmDialog(
            onDismiss = { showClearConfirmation = false },
            onConfirm = {
                scope.launch {
                    diagnostics.clear()
                    refreshData()
                    showClearConfirmation = false
                    Toast.makeText(context, "本地诊断日志已清空", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    selectedRecordForReview?.let { record ->
        DiagnosticReviewDialog(
            record = record,
            onDismiss = { selectedRecordForReview = null },
        )
    }
}

private fun filterRecords(
    records: List<DiagnosticRecord>,
    filter: String,
): List<DiagnosticRecord> =
    when (filter) {
        "CRASH" ->
            records.filter {
                it.kind == DiagnosticFailureKind.CRASH_UNCAUGHT || it.kind == DiagnosticFailureKind.WEBVIEW_RENDER_PROCESS_GONE
            }
        "AD" ->
            records.filter {
                it.kind == DiagnosticFailureKind.AD_FILTER_OVERKILL || it.kind == DiagnosticFailureKind.AD_FILTER_MISSED || it.adFilterTraces.isNotEmpty()
            }
        "SYSTEM" ->
            records.filter {
                it.kind == DiagnosticFailureKind.SYSTEM_ERROR || it.kind == DiagnosticFailureKind.FLOAT_SERVICE_FAILURE || it.kind == DiagnosticFailureKind.WINDOW_MUTEX_ERROR
            }
        else -> records
    }

@Composable
private fun DiagnosticLogsHeader(
    storageBytes: Long,
    canClear: Boolean,
    onClearClick: () -> Unit,
) {
    val formattedSize =
        remember(storageBytes) {
            when {
                storageBytes < 1024 -> "$storageBytes B"
                storageBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KiB", storageBytes / 1024.0)
                else -> String.format(Locale.US, "%.2f MiB", storageBytes / (1024.0 * 1024.0))
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "本地诊断与日志",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Slate900,
            )
            Text(
                text = "占用: $formattedSize / 限制 2.0 MiB (最多保留 7 天)",
                fontSize = 11.5.sp,
                color = Slate500,
            )
        }
        IconButton(
            onClick = onClearClick,
            enabled = canClear,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "清空",
                tint = if (canClear) Color(0xFFEF4444) else Slate400,
            )
        }
    }
}

@Composable
private fun DiagnosticFilterRow(
    activeFilter: String,
    totalCount: Int,
    onFilterChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = activeFilter == "ALL",
            onClick = { onFilterChange("ALL") },
            label = { Text("全部 ($totalCount)", fontSize = 11.sp) },
        )
        FilterChip(
            selected = activeFilter == "CRASH",
            onClick = { onFilterChange("CRASH") },
            label = { Text("崩溃", fontSize = 11.sp) },
        )
        FilterChip(
            selected = activeFilter == "AD",
            onClick = { onFilterChange("AD") },
            label = { Text("广告拦截", fontSize = 11.sp) },
        )
        FilterChip(
            selected = activeFilter == "SYSTEM",
            onClick = { onFilterChange("SYSTEM") },
            label = { Text("系统", fontSize = 11.sp) },
        )
    }
}

@Composable
private fun DiagnosticLogsList(
    records: List<DiagnosticRecord>,
    onRecordClick: (DiagnosticRecord) -> Unit,
) {
    if (records.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无相关诊断记录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500,
                )
                Text(
                    text = "系统运行稳定，所有数据均保留在本地",
                    fontSize = 11.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(records) { record ->
                DiagnosticRecordItem(
                    record = record,
                    onClick = { onRecordClick(record) },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticClearConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空本地诊断日志") },
        text = { Text("此操作将删除本设备上记录的所有本地诊断数据，无法撤销。") },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                onClick = onConfirm,
            ) {
                Text("确认清空", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DiagnosticRecordItem(
    record: DiagnosticRecord,
    onClick: () -> Unit,
) {
    val sdf = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(record.timestamp) { sdf.format(Date(record.timestamp)) }

    val badgeColor =
        when (record.kind) {
            DiagnosticFailureKind.CRASH_UNCAUGHT,
            DiagnosticFailureKind.WEBVIEW_RENDER_PROCESS_GONE,
            -> Color(0xFFEF4444)
            DiagnosticFailureKind.AD_FILTER_OVERKILL,
            DiagnosticFailureKind.AD_FILTER_MISSED,
            -> BrandBlue
            else -> Color(0xFFF59E0B)
        }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = record.kind.label,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = Slate500,
                )
            }

            Text(
                text = record.message,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Slate900,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (record.adFilterTraces.isNotEmpty()) {
                Text(
                    text = "包含 ${record.adFilterTraces.size} 项拦截足迹 · 点击审阅",
                    fontSize = 10.5.sp,
                    color = BrandBlue,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
