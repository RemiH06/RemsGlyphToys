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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ── Esquemas Material ─────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = DarkMagenta,
    onPrimary        = DarkBg,
    secondary        = DarkCyan,
    onSecondary      = DarkBg,
    tertiary         = DarkLime,
    background       = DarkBg,
    onBackground     = DarkText,
    surface          = DarkBg2,
    onSurface        = DarkText,
    surfaceVariant   = DarkBg3,
    onSurfaceVariant = DarkText2,
    outline          = DarkBorder2,
    outlineVariant   = DarkBorder,
    error            = DarkMagenta,
    onError          = DarkBg,
)

private val LightColorScheme = lightColorScheme(
    primary          = LightMagenta,
    onPrimary        = LightBg,
    secondary        = LightCyan,
    onSecondary      = LightBg,
    tertiary         = LightLime,
    background       = LightBg,
    onBackground     = LightText,
    surface          = LightBg2,
    onSurface        = LightText,
    surfaceVariant   = LightBg3,
    onSurfaceVariant = LightText2,
    outline          = LightBorder2,
    outlineVariant   = LightBorder,
    error            = LightMagenta,
    onError          = LightBg,
)

// ── Tokens semanticos sherry_theme ────────────────────────────────────────────
data class SherryColors(
    val bg:       Color,
    val bg2:      Color,
    val bg3:      Color,
    val text:     Color,
    val text2:    Color,
    val text3:    Color,
    val border:   Color,
    val border2:  Color,
    val magenta:  Color,
    val cyan:     Color,
    val lime:     Color,
    val violet:   Color,
    val electric: Color,
    /** Los neones solo brillan en oscuro; en claro el HTML pone --glow-* en none. */
    val glow:     Boolean,
    val isDark:   Boolean
) {
    val accent: Color get() = magenta
    val accent2: Color get() = cyan
}

val DarkSherryColors = SherryColors(
    bg = DarkBg, bg2 = DarkBg2, bg3 = DarkBg3,
    text = DarkText, text2 = DarkText2, text3 = DarkText3,
    border = DarkBorder, border2 = DarkBorder2,
    magenta = DarkMagenta, cyan = DarkCyan, lime = DarkLime,
    violet = DarkViolet, electric = DarkElectric,
    glow = true, isDark = true
)

val LightSherryColors = SherryColors(
    bg = LightBg, bg2 = LightBg2, bg3 = LightBg3,
    text = LightText, text2 = LightText2, text3 = LightText3,
    border = LightBorder, border2 = LightBorder2,
    magenta = LightMagenta, cyan = LightCyan, lime = LightLime,
    violet = LightViolet, electric = LightElectric,
    glow = false, isDark = false
)

val LocalSherryColors = staticCompositionLocalOf { DarkSherryColors }

val sherryColors: SherryColors
    @Composable get() = LocalSherryColors.current

/**
 * Scanlines CRT: franjas de 2dp cada 4dp, como el repeating-linear-gradient
 * de body.light::before en el demo (en oscuro el HTML las deja opcionales).
 */
fun Modifier.crtScanlines(color: Color): Modifier = drawWithContent {
    drawContent()
    val period = 4.dp.toPx()
    val line = 2.dp.toPx()
    var y = line
    while (y < size.height) {
        drawRect(color = color, topLeft = Offset(0f, y), size = Size(size.width, line))
        y += period
    }
}

@Composable
fun RemsGlyphToysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val sherry      = if (darkTheme) DarkSherryColors else LightSherryColors
    val view        = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSherryColors provides sherry) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
