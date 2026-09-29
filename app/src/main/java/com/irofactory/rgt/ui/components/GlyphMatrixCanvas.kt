package com.irofactory.rgt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.irofactory.rgt.glyph.GlyphFrames
import com.irofactory.rgt.ui.theme.sherryColors

/**
 * Vista previa de la Glyph Matrix con la forma real del Phone (3): los 489
 * LEDs como cuadros sobre un disco oscuro, y el resto de la grilla 25x25
 * como celdas tenues, igual que el diagrama oficial de Nothing. En oscuro
 * los LEDs encendidos llevan halo (los --glow-* del sherry_theme).
 */
@Composable
fun GlyphMatrixCanvas(
    grid:     Array<FloatArray>?,
    neon:     Color,
    modifier: Modifier = Modifier,
    onClick:  (() -> Unit)? = null
) {
    val sc = sherryColors
    val mask = remember { GlyphFrames.circularMask() }
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.78f)
            .aspectRatio(1f)
            .then(clickable)
    ) {
        val n = GlyphFrames.SIZE
        // Una celda de margen por lado: el disco es mas grande que la grilla
        val pitch = size.width / (n + 2)
        val led = pitch * 0.78f
        val inset = pitch + (pitch - led) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Disco detras de los LEDs, un poco mas grande que la matriz real
        drawCircle(color = sc.bg2, radius = pitch * (GlyphFrames.LED_RADIUS + 0.9f), center = center)
        drawCircle(
            color = sc.border2,
            radius = pitch * (GlyphFrames.LED_RADIUS + 0.9f),
            center = center,
            style = Stroke(width = 1f)
        )

        val gridColor = sc.border2.copy(alpha = 0.45f)
        val offColor = sc.border2.copy(alpha = if (sc.isDark) 0.9f else 0.7f)

        for (r in 0 until n) {
            for (c in 0 until n) {
                val topLeft = Offset(c * pitch + inset, r * pitch + inset)
                val cell = Size(led, led)

                if (!mask[r][c]) {
                    drawRect(color = gridColor, topLeft = topLeft, size = cell, style = Stroke(width = 1f))
                    continue
                }

                val b = grid?.getOrNull(r)?.getOrNull(c) ?: 0f
                if (b <= 0.02f || b.isNaN()) {
                    drawRect(color = offColor, topLeft = topLeft, size = cell)
                    continue
                }
                if (sc.glow && b > 0.35f) {
                    val halo = led * 0.45f
                    drawRect(
                        color = neon.copy(alpha = 0.14f * b),
                        topLeft = Offset(topLeft.x - halo, topLeft.y - halo),
                        size = Size(led + halo * 2, led + halo * 2)
                    )
                }
                drawRect(color = offColor, topLeft = topLeft, size = cell)
                drawRect(color = neon.copy(alpha = b.coerceIn(0.1f, 1f)), topLeft = topLeft, size = cell)
            }
        }
    }
}
