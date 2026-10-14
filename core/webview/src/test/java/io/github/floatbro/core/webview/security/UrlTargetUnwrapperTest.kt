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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlTargetUnwrapperTest {
    @Test
    fun `extractTargetUrl extracts clean destination from standard target param`() {
        val raw = "https://ad.network.com/jump?click_id=999&target=https%3A%2F%2Fm.biquge.com%2Fread%2F102.html"
        val extracted = UrlTargetUnwrapper.extractTargetUrl(raw)
        assertEquals("https://m.biquge.com/read/102.html", extracted)
    }

    @Test
    fun `extractTargetUrl extracts clean destination from dest or url params`() {
        val rawDest = "https://rogue.agency.com/go?dest=https%3A%2F%2Fmanhua.xyz%2Fchapter%2F5"
        assertEquals("https://manhua.xyz/chapter/5", UrlTargetUnwrapper.extractTargetUrl(rawDest))

        val rawUrl = "https://track.click.com/redirect?url=http%3A%2F%2Fbook.org%2Fnext"
        assertEquals("http://book.org/next", UrlTargetUnwrapper.extractTargetUrl(rawUrl))
    }

    @Test
    fun `extractTargetUrl unwraps double encoded targets`() {
        // Double-encoded: https%253A%252F%252Fm.biquge.com%252Fread%252F105.html
        val doubleEncoded = "https://ad.com/redirect?target=https%253A%252F%252Fm.biquge.com%252Fread%252F105.html"
        val extracted = UrlTargetUnwrapper.extractTargetUrl(doubleEncoded)
        assertEquals("https://m.biquge.com/read/105.html", extracted)
    }

    @Test
    fun `extractTargetUrl unwraps nested trampoline URLs up to max depth`() {
        val nested =
            "https://ad1.com/jump?target=" +
                java.net.URLEncoder.encode(
                    "https://ad2.com/forward?dest=" +
                        java.net.URLEncoder.encode(
                            "https://novel.com/chapter/50.html",
                            "UTF-8",
                        ),
                    "UTF-8",
                )
        val extracted = UrlTargetUnwrapper.extractTargetUrl(nested)
        assertEquals("https://novel.com/chapter/50.html", extracted)
    }

    @Test
    fun `extractTargetUrl returns null when no target parameter exists`() {
        val plainAd = "https://pure-ad-landing.com/download/game.html?uid=123"
        assertNull(UrlTargetUnwrapper.extractTargetUrl(plainAd))
    }

    @Test
    fun `extractTargetUrl rejects non-http schemes and malformed URLs`() {
        val appScheme = "https://ad.com/jump?target=baiduboxapp%3A%2F%2Futils"
        assertNull(UrlTargetUnwrapper.extractTargetUrl(appScheme))

        val jsScheme = "https://ad.com/jump?target=javascript%3Aalert(1)"
        assertNull(UrlTargetUnwrapper.extractTargetUrl(jsScheme))
    }

    @Test
    fun `extractTargetUrl safely rejects excessively long URLs`() {
        val excessive = "https://ad.com/jump?target=https://good.com/" + "a".repeat(2500)
        assertNull(UrlTargetUnwrapper.extractTargetUrl(excessive))
    }

    @Test
    fun `buildSafeReferer adheres strictly to W3C strict-origin-when-cross-origin`() {
        // 1. Same-origin preserves full path and query
        val sameOriginDoc = "https://m.biquge.com/read/101.html?user=12"
        val sameOriginTarget = "https://m.biquge.com/read/102.html"
        val sameOriginReferer = UrlTargetUnwrapper.buildSafeReferer(sameOriginDoc, sameOriginTarget)
        assertEquals(sameOriginDoc, sameOriginReferer)

        // 2. Cross-origin truncates to Origin only
        val crossOriginTarget = "https://cdn.biquge-res.com/read/102.html"
        val crossOriginReferer = UrlTargetUnwrapper.buildSafeReferer(sameOriginDoc, crossOriginTarget)
        assertEquals("https://m.biquge.com/", crossOriginReferer)

        // 3. HTTPS to HTTP downgrade strictly returns null (never leak over plaintext)
        val httpTarget = "http://m.biquge.com/read/102.html"
        val downgradedReferer = UrlTargetUnwrapper.buildSafeReferer(sameOriginDoc, httpTarget)
        assertNull(downgradedReferer)

        // 4. Blank or invalid current doc returns null
        assertNull(UrlTargetUnwrapper.buildSafeReferer("", sameOriginTarget))
        assertNull(UrlTargetUnwrapper.buildSafeReferer("about:blank", sameOriginTarget))
    }

    @Test
    fun `isValidWebUrl correctly identifies valid web addresses`() {
        assertTrue(UrlTargetUnwrapper.isValidWebUrl("https://biquge.com/read/1"))
        assertTrue(UrlTargetUnwrapper.isValidWebUrl("http://localhost.test/page"))
        assertFalse(UrlTargetUnwrapper.isValidWebUrl("ftp://files.org/"))
        assertFalse(UrlTargetUnwrapper.isValidWebUrl("javascript:void(0)"))
        assertFalse(UrlTargetUnwrapper.isValidWebUrl("not-a-url"))
    }
}
