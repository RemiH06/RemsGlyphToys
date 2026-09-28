package com.irofactory.rgt.gallery

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * GalleryBitmapRenderer
 * ───────────────────────────────────────────────────────────────────────────
 * Reduce una foto a la Glyph Matrix 25x25 por average pooling: recorta al
 * centro un cuadrado, la basa a una resolucion intermedia y promedia cada
 * bloque a un pixel en escala de grises, respetando el area circular real
 * de la matriz.
 */
object GalleryBitmapRenderer {

    fun toMatrix(source: Bitmap, size: Int = 25): Bitmap {
        val squareSize = minOf(source.width, source.height)
        val xOff = (source.width - squareSize) / 2
        val yOff = (source.height - squareSize) / 2
        val square = Bitmap.createBitmap(source, xOff, yOff, squareSize, squareSize)

        val poolSrcSize = size * 8
        val poolSrc = Bitmap.createScaledBitmap(square, poolSrcSize, poolSrcSize, true)
        if (square !== source) square.recycle()

        val mask = circularMask(size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val block = poolSrcSize / size

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!mask[r][c]) {
                    out.setPixel(c, r, Color.TRANSPARENT)
                    continue
                }
                var sum = 0L
                var count = 0
                val y0 = r * block
                val x0 = c * block
                for (y in y0 until y0 + block) {
                    for (x in x0 until x0 + block) {
                        val px = poolSrc.getPixel(x, y)
                        sum += (Color.red(px) * 30 + Color.green(px) * 59 + Color.blue(px) * 11) / 100
                        count++
                    }
                }
                val avg = (sum / count.coerceAtLeast(1)).toInt().coerceIn(0, 255)
                out.setPixel(c, r, Color.argb(255, avg, avg, avg))
            }
        }

        poolSrc.recycle()
        return out
    }

    private fun circularMask(size: Int): Array<BooleanArray> {
        val center = size / 2f
        val radius = size / 2f - 0.5f
        return Array(size) { r ->
            BooleanArray(size) { c ->
                val dx = c + 0.5f - center
                val dy = r + 0.5f - center
                sqrt(dx * dx + dy * dy) <= radius
            }
        }
    }
}
