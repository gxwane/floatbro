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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val BrandBlue = Color(0xFF2563EB)
val BrandBlueLight = Color(0xFFDBEAFE)
val BrandEmerald = Color(0xFF10B981)
val BrandEmeraldDark = Color(0xFF059669)

val FloatBroLightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = BrandEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    background = Color.White,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    outlineVariant = Slate200
)

val FloatBroDarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Slate900,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF34D399),
    onSecondary = Slate900,
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFA7F3D0),
    background = Slate900,
    onBackground = Color.White,
    surface = Slate800,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate200,
    outline = Slate500,
    outlineVariant = Slate600
)

object FloatBroUiTokens {
    val MinTouchTarget: Dp = 48.dp
    val TabCloseCapsuleSize: Dp = 18.dp
    val TabCloseIconSize: Dp = 12.dp
    val ToolbarButtonTouchTarget: Dp = 48.dp
    val GridSpacingStandard: Dp = 8.dp
}

object FloatingWindowThemeColors {
    const val LightWindowBackground: Int = 0xFFFFFFFF.toInt()
    const val DarkWindowBackground: Int = 0xFF1E293B.toInt()
    const val LightHeaderBackground: Int = 0xFFF1F5F9.toInt()
    const val DarkHeaderBackground: Int = 0xFF1E293B.toInt()
    const val ActiveBorder: Int = 0xFF2563EB.toInt()
    const val InactiveBorderLight: Int = 0xFFCBD5E1.toInt()
    const val InactiveBorderDark: Int = 0xFF334155.toInt()
}

val FloatBroTypography = Typography(
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun FloatBroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FloatBroDarkColorScheme else FloatBroLightColorScheme,
        typography = FloatBroTypography,
        content = content
    )
}
