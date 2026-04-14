package com.puzzle.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
 * Top-level Material Design 3 theme for the Puzzle Android app.
 *
 * On Android 12+ (API 31) dynamic colour is used when [dynamicColor] is true.
 * Light / dark switching follows the system setting automatically.
 */
@Composable
fun PuzzleAndroidTheme(
    darkTheme    : Boolean = isSystemInDarkTheme(),
    dynamicColor : Boolean = true,
    content      : @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PuzzleTypography,
        content     = content
    )
}
