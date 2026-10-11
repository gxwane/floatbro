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

enum class SplitOrientation {
    HORIZONTAL,
    VERTICAL
}

/**
 * Pure Kotlin domain state machine managing symmetric dual-pane split ratios,
 * orientation flow, and boundaries. Completely decoupled from Android Views
 * to enable fast, pure JVM millisecond unit testing.
 */
class TwoPaneState(
    initialRatio: Float = 0.5f,
    val minRatio: Float = 0.15f,
    val maxRatio: Float = 0.85f
) {
    init {
        require(minRatio < maxRatio) { "minRatio ($minRatio) must be strictly less than maxRatio ($maxRatio)" }
        require(minRatio in 0.0f..1.0f && maxRatio in 0.0f..1.0f) { "Ratios must be within [0.0, 1.0]" }
    }

    var splitRatio: Float = initialRatio.coerceIn(minRatio, maxRatio)
        private set

    var orientation: SplitOrientation = SplitOrientation.HORIZONTAL

    var isSecondPaneVisible: Boolean = false

    fun updateRatio(newRatio: Float) {
        splitRatio = newRatio.coerceIn(minRatio, maxRatio)
    }

    fun resetToCenter() {
        splitRatio = 0.5f.coerceIn(minRatio, maxRatio)
    }

    fun toggleOrientation(): SplitOrientation {
        orientation = if (orientation == SplitOrientation.HORIZONTAL) {
            SplitOrientation.VERTICAL
        } else {
            SplitOrientation.HORIZONTAL
        }
        return orientation
    }

    fun toggleSecondPane(): Boolean {
        isSecondPaneVisible = !isSecondPaneVisible
        return isSecondPaneVisible
    }
}
