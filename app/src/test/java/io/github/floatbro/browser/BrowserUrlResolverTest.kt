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

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserUrlResolverTest {

    @Test
    fun testEmptyInputReturnsEmpty() {
        assertEquals("", BrowserUrlResolver.resolve(""))
        assertEquals("", BrowserUrlResolver.resolve("   "))
    }

    @Test
    fun testExplicitHttpHttpsPreserved() {
        assertEquals("https://github.com", BrowserUrlResolver.resolve("https://github.com"))
        assertEquals("http://example.com/test", BrowserUrlResolver.resolve("http://example.com/test"))
    }

    @Test
    fun testSpecialSchemesPreserved() {
        assertEquals("about:blank", BrowserUrlResolver.resolve("about:blank"))
        assertEquals("javascript:void(0)", BrowserUrlResolver.resolve("javascript:void(0)"))
        assertEquals("file:///sdcard/test.html", BrowserUrlResolver.resolve("file:///sdcard/test.html"))
    }

    @Test
    fun testWwwPrefixPrependHttps() {
        assertEquals("https://www.zhihu.com", BrowserUrlResolver.resolve("www.zhihu.com"))
    }

    @Test
    fun testDomainHeuristic() {
        assertEquals("https://bilibili.com", BrowserUrlResolver.resolve("bilibili.com"))
        assertEquals("https://v.qq.com/channel/anime", BrowserUrlResolver.resolve("v.qq.com/channel/anime"))
    }

    @Test
    fun testSearchQueryRoutingBingDefault() {
        val result = BrowserUrlResolver.resolve("floatbro browser", SearchEngine.BING)
        assertEquals("https://cn.bing.com/search?q=floatbro+browser", result)
    }

    @Test
    fun testSearchQueryRoutingBaidu() {
        val result = BrowserUrlResolver.resolve("浮窗浏览器", SearchEngine.BAIDU)
        assertEquals("https://www.baidu.com/s?wd=%E6%B5%AE%E7%AA%97%E6%B5%8F%E8%A7%88%E5%99%A8", result)
    }

    @Test
    fun testSearchQueryRoutingGoogle() {
        val result = BrowserUrlResolver.resolve("android", SearchEngine.GOOGLE)
        assertEquals("https://www.google.com/search?q=android", result)
    }

    @Test
    fun testCustomSearchEngine() {
        val result = BrowserUrlResolver.resolve("keyword", SearchEngine.CUSTOM, "https://example.com/search?keyword=%s")
        assertEquals("https://example.com/search?keyword=keyword", result)
    }
}
