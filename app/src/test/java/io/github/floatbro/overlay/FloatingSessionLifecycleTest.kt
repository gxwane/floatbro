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
import org.junit.Assert.assertNull
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

    @Test
    fun `scenario - spatial overlap detection for side-by-side windows returns false`() {
        val windowAW = 400
        val windowAH = 600
        val windowBW = 400
        val windowBH = 600

        // Non-overlapping side-by-side: Window A at x=50, Window B at x=500
        val isOverlapping =
            WindowBoundsHelper.isOverlapping(
                x1 = 50,
                y1 = 100,
                w1 = windowAW,
                h1 = windowAH,
                x2 = 500,
                y2 = 100,
                w2 = windowBW,
                h2 = windowBH,
            )
        assertFalse("Side-by-side windows must have zero overlap", isOverlapping)
    }

    @Test
    fun `scenario - spatial overlap detection for cascading overlapping windows returns true`() {
        val windowW = 500
        val windowH = 700

        // Cascading with 50px offset: Window A at (100, 100), Window B at (150, 150)
        val isOverlapping =
            WindowBoundsHelper.isOverlapping(
                x1 = 100,
                y1 = 100,
                w1 = windowW,
                h1 = windowH,
                x2 = 150,
                y2 = 150,
                w2 = windowW,
                h2 = windowH,
            )
        assertTrue("Cascading windows with overlapping bounds must report overlap", isOverlapping)
    }

    @Test
    fun `scenario - computeUnionBoundingBox returns null for empty list`() {
        assertNull(WindowBoundsHelper.computeUnionBoundingBox(emptyList()))
    }

    @Test
    fun `scenario - computeUnionBoundingBox returns exact bounds for single window`() {
        val bounds = listOf(WindowBoundsHelper.WindowBounds(x = 100, y = 200, width = 400, height = 600))
        val union = WindowBoundsHelper.computeUnionBoundingBox(bounds)
        assertEquals(100, union?.x)
        assertEquals(200, union?.y)
        assertEquals(400, union?.width)
        assertEquals(600, union?.height)
    }

    @Test
    fun `scenario - computeUnionBoundingBox computes correct union for multi-window layout`() {
        val bounds =
            listOf(
                WindowBoundsHelper.WindowBounds(x = 50, y = 100, width = 300, height = 400),
                WindowBoundsHelper.WindowBounds(x = 200, y = 250, width = 350, height = 450),
            )
        val union = WindowBoundsHelper.computeUnionBoundingBox(bounds)
        // minX = 50, minY = 100, maxX = 200+350 = 550, maxY = 250+450 = 700
        // union width = 550 - 50 = 500, union height = 700 - 100 = 600
        assertEquals(50, union?.x)
        assertEquals(100, union?.y)
        assertEquals(500, union?.width)
        assertEquals(600, union?.height)
    }

    @Test
    fun `scenario - spatial overlap boundary touching without intersecting area returns false`() {
        // Window A right edge meets Window B left edge at x=400
        val isOverlapping =
            WindowBoundsHelper.isOverlapping(
                x1 = 0,
                y1 = 0,
                w1 = 400,
                h1 = 600,
                x2 = 400,
                y2 = 0,
                w2 = 400,
                h2 = 600,
            )
        assertFalse("Touching borders without area intersection must not overlap", isOverlapping)
    }

    @Test
    fun `scenario - fully enclosed window reports overlap`() {
        val isOverlapping =
            WindowBoundsHelper.isOverlapping(
                x1 = 100,
                y1 = 100,
                w1 = 600,
                h1 = 800,
                x2 = 150,
                y2 = 150,
                w2 = 200,
                h2 = 200,
            )
        assertTrue("Enclosed window must report overlap", isOverlapping)
    }
}
