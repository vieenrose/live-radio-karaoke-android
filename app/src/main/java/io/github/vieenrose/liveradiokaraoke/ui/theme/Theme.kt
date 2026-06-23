package io.github.vieenrose.liveradiokaraoke.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design tokens ported from frontend/css/style.css :root.
val BlueDark = Color(0xFF001F3F)
val BluePrimary = Color(0xFF0074D9)
val RedPrimary = Color(0xFFFF4136)
val Accent = Color(0xFF0074D9)
val AccentHover = Color(0xFF2A90EE)
val StatusLive = Color(0xFF4ADE80)
val StatusWarn = Color(0xFFFFD166)
val TextPrimary = Color(0xFFF0F0F0)
val TextSecondary = Color(0xFFC4C4C4)
val SurfaceGlass = Color(0x33000000)
val SurfaceGlass2 = Color(0x80000000)
val BorderGlass = Color(0x1FFFFFFF)

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
