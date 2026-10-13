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

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM Unit Tests for ElementZapperHelper logic and boundary guards.
 */
class ElementZapperHelperTest {

    @Test
    fun `buildRule formats standard ABP domain scoped rules correctly`() {
        val rule1 = ElementZapperHelper.buildRule("m.biquge.com", ".ad-box")
        assertEquals("m.biquge.com##.ad-box", rule1)

        val rule2 = ElementZapperHelper.buildRule("M.BiQuGe.COM", "#top_banner")
        assertEquals("m.biquge.com###top_banner", rule2)

        val rule3 = ElementZapperHelper.buildRule("  example.org  ", "  div.promoted  ")
        assertEquals("example.org##div.promoted", rule3)

        // Subdomain normalization
        val ruleBing1 = ElementZapperHelper.buildRule("cn.bing.com", "#headline")
        assertEquals("bing.com###headline", ruleBing1)

        val ruleBing2 = ElementZapperHelper.buildRule("www.bing.com", ".icon_text > a")
        assertEquals("bing.com##.icon_text > a", ruleBing2)

        val ruleWww = ElementZapperHelper.buildRule("www.zhihu.com", ".AdItem")
        assertEquals("zhihu.com##.AdItem", ruleWww)
    }

    @Test
    fun `buildRule returns null for invalid host or selector`() {
        assertNull(ElementZapperHelper.buildRule("", ".ad-box"))
        assertNull(ElementZapperHelper.buildRule("   ", ".ad-box"))
        assertNull(ElementZapperHelper.buildRule("example.com", ""))
        assertNull(ElementZapperHelper.buildRule("example.com", "   "))
        assertNull(ElementZapperHelper.buildRule("bad host.com", ".ad-box"))
        assertNull(ElementZapperHelper.buildRule("host#name", ".ad-box"))
    }

    @Test
    fun `buildRule blocks newlines, CSS braces, and destructive bare tags`() {
        // Newline injection
        assertNull(ElementZapperHelper.buildRule("example.com", ".ad\n.evil"))
        assertNull(ElementZapperHelper.buildRule("example.com", ".ad\r\n.evil"))

        // Brace CSS injection
        assertNull(ElementZapperHelper.buildRule("example.com", ".ad { color: red; }"))
        assertNull(ElementZapperHelper.buildRule("example.com", ".ad} body { display:none }"))

        // Bare destructive tags
        assertNull(ElementZapperHelper.buildRule("example.com", "div"))
        assertNull(ElementZapperHelper.buildRule("example.com", "DIV"))
        assertNull(ElementZapperHelper.buildRule("example.com", "span"))
        assertNull(ElementZapperHelper.buildRule("example.com", "p"))
        assertNull(ElementZapperHelper.buildRule("example.com", "body"))
        assertNull(ElementZapperHelper.buildRule("example.com", "html"))

        // Legitimate qualified tag with class or id is allowed
        assertEquals("example.com##div.banner", ElementZapperHelper.buildRule("example.com", "div.banner"))
        assertEquals("example.com##span#ad_id", ElementZapperHelper.buildRule("example.com", "span#ad_id"))
    }

    @Test
    fun `canExpandParent respects safe ceiling boundaries`() {
        // Normal container tags can expand
        assertTrue(ElementZapperHelper.canExpandParent("div", "article_container"))
        assertTrue(ElementZapperHelper.canExpandParent("section", "novel_read_wrap"))
        assertTrue(ElementZapperHelper.canExpandParent("aside", null))

        // Ceiling tags MUST stop expansion
        assertFalse(ElementZapperHelper.canExpandParent("body", null))
        assertFalse(ElementZapperHelper.canExpandParent("BODY", null))
        assertFalse(ElementZapperHelper.canExpandParent("html", null))
        assertFalse(ElementZapperHelper.canExpandParent("HTML", null))
        assertFalse(ElementZapperHelper.canExpandParent("head", null))

        // Framework root containers MUST stop expansion
        assertFalse(ElementZapperHelper.canExpandParent("div", "root"))
        assertFalse(ElementZapperHelper.canExpandParent("div", "app"))
        assertFalse(ElementZapperHelper.canExpandParent("div", "__next"))

        // Null / blank tag
        assertFalse(ElementZapperHelper.canExpandParent(null, null))
        assertFalse(ElementZapperHelper.canExpandParent("  ", null))
    }

    @Test
    fun `unescapeJson unescapes WebView evaluateJavascript response properly`() {
        val raw1 = "{\"selector\":\".ad-box\",\"tagName\":\"div\"}"
        assertEquals(raw1, ElementZapperHelper.unescapeJson(raw1))

        val rawQuoted = "\"{\\\"selector\\\":\\\".ad-box\\\",\\\"tagName\\\":\\\"div\\\"}\""
        assertEquals("{\"selector\":\".ad-box\",\"tagName\":\"div\"}", ElementZapperHelper.unescapeJson(rawQuoted))

        assertEquals("{}", ElementZapperHelper.unescapeJson(null))
        assertEquals("{}", ElementZapperHelper.unescapeJson(""))
    }
}
