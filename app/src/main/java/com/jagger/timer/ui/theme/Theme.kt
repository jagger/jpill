package com.jagger.timer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * jPill look: cool graphite surfaces, slate lines, one ice-blue accent. The per-medicine colour you pick is the
 * only saturated colour on screen, so it stays the thing the eye lands on. The home-screen widget uses the same tokens.
 */
private val Dark = darkColorScheme(
    primary = Color(0xFFBFE3FF), onPrimary = Color(0xFF0B2438),
    primaryContainer = Color(0xFF173247), onPrimaryContainer = Color(0xFFCFE8FF),
    secondary = Color(0xFF9FB3C6), onSecondary = Color(0xFF0F1720),
    secondaryContainer = Color(0xFF1F2A35), onSecondaryContainer = Color(0xFFCFE8FF),
    tertiary = Color(0xFF9ED8C8), onTertiary = Color(0xFF07251D),
    background = Color(0xFF0D1217), onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF10151B), onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF1F2A35), onSurfaceVariant = Color(0xFF8E9BAA),
    surfaceContainerLowest = Color(0xFF0B0F14), surfaceContainerLow = Color(0xFF12181F),
    surfaceContainer = Color(0xFF151C24), surfaceContainerHigh = Color(0xFF1A222B),
    surfaceContainerHighest = Color(0xFF1B242E),
    outline = Color(0xFF6B7D8F), outlineVariant = Color(0xFF26303B),
    error = Color(0xFFFF8A80), onError = Color(0xFF3B0A06),
    errorContainer = Color(0xFF4A1A16), onErrorContainer = Color(0xFFFFDAD6)
)

private val Light = lightColorScheme(
    primary = Color(0xFF0B3A5C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E7F5), onPrimaryContainer = Color(0xFF0B3A5C),
    secondary = Color(0xFF4B6074), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4EC), onSecondaryContainer = Color(0xFF0B3A5C),
    tertiary = Color(0xFF1F6F5C), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F8FB), onBackground = Color(0xFF0F1720),
    surface = Color(0xFFEEF2F6), onSurface = Color(0xFF0F1720),
    surfaceVariant = Color(0xFFDCE4EC), onSurfaceVariant = Color(0xFF516070),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE9EEF3), surfaceContainerHigh = Color(0xFFE3E9EF),
    surfaceContainerHighest = Color(0xFFE6ECF2),
    outline = Color(0xFF7A8998), outlineVariant = Color(0xFFD3DBE4),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private val Sans = FontFamily.SansSerif

/** Light-weight large type with open tracking reads precise; small type stays regular for legibility. */
private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
)

/** Big radii on cards, full pills on buttons and chips, so surfaces feel machined rather than boxy. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun JPillTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
