package com.irofactory.rgt.audio

import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AudioBlobSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Figura organica que respira con el sonido: su contorno es r(angulo), la
 * suma de un radio base mas un lobulo por cada banda de AudioSpectrumSource.
 * Cada banda tiene su propio numero de lobulos y gira a su propia velocidad,
 * asi la silueta nunca queda simetrica:
 *
 *   sub, bass  → inflan la figura y la empujan de lado (lobulos anchos)
 *   lowMid     → tres lobulos lentos
 *   vocal      → cuatro lobulos medianos, la "melodia" de la silueta
 *   presence   → cinco lobulos cortos
 *   air        → rizos finos en el borde y titileo del relleno
 *
 * Sin sonido queda un nucleo tenue que respira despacio. Motor puro, sin
 * dependencias de Android; lo comparten el toy y la vista previa.
 */
class AudioBlobSimulation {

    private val n = GlyphFrames.SIZE
    private val center = n / 2f
    private val maxRadius = GlyphFrames.LED_RADIUS + 0.4f

    // Por banda: lobulos alrededor del contorno, amplitud en celdas, giro (rad/s)
    private val lobes     = intArrayOf(1, 2, 3, 4, 5, 7)
    private val amplitude = floatArrayOf(1.8f, 2.2f, 1.5f, 2.0f, 1.1f, 0.7f)
    private val spin      = floatArrayOf(0.13f, -0.21f, 0.34f, -0.47f, 0.71f, -1.1f)
    private val phase     = FloatArray(AudioSpectrumSource.BAND_COUNT) { it * 1.3f }

    private val bands = FloatArray(AudioSpectrumSource.BAND_COUNT)
    private var loudness = 0f
    private var time = 0f
    private var frame = 0

    fun step(dt: Float, bandLevels: FloatArray, loud: Float) {
        time += dt
        frame++
        bandLevels.copyInto(bands, endIndex = minOf(bands.size, bandLevels.size))
        loudness = loud
        for (b in phase.indices) {
            // Las bandas activas giran un poco mas rapido: la figura se "agita" con ellas
            phase[b] += spin[b] * (1f + bands[b] * 1.5f) * dt
        }
    }

    private fun radiusAt(theta: Float): Float {
        val idle = 3.0f + 0.35f * sin(time * 1.1f)
        val inflate = 4.2f * (0.6f * bands[1] + 0.4f * bands[0]) + 1.6f * bands[3] + 0.8f * bands[2]
        var r = idle + inflate
        for (b in bands.indices) {
            val lobe = 0.5f + 0.5f * cos(lobes[b] * (theta - phase[b]))
            r += amplitude[b] * bands[b] * lobe
        }
        // Deriva lenta para que ni siquiera el reposo sea un circulo perfecto
        r += 0.45f * sin(2f * theta + time * 0.6f) * (0.4f + loudness)
        return r.coerceIn(1.5f, maxRadius)
    }

    fun rasterize(): Array<FloatArray> {
        val grid = Array(n) { FloatArray(n) }
        val fill = 0.22f + 0.38f * loudness
        val sparkle = bands[5]
        val edge = 1.1f

        for (row in 0 until n) {
            for (col in 0 until n) {
                val dx = col + 0.5f - center
                val dy = row + 0.5f - center
                val d = sqrt(dx * dx + dy * dy)
                if (d > GlyphFrames.LED_RADIUS) continue

                val r = radiusAt(atan2(dy, dx))
                if (d > r + 0.6f) continue

                val rim = (1f - abs(d - (r - 0.5f)) / edge).coerceIn(0f, 1f)
                var inside = if (d < r - 0.5f) fill else ((r + 0.6f - d) / 1.1f).coerceIn(0f, 1f) * fill
                if (d < r - 1.2f && sparkle > 0.05f) {
                    inside += sparkle * 0.45f * noise(col, row)
                }
                grid[row][col] = maxOf(inside, rim * (0.55f + 0.45f * loudness)).coerceIn(0f, 1f)
            }
        }
        return grid
    }

    /** Ruido determinista por celda y frame (0..1): titileo de los agudos. */
    private fun noise(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263 + (frame / 3) * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }
}
