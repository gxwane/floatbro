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
    fun `scenario - width at minimum allows height to shrink independently down to minHeight`() {
        // GIVEN: Raw width is at or below minWidth (300), and raw height is shrinking down to minHeight (400)
        val rawWidth = 200
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

        // THEN: Width is clamped to minWidth, AND height is free to clamp to minHeight without being blocked by ratio
        assertEquals(300, w)
        assertEquals(400, h)
    }

    @Test
    fun `scenario - height at minimum allows width to shrink independently down to minWidth`() {
        // GIVEN: Raw height is at or below minHeight (400), and raw width is shrinking down to minWidth (300)
        val rawWidth = 300
        val rawHeight = 250

        // WHEN: Clamping dimensions
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            minWidth = minWidth,
            maxWidth = maxWidth,
            minHeight = minHeight,
            maxHeight = maxHeight
        )

        // THEN: Height is clamped to minHeight, AND width is free to clamp to minWidth
        assertEquals(300, w)
        assertEquals(400, h)
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

        // THEN: Respects minWidth and minHeight independently
        assertEquals(minWidth, w)
        assertEquals(minHeight, h)
    }

    @Test
    fun `scenario - landscape phone constrained screen height never throws and clamps safely`() {
        // GIVEN: Phone landscape screen where maxHeight is restricted (e.g. 600px available)
        val landscapeMinHeight = 440
        val landscapeMaxHeight = 600
        val rawWidth = 1200
        val rawHeight = 800

        // WHEN: Clamping dimensions
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            minWidth = minWidth,
            maxWidth = 1500,
            minHeight = landscapeMinHeight,
            maxHeight = landscapeMaxHeight
        )

        // THEN: Resulting height is clamped to landscapeMaxHeight and ratio is bounded
        assertTrue("Height must be <= landscapeMaxHeight", h <= landscapeMaxHeight)
        assertTrue("Height must be >= landscapeMinHeight", h >= landscapeMinHeight)
        assertTrue("Width must be >= minWidth", w >= minWidth)
    }

    @Test
    fun `scenario - degenerate bounds where max is less than min never throws empty range exception`() {
        // GIVEN: Degenerate inputs where maxWidth < minWidth due to extreme rotation/insets
        val (w, h) = WindowBoundsHelper.clampDimensions(
            rawWidth = 500,
            rawHeight = 500,
            minWidth = 600,
            maxWidth = 400,
            minHeight = 800,
            maxHeight = 600
        )

        // THEN: Clamps to minWidth and minHeight safely without throwing IllegalArgumentException
        assertEquals(600, w)
        assertEquals(800, h)
    }
}
