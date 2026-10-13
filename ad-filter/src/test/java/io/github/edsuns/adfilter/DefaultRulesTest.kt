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
 * the modular built-in offline mobile ad-blocking rule packs:
 * - rules_easylist.txt (EasyList Lite)
 * - rules_easylist_china.txt (EasyList China Lite)
 * - rules_easyprivacy.txt (EasyPrivacy Lite)
 */
class DefaultRulesTest {

    private fun getAssetFile(fileName: String): File {
        val candidate = File("src/main/assets/adfilter/$fileName")
        return if (candidate.exists()) candidate else File("ad-filter/src/main/assets/adfilter/$fileName")
    }

    @Test
    fun `easylist pack exists and contains valid global rules`() {
        val file = getAssetFile("rules_easylist.txt")
        assertTrue("rules_easylist.txt must exist in assets", file.exists())
        val lines = file.readLines()
        assertTrue("rules_easylist.txt must contain at least 1500 rules", lines.size >= 1500)
        assertEquals("[Adblock Plus 2.0]", lines.first().trim())

        val content = file.readText()
        assertTrue(content.contains("||pagead2.googlesyndication.com^"))
        assertTrue(content.contains("||adservice.google.com^"))
        assertTrue(content.contains("||criteo.com^"))
        assertTrue(content.contains("||taboola.com^"))
        assertTrue(content.contains("||outbrain.com^"))
        assertTrue(content.contains("||inmobi.com^"))
        assertTrue(content.contains("||unityads.unity3d.com^"))
        assertTrue(content.contains("##.adsbygoogle"))

        // Overkill substring rules MUST NOT exist globally
        assertFalse(content.contains("##div[class*=\"ad\"]"))
    }

    @Test
    fun `china pack exists and contains critical chinese ad alliances`() {
        val file = getAssetFile("rules_easylist_china.txt")
        assertTrue("rules_easylist_china.txt must exist in assets", file.exists())
        val lines = file.readLines()
        assertTrue("rules_easylist_china.txt must contain at least 2000 rules", lines.size >= 2000)
        assertEquals("[Adblock Plus 2.0]", lines.first().trim())

        val content = file.readText()
        assertTrue(content.contains("||pos.baidu.com^"))
        assertTrue(content.contains("||cpro.baidu.com^"))
        assertTrue(content.contains("||mobads.baidu.com^"))
        assertTrue(content.contains("||pgdt.gtimg.cn^"))
        assertTrue(content.contains("||mi.gdt.qq.com^"))
        assertTrue(content.contains("||pglstatp-toutiao.com^"))
        assertTrue(content.contains("||pangolin-sdk-toutiao.com^"))
        assertTrue(content.contains("||tanx.com^"))
        assertTrue(content.contains("||alimama.com^"))
        assertTrue(content.contains("||ad.partner.gifshow.com^"))
        assertTrue(content.contains("||tuia.cn^"))

        // Overkill substring rules MUST NOT exist globally
        assertFalse(content.contains("##div[class*=\"ad\"]"))
    }

    @Test
    fun `privacy pack exists and contains tracking telemetry probes`() {
        val file = getAssetFile("rules_easyprivacy.txt")
        assertTrue("rules_easyprivacy.txt must exist in assets", file.exists())
        val lines = file.readLines()
        assertTrue("rules_easyprivacy.txt must contain at least 1000 rules", lines.size >= 1000)
        assertEquals("[Adblock Plus 2.0]", lines.first().trim())

        val content = file.readText()
        assertTrue(content.contains("||hm.baidu.com^"))
        assertTrue(content.contains("||umeng.com^"))
        assertTrue(content.contains("||cnzz.com^"))
        assertTrue(content.contains("||51.la^"))
        assertTrue(content.contains("||sensorsdata.cn^"))
        assertTrue(content.contains("||zhugeio.com^"))
        assertTrue(content.contains("||growingio.com^"))
        assertTrue(content.contains("||beacon.qq.com^"))
        assertTrue(content.contains("||google-analytics.com^"))
        assertTrue(content.contains("||googletagmanager.com^"))
    }
}
