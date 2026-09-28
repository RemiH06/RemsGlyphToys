package com.irofactory.rgt.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Esquemas ──────────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = AccentGreen,
    onPrimary        = Background,
    secondary        = PurpleAccent,
    onSecondary      = Background,
    tertiary         = InfoBlue,
    background       = Background,
    onBackground     = TextPrimary,
    surface          = Surface1,
    onSurface        = TextPrimary,
    surfaceVariant   = Surface2,
    onSurfaceVariant = TextSecondary,
    outline          = Border,
    error            = DangerRed,
    onError          = Background,
)

private val LightColorScheme = lightColorScheme(
    primary          = LightAccent,
    onPrimary        = LightBackground,
    secondary        = LightPurple,
    onSecondary      = LightBackground,
    tertiary         = LightBlue,
    background       = LightBackground,
    onBackground     = LightTextPrimary,
    surface          = LightSurface1,
    onSurface        = LightTextPrimary,
    surfaceVariant   = LightSurface2,
    onSurfaceVariant = LightTextSecondary,
    outline          = LightBorder,
    error            = LightDanger,
    onError          = LightBackground,
)

// ── CompositionLocal para colores semanticos extra (metro_theme) ─────────────
data class MetroColors(
    val background:    Color,
    val surface1:      Color,
    val surface2:      Color,
    val border:        Color,
    val textPrimary:   Color,
    val textSecondary: Color,
    val textMuted:     Color,
    val accent:        Color,
    val warn:          Color,
    val danger:        Color,
    val blue:          Color,
    val purple:        Color,
    val orange:        Color,
    val isDark:        Boolean
)

val DarkMetroColors = MetroColors(
    background    = Background,
    surface1      = Surface1,
    surface2      = Surface2,
    border        = Border,
    textPrimary   = TextPrimary,
    textSecondary = TextSecondary,
    textMuted     = TextMuted,
    accent        = AccentGreen,
    warn          = WarnAmber,
    danger        = DangerRed,
    blue          = InfoBlue,
    purple        = PurpleAccent,
    orange        = OrangeAccent,
    isDark        = true
)

val LightMetroColors = MetroColors(
    background    = LightBackground,
    surface1      = LightSurface1,
    surface2      = LightSurface2,
    border        = LightBorder,
    textPrimary   = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted     = LightTextMuted,
    accent        = LightAccent,
    warn          = LightWarn,
    danger        = LightDanger,
    blue          = LightBlue,
    purple        = LightPurple,
    orange        = LightOrange,
    isDark        = false
)

val LocalMetroColors = staticCompositionLocalOf { DarkMetroColors }

// Acceso facil desde cualquier composable
val metroColors: MetroColors
    @Composable get() = LocalMetroColors.current

@Composable
fun RemsGlyphToysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val metro       = if (darkTheme) DarkMetroColors else LightMetroColors
    val view        = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalMetroColors provides metro) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
