package io.github.vieenrose.liveradiokaraoke.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Refined dark-blue "broadcast" palette.
val BlueDark = Color(0xFF02152B)
val BluePrimary = Color(0xFF2E9BFF)
val RedPrimary = Color(0xFFFF5247)        // softened stop/alert red
val Accent = Color(0xFF2E9BFF)            // vivid azure — pops on the dark gradient
val AccentHover = Color(0xFF5BB2FF)
val StatusLive = Color(0xFF49E08B)        // live/green
val StatusWarn = Color(0xFFFFCE5C)
val TextPrimary = Color(0xFFEAF1F8)
val TextSecondary = Color(0xFFA9BBD0)     // cool grey-blue
val SurfaceGlass = Color(0x12FFFFFF)      // frosted card (light tint, elevated)
val SurfaceGlass2 = Color(0xD9061A33)     // deep navy player bar
val BorderGlass = Color(0x22FFFFFF)

/** Subtle vertical gradient backdrop. */
val AppBackground = Brush.verticalGradient(listOf(Color(0xFF0A2E57), Color(0xFF03101F)))

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = RedPrimary,
    background = BlueDark,
    onBackground = TextPrimary,
    surface = BlueDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGlass,
    onSurfaceVariant = TextSecondary,
    error = RedPrimary,
)

@Composable
fun KaraokeTheme(content: @Composable () -> Unit) {
    // The app is dark-blue by design (matches the web app); ignore system light/dark.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkColors, typography = Typography(), content = content)
}
