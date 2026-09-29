package com.irofactory.rgt.gallery

import android.graphics.Bitmap
import android.graphics.Color
import com.irofactory.rgt.glyph.GlyphFrames

/**
 * GalleryBitmapRenderer
 * ───────────────────────────────────────────────────────────────────────────
 * Reduce una foto a la Glyph Matrix 25x25 por average pooling: recorta al
 * centro un cuadrado, lo baja a una resolucion intermedia y promedia la
 * luminancia de cada bloque. Devuelve una grilla de brillo 0f..1f, con las
 * celdas fuera del circulo real de la matriz en 0.
 */
object GalleryBitmapRenderer {

    fun toGrid(source: Bitmap, size: Int = GlyphFrames.SIZE): Array<FloatArray> {
        val squareSize = minOf(source.width, source.height)
        val xOff = (source.width - squareSize) / 2
        val yOff = (source.height - squareSize) / 2
        val square = Bitmap.createBitmap(source, xOff, yOff, squareSize, squareSize)

        val poolSrcSize = size * 8
        val poolSrc = Bitmap.createScaledBitmap(square, poolSrcSize, poolSrcSize, true)
        if (square !== source && square !== poolSrc) square.recycle()

        val mask = GlyphFrames.circularMask(size)
        val block = poolSrcSize / size
        val pixels = IntArray(poolSrcSize * poolSrcSize)
        poolSrc.getPixels(pixels, 0, poolSrcSize, 0, 0, poolSrcSize, poolSrcSize)
        poolSrc.recycle()

        return Array(size) { r ->
            FloatArray(size) { c ->
                if (!mask[r][c]) return@FloatArray 0f
                var sum = 0L
                for (y in r * block until (r + 1) * block) {
                    for (x in c * block until (c + 1) * block) {
                        val px = pixels[y * poolSrcSize + x]
                        sum += (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                    }
                }
                (sum / (block * block).toFloat() / 255f).coerceIn(0f, 1f)
            }
        }
    }
}
