package io.github.floatbro.core.common.diagnostics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `append writes records in json lines and readAll parses them in order`() =
        runTest {
            val logFile = tempFolder.newFile("test_diagnostics.jsonl")
            val store = DiagnosticStore(logFile, maxBytes = 1024 * 1024L)

            val env = DiagnosticEnvironment(appVersion = "0.1.4", buildNumber = "10005", androidSdk = 34, osVersion = "14")
            val record1 =
                DiagnosticRecord(
                    timestamp = 1000L,
                    kind = DiagnosticFailureKind.SYSTEM_ERROR,
                    errorType = "IOException",
                    message = "Network error",
                    environment = env,
                )
            val record2 =
                DiagnosticRecord(
                    timestamp = 2000L,
                    kind = DiagnosticFailureKind.CRASH_UNCAUGHT,
                    errorType = "NullPointerException",
                    message = "Null reference",
                    environment = env,
                )

            store.append(record1)
            store.append(record2)

            val records = store.readAll()
            assertEquals(2, records.size)
            // readAll returns records sorted descending by timestamp
            assertEquals(2000L, records[0].timestamp)
            assertEquals("Null reference", records[0].message)
            assertEquals(1000L, records[1].timestamp)
            assertEquals("Network error", records[1].message)
        }

    @Test
    fun `clear deletes existing logs`() =
        runTest {
            val logFile = tempFolder.newFile("test_diagnostics_clear.jsonl")
            val store = DiagnosticStore(logFile)

            val record =
                DiagnosticRecord(
                    timestamp = 1000L,
                    kind = DiagnosticFailureKind.SYSTEM_ERROR,
                    errorType = "TestError",
                    message = "Clear me",
                    environment = DiagnosticEnvironment(),
                )
            store.append(record)
            assertTrue(store.storageBytes() > 0)

            store.clear()
            assertEquals(0L, store.storageBytes())
            assertTrue(store.readAll().isEmpty())
        }

    @Test
    fun `prune maintains size under max threshold`() =
        runTest {
            val logFile = tempFolder.newFile("test_prune.jsonl")
            // Set very small maxBytes of 500 bytes to trigger prune quickly
            val store = DiagnosticStore(logFile, maxBytes = 500L, retentionMillis = 100000L)

            for (i in 1..20) {
                val record =
                    DiagnosticRecord(
                        timestamp = i * 1000L,
                        kind = DiagnosticFailureKind.SYSTEM_ERROR,
                        errorType = "Error$i",
                        message = "Log message number $i",
                        environment = DiagnosticEnvironment(),
                    )
                store.append(record)
            }

            val remainingRecords = store.readAll()
            // Must be pruned so only the latest fit within ~500 bytes
            assertTrue(remainingRecords.size in 1..8)
            // Latest records must be preserved
            assertEquals("Log message number 20", remainingRecords.first().message)
        }
}
