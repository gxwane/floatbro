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

package io.github.edsuns.adfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure JVM Unit Tests verifying integrity and correctness of
 * the built-in offline mobile ad-blocking rule pack (default_rules.txt).
 */
class DefaultRulesTest {

    private val rulesFile: File by lazy {
        // Look up default_rules.txt in src/main/assets/adfilter/
        val candidate = File("src/main/assets/adfilter/default_rules.txt")
        if (candidate.exists()) candidate else File("ad-filter/src/main/assets/adfilter/default_rules.txt")
    }

    @Test
    fun `default_rules file exists and has valid header and size`() {
        assertTrue("default_rules.txt must exist in assets", rulesFile.exists())
        val lines = rulesFile.readLines()
        assertTrue("default_rules.txt must contain at least 2000 rules", lines.size >= 2000)
        assertEquals("[Adblock Plus 2.0]", lines.first().trim())
    }

    @Test
    fun `default_rules contains critical tracking and rogue ad network rules`() {
        val content = rulesFile.readText()

        // Critical Chinese trackers & ad networks
        assertTrue(content.contains("||hm.baidu.com^"))
        assertTrue(content.contains("||zz.bdstatic.com^"))
        assertTrue(content.contains("||pos.baidu.com^"))
        assertTrue(content.contains("||cpro.baidustatic.com^"))
        assertTrue(content.contains("||pgdt.gtimg.cn^"))
        assertTrue(content.contains("||pglstatp-toutiao.com^"))
        assertTrue(content.contains("||tanx.com^"))

        // Rogue novel spam ad networks found on m.piquge.com
        assertTrue(content.contains("||f9xc9t.top^"))
        assertTrue(content.contains("||xsead519.vip^"))
        assertTrue(content.contains("||sj1013a901.f9xc9t.top^"))
        assertTrue(content.contains("||kbe.xsead519.vip^"))
    }

    @Test
    fun `default_rules contains essential cosmetic and floating overlay hiding rules`() {
        val content = rulesFile.readText()

        // Element hiding rules
        assertTrue(content.contains("##div[style*=\"z-index: 2147483647\"]"))
        assertTrue(content.contains("##div[style*=\"position: fixed\"][style*=\"bottom: 0\"]"))
        assertTrue(content.contains("##.bottom-ad"))
        assertTrue(content.contains("##.pop-ad"))

        // Specific novel site element hiding
        assertTrue(content.contains("||m.piquge.com##div[style*=\"position: fixed\"]"))
        assertTrue(content.contains("||m.piquge.com##iframe"))
    }
}
