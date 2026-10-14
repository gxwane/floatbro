package io.github.floatbro.core.common.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DiagnosticStore(
    private val logFile: File,
    /** Max storage limit (default 2 MiB) */
    private val maxBytes: Long = 2 * 1024 * 1024L,
    /** Retention window (default 7 days) */
    private val retentionMillis: Long = 7 * 24 * 3600 * 1000L,
) {
    private val mutex = Mutex()

    suspend fun append(record: DiagnosticRecord) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                appendInternal(record)
            }
        }

    /**
     * Emergency direct synchronous write for uncaught crash handler.
     * ZERO coroutines, direct file descriptor flush.
     */
    fun emergencyWrite(record: DiagnosticRecord) {
        synchronized(this) {
            try {
                logFile.parentFile?.mkdirs()
                FileOutputStream(logFile, true).use { fos ->
                    val bytes = (record.toJsonString() + "\n").toByteArray(Charsets.UTF_8)
                    fos.write(bytes)
                    fos.flush()
                    try {
                        fos.fd.sync()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun readAll(): List<DiagnosticRecord> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!logFile.exists()) return@withContext emptyList()
                val records = mutableListOf<DiagnosticRecord>()
                logFile.forEachLine { line ->
                    if (line.isNotBlank()) {
                        try {
                            records.add(DiagnosticRecord.fromJsonString(line))
                        } catch (_: Exception) {
                        }
                    }
                }
                records.sortedByDescending { it.timestamp }
            }
        }

    suspend fun clear() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (logFile.exists()) {
                    logFile.delete()
                }
            }
        }

    suspend fun storageBytes(): Long =
        withContext(Dispatchers.IO) {
            if (logFile.exists()) logFile.length() else 0L
        }

    private fun appendInternal(record: DiagnosticRecord) {
        logFile.parentFile?.mkdirs()
        FileOutputStream(logFile, true).bufferedWriter().use { writer ->
            writer.write(record.toJsonString())
            writer.newLine()
        }
        if (logFile.length() > maxBytes) {
            pruneInternal()
        }
    }

    private fun pruneInternal() {
        if (!logFile.exists()) return
        val cutoff = System.currentTimeMillis() - retentionMillis
        val records = loadValidRecords(cutoff)
        val tempFile = File(logFile.parentFile, "${logFile.name}.tmp")
        val targetBytes = (maxBytes * 0.8).toLong()

        try {
            writePrunedRecords(tempFile, records, targetBytes)
            if (tempFile.exists()) {
                if (logFile.exists()) logFile.delete()
                tempFile.renameTo(logFile)
            }
        } catch (_: Exception) {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun loadValidRecords(cutoff: Long): List<DiagnosticRecord> {
        val records = mutableListOf<DiagnosticRecord>()
        logFile.forEachLine { line ->
            if (line.isNotBlank()) {
                val record = runCatching { DiagnosticRecord.fromJsonString(line) }.getOrNull()
                if (record != null && record.timestamp >= cutoff) {
                    records.add(record)
                }
            }
        }
        records.sortBy { it.timestamp }
        return records
    }

    private fun writePrunedRecords(
        tempFile: File,
        records: List<DiagnosticRecord>,
        targetBytes: Long,
    ) {
        val keepList = mutableListOf<DiagnosticRecord>()
        var accumulated = 0L

        for (rec in records.reversed()) {
            val bytes = (rec.toJsonString() + "\n").toByteArray(Charsets.UTF_8).size
            if (accumulated + bytes <= targetBytes) {
                keepList.add(0, rec)
                accumulated += bytes
            } else {
                break
            }
        }

        tempFile.bufferedWriter().use { writer ->
            for (rec in keepList) {
                writer.write(rec.toJsonString())
                writer.newLine()
            }
        }
    }
}
