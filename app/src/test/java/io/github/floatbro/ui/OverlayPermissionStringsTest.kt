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

package io.github.floatbro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPermissionStringsTest {
    @Test
    fun testRationaleTitleAndButtonsAreConcise() {
        assertEquals("开启悬浮窗权限", OverlayPermissionStrings.RATIONALE_TITLE)
        assertEquals("去开启", OverlayPermissionStrings.ACTION_CONFIRM)
        assertEquals("暂不开启", OverlayPermissionStrings.ACTION_DISMISS)
    }

    @Test
    fun testRationaleMessageIsCrispAndConcise() {
        val message = OverlayPermissionStrings.RATIONALE_MESSAGE
        assertEquals("开启后支持悬浮浏览与贴边气泡。", message)
        assertTrue("文案字数应控制在20字内以利于快速扫读，当前字数: ${message.length}", message.length <= 20)
    }

    @Test
    fun testNegativeAssertionsAntiLazinessGuard() {
        val message = OverlayPermissionStrings.RATIONALE_MESSAGE
        // 确保排除了原有的冗余营销词汇与废话
        assertFalse("不应包含冗余的主语和长定语", message.contains("FloatBro 需要悬浮窗权限"))
        assertFalse("不应包含宣传修饰词'无缝'", message.contains("无缝"))
        assertFalse("不应包含与操作按钮重复的机械废话'点击前往开启权限'", message.contains("点击前往开启权限"))
    }
}
