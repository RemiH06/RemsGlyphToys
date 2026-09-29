package com.irofactory.rgt.audio

import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * AudioBlobSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Tres anillos huecos anidados, uno por familia del sonido de
 * AudioSpectrumSource:
 *
 *   graves  (sub, bass)            el que puede llegar al borde
 *   voces   (lowMid, vocal)        lobulos suaves
 *   agudos  (presence, air)        rizado fino y temblor
 *
 * Cada anillo crece con la energia de su familia: el que mas suena queda
 * afuera y los callados se encogen al centro. Flotan con un resorte que los
 * regresa al centro, y cuanto flotan depende de cuanto suena su familia: si
 * solo hay voces, graves y agudos quedan quietos en el centro como una masa
 * pequena y solo se mueve el anillo de la voz.
 *
 * Un anillo mas chico siempre queda dentro del mas grande, con hueco
 * visible; solo se cruzan cuando dos familias suenan casi igual (radios
 * parecidos). Los cruces suman brillo. Motor puro, sin dependencias de Android.
 */
class AudioBlobSimulation {

    private companion object {
        // Radios mas parecidos que esto se pueden cruzar (familias "empatadas");
        // a partir de CROSS + CROSS_BLEND se anidan por completo, con transicion suave.
        const val CROSS = 0.8f
        const val CROSS_BLEND = 2.0f
        // Hueco visible entre contornos anidados, ademas del grosor de ambos
        const val CLEARANCE = 0.8f
    }

    private class Ring(
        val minRadius: Float,
        val maxRadius: Float,
        val lobes: IntArray,
        val lobeAmp: FloatArray,
        val spin: FloatArray,
        val drift: Float,
        val driftSpeed: Float,
        val thickness: Float,
        val seed: Float
    ) {
        val phase = FloatArray(lobes.size) { seed + it * 1.7f }
        val lobeLevel = FloatArray(lobes.size)
        var energy = 0f
        var x = 0f
        var y = 0f
        var clock = seed
        /** Compresion para caber dentro de un anillo mas grande (1 = tamano libre). */
        var squeeze = 1f

        val base get() = minRadius + (maxRadius - minRadius) * energy
        private val lobeReach get() = 0.5f * lobes.indices.sumOf { (lobeAmp[it] * lobeLevel[it]).toDouble() }.toFloat()
        val outer get() = (base + lobeReach) * squeeze
        val inner get() = ((base - lobeReach) * squeeze).coerceAtLeast(0.5f)

        fun radius(theta: Float): Float {
            var r = base
            for (i in lobes.indices) r += lobeAmp[i] * lobeLevel[i] * (0.5f * cos(lobes[i] * (theta - phase[i])))
            return (r * squeeze).coerceAtLeast(0.6f)
        }
    }

    private val n = GlyphFrames.SIZE
    private val center = n / 2f
    private val edge = GlyphFrames.LED_RADIUS - 0.2f

    private val low = Ring(1.2f, 12.2f, intArrayOf(1, 2), floatArrayOf(1.4f, 1.8f), floatArrayOf(0.13f, -0.21f),
        drift = 1.2f, driftSpeed = 0.35f, thickness = 1.0f, seed = 0f)
    private val mid = Ring(1.2f, 10.5f, intArrayOf(3, 4), floatArrayOf(1.2f, 1.6f), floatArrayOf(0.4f, -0.55f),
        drift = 2.0f, driftSpeed = 0.5f, thickness = 0.9f, seed = 2.1f)
    private val high = Ring(1.0f, 9.0f, intArrayOf(5, 7), floatArrayOf(0.8f, 0.7f), floatArrayOf(0.8f, -1.2f),
        drift = 1.5f, driftSpeed = 0.9f, thickness = 0.85f, seed = 4.3f)
    private val rings = arrayOf(low, mid, high)

    private var loudness = 0f
    private var time = 0f

    fun step(dt: Float, bands: FloatArray, loud: Float) {
        time += dt
        loudness = loud

        low.energy = 0.6f * bands[1] + 0.4f * bands[0]
        low.lobeLevel[0] = bands[0]; low.lobeLevel[1] = bands[1]
        mid.energy = 0.4f * bands[2] + 0.6f * bands[3]
        mid.lobeLevel[0] = bands[2]; mid.lobeLevel[1] = bands[3]
        high.energy = 0.55f * bands[4] + 0.45f * bands[5]
        high.lobeLevel[0] = bands[4]; high.lobeLevel[1] = bands[5]

        val follow = 1f - exp(-dt / 0.4f)
        for (ring in rings) {
            for (i in ring.phase.indices) ring.phase[i] += ring.spin[i] * (1f + 1.5f * ring.lobeLevel[i]) * dt

            // Flotar: deriva lenta proporcional a su energia, resorte al centro
            ring.clock += dt * ring.driftSpeed * (0.3f + ring.energy)
            val amp = ring.drift * ring.energy
            val tx = amp * sin(ring.clock + ring.seed)
            val ty = amp * sin(ring.clock * 1.3f + 2f * ring.seed) * cos(ring.clock * 0.7f)
            ring.x += (tx - ring.x) * follow
            ring.y += (ty - ring.y) * follow
        }
        // Temblor de los agudos
        high.x += 0.25f * high.energy * sin(time * 11f)
        high.y += 0.25f * high.energy * cos(time * 13f)

        // Anidado, de afuera hacia adentro: el mas chico queda dentro del mas
        // grande con un hueco visible (comprimiendose si no cabe) salvo que sus
        // radios se parezcan, que es cuando se cruzan. Luego, todos en la matriz.
        for (ring in rings) ring.squeeze = 1f
        val bySize = rings.sortedByDescending { it.base }
        for (i in bySize.indices) for (j in i + 1 until bySize.size) {
            val big = bySize[i]
            val small = bySize[j]
            val nest = smoothstep(CROSS, CROSS + CROSS_BLEND, big.base - small.base)
            if (nest <= 0f) continue
            val gap = big.thickness + small.thickness + CLEARANCE

            val maxOuter = big.inner - gap
            if (small.outer > maxOuter) {
                val fit = (maxOuter / small.outer * small.squeeze).coerceIn(0.2f, 1f)
                small.squeeze = small.squeeze + (fit - small.squeeze) * nest
            }

            val allowed = (big.inner - small.outer - gap).coerceAtLeast(0f)
            val dx = small.x - big.x
            val dy = small.y - big.y
            val d = hypot(dx, dy)
            if (d > allowed && d > 1e-3f) {
                val k = 1f - nest + nest * allowed / d
                small.x = big.x + dx * k
                small.y = big.y + dy * k
            }
        }
        for (ring in rings) {
            val d = hypot(ring.x, ring.y)
            val limit = (edge - ring.outer).coerceAtLeast(0f)
            if (d > limit && d > 1e-3f) { ring.x = ring.x / d * limit; ring.y = ring.y / d * limit }
        }
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun rasterize(): Array<FloatArray> {
        val grid = Array(n) { FloatArray(n) }
        val light = 0.5f + 0.5f * loudness
        val levels = FloatArray(rings.size) { (0.4f + 0.6f * rings[it].energy) * light }

        for (row in 0 until n) {
            for (col in 0 until n) {
                val x = col + 0.5f - center
                val y = row + 0.5f - center
                if (hypot(x, y) > GlyphFrames.LED_RADIUS) continue

                // Mezcla tipo "screen": los cruces brillan mas sin saturar de golpe
                var dark = 1f
                for (i in rings.indices) dark *= 1f - rim(x, y, rings[i]) * levels[i]
                grid[row][col] = 1f - dark
            }
        }
        return grid
    }

    private fun rim(x: Float, y: Float, ring: Ring): Float {
        val dx = x - ring.x
        val dy = y - ring.y
        val d = hypot(dx, dy)
        return (1f - abs(d - ring.radius(atan2(dy, dx))) / ring.thickness).coerceIn(0f, 1f)
    }
}
