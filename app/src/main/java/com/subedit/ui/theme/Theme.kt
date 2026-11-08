package com.subedit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SubEditColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = BgDarkest,
    secondary = AccentGreen,
    onSecondary = BgDarkest,
    tertiary = AccentPurple,
    background = BgMain,
    onBackground = TextMain,
    surface = BgCard,
    onSurface = TextMain,
    surfaceVariant = BgSurface,
    onSurfaceVariant = TextMuted,
    outline = BorderColor,
    error = AccentCoral,
)

@Composable
fun SubEditTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SubEditColorScheme,
        typography = SubEditTypography,
        content = content,
    )
}
