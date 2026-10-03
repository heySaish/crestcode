package com.crest.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CrestAccentPrimary,
    secondary = CrestAccentGreen,
    background = CrestBackground,
    surface = CrestSurface,
    surfaceVariant = CrestSurfaceHeader,
    onPrimary = CrestTextActive,
    onBackground = CrestTextPrimary,
    onSurface = CrestTextPrimary
)

@Composable
fun CrestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
