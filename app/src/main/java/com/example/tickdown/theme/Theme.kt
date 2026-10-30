package com.example.tickdown.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TickdownBlueDark,
    onPrimary = TickdownBlackDark,
    primaryContainer = TickdownBlueBadge,
    onPrimaryContainer = TickdownNearWhite,
    secondary = TickdownNearWhite,
    onSecondary = TickdownBlackDark,
    secondaryContainer = TickdownSecondaryCardDark,
    onSecondaryContainer = TickdownNearWhite,
    tertiary = TickdownBlueDark,
    background = TickdownBlackDark,
    onBackground = TickdownNearWhite,
    surface = TickdownSurfaceDark,
    onSurface = TickdownNearWhite,
    surfaceVariant = TickdownSecondaryCardDark,
    onSurfaceVariant = TickdownMutedDark,
    outline = TickdownBorderDark,
    outlineVariant = TickdownBorderDark.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = TickdownBlueAccent,
    onPrimary = Color.White,
    primaryContainer = TickdownBlueLight.copy(alpha = 0.15f),
    onPrimaryContainer = TickdownBlueAccent,
    secondary = TickdownNearBlack,
    onSecondary = TickdownOffWhite,
    secondaryContainer = TickdownSecondaryCardLight,
    onSecondaryContainer = TickdownNearBlack,
    tertiary = TickdownBlueAccent,
    background = TickdownOffWhite,
    onBackground = TickdownNearBlack,
    surface = TickdownSurfaceLight,
    onSurface = TickdownNearBlack,
    surfaceVariant = TickdownSecondaryCardLight,
    onSurfaceVariant = TickdownMutedLight,
    outline = TickdownBorderLight,
    outlineVariant = TickdownBorderLight.copy(alpha = 0.4f)
)

@Composable
fun TickdownTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
