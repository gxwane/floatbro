package io.github.floatbro.core.common.diagnostics

import java.net.URI

object DiagnosticSanitizer {
    private const val REDACTED = "<redacted>"
    private const val SEARCH_QUERY = "<search-query>"
    private const val APP_STORAGE = "<app-storage>"

    private val ALLOWED_STRUCTURAL_QUERY_KEYS =
        setOf(
            "page",
            "p",
            "tab",
            "size",
            "limit",
            "offset",
            "sort",
            "order",
            "lang",
            "type",
            "format",
            "safe",
        )

    private val SENSITIVE_QUERY_KEYS =
        setOf(
            "token",
            "access_token",
            "refresh_token",
            "auth",
            "authorization",
            "session",
            "cookie",
            "sessdata",
            "bili_jct",
            "csrf",
            "sig",
            "signature",
            "password",
            "passwd",
            "code",
            "secret",
            "key",
            "api_key",
            "uid",
            "mid",
            "phone",
            "email",
        )

    private val SEARCH_QUERY_KEYS =
        setOf(
            "q",
            "query",
            "wd",
            "word",
            "keyword",
            "search_word",
            "text",
        )

    private val ANDROID_PATH_REGEX = Regex("""/(?:data/(?:user/\d+|data)|storage/emulated/\d+)/[^\s:]+""")

    fun sanitizeText(
        input: String?,
        maxLength: Int = 4096,
    ): String {
        if (input.isNullOrEmpty()) return ""
        var text = input.replace(ANDROID_PATH_REGEX, APP_STORAGE)
        // Also strip raw token= patterns in free text
        text =
            text.replace(Regex("""\b(token|auth|password|secret|code)=([^\s&]+)""", RegexOption.IGNORE_CASE)) {
                "${it.groupValues[1]}=$REDACTED"
            }
        if (text.length > maxLength) {
            text = text.substring(0, maxLength) + " [truncated]"
        }
        return text
    }

    fun sanitizeUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        return try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return "<invalid-url>"
            val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            val path = uri.rawPath ?: ""
            val rawQuery = uri.rawQuery

            val sanitizedQuery =
                if (!rawQuery.isNullOrBlank()) {
                    rawQuery.split("&").joinToString("&") { param ->
                        val parts = param.split("=", limit = 2)
                        val key = parts[0]
                        val lowerKey = key.lowercase()
                        when {
                            SEARCH_QUERY_KEYS.contains(lowerKey) -> "$key=$SEARCH_QUERY"
                            SENSITIVE_QUERY_KEYS.contains(lowerKey) -> "$key=$REDACTED"
                            ALLOWED_STRUCTURAL_QUERY_KEYS.contains(lowerKey) -> if (parts.size == 2) "$key=${parts[1]}" else key
                            else -> if (parts.size == 2) "$key=$REDACTED" else key
                        }
                    }
                } else {
                    null
                }

            val queryPart = if (sanitizedQuery != null) "?$sanitizedQuery" else ""
            "$scheme://$host$port$path$queryPart"
        } catch (_: Exception) {
            "<malformed-url>"
        }
    }
}
