package com.lifetrace.finance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BeeYellow = Color(0xFFFFC928)
private val BeeYellowDeep = Color(0xFFF3B900)
private val BeeInk = Color(0xFF17191F)

private data class AccentPalette(val primary: Color, val lightContainer: Color, val darkContainer: Color)

private fun lightColors(accent: AccentPalette) = lightColorScheme(
    primary = accent.primary,
    onPrimary = BeeInk,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = BeeInk,
    secondary = Color(0xFF635A75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2EDF9),
    onSecondaryContainer = Color(0xFF342E3D),
    background = Color(0xFFF6F6F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F1F1),
    onSurface = BeeInk,
    onSurfaceVariant = Color(0xFF777A82),
    outline = Color(0xFFE2E2E2),
    error = Color(0xFFBA1A1A),
)

private fun darkColors(accent: AccentPalette) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color(0xFF171717),
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.primary,
    secondary = Color(0xFFCFC4DC),
    background = Color.Black,
    surface = Color(0xFF101010),
    surfaceVariant = Color(0xFF202020),
    onSurface = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFFB9B9B9),
    outline = Color(0xFF3A3A3A),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
)

@Composable
fun LifeTraceTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val revision by com.lifetrace.finance.domain.AppearanceSettings.changes.collectAsState()
    val settings = com.lifetrace.finance.domain.AppearanceSettings(context)
    val useDark = when (settings.themeMode) {
        com.lifetrace.finance.domain.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.lifetrace.finance.domain.ThemeMode.LIGHT -> false
        com.lifetrace.finance.domain.ThemeMode.DARK -> true
    }
    val accent = when (settings.accent) {
        "green" -> AccentPalette(Color(0xFF55A86B), Color(0xFFD8F3DF), Color(0xFF173D21))
        "blue" -> AccentPalette(Color(0xFF4F9DDE), Color(0xFFD6ECFF), Color(0xFF173A55))
        "rose" -> AccentPalette(Color(0xFFEC7180), Color(0xFFFFDDE1), Color(0xFF5A2029))
        else -> AccentPalette(BeeYellow, Color(0xFFFFE9A6), Color(0xFF4B3B00))
    }
    MaterialTheme(
        colorScheme = if (useDark) darkColors(accent) else lightColors(accent),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
