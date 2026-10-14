package io.github.floatbro.core.common.diagnostics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalDiagnosticsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `dual channel buffer isolates system breadcrumbs from adfilter traces`() =
        runTest {
            val rootDir = tempFolder.newFolder("diagnostics_test")
            val diagnostics =
                LocalDiagnostics(
                    directoryProvider = { rootDir },
                    environmentLoader = {
                        DiagnosticEnvironment(appVersion = "0.1.4", buildNumber = "10005", androidSdk = 34, osVersion = "14")
                    },
                )
            diagnostics.initialize()
            diagnostics.setEnabled(true, clearExisting = true)

            // Add 60 system breadcrumbs (capacity is 50)
            for (i in 1..60) {
                diagnostics.addBreadcrumb(
                    DiagnosticBreadcrumb(timestamp = i.toLong(), category = "SYSTEM", event = "Event $i"),
                )
            }

            // Add 150 ad filter traces (capacity is 100)
            for (i in 1..150) {
                diagnostics.recordAdFilterTrace(
                    AdFilterTraceEvent(
                        timestamp = i.toLong(),
                        mainFrameHost = "example.com",
                        resourceHost = "ad$i.com",
                        resourceType = "SCRIPT",
                        action = AdFilterAction.BLOCKED,
                        matchedRule = "||ad$i.com^",
                    ),
                )
            }

            // System breadcrumbs must retain the latest 50
            val breadcrumbsSnapshot = diagnostics.getSystemBreadcrumbsSnapshot()
            assertEquals(50, breadcrumbsSnapshot.size)
            assertEquals("Event 60", breadcrumbsSnapshot.last().event)

            // AdFilter traces must retain the latest 100
            val adTracesSnapshot = diagnostics.getAdFilterTracesSnapshot()
            assertEquals(100, adTracesSnapshot.size)
            assertEquals("ad150.com", adTracesSnapshot.last().resourceHost)
        }

    @Test
    fun `deduplicator prevents logging identical errors within 3 second window`() =
        runTest {
            val rootDir = tempFolder.newFolder("dedup_test")
            var fakeTime = 1000L
            val diagnostics =
                LocalDiagnostics(
                    directoryProvider = { rootDir },
                    environmentLoader = { DiagnosticEnvironment() },
                    timeProvider = { fakeTime },
                )
            diagnostics.initialize()
            diagnostics.setEnabled(true, clearExisting = true)

            // Record first error
            diagnostics.recordFailure(
                kind = DiagnosticFailureKind.SYSTEM_ERROR,
                error = "Frequent Database Lock",
                throwable = RuntimeException("Lock conflict"),
            )

            // Record duplicate immediately (same fakeTime)
            diagnostics.recordFailure(
                kind = DiagnosticFailureKind.SYSTEM_ERROR,
                error = "Frequent Database Lock",
                throwable = RuntimeException("Lock conflict"),
            )

            var records = diagnostics.readFailures()
            assertEquals(1, records.size)

            // Advance fakeTime past 3 seconds (3001 ms)
            fakeTime += 3001L

            diagnostics.recordFailure(
                kind = DiagnosticFailureKind.SYSTEM_ERROR,
                error = "Frequent Database Lock",
                throwable = RuntimeException("Lock conflict"),
            )

            records = diagnostics.readFailures()
            assertEquals(2, records.size)
        }

    @Test
    fun `formatReport outputs clean markdown and respects includeDeviceInfo toggle`() =
        runTest {
            val env =
                DiagnosticEnvironment(
                    appVersion = "0.1.4",
                    buildNumber = "10005",
                    androidSdk = 34,
                    osVersion = "14",
                    manufacturer = "Xiaomi",
                    model = "Mi 14",
                    webViewVersion = "124.0.0.0",
                )
            val record =
                DiagnosticRecord(
                    timestamp = 1700000000000L,
                    kind = DiagnosticFailureKind.CRASH_UNCAUGHT,
                    errorType = "NullPointerException",
                    message = "Test message with secret=xyz",
                    stackTrace = "at io.github.floatbro.Test.run(Test.kt:10)",
                    environment = env,
                    breadcrumbs =
                        listOf(
                            DiagnosticBreadcrumb(timestamp = 1700000000000L, category = "OVERLAY", event = "Window created"),
                        ),
                )

            val reportWithoutDevice = LocalDiagnostics.formatReport(record, includeDeviceInfo = false)
            assertFalse(reportWithoutDevice.contains("Xiaomi"))
            assertFalse(reportWithoutDevice.contains("Mi 14"))
            assertTrue(reportWithoutDevice.contains("NullPointerException"))
            assertTrue(reportWithoutDevice.contains("Window created"))

            val reportWithDevice = LocalDiagnostics.formatReport(record, includeDeviceInfo = true)
            assertTrue(reportWithDevice.contains("Xiaomi"))
            assertTrue(reportWithDevice.contains("Mi 14"))
            assertTrue(reportWithDevice.contains("124.0.0.0"))
        }
}
