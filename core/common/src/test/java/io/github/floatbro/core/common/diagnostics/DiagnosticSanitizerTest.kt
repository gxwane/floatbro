package io.github.floatbro.core.common.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSanitizerTest {
    @Test
    fun `sanitizeUrl preserves scheme host and allowed parameters while redacting sensitive tokens`() {
        val rawUrl = "https://m.bilibili.com/video/BV1xx411c7mD?page=2&token=secret123&auth=my_auth_token&tab=all"
        val sanitized = DiagnosticSanitizer.sanitizeUrl(rawUrl)

        assertTrue(sanitized.contains("https://m.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(sanitized.contains("page=2"))
        assertTrue(sanitized.contains("tab=all"))
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("my_auth_token"))
        assertTrue(sanitized.contains("token=<redacted>"))
        assertTrue(sanitized.contains("auth=<redacted>"))
    }

    @Test
    fun `sanitizeUrl cleans search query parameters`() {
        val rawUrl = "https://www.google.com/search?q=private+medical+symptom&safe=active"
        val sanitized = DiagnosticSanitizer.sanitizeUrl(rawUrl)

        assertTrue(sanitized.contains("https://www.google.com/search"))
        assertFalse(sanitized.contains("private+medical+symptom"))
        assertTrue(sanitized.contains("q=<search-query>"))
    }

    @Test
    fun `sanitizeText replaces private android paths and sensitive credentials`() {
        val message = "Failed to load cache from /data/user/0/io.github.floatbro/databases/history.db with token=xyz789"
        val sanitized = DiagnosticSanitizer.sanitizeText(message)

        assertFalse(sanitized.contains("/data/user/0/io.github.floatbro"))
        assertTrue(sanitized.contains("<app-storage>"))
        assertFalse(sanitized.contains("xyz789"))
    }

    @Test
    fun `sanitizeText truncates excessively long strings`() {
        val longString = "A".repeat(5000)
        val sanitized = DiagnosticSanitizer.sanitizeText(longString, maxLength = 200)

        assertTrue(sanitized.length <= 250)
        assertTrue(sanitized.endsWith("[truncated]"))
    }
}
