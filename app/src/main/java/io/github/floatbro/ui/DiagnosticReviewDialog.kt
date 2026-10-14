package io.github.floatbro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.floatbro.core.common.diagnostics.DiagnosticRecord
import io.github.floatbro.core.common.diagnostics.LocalDiagnostics
import io.github.floatbro.ui.theme.Slate200
import io.github.floatbro.ui.theme.Slate500
import io.github.floatbro.ui.theme.Slate900

@Composable
fun DiagnosticReviewDialog(
    record: DiagnosticRecord,
    onDismiss: () -> Unit,
) {
    var includeDeviceInfo by remember { mutableStateOf(false) }
    val reportText =
        remember(record, includeDeviceInfo) {
            LocalDiagnostics.formatReport(record, includeDeviceInfo)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "审阅诊断报告",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Slate900,
            )
        },
        text = {
            ReviewDialogContent(
                includeDeviceInfo = includeDeviceInfo,
                onIncludeDeviceInfoChange = { includeDeviceInfo = it },
                reportText = reportText,
            )
        },
        confirmButton = {
            ReviewDialogActions(
                reportText = reportText,
                errorType = record.errorType,
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun ReviewDialogContent(
    includeDeviceInfo: Boolean,
    onIncludeDeviceInfoChange: (Boolean) -> Unit,
    reportText: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = includeDeviceInfo,
                onCheckedChange = onIncludeDeviceInfoChange,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "包含设备兼容信息",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate900,
                )
                Text(
                    text = "厂商、机型、系统版本、ABI与内核",
                    fontSize = 11.sp,
                    color = Slate500,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Slate200)

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp),
        ) {
            val scrollState = rememberScrollState()
            SelectionContainer {
                Text(
                    text = reportText,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate900,
                    modifier = Modifier.verticalScroll(scrollState),
                )
            }
        }
    }
}

@Composable
private fun ReviewDialogActions(
    reportText: String,
    errorType: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Text("关闭", fontSize = 13.sp)
        }

        Row {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("FloatBro Diagnostic Report", reportText))
                    Toast.makeText(context, "诊断报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("复制", fontSize = 13.sp)
            }

            TextButton(
                onClick = {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "FloatBro 本地诊断报告")
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, "分享诊断报告"))
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("系统分享", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("FloatBro Diagnostic Report", reportText))
                    Toast.makeText(context, "报告已复制，请在 GitHub 中粘贴提交", Toast.LENGTH_LONG).show()

                    val issueUrl = "https://github.com/floatbro/floatbro/issues/new?title=%5B%E8%AF%8A%E6%96%AD%E5%8F%8D%E9%A6%88%5D+${Uri.encode(errorType)}"
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)))
                    } catch (_: Exception) {
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("反馈", fontSize = 13.sp)
            }
        }
    }
}
