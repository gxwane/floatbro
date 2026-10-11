package io.github.floatbro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

private val LightColorScheme = lightColorScheme(
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

@Composable
fun FloatBroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
