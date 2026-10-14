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

import kotlin.math.hypot

object OverlayPhysicsHelper {
    /**
     * Calculates the adjusted Y coordinate for a floating window to avoid being covered
     * by the software keyboard (IME).
     *
     * @param currentY Current top coordinate of the window
     * @param windowHeight Height of the window
     * @param screenHeight Total height of the screen
     * @param keyboardHeight Height of the soft keyboard
     * @param safeMarginPx Spacing between bottom of window and top of keyboard
     * @return Adjusted top coordinate (Y) of the window, clamped >= 0
     */
    fun calculateKeyboardAvoidanceY(
        currentY: Int,
        windowHeight: Int,
        screenHeight: Int,
        keyboardHeight: Int,
        safeMarginPx: Int = 32,
    ): Int {
        if (keyboardHeight <= 0) return currentY
        val keyboardTop = screenHeight - keyboardHeight
        val windowBottom = currentY + windowHeight
        if (windowBottom <= keyboardTop) {
            return currentY
        }
        val targetY = keyboardTop - windowHeight - safeMarginPx
        return targetY.coerceAtLeast(0)
    }

    /**
     * Calculates the target X coordinate for horizontal edge snapping.
     *
     * @param currentX Current X coordinate of window
     * @param windowWidth Width of the window
     * @param screenWidth Total display width
     * @param snapThresholdPx Threshold distance to trigger snapping
     * @return Target X coordinate to snap to, or currentX if not within threshold
     */
    fun calculateSnapTargetX(
        currentX: Int,
        windowWidth: Int,
        screenWidth: Int,
        snapThresholdPx: Int,
    ): Int {
        val windowRight = currentX + windowWidth
        return when {
            currentX < snapThresholdPx -> 0
            windowRight > screenWidth - snapThresholdPx -> (screenWidth - windowWidth).coerceAtLeast(0)
            else -> currentX
        }
    }

    /**
     * Determines whether a bubble is dragged into the drop target area (e.g. dismiss zone).
     */
    fun isDropTargetHit(
        bubbleCenterX: Float,
        bubbleCenterY: Float,
        targetCenterX: Float,
        targetCenterY: Float,
        hitRadiusPx: Float,
    ): Boolean {
        val distance = hypot(bubbleCenterX - targetCenterX, bubbleCenterY - targetCenterY)
        return distance <= hitRadiusPx
    }

    /**
     * Resolves the resulting action when a floating window drag gesture ends.
     */
    fun resolveWindowDragAction(
        currentY: Int,
        currentX: Int,
        windowWidth: Int,
        screenWidth: Int,
        isDismissHit: Boolean,
        collapseRatio: Float = 0.40f,
    ): WindowDragAction {
        if (isDismissHit) {
            return WindowDragAction.DISMISS_TO_TARGET
        }
        val isPushedOffLeft = currentX < -(windowWidth * collapseRatio)
        val isPushedOffRight = (currentX + windowWidth) > (screenWidth + windowWidth * collapseRatio)
        if (isPushedOffLeft || isPushedOffRight) {
            return WindowDragAction.COLLAPSE_TO_BUBBLE
        }
        return WindowDragAction.SNAP_EDGE
    }
}

/**
 * Resulting action when a floating window drag gesture completes.
 */
enum class WindowDragAction {
    DISMISS_TO_TARGET,
    COLLAPSE_TO_BUBBLE,
    SNAP_EDGE,
}
