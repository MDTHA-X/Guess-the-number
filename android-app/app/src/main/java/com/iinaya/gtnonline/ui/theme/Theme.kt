package com.iinaya.gtnonline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    secondary = Ocean,
    tertiary = Tangerine,
    background = LightSurface,
    surface = CardGlass,
    onSurface = Slate,
    error = DangerRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    secondary = Ocean,
    tertiary = Tangerine,
    background = Color(0xFF0B1428),
    surface = Color(0xFF121E36),
    onSurface = Color(0xFFEAF1FF),
    onBackground = Color(0xFFEAF1FF),
    error = DangerRed,
)

@Composable
fun GuessTheNumberOnlineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}
