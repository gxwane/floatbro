/*
 * Copyright (c) 2026 FloatBro Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.floatbro.core.webview.security

import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Robust target URL extractor and W3C Referrer policy handler.
 * Decoupled, defensive against ReDoS, and strictly bounds parsing recursion depth.
 * Implemented using pure Kotlin string parsing to guarantee seamless pure JVM execution and zero Android stub issues.
 */
object UrlTargetUnwrapper {
    const val MAX_URL_LENGTH = 2048
    const val MAX_RECURSION_DEPTH = 2

    private val TARGET_PARAM_KEYS =
        setOf(
            "target",
            "url",
            "dest",
            "destination",
            "redirect",
            "redirect_url",
            "redirect_uri",
            "to",
            "link",
            "jump",
            "goto",
            "continue",
            "next",
        )

    /**
     * Attempts to extract a genuine destination URL from query parameters of an ad / trampoline URL.
     *
     * @param rawUrl The candidate redirection or trampoline URL
     * @return Decoded valid HTTP/HTTPS destination URL, or null if no valid target found
     */
    fun extractTargetUrl(rawUrl: String): String? = extractInternal(rawUrl.trim(), depth = 0)

    private fun extractInternal(
        url: String,
        depth: Int,
    ): String? {
        if (depth > MAX_RECURSION_DEPTH || url.length > MAX_URL_LENGTH) {
            return null
        }

        val qIdx = url.indexOf('?')
        if (qIdx == -1 || qIdx == url.length - 1) {
            return null
        }

        val hashIdx = url.indexOf('#', qIdx)
        val query = if (hashIdx == -1) url.substring(qIdx + 1) else url.substring(qIdx + 1, hashIdx)

        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eqIdx = pair.indexOf('=')
            val key = if (eqIdx == -1) pair else pair.substring(0, eqIdx)
            if (TARGET_PARAM_KEYS.contains(key.lowercase())) {
                val rawVal = if (eqIdx == -1) "" else pair.substring(eqIdx + 1)
                val decoded = safeUrlDecode(rawVal) ?: continue
                val candidate = decoded.trim()

                if (isValidWebUrl(candidate)) {
                    // If candidate itself contains an inner redirect trampoline, try unwrapping deeper up to MAX_RECURSION_DEPTH
                    val deeper = extractInternal(candidate, depth + 1)
                    return deeper ?: candidate
                }
            }
        }

        return null
    }

    /**
     * Safely decodes a URL string with defensive exception handling and double-encoding handling.
     */
    fun safeUrlDecode(input: String): String? {
        if (input.isBlank() || input.length > MAX_URL_LENGTH) return null
        return try {
            var decoded = URLDecoder.decode(input, StandardCharsets.UTF_8.name())
            // Detect double-encoded URLs (e.g. containing %2F or %3A)
            if (decoded.contains("%2F", ignoreCase = true) || decoded.contains("%3A", ignoreCase = true)) {
                try {
                    decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    // Fall back to once-decoded string
                }
            }
            decoded
        } catch (e: Exception) {
            Timber.v("safeUrlDecode failed: %s", e.message)
            null
        }
    }

    /**
     * Verifies if a string is a standard, well-formed HTTP/HTTPS URL.
     */
    fun isValidWebUrl(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return false
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return false

        val afterScheme = trimmed.substring(schemeEnd + 3)
        val hostEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#', ':'))
        val host = if (hostEnd == -1) afterScheme else afterScheme.substring(0, hostEnd)
        return host.isNotBlank() && host.contains(".") && !host.contains(" ")
    }

    /**
     * Constructs a safe Referer header strictly adhering to the W3C strict-origin-when-cross-origin policy:
     * 1. If downgrading from HTTPS to HTTP, returns null (never leak Referer over cleartext).
     * 2. If cross-origin, truncates to Origin only (e.g. "https://example.com/").
     * 3. If same-origin, preserves the full current document URL.
     */
    fun buildSafeReferer(
        currentDocUrl: String?,
        targetUrl: String,
    ): String? {
        if (currentDocUrl.isNullOrBlank()) return null
        if (!isValidWebUrl(currentDocUrl) || !isValidWebUrl(targetUrl)) return null

        val currentOrigin = extractOrigin(currentDocUrl) ?: return null
        val targetOrigin = extractOrigin(targetUrl) ?: return null

        // HTTPS -> HTTP downgrade: strict privacy block
        if (currentOrigin.scheme == "https" && targetOrigin.scheme == "http") {
            return null
        }

        val isSameOrigin =
            currentOrigin.scheme == targetOrigin.scheme &&
                currentOrigin.host == targetOrigin.host &&
                currentOrigin.port == targetOrigin.port

        return if (isSameOrigin) {
            // Same origin: send full path
            currentDocUrl
        } else {
            // Cross origin: send Origin only (scheme://authority/)
            val authority =
                if (currentOrigin.port != defaultPort(currentOrigin.scheme)) {
                    "${currentOrigin.host}:${currentOrigin.port}"
                } else {
                    currentOrigin.host
                }
            "${currentOrigin.scheme}://$authority/"
        }
    }

    internal data class OriginInfo(
        val scheme: String,
        val host: String,
        val port: Int,
    )

    internal fun extractOrigin(url: String): OriginInfo? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        val afterScheme = trimmed.substring(schemeEnd + 3)
        val pathEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#'))
        val hostAndPort = if (pathEnd == -1) afterScheme else afterScheme.substring(0, pathEnd)
        if (hostAndPort.isBlank()) return null

        val colonIdx = hostAndPort.indexOf(':')
        val host = if (colonIdx == -1) hostAndPort.lowercase() else hostAndPort.substring(0, colonIdx).lowercase()
        val port =
            if (colonIdx != -1) {
                hostAndPort.substring(colonIdx + 1).toIntOrNull() ?: defaultPort(scheme)
            } else {
                defaultPort(scheme)
            }
        return OriginInfo(scheme, host, port)
    }

    private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
}
