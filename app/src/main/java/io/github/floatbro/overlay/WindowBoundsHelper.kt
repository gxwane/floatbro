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
    fun clampDimensions(
        rawWidth: Int,
        rawHeight: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
        minRatio: Float = 0.45f,
        maxRatio: Float = 2.20f
    ): Pair<Int, Int> {
        val clampedWidth = rawWidth.coerceIn(minWidth, maxWidth)
        val clampedHeight = rawHeight.coerceIn(minHeight, maxHeight)
        val currentRatio = clampedWidth.toFloat() / clampedHeight.toFloat()

        return when {
            currentRatio < minRatio -> {
                val adjustedHeight = (clampedWidth / minRatio).toInt().coerceIn(minHeight, maxHeight)
                clampedWidth to adjustedHeight
            }
            currentRatio > maxRatio -> {
                val adjustedWidth = (clampedHeight * maxRatio).toInt().coerceIn(minWidth, maxWidth)
                adjustedWidth to clampedHeight
            }
            else -> clampedWidth to clampedHeight
        }
    }
}
