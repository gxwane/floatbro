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
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSecurityGuardTest {

    @Test
    fun `extractEtldPlusOne handles multi-level compound TLDs accurately`() {
        assertEquals("biquge.com.cn", NavigationSecurityGuard.extractEtldPlusOne("m.biquge.com.cn"))
        assertEquals("tsinghua.edu.cn", NavigationSecurityGuard.extractEtldPlusOne("lib.tsinghua.edu.cn"))
        assertEquals("bbc.co.uk", NavigationSecurityGuard.extractEtldPlusOne("news.bbc.co.uk"))
        assertEquals("project.github.io", NavigationSecurityGuard.extractEtldPlusOne("docs.project.github.io"))
        assertEquals("biquge.com", NavigationSecurityGuard.extractEtldPlusOne("chapter.biquge.com"))
        assertEquals("localhost", NavigationSecurityGuard.extractEtldPlusOne("localhost"))
    }

    @Test
    fun `isExempted exempts same eTLD+1 navigations`() {
        val currentDoc = "https://m.biquge.com/read/101.html"
        val nextDoc = "https://chapter.biquge.com/read/102.html"
        assertTrue(NavigationSecurityGuard.isExempted(currentDoc, nextDoc))
    }

    @Test
    fun `isExempted exempts search engines and hub redirects`() {
        val searchDoc = "https://m.baidu.com/from=844b/s?word=floatbro"
        val targetResult = "https://www.cnblogs.com/art/123.html"
        assertTrue(NavigationSecurityGuard.isExempted(searchDoc, targetResult))

        val bingDoc = "https://cn.bing.com/search?q=kotlin"
        val bingTarget = "https://kotlinlang.org/docs/home.html"
        assertTrue(NavigationSecurityGuard.isExempted(bingDoc, bingTarget))
    }

    @Test
    fun `isExempted exempts OAuth identity providers with redirect_uri`() {
        val currentDoc = "https://novel.xyz/login"
        val oauthTarget = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx123&redirect_uri=https%3A%2F%2Fnovel.xyz%2Fcallback"
        assertTrue(NavigationSecurityGuard.isExempted(currentDoc, oauthTarget))
    }

    @Test
    fun `isExempted exempts payment gateways`() {
        val cartDoc = "https://shop.xyz/pay"
        val alipayTarget = "https://cashier.alipay.com/gateway.do?order_id=888"
        assertTrue(NavigationSecurityGuard.isExempted(cartDoc, alipayTarget))
    }

    @Test
    fun `bypassToken is single-use and consumed atomically`() {
        val currentDoc = "https://m.biquge.com/read/101.html"
        val rogueTarget = "https://external-ad.com/landing.html"

        // Initially not exempted
        assertFalse(NavigationSecurityGuard.isExempted(currentDoc, rogueTarget))

        // Issue token
        NavigationSecurityGuard.issueBypassToken("m.biquge.com", "external-ad.com", 5000L)

        // First probe: successfully exempted and consumed!
        assertTrue(NavigationSecurityGuard.isExempted(currentDoc, rogueTarget))

        // Second probe: token already consumed, no longer exempted!
        assertFalse(NavigationSecurityGuard.isExempted(currentDoc, rogueTarget))
    }

    @Test
    fun `shouldAuditNavigation adheres to REDLINE-2 defense trigger context`() {
        val readingDoc = "https://sspai.com/post/999"
        val articleOutlink = "https://github.com/floatbro/floatbro"

        // Case 1: Normal web browsing (not in pagination context) is NEVER audited
        assertFalse(NavigationSecurityGuard.shouldAuditNavigation(readingDoc, articleOutlink, isRedirect = false))

        // Case 2: Clear rogue fingerprint (e.g. baiduboxapp pseudo-host) is always audited
        val rogueApp = "https://baiduboxapp//utils?action=download"
        assertTrue(NavigationSecurityGuard.shouldAuditNavigation(readingDoc, rogueApp, isRedirect = false))

        // Case 3: In pagination context, cross-origin navigation is audited
        val novelDoc = "https://m.biquge.com/read/101.html"
        val rogueAd = "https://pure-ad-landing.com/game.html"
        NavigationSecurityGuard.notifyPaginationClicked()
        assertTrue(NavigationSecurityGuard.shouldAuditNavigation(novelDoc, rogueAd, isRedirect = false))
    }
}
