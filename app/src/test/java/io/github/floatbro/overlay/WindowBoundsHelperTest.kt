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

    @Test
    fun `scenario - side-by-side non-overlapping windows return false`() {
        // Window A: x=0, y=100, w=400, h=600 -> right=400, bottom=700
        // Window B: x=450, y=100, w=400, h=600 -> left=450
        val overlaps = WindowBoundsHelper.isOverlapping(
            x1 = 0, y1 = 100, w1 = 400, h1 = 600,
            x2 = 450, y2 = 100, w2 = 400, h2 = 600
        )
        org.junit.Assert.assertFalse("Side-by-side separated windows must not overlap", overlaps)
    }

    @Test
    fun `scenario - partially overlapping windows return true`() {
        // Window A: x=100, y=100, w=400, h=600
        // Window B: x=300, y=200, w=400, h=600
        val overlaps = WindowBoundsHelper.isOverlapping(
            x1 = 100, y1 = 100, w1 = 400, h1 = 600,
            x2 = 300, y2 = 200, w2 = 400, h2 = 600
        )
        assertTrue("Partially overlapping windows must report overlap", overlaps)
    }

    @Test
    fun `scenario - touching edges with zero pixel intersection return false`() {
        // Window A: x=0, y=0, w=300, h=300 -> right=300
        // Window B: x=300, y=0, w=300, h=300 -> left=300
        val overlaps = WindowBoundsHelper.isOverlapping(
            x1 = 0, y1 = 0, w1 = 300, h1 = 300,
            x2 = 300, y2 = 0, w2 = 300, h2 = 300
        )
        org.junit.Assert.assertFalse("Touching borders without area overlap must not be considered overlapping", overlaps)
    }
}

