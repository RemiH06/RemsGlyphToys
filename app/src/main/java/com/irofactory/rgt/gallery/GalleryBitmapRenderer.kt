package com.irofactory.rgt.gallery

import android.graphics.Bitmap
import android.graphics.Color
import com.irofactory.rgt.glyph.GlyphFrames

/**
 * GalleryBitmapRenderer
 * ───────────────────────────────────────────────────────────────────────────
 * Reduce una foto a la Glyph Matrix 25x25 para que siga siendo reconocible:
 *
 *   1. Recorte cuadrado al centro y average pooling por bloques.
 *   2. Niveles: estira del percentil 2% al 98% de la foto a 0..1. Sin esto
 *      el promedio deja casi todo en grises medios y en LEDs se ve una mancha.
 *   3. Unsharp mask 3x3: recupera bordes que el pooling suaviza.
 *
 * La grilla queda en espacio perceptual (como se ve en pantalla); la
 * correccion gamma para los LEDs se aplica al armar el frame.
 */
object GalleryBitmapRenderer {

    const val LED_GAMMA = 2.2f
    private const val SHARPEN = 0.6f

    class Stats(val mean: Float, val low: Float, val high: Float)

    fun toGrid(source: Bitmap): Pair<Array<FloatArray>, Stats> = process(pool(source))

    private fun pool(source: Bitmap): Array<FloatArray> {
        val size = GlyphFrames.SIZE
        val squareSize = minOf(source.width, source.height)
        val xOff = (source.width - squareSize) / 2
        val yOff = (source.height - squareSize) / 2
        val square = Bitmap.createBitmap(source, xOff, yOff, squareSize, squareSize)

        val poolSrcSize = size * 8
        val poolSrc = Bitmap.createScaledBitmap(square, poolSrcSize, poolSrcSize, true)
        if (square !== source && square !== poolSrc) square.recycle()

        val block = poolSrcSize / size
        val pixels = IntArray(poolSrcSize * poolSrcSize)
        poolSrc.getPixels(pixels, 0, poolSrcSize, 0, 0, poolSrcSize, poolSrcSize)
        if (poolSrc !== source) poolSrc.recycle()

        return Array(size) { r ->
            FloatArray(size) { c ->
                var sum = 0L
                for (y in r * block until (r + 1) * block) {
                    for (x in c * block until (c + 1) * block) {
                        val px = pixels[y * poolSrcSize + x]
                        sum += (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                    }
                }
                sum / (block * block).toFloat() / 255f
            }
        }
    }

    private fun process(lum: Array<FloatArray>): Pair<Array<FloatArray>, Stats> {
        val size = GlyphFrames.SIZE
        val mask = GlyphFrames.circularMask()

        val values = buildList {
            for (r in 0 until size) for (c in 0 until size) if (mask[r][c]) add(lum[r][c])
        }.sorted()
        val low = values[(values.size * 0.02f).toInt()]
        val high = values[((values.size - 1) * 0.98f).toInt()]
        val span = (high - low).takeIf { it > 0.04f } ?: 1f
        val offset = if (span == 1f) 0f else low

        val leveled = Array(size) { r -> FloatArray(size) { c -> ((lum[r][c] - offset) / span).coerceIn(0f, 1f) } }

        val out = Array(size) { r ->
            FloatArray(size) { c ->
                if (!mask[r][c]) return@FloatArray 0f
                var sum = 0f
                var count = 0
                for (dr in -1..1) for (dc in -1..1) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr in 0 until size && cc in 0 until size && mask[rr][cc]) {
                        sum += leveled[rr][cc]; count++
                    }
                }
                val v = leveled[r][c]
                (v + SHARPEN * (v - sum / count)).coerceIn(0f, 1f)
            }
        }
        return out to Stats(mean = values.average().toFloat(), low = low, high = high)
    }
}
