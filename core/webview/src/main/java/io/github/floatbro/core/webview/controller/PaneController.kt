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

package io.github.floatbro.core.webview.controller

import io.github.floatbro.core.webview.twopane.TwoPaneLayout

/**
 * Controller coordinator managing dual-pane layout operations,
 * split orientation, and cross-pane dispatching.
 */
class PaneController(
    private val layout: TwoPaneLayout
) {
    fun toggleSecondPane() {
        layout.isSecondPaneVisible = !layout.isSecondPaneVisible
    }

    fun setSecondPaneVisible(visible: Boolean) {
        layout.isSecondPaneVisible = visible
    }

    fun toggleOrientation() {
        layout.splitOrientation = if (layout.splitOrientation == TwoPaneLayout.Orientation.HORIZONTAL) {
            TwoPaneLayout.Orientation.VERTICAL
        } else {
            TwoPaneLayout.Orientation.HORIZONTAL
        }
    }

    fun swapPanes() {
        layout.swapPanes()
    }

    fun resetRatio() {
        layout.splitRatio = 0.5f
    }

    fun openUrlInPane1(url: String) {
        layout.pane1.loadUrl(url)
    }

    fun openUrlInPane2(url: String) {
        if (!layout.isSecondPaneVisible) {
            layout.isSecondPaneVisible = true
        }
        layout.pane2.loadUrl(url)
    }
}
