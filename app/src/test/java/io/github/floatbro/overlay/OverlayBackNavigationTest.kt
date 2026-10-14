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
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure JVM Unit Tests for BackNavigationDispatcher.
 * Verifies tiered Back button dispatch rules: Menu -> Pane 2 -> Pane 1 -> Minimize to Bubble.
 */
class OverlayBackNavigationTest {
    @Test
    fun `scenario - when video is fullscreen, back button prioritizes exiting video fullscreen above all`() {
        // GIVEN: Video is in fullscreen, even if menu is open and panes have history
        val isVideoFullscreen = true
        val isMenuOpen = true
        val canPane2Back = true
        val canPane1Back = true

        // WHEN: Resolving back action
        val action =
            BackNavigationDispatcher.resolveBackAction(
                isVideoFullscreen = isVideoFullscreen,
                isMenuOpen = isMenuOpen,
                canPane2GoBack = canPane2Back,
                canPane1GoBack = canPane1Back,
            )

        // THEN: Priority 1 is EXIT_VIDEO_FULLSCREEN
        assertEquals(BackAction.EXIT_VIDEO_FULLSCREEN, action)
        assertNotEquals(BackAction.CLOSE_MENU, action)
        assertNotEquals(BackAction.GO_BACK_PANE2, action)
        assertNotEquals(BackAction.GO_BACK_PANE1, action)
        assertNotEquals(BackAction.MINIMIZE_TO_BUBBLE, action)
    }

    @Test
    fun `scenario - when in-window menu is open, back button prioritizes closing menu`() {
        // GIVEN: Menu is open, even if web pages have history
        val isMenuOpen = true
        val canPane2Back = true
        val canPane1Back = true

        // WHEN: Resolving back action
        val action = BackNavigationDispatcher.resolveBackAction(isMenuOpen, canPane2Back, canPane1Back)

        // THEN: Priority 1 is CLOSE_MENU
        assertEquals(BackAction.CLOSE_MENU, action)
        // NEGATIVE ASSERTION: Must not navigate web pages while menu is active
        assertNotEquals(BackAction.GO_BACK_PANE2, action)
        assertNotEquals(BackAction.GO_BACK_PANE1, action)
        assertNotEquals(BackAction.MINIMIZE_TO_BUBBLE, action)
    }

    @Test
    fun `scenario - when menu is closed and pane 2 can go back, back button navigates pane 2`() {
        // GIVEN: Menu is closed, Pane 2 has history
        val isMenuOpen = false
        val canPane2Back = true
        val canPane1Back = true

        // WHEN: Resolving back action
        val action = BackNavigationDispatcher.resolveBackAction(isMenuOpen, canPane2Back, canPane1Back)

        // THEN: Priority 2 is GO_BACK_PANE2
        assertEquals(BackAction.GO_BACK_PANE2, action)
        assertNotEquals(BackAction.CLOSE_MENU, action)
        assertNotEquals(BackAction.GO_BACK_PANE1, action)
    }

    @Test
    fun `scenario - when pane 2 cannot go back but pane 1 can, back button navigates pane 1`() {
        // GIVEN: Menu is closed, Pane 2 has no history, Pane 1 has history
        val isMenuOpen = false
        val canPane2Back = false
        val canPane1Back = true

        // WHEN: Resolving back action
        val action = BackNavigationDispatcher.resolveBackAction(isMenuOpen, canPane2Back, canPane1Back)

        // THEN: Priority 3 is GO_BACK_PANE1
        assertEquals(BackAction.GO_BACK_PANE1, action)
        assertNotEquals(BackAction.MINIMIZE_TO_BUBBLE, action)
    }

    @Test
    fun `scenario - when no history and menu closed, back button collapses window into bubble`() {
        // GIVEN: Menu closed, neither pane has backwards history
        val isMenuOpen = false
        val canPane2Back = false
        val canPane1Back = false

        // WHEN: Resolving back action
        val action = BackNavigationDispatcher.resolveBackAction(isMenuOpen, canPane2Back, canPane1Back)

        // THEN: Priority 4 is MINIMIZE_TO_BUBBLE
        assertEquals(BackAction.MINIMIZE_TO_BUBBLE, action)
        assertNotEquals(BackAction.CLOSE_MENU, action)
        assertNotEquals(BackAction.GO_BACK_PANE1, action)
        assertNotEquals(BackAction.GO_BACK_PANE2, action)
    }
}
