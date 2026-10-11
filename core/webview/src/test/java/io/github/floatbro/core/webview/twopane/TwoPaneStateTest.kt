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

package io.github.floatbro.core.webview.twopane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests for TwoPaneState domain state machine.
 * Verifies boundaries, ratio clamping, orientation toggling, and negative invariants.
 */
class TwoPaneStateTest {

    @Test
    fun `scenario - default state initialization has 50 percent ratio and horizontal orientation`() {
        // GIVEN: Default TwoPaneState
        val state = TwoPaneState()

        // THEN: Default ratio is 0.5f, orientation is HORIZONTAL, second pane is not visible
        assertEquals(0.5f, state.splitRatio, 0.001f)
        assertEquals(SplitOrientation.HORIZONTAL, state.orientation)
        assertFalse(state.isSecondPaneVisible)
    }

    @Test
    fun `scenario - ratio updates within bounds are respected`() {
        // GIVEN: TwoPaneState
        val state = TwoPaneState()

        // WHEN: Setting valid ratio within [0.15, 0.85]
        state.updateRatio(0.35f)

        // THEN: Exactly 0.35f is reflected
        assertEquals(0.35f, state.splitRatio, 0.001f)
    }

    @Test
    fun `scenario - ratio updates below minRatio clamp to minRatio and reject underflow`() {
        // GIVEN: TwoPaneState with minRatio 0.15f
        val state = TwoPaneState(minRatio = 0.15f, maxRatio = 0.85f)

        // WHEN: Attempting to set ratio below minRatio
        state.updateRatio(0.05f)

        // THEN: Clamped to minRatio
        assertEquals(0.15f, state.splitRatio, 0.001f)
        // NEGATIVE ASSERTION: Must not accept the raw 0.05f value
        assertNotEquals(0.05f, state.splitRatio, 0.001f)

        // WHEN: Attempting negative ratio
        state.updateRatio(-0.5f)

        // THEN: Still clamped to minRatio
        assertEquals(0.15f, state.splitRatio, 0.001f)
        assertNotEquals(-0.5f, state.splitRatio, 0.001f)
    }

    @Test
    fun `scenario - ratio updates above maxRatio clamp to maxRatio and reject overflow`() {
        // GIVEN: TwoPaneState with maxRatio 0.85f
        val state = TwoPaneState(minRatio = 0.15f, maxRatio = 0.85f)

        // WHEN: Attempting to set ratio above maxRatio
        state.updateRatio(0.95f)

        // THEN: Clamped to maxRatio
        assertEquals(0.85f, state.splitRatio, 0.001f)
        // NEGATIVE ASSERTION: Must not accept the raw 0.95f value
        assertNotEquals(0.95f, state.splitRatio, 0.001f)

        // WHEN: Attempting overflow ratio > 1.0f
        state.updateRatio(1.8f)

        // THEN: Still clamped to maxRatio
        assertEquals(0.85f, state.splitRatio, 0.001f)
        assertNotEquals(1.8f, state.splitRatio, 0.001f)
    }

    @Test
    fun `scenario - resetToCenter restores split ratio to 50 percent`() {
        // GIVEN: State modified to 0.20f
        val state = TwoPaneState()
        state.updateRatio(0.20f)
        assertEquals(0.20f, state.splitRatio, 0.001f)

        // WHEN: Reset requested
        state.resetToCenter()

        // THEN: Ratio is restored to 0.5f
        assertEquals(0.5f, state.splitRatio, 0.001f)
    }

    @Test
    fun `scenario - toggleOrientation alternates between horizontal and vertical`() {
        // GIVEN: State with HORIZONTAL orientation
        val state = TwoPaneState()
        assertEquals(SplitOrientation.HORIZONTAL, state.orientation)

        // WHEN: Toggling once
        val newOrientation = state.toggleOrientation()

        // THEN: Flipped to VERTICAL
        assertEquals(SplitOrientation.VERTICAL, newOrientation)
        assertEquals(SplitOrientation.VERTICAL, state.orientation)

        // WHEN: Toggling again
        val finalOrientation = state.toggleOrientation()

        // THEN: Flipped back to HORIZONTAL
        assertEquals(SplitOrientation.HORIZONTAL, finalOrientation)
        assertEquals(SplitOrientation.HORIZONTAL, state.orientation)
    }

    @Test
    fun `scenario - toggleSecondPane alternates visibility`() {
        // GIVEN: Default invisible state
        val state = TwoPaneState()
        assertFalse(state.isSecondPaneVisible)

        // WHEN: Toggle
        assertTrue(state.toggleSecondPane())
        assertTrue(state.isSecondPaneVisible)

        // WHEN: Toggle again
        assertFalse(state.toggleSecondPane())
        assertFalse(state.isSecondPaneVisible)
    }

    @Test
    fun `scenario - constructor rejects invalid min and max boundaries`() {
        // THEN: minRatio >= maxRatio throws IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            TwoPaneState(minRatio = 0.8f, maxRatio = 0.2f)
        }

        // THEN: Equal boundaries throw IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            TwoPaneState(minRatio = 0.5f, maxRatio = 0.5f)
        }
    }
}
