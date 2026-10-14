package io.github.floatbro.core.common.diagnostics

import org.json.JSONArray
import org.json.JSONObject

enum class DiagnosticFailureKind(
    val label: String,
) {
    CRASH_UNCAUGHT("未捕获致命崩溃"),
    SYSTEM_ERROR("系统异常"),
    FLOAT_SERVICE_FAILURE("悬浮窗服务异常"),
    WINDOW_MUTEX_ERROR("窗口焦点与互斥异常"),
    WEBVIEW_RENDER_PROCESS_GONE("WebView 渲染进程终止"),
    WEBVIEW_NAVIGATION_ERROR("网页致命加载失败"),
    AD_FILTER_OVERKILL("广告拦截规则疑似误杀"),
    AD_FILTER_MISSED("广告拦截规则疑似漏杀"),
    MEMORY_PRESSURE_CRITICAL("系统极低内存告警"),
}

data class DiagnosticEnvironment(
    val appVersion: String = "",
    val buildNumber: String = "",
    val androidSdk: Int = 0,
    val osVersion: String = "",
    val manufacturer: String? = null,
    val model: String? = null,
    val supportedAbis: List<String> = emptyList(),
    val webViewVersion: String? = null,
) {
    fun toJsonObject(): JSONObject =
        JSONObject().apply {
            put("appVersion", appVersion)
            put("buildNumber", buildNumber)
            put("androidSdk", androidSdk)
            put("osVersion", osVersion)
            manufacturer?.let { put("manufacturer", it) }
            model?.let { put("model", it) }
            if (supportedAbis.isNotEmpty()) {
                put("supportedAbis", JSONArray(supportedAbis))
            }
            webViewVersion?.let { put("webViewVersion", it) }
        }

    companion object {
        fun fromJsonObject(json: JSONObject): DiagnosticEnvironment {
            val abis = mutableListOf<String>()
            val abisArray = json.optJSONArray("supportedAbis")
            if (abisArray != null) {
                for (i in 0 until abisArray.length()) {
                    abis.add(abisArray.optString(i))
                }
            }
            return DiagnosticEnvironment(
                appVersion = json.optString("appVersion"),
                buildNumber = json.optString("buildNumber"),
                androidSdk = json.optInt("androidSdk"),
                osVersion = json.optString("osVersion"),
                manufacturer = if (json.has("manufacturer")) json.optString("manufacturer") else null,
                model = if (json.has("model")) json.optString("model") else null,
                supportedAbis = abis,
                webViewVersion = if (json.has("webViewVersion")) json.optString("webViewVersion") else null,
            )
        }
    }
}

data class DiagnosticBreadcrumb(
    val timestamp: Long,
    val category: String, // "OVERLAY", "AD_FILTER", "WEBVIEW", "SYSTEM"
    val event: String,
    val details: Map<String, String> = emptyMap(),
) {
    fun toJsonObject(): JSONObject =
        JSONObject().apply {
            put("timestamp", timestamp)
            put("category", category)
            put("event", event)
            if (details.isNotEmpty()) {
                val detailsObj = JSONObject()
                details.forEach { (k, v) -> detailsObj.put(k, v) }
                put("details", detailsObj)
            }
        }

    companion object {
        fun fromJsonObject(json: JSONObject): DiagnosticBreadcrumb {
            val detailsMap = mutableMapOf<String, String>()
            val detailsObj = json.optJSONObject("details")
            if (detailsObj != null) {
                detailsObj.keys().forEach { key ->
                    detailsMap[key] = detailsObj.optString(key)
                }
            }
            return DiagnosticBreadcrumb(
                timestamp = json.optLong("timestamp"),
                category = json.optString("category", "SYSTEM"),
                event = json.optString("event"),
                details = detailsMap,
            )
        }
    }
}

enum class AdFilterAction {
    BLOCKED,
    ALLOWED_BY_EXCEPTION,
    PASSED,
}

data class AdFilterTraceEvent(
    val timestamp: Long,
    val mainFrameHost: String,
    val resourceHost: String,
    val resourceType: String,
    val action: AdFilterAction,
    val matchedRule: String? = null,
    val rulePackId: String? = null,
) {
    fun toJsonObject(): JSONObject =
        JSONObject().apply {
            put("timestamp", timestamp)
            put("mainFrameHost", mainFrameHost)
            put("resourceHost", resourceHost)
            put("resourceType", resourceType)
            put("action", action.name)
            matchedRule?.let { put("matchedRule", it) }
            rulePackId?.let { put("rulePackId", it) }
        }

    companion object {
        fun fromJsonObject(json: JSONObject): AdFilterTraceEvent =
            AdFilterTraceEvent(
                timestamp = json.optLong("timestamp"),
                mainFrameHost = json.optString("mainFrameHost"),
                resourceHost = json.optString("resourceHost"),
                resourceType = json.optString("resourceType"),
                action =
                    try {
                        AdFilterAction.valueOf(json.optString("action"))
                    } catch (_: Exception) {
                        AdFilterAction.BLOCKED
                    },
                matchedRule = if (json.has("matchedRule")) json.optString("matchedRule") else null,
                rulePackId = if (json.has("rulePackId")) json.optString("rulePackId") else null,
            )
    }
}

data class DiagnosticRecord(
    val timestamp: Long,
    val kind: DiagnosticFailureKind,
    val errorType: String,
    val message: String,
    val stackTrace: String = "",
    val environment: DiagnosticEnvironment,
    val breadcrumbs: List<DiagnosticBreadcrumb> = emptyList(),
    val adFilterTraces: List<AdFilterTraceEvent> = emptyList(),
) {
    fun toJsonString(): String {
        val root =
            JSONObject().apply {
                put("timestamp", timestamp)
                put("kind", kind.name)
                put("errorType", errorType)
                put("message", message)
                put("stackTrace", stackTrace)
                put("environment", environment.toJsonObject())

                val breadcrumbsArray = JSONArray()
                breadcrumbs.forEach { breadcrumbsArray.put(it.toJsonObject()) }
                put("breadcrumbs", breadcrumbsArray)

                val adTracesArray = JSONArray()
                adFilterTraces.forEach { adTracesArray.put(it.toJsonObject()) }
                put("adFilterTraces", adTracesArray)
            }
        return root.toString()
    }

    companion object {
        fun fromJsonString(raw: String): DiagnosticRecord {
            val root = JSONObject(raw)
            val kindEnum =
                try {
                    DiagnosticFailureKind.valueOf(root.optString("kind"))
                } catch (_: Exception) {
                    DiagnosticFailureKind.SYSTEM_ERROR
                }

            val env =
                root.optJSONObject("environment")?.let {
                    DiagnosticEnvironment.fromJsonObject(it)
                } ?: DiagnosticEnvironment()

            val breadcrumbs = mutableListOf<DiagnosticBreadcrumb>()
            val bcArray = root.optJSONArray("breadcrumbs")
            if (bcArray != null) {
                for (i in 0 until bcArray.length()) {
                    bcArray.optJSONObject(i)?.let { breadcrumbs.add(DiagnosticBreadcrumb.fromJsonObject(it)) }
                }
            }

            val adTraces = mutableListOf<AdFilterTraceEvent>()
            val atArray = root.optJSONArray("adFilterTraces")
            if (atArray != null) {
                for (i in 0 until atArray.length()) {
                    atArray.optJSONObject(i)?.let { adTraces.add(AdFilterTraceEvent.fromJsonObject(it)) }
                }
            }

            return DiagnosticRecord(
                timestamp = root.optLong("timestamp"),
                kind = kindEnum,
                errorType = root.optString("errorType"),
                message = root.optString("message"),
                stackTrace = root.optString("stackTrace"),
                environment = env,
                breadcrumbs = breadcrumbs,
                adFilterTraces = adTraces,
            )
        }
    }
}
