package com.manish.vahakvigil.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccentBlue,
    onPrimary = Color(0xFF111827),
    primaryContainer = DarkAccentBlueContainer,
    onPrimaryContainer = DarkText,
    secondary = DarkSuccess,
    onSecondary = Color(0xFF082114),
    tertiary = Color(0xFFE5A6C5),
    background = DarkGradientCenter,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkCardHigh,
    onSurfaceVariant = DarkMutedText,
    outline = DarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccentRed,
    onPrimary = Color.White,
    primaryContainer = LightAccentRedContainer,
    onPrimaryContainer = LightText,
    secondary = LightSuccess,
    onSecondary = Color.White,
    tertiary = Color(0xFFB66A8E),
    background = LightGradientMiddle,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightCardHigh,
    onSurfaceVariant = LightMutedText,
    outline = LightDivider
)

data class VahakVigilColors(
    val backgroundStops: List<Pair<Float, Color>>,
    val card: Color,
    val cardHigh: Color,
    val headingText: Color,
    val mutedText: Color,
    val divider: Color,
    val primary: Color,
    val primaryContainer: Color,
    val success: Color,
    val successContainer: Color,
    val cardHighlight: Color,
    val cardShadow: Color,
    val insetHighlight: Color,
    val insetShadow: Color,
    val motorcycleLine: Color
)

private val LightVahakColors = VahakVigilColors(
    backgroundStops = listOf(
        0.00f to LightGradientTop,
        0.24f to LightGradientUpperMiddle,
        0.50f to LightGradientCenter,
        0.74f to LightGradientLower,
        1.00f to LightGradientBottom
    ),
    card = LightCard,
    cardHigh = LightCardHigh,
    headingText = LightText,
    mutedText = LightMutedText,
    divider = LightDivider,
    primary = LightAccentRed,
    primaryContainer = LightAccentRedContainer,
    success = LightSuccess,
    successContainer = LightSuccessContainer,
    cardHighlight = Color.White.copy(alpha = 0.90f),
    cardShadow = Color(0xFF64748B).copy(alpha = 0.16f),
    insetHighlight = Color.White.copy(alpha = 0.85f),
    insetShadow = Color(0xFF64748B).copy(alpha = 0.20f),
    motorcycleLine = Color(0xFF1E293B)
)

private val DarkVahakColors = VahakVigilColors(
    backgroundStops = listOf(
        0.00f to DarkGradientTop,
        0.24f to DarkGradientUpperMiddle,
        0.50f to DarkGradientCenter,
        0.74f to DarkGradientLower,
        1.00f to DarkGradientBottom
    ),
    card = DarkCard,
    cardHigh = DarkCardHigh,
    headingText = DarkText,
    mutedText = DarkMutedText,
    divider = DarkDivider,
    primary = DarkAccentBlue,
    primaryContainer = DarkAccentBlueContainer,
    success = DarkSuccess,
    successContainer = DarkSuccessContainer,
    cardHighlight = Color.White.copy(alpha = 0.06f),
    cardShadow = Color.Black.copy(alpha = 0.42f),
    insetHighlight = Color.White.copy(alpha = 0.05f),
    insetShadow = Color.Black.copy(alpha = 0.34f),
    motorcycleLine = Color(0xFFF8F8F8)
)

private val LocalVahakVigilColors = staticCompositionLocalOf { LightVahakColors }

val MaterialTheme.vahakColors: VahakVigilColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVahakVigilColors.current

@Suppress("UNUSED_PARAMETER")
@Composable
fun VahakVigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val vahakColors = if (darkTheme) DarkVahakColors else LightVahakColors

    CompositionLocalProvider(LocalVahakVigilColors provides vahakColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun VahakVigilGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val appColors = MaterialTheme.vahakColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = appColors.backgroundStops.toTypedArray()
                )
            )
    ) {
        content()
    }
}
