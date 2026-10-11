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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests for WindowBoundsHelper.
 * Validates smooth diagonal resizing constraints: min/max bounds and aspect ratio boundaries (0.45 ~ 2.20).
 */
class WindowBoundsHelperTest {

    private val minWidth = 300
    private val maxWidth = 1080
    private val minHeight = 400
    private val maxHeight = 2400

    @Test
    fun `scenario - normal dimensions within ratio range remain unchanged`() {
        // GIVEN: 600x800 window (ratio 0.75, valid range [0.45, 2.20])
        val rawWidth = 600
        val rawHeight = 800

        // WHEN: Clamping dimensions
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight
        )

        // THEN: Dimensions are preserved exactly
        assertEquals(600, w)
        assertEquals(800, h)
    }

    @Test
    fun `scenario - ultra narrow aspect ratio is clamped to minRatio 0_45`() {
        // GIVEN: 300x1000 window (ratio 0.30 < 0.45)
        val rawWidth = 300
        val rawHeight = 1000

        // WHEN: Clamping dimensions
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight
        )

        // THEN: Width stays 300, Height adjusted to 300 / 0.45 = 666
        assertEquals(300, w)
        assertEquals(666, h)
        val ratio = w.toFloat() / h.toFloat()
        assertTrue("Ratio should be approximately 0.45, got $ratio", ratio in 0.44f..0.46f)
    }

    @Test
    fun `scenario - ultra wide aspect ratio is clamped to maxRatio 2_20`() {
        // GIVEN: 1000x400 window (ratio 2.50 > 2.20)
        val rawWidth = 1000
        val rawHeight = 400

        // WHEN: Clamping dimensions
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight
        )

        // THEN: Height stays 400, Width adjusted to 400 * 2.20 = 880
        assertEquals(880, w)
        assertEquals(400, h)
        val ratio = w.toFloat() / h.toFloat()
        assertTrue("Ratio should be approximately 2.20, got $ratio", ratio in 2.19f..2.21f)
    }

    @Test
    fun `scenario - dimensions below minimum are clamped to min bounds`() {
        // GIVEN: Raw dimensions smaller than minWidth and minHeight
        val rawWidth = 150
        val rawHeight = 200

        // WHEN: Clamping dimensions
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight
        )

        // THEN: Respects minWidth and minHeight
        assertTrue("Width must be >= minWidth", w >= minWidth)
        assertTrue("Height must be >= minHeight", h >= minHeight)
    }
}
