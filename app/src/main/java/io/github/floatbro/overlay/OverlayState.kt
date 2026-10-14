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

/**
 * Display modes for the system overlay.
 */
enum class OverlayMode {
    /**
     * Fully expanded floating window with TwoPaneLayout.
     */
    WINDOW,

    /**
     * Minimized 48dp circular bubble docked to the screen edge.
     */
    BUBBLE,

    /**
     * Completely hidden from the screen.
     */
    HIDDEN,
}

/**
 * Bounds and position for the floating window.
 */
data class WindowBounds(
    var x: Int = 80,
    var y: Int = 160,
    var width: Int = 960,
    var height: Int = 1280,
)

/**
 * Position for the minimized floating bubble.
 */
data class BubblePosition(
    var x: Int = 0,
    var y: Int = 300,
)
