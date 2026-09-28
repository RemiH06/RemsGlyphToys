package com.irofactory.rgt.fluid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * FluidBitmapRenderer
 * ───────────────────────────────────────────────────────────────────────────
 * Convierte la grilla rasterizada de [FluidSimulation] en un Bitmap que el
 * SDK de la Glyph Matrix escala 1:1 a los 25x25 LEDs reales via
 * GlyphMatrixObject.setImageSource.
 */
object FluidBitmapRenderer {

    fun render(
        grid:      Array<FloatArray>,
        mask:      Array<BooleanArray>,
        sizePx:    Int,
        colorArgb: Int = Color.WHITE
    ): Bitmap {
        val rows   = grid.size
        val cols   = grid.firstOrNull()?.size ?: 0
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (cols == 0 || rows == 0) return bitmap

        val cellSz = sizePx.toFloat() / maxOf(cols, rows)
        val dotR   = cellSz * 0.42f
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
        val r      = Color.red(colorArgb)
        val g      = Color.green(colorArgb)
        val b      = Color.blue(colorArgb)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (!mask[row][col]) continue
                val brightness = grid[row][col]
                if (brightness < 0.03f) continue

                val alpha = (brightness.coerceIn(0.15f, 1f) * 255).toInt()
                paint.color = Color.argb(alpha, r, g, b)

                val cx = col * cellSz + cellSz / 2f
                val cy = row * cellSz + cellSz / 2f
                canvas.drawCircle(cx, cy, dotR, paint)
            }
        }

        return bitmap
    }
}
