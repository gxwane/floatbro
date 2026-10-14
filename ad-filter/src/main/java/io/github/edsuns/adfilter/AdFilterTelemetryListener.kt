package io.github.edsuns.adfilter

data class AdFilterResourceEvent(
    val mainFrameUrl: String,
    val resourceUrl: String,
    val resourceType: String,
    val isBlocked: Boolean,
    val isException: Boolean,
    val matchedRule: String?,
)

/**
 * Decoupled SPI listener for AdFilter telemetry and diagnostic events.
 * Enables zero-dependency observability and rule evaluation tracking.
 */
interface AdFilterTelemetryListener {
    fun onResourceEvaluated(event: AdFilterResourceEvent)
}
