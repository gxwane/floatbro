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

import android.animation.ValueAnimator
import android.content.Context
import android.util.TypedValue
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import timber.log.Timber

/**
 * Manages spatial positioning, vertical stacking, and gap collapse
 * for multiple edge-docked floating bubbles.
 */
class BubbleStackCoordinator(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val activeBubbles = mutableListOf<FloatingSession>()

    private val bubblePitchPx = dpToPx(62f)
    private val baseMarginYPx = dpToPx(120f)

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Registers a session into the edge bubble stack and positions it at the next available slot.
     */
    fun attachBubble(session: FloatingSession) {
        if (!activeBubbles.any { it.id == session.id }) {
            activeBubbles.add(session)
        }
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val minY = dpToPx(48f)
        val maxY = (displayMetrics.heightPixels - dpToPx(100f)).coerceAtLeast(minY)

        val isCurrentOnRight = session.bubbleParams.x > screenWidth / 2
        val sameSideBubbles = activeBubbles.filter {
            it.id != session.id && (it.bubbleParams.x > screenWidth / 2) == isCurrentOnRight
        }

        if (sameSideBubbles.isNotEmpty()) {
            var adjustedY = session.bubbleParams.y
            for (other in sameSideBubbles) {
                if (Math.abs(adjustedY - other.bubbleParams.y) < bubblePitchPx) {
                    adjustedY = (other.bubbleParams.y + bubblePitchPx).coerceAtMost(maxY)
                }
            }
            session.bubbleParams.y = adjustedY.coerceIn(minY, maxY)
        }
    }

    /**
     * Removes a session from the bubble stack.
     */
    fun detachBubble(session: FloatingSession) {
        activeBubbles.removeAll { it.id == session.id }
    }

    /**
     * Recomputes vertical stacked positions for all active bubbles and animates them into place.
     */
    fun repositionBubbles(animate: Boolean) {
        val displayMetrics = context.resources.displayMetrics
        val minY = dpToPx(48f)
        val maxY = (displayMetrics.heightPixels - dpToPx(100f)).coerceAtLeast(minY)

        activeBubbles.forEachIndexed { index, session ->
            val bubble = session.bubbleView ?: return@forEachIndexed
            if (!bubble.isAttachedToWindow) return@forEachIndexed

            val targetY = (baseMarginYPx + index * bubblePitchPx).coerceIn(minY, maxY)
            val currentY = session.bubbleParams.y

            if (currentY != targetY) {
                if (animate) {
                    ValueAnimator.ofInt(currentY, targetY).apply {
                        duration = 200
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            session.bubbleParams.y = anim.animatedValue as Int
                            if (bubble.isAttachedToWindow) {
                                try {
                                    windowManager.updateViewLayout(bubble, session.bubbleParams)
                                } catch (e: Exception) {
                                    Timber.w(e, "Error updating bubble layout for session ${session.id}")
                                }
                            }
                        }
                        start()
                    }
                } else {
                    session.bubbleParams.y = targetY
                    try {
                        windowManager.updateViewLayout(bubble, session.bubbleParams)
                    } catch (e: Exception) {
                        Timber.w(e, "Error updating bubble layout for session ${session.id}")
                    }
                }
            }
        }
    }

    fun clear() {
        activeBubbles.clear()
    }
}
