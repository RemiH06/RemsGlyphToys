package com.irofactory.rgt.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * AudioSphereSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Esfera que respira con el nivel de audio y dispara ondas expansivas al
 * detectar un pico (estilo NCS). Motor puro, sin dependencias de Android;
 * [rawLevel] entra ya calculado (ver AudioLevelSource, RMS del waveform de
 * salida del sistema, funciona igual con bocina o audifonos).
 */
class AudioSphereSimulation(
    val cols: Int = 25,
    val rows: Int = 25
) {
    data class Ring(var radius: Float, var life: Float)

    val centerX: Float = cols / 2f
    val centerY: Float = rows / 2f
    val boundsRadius: Float = minOf(cols, rows) / 2f - 0.5f

    private val baseRadius     = boundsRadius * 0.18f
    private val ringThickness  = 1.1f
    private val ringSpeed      = boundsRadius * 1.6f  // celdas/seg
    private val ringDecay      = 1.1f                  // vida/seg
    private val peakThreshold  = 1.35f
    private val peakMargin     = 0.03f
    private val peakCooldown   = 0.16f                 // seg minimos entre ondas

    var level: Float = 0f
        private set
    private var smoothedLevel = 0f
    private var rollingAvg    = 0.05f
    private var cooldown      = 0f
    private val rings = mutableListOf<Ring>()

    fun step(dt: Float, rawLevel: Float) {
        level = rawLevel.coerceIn(0f, 1f)

        val fastLerp = 1f - exp(-dt / 0.08f)
        val slowLerp = 1f - exp(-dt / 0.8f)
        smoothedLevel += (level - smoothedLevel) * fastLerp
        rollingAvg += (level - rollingAvg) * slowLerp
        cooldown = (cooldown - dt).coerceAtLeast(0f)

        if (cooldown <= 0f && level > rollingAvg * peakThreshold + peakMargin) {
            rings.add(Ring(radius = baseRadius, life = 1f))
            cooldown = peakCooldown
        }

        val it = rings.iterator()
        while (it.hasNext()) {
            val ring = it.next()
            ring.radius += ringSpeed * dt
            ring.life -= ringDecay * dt
            if (ring.life <= 0f || ring.radius > boundsRadius + ringThickness) it.remove()
        }
    }

    fun rasterize(): Array<FloatArray> {
        val grid = Array(rows) { FloatArray(cols) }
        val coreRadius = baseRadius + smoothedLevel * (boundsRadius - baseRadius) * 0.65f
        val coreEdge = (coreRadius * 0.35f).coerceAtLeast(0.35f)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val dx = c + 0.5f - centerX
                val dy = r + 0.5f - centerY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > boundsRadius) continue

                var b = 0f
                if (dist < coreRadius) {
                    b = if (dist < coreRadius - coreEdge) {
                        1f
                    } else {
                        (1f - (dist - (coreRadius - coreEdge)) / coreEdge).coerceIn(0f, 1f)
                    }
                }

                for (ring in rings) {
                    val ringB = (1f - abs(dist - ring.radius) / ringThickness).coerceIn(0f, 1f) * ring.life
                    if (ringB > b) b = ringB
                }

                grid[r][c] = b.coerceIn(0f, 1f)
            }
        }
        return grid
    }

    fun circularMask(): Array<BooleanArray> = Array(rows) { r ->
        BooleanArray(cols) { c ->
            val dx = c + 0.5f - centerX
            val dy = r + 0.5f - centerY
            sqrt(dx * dx + dy * dy) <= boundsRadius
        }
    }
}
