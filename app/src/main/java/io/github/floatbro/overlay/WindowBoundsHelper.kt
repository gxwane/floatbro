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
        maxHeight: Int
    ): Pair<Int, Int> {
        val safeMaxWidth = maxWidth.coerceAtLeast(minWidth)
        val safeMaxHeight = maxHeight.coerceAtLeast(minHeight)
        val clampedWidth = rawWidth.coerceIn(minWidth, safeMaxWidth)
        val clampedHeight = rawHeight.coerceIn(minHeight, safeMaxHeight)
        return clampedWidth to clampedHeight
    }
}
