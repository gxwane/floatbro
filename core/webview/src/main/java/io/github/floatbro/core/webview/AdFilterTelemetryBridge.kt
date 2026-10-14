package io.github.floatbro.core.webview

import android.content.Context
import io.github.edsuns.adfilter.AdFilterResourceEvent
import io.github.edsuns.adfilter.AdFilterTelemetryListener
import io.github.floatbro.core.common.diagnostics.AdFilterAction
import io.github.floatbro.core.common.diagnostics.AdFilterTraceEvent
import io.github.floatbro.core.common.diagnostics.LocalDiagnostics
import java.net.URI

class AdFilterTelemetryBridge(
    private val context: Context,
) : AdFilterTelemetryListener {
    override fun onResourceEvaluated(event: AdFilterResourceEvent) {
        val action =
            when {
                event.isBlocked -> AdFilterAction.BLOCKED
                event.isException -> AdFilterAction.ALLOWED_BY_EXCEPTION
                else -> AdFilterAction.PASSED
            }
        val mainHost =
            try {
                URI(event.mainFrameUrl).host ?: ""
            } catch (_: Exception) {
                ""
            }
        val resHost =
            try {
                URI(event.resourceUrl).host ?: ""
            } catch (_: Exception) {
                ""
            }

        val trace =
            AdFilterTraceEvent(
                timestamp = System.currentTimeMillis(),
                mainFrameHost = mainHost,
                resourceHost = resHost,
                resourceType = event.resourceType,
                action = action,
                matchedRule = event.matchedRule,
            )
        LocalDiagnostics.getInstance(context.applicationContext).recordAdFilterTrace(trace)
    }
}
