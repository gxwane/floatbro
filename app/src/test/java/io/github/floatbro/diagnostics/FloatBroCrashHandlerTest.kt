package io.github.floatbro.diagnostics

import io.github.floatbro.core.common.diagnostics.DiagnosticEnvironment
import io.github.floatbro.core.common.diagnostics.DiagnosticFailureKind
import io.github.floatbro.core.common.diagnostics.LocalDiagnostics
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FloatBroCrashHandlerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `crash handler flushes crash record and delegates to default handler`() =
        runTest {
            val rootDir = tempFolder.newFolder("crash_test")
            val diagnostics =
                LocalDiagnostics(
                    directoryProvider = { rootDir },
                    environmentLoader = { DiagnosticEnvironment(appVersion = "0.1.4", buildNumber = "10005", androidSdk = 34, osVersion = "14") },
                )
            diagnostics.initialize()

            var defaultHandlerCalled = false
            val mockDefaultHandler =
                Thread.UncaughtExceptionHandler { _, _ ->
                    defaultHandlerCalled = true
                }

            val crashHandler =
                FloatBroCrashHandler(
                    diagnostics = diagnostics,
                    defaultHandler = mockDefaultHandler,
                )

            val crashException = RuntimeException("Fatal crash test")
            crashHandler.uncaughtException(Thread.currentThread(), crashException)

            assertTrue("Default handler must be called", defaultHandlerCalled)

            val records = diagnostics.readFailures()
            assertEquals(1, records.size)
            assertEquals(DiagnosticFailureKind.CRASH_UNCAUGHT, records.first().kind)
            assertTrue(records.first().message.contains("Fatal crash test"))
        }
}
