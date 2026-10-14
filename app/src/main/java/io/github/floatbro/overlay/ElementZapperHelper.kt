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

package io.github.floatbro.overlay

/**
 * Pure helper for Element Zapper rule construction, ceiling validation, and JSON decoding.
 */
object ElementZapperHelper {
    private val DESTRUCTIVE_BARE_TAGS =
        setOf(
            "div",
            "span",
            "p",
            "a",
            "img",
            "ul",
            "ol",
            "li",
            "table",
            "tr",
            "td",
            "th",
            "header",
            "footer",
            "main",
            "nav",
            "section",
            "article",
            "aside",
            "form",
            "body",
            "html",
        )

    /**
     * Normalize host to handle common web subdomains (e.g. www.* or cn.bing.com)
     * so that rules seamlessly cover sibling subdomains via parent domain fallback.
     */
    fun normalizeHost(host: String): String {
        val clean = host.trim().lowercase()
        if (clean.startsWith("www.")) {
            return clean.removePrefix("www.")
        }
        if (clean.endsWith(".bing.com") || clean == "cn.bing.com") {
            return "bing.com"
        }
        return clean
    }

    /**
     * Build standard ABP rule binding to target hostname: hostname##selector
     * Enforces security guards against newlines, CSS injection braces, and destructive bare tags.
     */
    fun buildRule(
        host: String,
        selector: String,
    ): String? {
        val cleanHost = normalizeHost(host)
        val cleanSelector = selector.trim()
        if (cleanHost.isBlank() || cleanSelector.isBlank()) return null
        if (cleanHost.contains(" ") || cleanHost.contains("\n") || cleanHost.contains("\r") || cleanHost.contains("#")) {
            return null
        }
        // Prevent newline injection or CSS brace escape attacks
        if (cleanSelector.contains('\r') ||
            cleanSelector.contains('\n') ||
            cleanSelector.contains('{') ||
            cleanSelector.contains('}')
        ) {
            return null
        }
        // Prevent bare destructive tags from destroying full page layout
        if (DESTRUCTIVE_BARE_TAGS.contains(cleanSelector.lowercase())) {
            return null
        }
        return "$cleanHost##$cleanSelector"
    }

    /**
     * Validate whether an element can safely expand to its parent without hitting ceilings.
     */
    fun canExpandParent(
        parentTag: String?,
        parentId: String?,
    ): Boolean {
        if (parentTag.isNullOrBlank()) return false
        val pTag = parentTag.trim().uppercase()
        if (pTag == "BODY" || pTag == "HTML" || pTag == "HEAD") return false
        val pId = parentId?.trim()?.lowercase()
        if (pId == "root" || pId == "app" || pId == "__next") return false
        return true
    }

    /**
     * Unescape raw string response returned by WebView evaluateJavascript.
     */
    fun unescapeJson(raw: String?): String {
        if (raw.isNullOrBlank()) return "{}"
        var s = raw.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s =
                s
                    .substring(1, s.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
        }
        return if (s.isBlank()) "{}" else s
    }
}
