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

class OverlayPhysicsHelperTest {

    @Test
    fun testKeyboardAvoidanceOffsetCalculation() {
        val screenHeight = 2000
        val windowHeight = 800
        val keyboardHeight = 700
        val safeMargin = 50

        // Case 1: Window is in lower half (Y=1000).
        // Bottom is 1800, which collides with keyboard top (2000 - 700 = 1300).
        // Expected Y: 1300 - 800 - 50 = 450
        val targetY = OverlayPhysicsHelper.calculateKeyboardAvoidanceY(
            currentY = 1000,
            windowHeight = windowHeight,
            screenHeight = screenHeight,
            keyboardHeight = keyboardHeight,
            safeMarginPx = safeMargin
        )
        assertEquals(450, targetY)

        // Case 2: Window is in upper half (Y=200). Bottom is 1000 <= 1300.
        // No collision -> returns original Y (200)
        val nonCollidingY = OverlayPhysicsHelper.calculateKeyboardAvoidanceY(
            currentY = 200,
            windowHeight = windowHeight,
            screenHeight = screenHeight,
            keyboardHeight = keyboardHeight,
            safeMarginPx = safeMargin
        )
        assertEquals(200, nonCollidingY)

        // Case 3: Extreme keyboard height where target would be negative -> clamps to 0
        val clampedY = OverlayPhysicsHelper.calculateKeyboardAvoidanceY(
            currentY = 1000,
            windowHeight = 1500,
            screenHeight = screenHeight,
            keyboardHeight = 800,
            safeMarginPx = safeMargin
        )
        assertEquals(0, clampedY)
    }

    @Test
    fun testSnapTargetXCalculation() {
        val screenWidth = 1080
        val windowWidth = 600
        val snapThreshold = 120

        // Near left edge (currentX = 50 < 120) -> snaps to 0
        assertEquals(
            0,
            OverlayPhysicsHelper.calculateSnapTargetX(
                currentX = 50,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                snapThresholdPx = snapThreshold
            )
        )

        // Near right edge (currentX = 400, right edge = 1000 > 1080 - 120 = 960) -> snaps to 1080 - 600 = 480
        assertEquals(
            480,
            OverlayPhysicsHelper.calculateSnapTargetX(
                currentX = 400,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                snapThresholdPx = snapThreshold
            )
        )

        // In middle (currentX = 200, left 200 >= 120, right 800 <= 960) -> no snap
        assertEquals(
            200,
            OverlayPhysicsHelper.calculateSnapTargetX(
                currentX = 200,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                snapThresholdPx = snapThreshold
            )
        )
    }

    @Test
    fun testBubbleDropTargetDetection() {
        val targetCenterX = 540f
        val targetCenterY = 2200f
        val hitRadius = 150f

        // Center inside hit radius
        assertTrue(
            OverlayPhysicsHelper.isDropTargetHit(
                bubbleCenterX = 550f,
                bubbleCenterY = 2190f,
                targetCenterX = targetCenterX,
                targetCenterY = targetCenterY,
                hitRadiusPx = hitRadius
            )
        )

        // Far away outside hit radius
        assertFalse(
            OverlayPhysicsHelper.isDropTargetHit(
                bubbleCenterX = 100f,
                bubbleCenterY = 500f,
                targetCenterX = targetCenterX,
                targetCenterY = targetCenterY,
                hitRadiusPx = hitRadius
            )
        )
    }

    @Test
    fun testResolveWindowDragAction() {
        val windowWidth = 600
        val screenWidth = 1080

        // 1. Upward drag to top edge -> SNAP_EDGE (dock to app gesture removed, normal edge docking)
        assertEquals(
            WindowDragAction.SNAP_EDGE,
            OverlayPhysicsHelper.resolveWindowDragAction(
                currentY = 50,
                currentX = 200,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                isDismissHit = false
            )
        )

        // 2. Downward drag hitting dismiss target -> DISMISS_TO_TARGET
        assertEquals(
            WindowDragAction.DISMISS_TO_TARGET,
            OverlayPhysicsHelper.resolveWindowDragAction(
                currentY = 1500,
                currentX = 240,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                isDismissHit = true
            )
        )

        // 3. Pushed off screen left (x = -300 < -240) -> COLLAPSE_TO_BUBBLE
        assertEquals(
            WindowDragAction.COLLAPSE_TO_BUBBLE,
            OverlayPhysicsHelper.resolveWindowDragAction(
                currentY = 500,
                currentX = -300,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                isDismissHit = false
            )
        )

        // 4. Normal drag inside screen -> SNAP_EDGE
        assertEquals(
            WindowDragAction.SNAP_EDGE,
            OverlayPhysicsHelper.resolveWindowDragAction(
                currentY = 500,
                currentX = 200,
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                isDismissHit = false
            )
        )
    }
}
