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

package io.github.floatbro.browser

import android.net.Uri

object BrowserUrlResolver {

    /**
     * Resolves raw text into a fully qualified URL, applying domain heuristics
     * or routing to the configured search engine.
     */
    fun resolve(
        input: String,
        engine: SearchEngine = SearchEngine.BING,
        customSearchUrl: String? = null
    ): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.startsWith("about:") || trimmed.startsWith("javascript:") || trimmed.startsWith("file://")) {
            return trimmed
        }
        if (trimmed.startsWith("www.")) {
            return "https://$trimmed"
        }

        // Domain heuristic: contains dot, no spaces, not starting/ending with dot
        val isDomainLike = trimmed.contains(".") &&
            !trimmed.contains(" ") &&
            !trimmed.endsWith(".") &&
            !trimmed.startsWith(".") &&
            trimmed.indexOf(".") < trimmed.length - 1

        if (isDomainLike) {
            return "https://$trimmed"
        }

        val encodedQuery = try {
            java.net.URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }
        val template = if (engine == SearchEngine.CUSTOM && !customSearchUrl.isNullOrBlank()) {
            if (customSearchUrl.contains("%s")) {
                customSearchUrl
            } else {
                "${customSearchUrl}%s"
            }
        } else {
            engine.searchUrlTemplate
        }

        return String.format(template, encodedQuery)
    }
}
