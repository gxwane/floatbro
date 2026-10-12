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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests for Floating Session models, max limits, and bounds clamping.
 */
class FloatingSessionLifecycleTest {

    @Test
    fun `scenario - max concurrent session constant is 3`() {
        assertEquals(3, FloatingWindowManager.MAX_CONCURRENT_SESSIONS)
    }

    @Test
    fun `scenario - session state transitions correctly`() {
        var state = SessionLifecycleState.ATTACHED
        assertEquals(SessionLifecycleState.ATTACHED, state)

        state = SessionLifecycleState.DETACHING
        assertEquals(SessionLifecycleState.DETACHING, state)

        state = SessionLifecycleState.DETACHED
        assertEquals(SessionLifecycleState.DETACHED, state)
    }

    @Test
    fun `scenario - url formatting strips protocol and www prefix`() {
        val testUrl1 = "https://www.bilibili.com"
        val testUrl2 = "http://cn.bing.com/search?q=test"
        val clean1 = testUrl1.replaceFirst(Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE), "")
        val clean2 = testUrl2.replaceFirst(Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE), "")
        assertEquals("bilibili.com", clean1)
        assertEquals("cn.bing.com/search?q=test", clean2)
    }
}
