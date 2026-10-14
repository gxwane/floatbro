package io.github.floatbro.diagnostics

import io.github.floatbro.core.common.diagnostics.LocalDiagnostics

class FloatBroCrashHandler(
    private val diagnostics: LocalDiagnostics,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            diagnostics.emergencySyncFlushCrash(thread, throwable)
        } catch (_: Exception) {
            // Guard against any crash in emergency handler to ensure defaultHandler runs
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
