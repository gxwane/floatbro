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

package io.github.floatbro.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun testAccessibilityTouchTargetTokens() {
        // WCAG 2.2 and Material 3 require minimum 48dp touch target
        assertEquals("Minimum touch target must be 48dp", 48.dp, FloatBroUiTokens.MinTouchTarget)
        assertEquals("Tab close capsule visual size must be 18dp", 18.dp, FloatBroUiTokens.TabCloseCapsuleSize)
        assertEquals("Tab close icon size must be 12dp", 12.dp, FloatBroUiTokens.TabCloseIconSize)
        assertEquals("Toolbar button touch target must be 48dp", 48.dp, FloatBroUiTokens.ToolbarButtonTouchTarget)
        assertEquals("Standard grid gap must be 8dp", 8.dp, FloatBroUiTokens.GridSpacingStandard)
    }

    @Test
    fun testLightAndDarkColorSchemeContrast() {
        assertNotNull(FloatBroLightColorScheme)
        assertNotNull(FloatBroDarkColorScheme)

        // Light theme background should have high luminance
        assertTrue(
            "Light background luminance should be > 0.8",
            FloatBroLightColorScheme.background.luminance() > 0.8f
        )
        // Dark theme background should have low luminance
        assertTrue(
            "Dark background luminance should be < 0.2",
            FloatBroDarkColorScheme.background.luminance() < 0.2f
        )

        // Dark theme onBackground should have high contrast with background
        assertTrue(
            "Dark theme onBackground luminance should be > 0.8",
            FloatBroDarkColorScheme.onBackground.luminance() > 0.8f
        )
    }

    @Test
    fun testFloatingWindowThemeColors() {
        assertEquals(0xFFFFFFFF.toInt(), FloatingWindowThemeColors.LightWindowBackground)
        assertEquals(0xFF1E293B.toInt(), FloatingWindowThemeColors.DarkWindowBackground)
        assertEquals(0xFFF1F5F9.toInt(), FloatingWindowThemeColors.LightHeaderBackground)
        assertEquals(0xFF1E293B.toInt(), FloatingWindowThemeColors.DarkHeaderBackground)
    }
}
