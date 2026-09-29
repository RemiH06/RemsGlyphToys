package com.irofactory.rgt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.ui.theme.sherryColors

/**
 * Vista previa en pantalla de la Glyph Matrix: puntos sobre un disco, brillo
 * 0f..1f por celda. En oscuro los puntos encendidos llevan halo, como los
 * --glow-* del sherry_theme.
 */
@Composable
fun GlyphMatrixCanvas(
    grid:     Array<FloatArray>?,
    mask:     Array<BooleanArray>,
    neon:     Color,
    modifier: Modifier = Modifier,
    onClick:  (() -> Unit)? = null
) {
    val sc = sherryColors
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(sc.bg2)
            .border(1.dp, sc.border2, CircleShape)
            .then(clickable)
            .padding(6.dp)
    ) {
        val rows = mask.size
        val cols = mask.firstOrNull()?.size ?: return@Canvas
        val cellW = size.width / cols
        val cellH = size.height / rows
        val dotR = cellW * 0.38f
        val offColor = sc.border2.copy(alpha = 0.55f)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!mask[r][c]) continue
                val brightness = grid?.getOrNull(r)?.getOrNull(c) ?: 0f
                val center = Offset(c * cellW + cellW / 2f, r * cellH + cellH / 2f)

                if (brightness <= 0.01f || brightness.isNaN()) {
                    drawCircle(color = offColor, radius = dotR * 0.8f, center = center)
                    continue
                }
                if (sc.glow && brightness > 0.35f) {
                    drawCircle(
                        color = neon.copy(alpha = 0.16f * brightness),
                        radius = dotR * 2f,
                        center = center
                    )
                }
                drawCircle(
                    color = neon.copy(alpha = brightness.coerceIn(0.08f, 1f)),
                    radius = dotR,
                    center = center
                )
            }
        }
    }
}
