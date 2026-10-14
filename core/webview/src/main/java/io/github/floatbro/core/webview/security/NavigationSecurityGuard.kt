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
import java.util.concurrent.ConcurrentHashMap

/**
 * Kernel Navigation Security Guard.
 * Implements Public Suffix List (PSL) extraction, four-dimensional exemption matrix,
 * single-use atomic bypass tokens, and pagination-contextual hijack detection.
 */
object NavigationSecurityGuard {
    private const val PAGINATION_WINDOW_TTL_MS = 1500L
    private const val DEFAULT_BYPASS_TOKEN_TTL_MS = 10_000L

    @Volatile
    private var lastPaginationClickTime: Long = 0L

    // Flat atomic single-use bypass tokens: "$sourceEtldPlusOne->$targetEtldPlusOne" -> expiryMs
    private val bypassTokens = ConcurrentHashMap<String, Long>()

    // Compound public suffixes (multi-level TLDs)
    private val COMPOUND_PUBLIC_SUFFIXES =
        listOf(
            // China
            ".com.cn",
            ".net.cn",
            ".org.cn",
            ".edu.cn",
            ".gov.cn",
            ".mil.cn",
            // Hong Kong / Taiwan
            ".edu.hk",
            ".com.hk",
            ".org.hk",
            ".gov.hk",
            ".com.tw",
            ".org.tw",
            ".idv.tw",
            ".edu.tw",
            // Japan
            ".co.jp",
            ".ne.jp",
            ".or.jp",
            ".ac.jp",
            ".go.jp",
            // United Kingdom
            ".co.uk",
            ".org.uk",
            ".me.uk",
            ".ltd.uk",
            ".plc.uk",
            // Australia
            ".com.au",
            ".net.au",
            ".org.au",
            // Common shared hosting domains
            ".github.io",
            ".gitlab.io",
            ".workers.dev",
            ".pages.dev",
            ".vercel.app",
        )

    // Dimension 1: Search engine and shortlink hub root domains
    private val SEARCH_AND_HUB_DOMAINS =
        setOf(
            "baidu.com",
            "bing.com",
            "google.com",
            "sogou.com",
            "so.com",
            "sm.cn",
            "duckduckgo.com",
            "t.cn",
            "url.cn",
            "zhihu.com",
            "qq.com",
            "weibo.com",
            "bilibili.com",
        )

    // Dimension 2: OAuth 2.0 social authentication hosts
    private val OAUTH_AUTH_HOSTS =
        setOf(
            "open.weixin.qq.com",
            "graph.qq.com",
            "accounts.google.com",
            "appleid.apple.com",
            "passport.weibo.com",
            "account.aliyun.com",
        )

    // Dimension 3: Mobile payment gateway root domains
    private val PAYMENT_DOMAINS =
        setOf(
            "alipay.com",
            "tenpay.com",
            "unionpay.com",
            "paypal.com",
        )

    /**
     * Called by JS bridge when a user clicks on an identified pagination navigation element.
     */
    fun notifyPaginationClicked() {
        lastPaginationClickTime = System.currentTimeMillis()
        Timber.v("Pagination context click noted at %d", lastPaginationClickTime)
    }

    /**
     * Checks if a user action in a pagination context occurred recently (within 1500ms).
     */
    fun isPaginationContextActive(): Boolean = System.currentTimeMillis() - lastPaginationClickTime <= PAGINATION_WINDOW_TTL_MS

    /**
     * Issues a single-use bypass token valid for 10 seconds.
     */
    fun issueBypassToken(
        sourceHost: String,
        targetHost: String,
        durationMs: Long = DEFAULT_BYPASS_TOKEN_TTL_MS,
    ) {
        val sEtld = extractEtldPlusOne(sourceHost)
        val tEtld = extractEtldPlusOne(targetHost)
        if (sEtld.isBlank() || tEtld.isBlank()) return
        val key = "$sEtld->$tEtld"
        val expiry = System.currentTimeMillis() + durationMs
        bypassTokens[key] = expiry
        Timber.i("Issued bypass token: %s (valid for %d ms)", key, durationMs)
    }

    /**
     * Consumes a bypass token atomically (Single-Use: remove immediately on probe).
     */
    fun consumeBypassToken(
        sourceHost: String,
        targetHost: String,
    ): Boolean {
        val sEtld = extractEtldPlusOne(sourceHost)
        val tEtld = extractEtldPlusOne(targetHost)
        if (sEtld.isBlank() || tEtld.isBlank()) return false
        val key = "$sEtld->$tEtld"
        val expiry = bypassTokens.remove(key) ?: return false
        val isValid = System.currentTimeMillis() <= expiry
        if (isValid) {
            Timber.i("Consumed valid single-use bypass token for %s", key)
        } else {
            Timber.w("Expired bypass token for %s", key)
        }
        return isValid
    }

    /**
     * Evaluates the four-dimensional exemption matrix.
     * Returns true if the navigation is an exempted legitimate transition.
     */
    fun isExempted(
        currentDocUrl: String?,
        targetUrl: String,
    ): Boolean {
        val targetHost = extractHost(targetUrl) ?: return false
        val targetEtld = extractEtldPlusOne(targetHost)

        val currentHost = currentDocUrl?.let { extractHost(it) } ?: ""
        val currentEtld = if (currentHost.isNotBlank()) extractEtldPlusOne(currentHost) else ""

        // Same eTLD+1 is always internal navigation, exempt from cross-domain defense
        if (currentEtld.isNotBlank() && currentEtld.equals(targetEtld, ignoreCase = true)) {
            return true
        }

        // Dimension 4: Check and consume single-use bypass token
        if (currentHost.isNotBlank() && consumeBypassToken(currentHost, targetHost)) {
            return true
        }

        // Dimension 1: Search engine hubs & shortlinks
        if (SEARCH_AND_HUB_DOMAINS.contains(currentEtld.lowercase()) ||
            SEARCH_AND_HUB_DOMAINS.contains(targetEtld.lowercase())
        ) {
            return true
        }

        // Dimension 2: OAuth 2.0 Identity Providers
        if (OAUTH_AUTH_HOSTS.contains(targetHost.lowercase())) {
            val lowerUrl = targetUrl.lowercase()
            if (lowerUrl.contains("redirect_uri=") || lowerUrl.contains("client_id=") || lowerUrl.contains("response_type=")) {
                return true
            }
        }

        // Dimension 3: Mobile Payment Gateways
        if (PAYMENT_DOMAINS.contains(targetEtld.lowercase())) {
            return true
        }

        return false
    }

    /**
     * Determines whether a navigation request should be audited for malicious next-page hijacking.
     *
     * In accordance with REDLINE-2:
     * Regular web browsing (reading news, clicking external links on Hacker News, Zhihu, SSPAI, Weibo)
     * is NEVER intercepted unless:
     * (1) It occurs within the active pagination click window (isPaginationContextActive()); OR
     * (2) It contains clear rogue ad/hijack fingerprints (e.g. baiduboxapp pseudo-host, rogue jump params).
     */
    fun shouldAuditNavigation(
        currentDocUrl: String?,
        targetUrl: String,
        isRedirect: Boolean,
    ): Boolean {
        // 1. If exempted by matrix (search, oauth, payment, same-domain, bypass token), do NOT audit
        if (isExempted(currentDocUrl, targetUrl)) {
            return false
        }

        // 2. Clear rogue hijacking fingerprints (e.g. pseudo-host baiduboxapp or known ad trampolines)
        val targetHost = extractHost(targetUrl)?.lowercase() ?: ""
        if (targetHost.contains("baiduboxapp")) {
            return true
        }

        // 3. If triggered within the active pagination click window, must be audited!
        if (isPaginationContextActive()) {
            return true
        }

        // 4. If an unexpected 302 cross-domain redirect occurs on an unwrappable ad link
        if (isRedirect && UrlTargetUnwrapper.extractTargetUrl(targetUrl) != null) {
            return true
        }

        // Default: allow normal external web navigation freely without interruption
        return false
    }

    /**
     * Extracts eTLD+1 from a hostname using Public Suffix List rules.
     */
    fun extractEtldPlusOne(host: String): String {
        val cleanHost =
            host
                .trim()
                .lowercase()
                .removePrefix(".")
                .removeSuffix(".")
        if (cleanHost.isBlank()) return ""

        // Check if host matches any compound suffix
        for (suffix in COMPOUND_PUBLIC_SUFFIXES) {
            if (cleanHost.endsWith(suffix) && cleanHost.length > suffix.length) {
                val preSuffix = cleanHost.substring(0, cleanHost.length - suffix.length)
                val dotIdx = preSuffix.lastIndexOf('.')
                val label = if (dotIdx == -1) preSuffix else preSuffix.substring(dotIdx + 1)
                return "$label$suffix"
            }
        }

        // Standard 1-level TLD: take last two labels
        val parts = cleanHost.split('.')
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            cleanHost
        }
    }

    /**
     * Extracts host from a web URL safely using string slicing.
     */
    fun extractHost(url: String): String? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        val afterScheme = if (schemeEnd != -1) trimmed.substring(schemeEnd + 3) else trimmed
        val hostEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#', ':'))
        val host = if (hostEnd == -1) afterScheme else afterScheme.substring(0, hostEnd)
        return host.trim().takeIf { it.isNotBlank() }
    }
}
