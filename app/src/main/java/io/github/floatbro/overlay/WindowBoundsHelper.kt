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

object WindowBoundsHelper {
    const val MIN_WIDTH_DP = 240f
    const val MIN_HEIGHT_PORTRAIT_DP = 160f
    const val MIN_HEIGHT_LANDSCAPE_DP = 140f

    /**
     * Clamps window dimensions strictly within safe min/max boundaries.
     * Width and height are clamped independently to allow full freeform resizing freedom.
     */
    fun clampDimensions(
        rawWidth: Int,
        rawHeight: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
    ): Pair<Int, Int> {
        val safeMaxWidth = maxWidth.coerceAtLeast(minWidth)
        val safeMaxHeight = maxHeight.coerceAtLeast(minHeight)
        val clampedWidth = rawWidth.coerceIn(minWidth, safeMaxWidth)
        val clampedHeight = rawHeight.coerceIn(minHeight, safeMaxHeight)
        return clampedWidth to clampedHeight
    }

    data class WindowBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * Determines whether two window rectangles overlap in screen space.
     * Touching borders without strictly intersecting area return false.
     */
    fun isOverlapping(
        a: WindowBounds,
        b: WindowBounds,
    ): Boolean = isOverlapping(a.x, a.y, a.width, a.height, b.x, b.y, b.width, b.height)

    /**
     * Determines whether two window rectangles overlap in screen space.
     * Touching borders without strictly intersecting area return false.
     */
    fun isOverlapping(
        x1: Int,
        y1: Int,
        w1: Int,
        h1: Int,
        x2: Int,
        y2: Int,
        w2: Int,
        h2: Int,
    ): Boolean {
        if (w1 <= 0 || h1 <= 0 || w2 <= 0 || h2 <= 0) return false
        val r1 = x1 + w1
        val b1 = y1 + h1
        val r2 = x2 + w2
        val b2 = y2 + h2

        return x1 < r2 && r1 > x2 && y1 < b2 && b1 > y2
    }

    /**
     * Computes the bounding box covering a list of window bounds.
     * Returns null if the list is empty.
     */
    fun computeUnionBoundingBox(boundsList: List<WindowBounds>): WindowBounds? {
        if (boundsList.isEmpty()) return null
        val minX = boundsList.minOf { it.x }
        val minY = boundsList.minOf { it.y }
        val maxX = boundsList.maxOf { it.x + it.width }
        val maxY = boundsList.maxOf { it.y + it.height }
        return WindowBounds(
            x = minX,
            y = minY,
            width = (maxX - minX).coerceAtLeast(0),
            height = (maxY - minY).coerceAtLeast(0),
        )
    }
}
