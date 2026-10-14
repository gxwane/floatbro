package io.github.floatbro.core.common.diagnostics

import android.content.Context
import android.os.Build
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class LocalDiagnostics(
    private val directoryProvider: () -> File,
    private val environmentLoader: () -> DiagnosticEnvironment,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val MAX_SYSTEM_BREADCRUMBS = 50
        const val MAX_AD_FILTER_TRACES = 100
        private const val DEDUP_COOLDOWN_MS = 3000L

        @Volatile
        private var defaultInstance: LocalDiagnostics? = null

        fun getInstance(context: Context): LocalDiagnostics =
            defaultInstance ?: synchronized(this) {
                defaultInstance ?: LocalDiagnostics(
                    directoryProvider = { File(context.cacheDir, "diagnostics") },
                    environmentLoader = { loadAndroidEnvironment(context) },
                ).also { defaultInstance = it }
            }

        private fun loadAndroidEnvironment(context: Context): DiagnosticEnvironment {
            var appVersion = ""
            var buildNumber = ""
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                appVersion = packageInfo.versionName ?: ""
                buildNumber =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toString()
                    }
            } catch (_: Exception) {
            }

            var webViewVersion: String? = null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val wvPackage = WebView.getCurrentWebViewPackage()
                    webViewVersion = wvPackage?.versionName
                }
            } catch (_: Exception) {
            }

            return DiagnosticEnvironment(
                appVersion = appVersion,
                buildNumber = buildNumber,
                androidSdk = Build.VERSION.SDK_INT,
                osVersion = Build.VERSION.RELEASE ?: "",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                webViewVersion = webViewVersion,
            )
        }

        fun formatReport(
            record: DiagnosticRecord,
            includeDeviceInfo: Boolean,
        ): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date(record.timestamp))
            val sb = StringBuilder()

            sb.appendLine("# FloatBro 诊断报告")
            sb.appendLine("> 此内容由用户主动导出并在设备内审阅；FloatBro 绝不自动后台上传任何数据。")
            sb.appendLine()
            sb.appendLine("## 核心故障信息")
            sb.appendLine("- **时间**: $dateStr")
            sb.appendLine("- **故障类型**: ${record.kind.label} (${record.kind.name})")
            sb.appendLine("- **异常类名**: ${record.errorType}")
            sb.appendLine("- **信息摘要**: ${record.message}")
            sb.appendLine("- **应用版本**: ${record.environment.appVersion} (${record.environment.buildNumber})")
            sb.appendLine()

            if (includeDeviceInfo) {
                val env = record.environment
                sb.appendLine("## 设备兼容信息")
                sb.appendLine("- **设备厂商**: ${env.manufacturer ?: "未知"}")
                sb.appendLine("- **设备型号**: ${env.model ?: "未知"}")
                sb.appendLine("- **Android 系统**: ${env.osVersion} (API ${env.androidSdk})")
                sb.appendLine("- **支持架构**: ${env.supportedAbis.joinToString(", ")}")
                if (env.webViewVersion != null) {
                    sb.appendLine("- **WebView 内核**: ${env.webViewVersion}")
                }
                sb.appendLine()
            }

            if (record.stackTrace.isNotBlank()) {
                sb.appendLine("## 异常调用堆栈")
                sb.appendLine("```")
                sb.appendLine(record.stackTrace.trim())
                sb.appendLine("```")
                sb.appendLine()
            }

            if (record.adFilterTraces.isNotEmpty()) {
                sb.appendLine("## 广告拦截足迹 (最近 ${record.adFilterTraces.size} 项)")
                record.adFilterTraces.forEach { trace ->
                    val traceTime = sdf.format(Date(trace.timestamp))
                    sb.appendLine("- `[$traceTime]` [${trace.action}] [${trace.resourceType}] 主站: `${trace.mainFrameHost}` -> 资源: `${trace.resourceHost}` (规则: `${trace.matchedRule ?: "none"}`)")
                }
                sb.appendLine()
            }

            if (record.breadcrumbs.isNotEmpty()) {
                sb.appendLine("## 系统与浮窗运行面包屑 (最近 ${record.breadcrumbs.size} 项)")
                record.breadcrumbs.forEach { bc ->
                    val bcTime = sdf.format(Date(bc.timestamp))
                    val detailsStr = if (bc.details.isNotEmpty()) " ${bc.details}" else ""
                    sb.appendLine("- `[$bcTime]` [${bc.category}] ${bc.event}$detailsStr")
                }
                sb.appendLine()
            }

            return sb.toString()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val systemBreadcrumbs = ConcurrentLinkedQueue<DiagnosticBreadcrumb>()
    private val adFilterTraces = ConcurrentLinkedQueue<AdFilterTraceEvent>()
    private val recentFailures = ConcurrentHashMap<String, Long>()

    private var store: DiagnosticStore? = null
    private var configFile: File? = null
    private var isInitialized = false
    private var isEnabled = true

    fun isEnabled(): Boolean = isEnabled

    @Synchronized
    fun initialize() {
        if (isInitialized) return
        try {
            val dir = directoryProvider()
            dir.mkdirs()
            configFile = File(dir, ".floatbro_diagnostics_config.json")
            store = DiagnosticStore(File(dir, ".floatbro_diagnostics.jsonl"))
            isEnabled = readConfig()
        } catch (_: Exception) {
            isEnabled = false
            store = null
        } finally {
            isInitialized = true
        }
    }

    fun setEnabled(
        value: Boolean,
        clearExisting: Boolean = false,
    ) {
        if (!isInitialized) initialize()
        isEnabled = value
        scope.launch {
            try {
                configFile?.let {
                    val obj = JSONObject().apply { put("enabled", value) }
                    it.writeText(obj.toString())
                }
                if (!value || clearExisting) {
                    clear()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun addBreadcrumb(breadcrumb: DiagnosticBreadcrumb) {
        if (!isEnabled) return
        val sanitizedEvent = DiagnosticSanitizer.sanitizeText(breadcrumb.event, maxLength = 256)
        val sanitizedDetails =
            breadcrumb.details.mapValues {
                DiagnosticSanitizer.sanitizeText(it.value, maxLength = 512)
            }
        val safeBreadcrumb = breadcrumb.copy(event = sanitizedEvent, details = sanitizedDetails)

        systemBreadcrumbs.add(safeBreadcrumb)
        while (systemBreadcrumbs.size > MAX_SYSTEM_BREADCRUMBS) {
            systemBreadcrumbs.poll()
        }
    }

    fun recordAdFilterTrace(trace: AdFilterTraceEvent) {
        if (!isEnabled) return
        // AdFilter Action: PASSED is not recorded to preserve bandwidth and buffer isolation
        if (trace.action == AdFilterAction.PASSED) return

        val sanitizedTrace =
            trace.copy(
                mainFrameHost = DiagnosticSanitizer.sanitizeText(trace.mainFrameHost, maxLength = 128),
                resourceHost = DiagnosticSanitizer.sanitizeText(trace.resourceHost, maxLength = 128),
                matchedRule = trace.matchedRule?.let { DiagnosticSanitizer.sanitizeText(it, maxLength = 256) },
            )

        adFilterTraces.add(sanitizedTrace)
        while (adFilterTraces.size > MAX_AD_FILTER_TRACES) {
            adFilterTraces.poll()
        }
    }

    fun getSystemBreadcrumbsSnapshot(): List<DiagnosticBreadcrumb> = systemBreadcrumbs.toList()

    fun getAdFilterTracesSnapshot(): List<AdFilterTraceEvent> = adFilterTraces.toList()

    fun recordFailure(
        kind: DiagnosticFailureKind,
        error: String,
        throwable: Throwable? = null,
        breadcrumbs: List<DiagnosticBreadcrumb>? = null,
        adFilterTraces: List<AdFilterTraceEvent>? = null,
    ) {
        if (!isEnabled) return
        val now = timeProvider()
        val errorType = throwable?.javaClass?.simpleName ?: "DiagnosticEvent"
        val sanitizedMsg = DiagnosticSanitizer.sanitizeText(error)
        val stackTrace =
            throwable?.let {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                it.printStackTrace(pw)
                DiagnosticSanitizer.sanitizeText(sw.toString(), maxLength = 32 * 1024)
            } ?: ""

        val firstStackLine = stackTrace.lines().firstOrNull() ?: ""
        val fingerprint = "$kind|$errorType|$sanitizedMsg|$firstStackLine"

        val lastLogged = recentFailures[fingerprint]
        if (lastLogged != null && (now - lastLogged) < DEDUP_COOLDOWN_MS) {
            return // Skip duplicate within cooldown window
        }
        recentFailures[fingerprint] = now
        // Clean up old deduplication entries (> 60s)
        recentFailures.entries.removeIf { (now - it.value) > 60_000L }

        val env =
            try {
                environmentLoader()
            } catch (_: Exception) {
                DiagnosticEnvironment()
            }

        val finalBreadcrumbs = breadcrumbs ?: getSystemBreadcrumbsSnapshot()
        val finalAdTraces = adFilterTraces ?: getAdFilterTracesSnapshot()

        val record =
            DiagnosticRecord(
                timestamp = now,
                kind = kind,
                errorType = errorType,
                message = sanitizedMsg,
                stackTrace = stackTrace,
                environment = env,
                breadcrumbs = finalBreadcrumbs,
                adFilterTraces = finalAdTraces,
            )

        scope.launch {
            try {
                store?.append(record)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Emergency direct synchronous write for unhandled crashes.
     */
    fun emergencySyncFlushCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            val now = timeProvider()
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stack = DiagnosticSanitizer.sanitizeText(sw.toString(), maxLength = 32 * 1024)

            val record =
                DiagnosticRecord(
                    timestamp = now,
                    kind = DiagnosticFailureKind.CRASH_UNCAUGHT,
                    errorType = throwable.javaClass.name,
                    message = "Fatal crash on thread [${thread.name}]: ${DiagnosticSanitizer.sanitizeText(throwable.message)}",
                    stackTrace = stack,
                    environment =
                        try {
                            environmentLoader()
                        } catch (_: Exception) {
                            DiagnosticEnvironment()
                        },
                    breadcrumbs = getSystemBreadcrumbsSnapshot(),
                    adFilterTraces = getAdFilterTracesSnapshot(),
                )
            store?.emergencyWrite(record)
        } catch (_: Exception) {
        }
    }

    suspend fun readFailures(): List<DiagnosticRecord> = store?.readAll() ?: emptyList()

    suspend fun storageBytes(): Long = store?.storageBytes() ?: 0L

    suspend fun clear() {
        systemBreadcrumbs.clear()
        adFilterTraces.clear()
        recentFailures.clear()
        store?.clear()
    }

    private fun readConfig(): Boolean {
        val file = configFile ?: return true
        if (!file.exists()) return true
        return try {
            val obj = JSONObject(file.readText())
            obj.optBoolean("enabled", true)
        } catch (_: Exception) {
            true
        }
    }
}
