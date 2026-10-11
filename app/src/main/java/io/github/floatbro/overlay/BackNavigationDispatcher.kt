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
 * Possible actions taken when the user presses the system Back button.
 */
enum class BackAction {
    /**
     * Close the in-window Compose overlay menu.
     */
    CLOSE_MENU,

    /**
     * Navigate backwards in the second viewport.
     */
    GO_BACK_PANE2,

    /**
     * Navigate backwards in the primary viewport.
     */
    GO_BACK_PANE1,

    /**
     * Collapse the window into an edge-docking bubble.
     */
    MINIMIZE_TO_BUBBLE
}

/**
 * Pure Kotlin decision dispatcher for tiered Back button navigation.
 * Hierarchy:
 * 1. In-Window Menu (Top Priority)
 * 2. Second Pane Web History (if active and can go back)
 * 3. Primary Pane Web History
 * 4. Smooth Minimize to Bubble (Bottom Priority)
 */
object BackNavigationDispatcher {

    fun resolveBackAction(
        isMenuOpen: Boolean,
        canPane2GoBack: Boolean,
        canPane1GoBack: Boolean
    ): BackAction {
        return when {
            isMenuOpen -> BackAction.CLOSE_MENU
            canPane2GoBack -> BackAction.GO_BACK_PANE2
            canPane1GoBack -> BackAction.GO_BACK_PANE1
            else -> BackAction.MINIMIZE_TO_BUBBLE
        }
    }
}
