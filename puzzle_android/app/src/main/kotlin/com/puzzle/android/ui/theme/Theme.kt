package com.puzzle.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary          = PrimaryLight,
    onPrimary        = OnPrimaryLight,
    primaryContainer = PrimaryContainerL,
    onPrimaryContainer = OnPrimaryContainerL,
    secondary        = SecondaryLight,
    tertiary         = TertiaryLight,
    background       = BackgroundLight,
    surface          = SurfaceLight,
    error            = ErrorLight
)

private val DarkColorScheme = darkColorScheme(
    primary          = PrimaryDark,
    onPrimary        = OnPrimaryDark,
    primaryContainer = PrimaryContainerD,
    onPrimaryContainer = OnPrimaryContainerD,
    secondary        = SecondaryDark,
    tertiary         = TertiaryDark,
    background       = BackgroundDark,
    surface          = SurfaceDark,
    error            = ErrorDark
)

/**
 * Top-level Material Design 3 theme for Puzzle Rose.
 *
 * Uses a static colour scheme (light/dark) for maximum OEM compatibility.
 * Light / dark switching follows the system setting automatically.
 */
@Composable
fun PuzzleAndroidTheme(
    darkTheme    : Boolean = isSystemInDarkTheme(),
    dynamicColor : Boolean = false,
    content      : @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PuzzleTypography,
        content     = content
    )
}
