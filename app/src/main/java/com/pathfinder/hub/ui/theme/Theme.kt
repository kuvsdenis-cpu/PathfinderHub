package com.pathfinder.hub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PathfinderBlue,
    onPrimary = Color.White,
    primaryContainer = PathfinderBlueLight,
    onPrimaryContainer = Color.White,
    secondary = PathfinderYellow,
    onSecondary = Color.Black,
    tertiary = PathfinderGreen,
    error = PathfinderRed,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = PathfinderGrayLight,
    onSurfaceVariant = PathfinderGray
)

private val DarkColors = darkColorScheme(
    primary = PathfinderBlueLight,
    onPrimary = Color.Black,
    primaryContainer = PathfinderBlueDark,
    onPrimaryContainer = Color.White,
    secondary = PathfinderYellowDark,
    onSecondary = Color.Black,
    tertiary = PathfinderGreen,
    error = PathfinderRed,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun PathfinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PathfinderTypography,
        content = content
    )
}