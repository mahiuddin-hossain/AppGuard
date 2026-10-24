package com.mahiuddin.appguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppGuardColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = LightGreenSurface,
    onPrimaryContainer = DarkGreen,
    secondary = DarkGreen,
    onSecondary = Color.White,
    secondaryContainer = LightGreenSurface,
    onSecondaryContainer = DarkGreen,
    background = MainBackground,
    onBackground = PrimaryText,
    surface = CardBackground,
    onSurface = PrimaryText,
    surfaceVariant = MutedBackground,
    onSurfaceVariant = SecondaryText,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder,
    error = StatusRed,
    onError = Color.White,
    errorContainer = StatusRedSurface,
    onErrorContainer = StatusRed
)

@Composable
fun AppGuardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppGuardColorScheme,
        typography = Typography,
        content = content
    )
}
