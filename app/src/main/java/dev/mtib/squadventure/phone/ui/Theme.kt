package dev.mtib.squadventure.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Squadrat green — the "big square" accent and the app's primary. */
val Squadrat = Color(0xFF5BD98A)

/** Squadratinho amber — the "small square" accent. */
val Squadratinho = Color(0xFFF5A623)

/** Trail blue — GPX routes and the Strava-style heatmap base hue. */
val Trail = Color(0xFF7CC7FF)

private val SquadventureColors = darkColorScheme(
    primary = Squadrat,
    secondary = Squadratinho,
    tertiary = Trail,
    background = Color(0xFF000000),
    surface = Color(0xFF0A0F0C),
    surfaceVariant = Color(0xFF141B16),
    onPrimary = Color(0xFF00160A),
    onBackground = Color(0xFFE6EFE9),
    onSurface = Color(0xFFE6EFE9),
)

@Composable
fun SquadventureTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SquadventureColors, content = content)
}
