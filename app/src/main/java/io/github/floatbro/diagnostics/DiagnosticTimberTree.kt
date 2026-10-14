package io.github.floatbro.diagnostics

import android.util.Log
import io.github.floatbro.BuildConfig
import io.github.floatbro.core.common.diagnostics.DiagnosticBreadcrumb
import io.github.floatbro.core.common.diagnostics.DiagnosticFailureKind
import io.github.floatbro.core.common.diagnostics.LocalDiagnostics
import timber.log.Timber

class DiagnosticTimberTree(
    private val diagnostics: LocalDiagnostics,
) : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        // In debug builds, preserve direct logcat visibility
        if (BuildConfig.DEBUG) {
            val safeTag = tag ?: "FloatBro"
            val safeMsg = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
            Log.println(priority, safeTag, safeMsg)
        }

        val category =
            when {
                tag?.contains("AdFilter", ignoreCase = true) == true -> "AD_FILTER"
                tag?.contains("Floating", ignoreCase = true) == true || tag?.contains("Bubble", ignoreCase = true) == true -> "OVERLAY"
                tag?.contains("WebView", ignoreCase = true) == true -> "WEBVIEW"
                tag?.contains("Service", ignoreCase = true) == true -> "SERVICE"
                else -> "SYSTEM"
            }

        val eventText = if (!tag.isNullOrBlank()) "[$tag] $message" else message

        // Add to in-memory system breadcrumbs
        diagnostics.addBreadcrumb(
            DiagnosticBreadcrumb(
                timestamp = System.currentTimeMillis(),
                category = category,
                event = eventText,
            ),
        )

        // Serious errors (WARN with exception or ERROR) are recorded as persistent failure records
        if (priority >= Log.ERROR) {
            diagnostics.recordFailure(
                kind = DiagnosticFailureKind.SYSTEM_ERROR,
                error = eventText,
                throwable = t,
            )
        }
    }
}
