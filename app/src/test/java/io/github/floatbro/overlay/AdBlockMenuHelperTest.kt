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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdBlockMenuHelperTest {

    @Test
    fun testGlobalDisabledTakesPrecedence() {
        // 当全局关闭时，无论站点是否在白名单，都应明确提示“全局已关闭”，严禁误报为“已加白名单”
        val titleWhitelisted = AdBlockMenuHelper.resolveMenuTitle(isGlobalEnabled = false, isSiteWhitelisted = true)
        assertEquals(AdBlockMenuHelper.TITLE_GLOBAL_DISABLED, titleWhitelisted)
        assertFalse(titleWhitelisted.contains("已加白名单"))

        val titleNormal = AdBlockMenuHelper.resolveMenuTitle(isGlobalEnabled = false, isSiteWhitelisted = false)
        assertEquals(AdBlockMenuHelper.TITLE_GLOBAL_DISABLED, titleNormal)
    }

    @Test
    fun testGlobalEnabledSiteWhitelisted() {
        val title = AdBlockMenuHelper.resolveMenuTitle(isGlobalEnabled = true, isSiteWhitelisted = true)
        assertEquals(AdBlockMenuHelper.TITLE_SITE_WHITELISTED, title)
        assertEquals("广告拦截: 已加白名单 (点击恢复拦截)", title)
    }

    @Test
    fun testGlobalEnabledSiteActive() {
        val title = AdBlockMenuHelper.resolveMenuTitle(isGlobalEnabled = true, isSiteWhitelisted = false)
        assertEquals(AdBlockMenuHelper.TITLE_SITE_ENABLED, title)
        assertEquals("广告拦截: 已开启 (点击加白名单)", title)
    }
}
