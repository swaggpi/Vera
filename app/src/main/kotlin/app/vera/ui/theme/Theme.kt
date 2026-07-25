package app.vera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Vera "investigator's desk" palette — amber = insight, violet = method. Ported from the web prototype.
val Amber = Color(0xFFF4B740)
val Violet = Color(0xFF9A8CF5)
val Rose = Color(0xFFE8836B)
val Teal = Color(0xFF57C99A)
val Ink = Color(0xFF12141C)
val Surface = Color(0xFF1B2030)
val SurfaceHi = Color(0xFF262C3D)
val TextHi = Color(0xFFECEEF5)
val TextMut = Color(0xFF9AA3B8)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Violet,
    onSecondary = Ink,
    tertiary = Teal,
    background = Ink,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = TextMut,
    error = Rose,
    outline = Color(0xFF3A4157)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB5791E),
    secondary = Color(0xFF6D5CD6),
    background = Color(0xFFF5F3EE),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1D27),
    onSurface = Color(0xFF1A1D27)
)

@Composable
fun VeraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
