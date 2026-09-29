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
        const val PI_F = 3.1415927f
        const val TWO_PI = 6.2831855f
        // Radios mas parecidos que esto se pueden cruzar (familias "empatadas");
        // a partir de CROSS + CROSS_BLEND se anidan por completo, con transicion suave.
        const val CROSS = 0.8f
        const val CROSS_BLEND = 2.0f
        // Hueco visible entre contornos anidados, ademas del grosor de ambos
        const val CLEARANCE = 0.8f
    }

    /**
     * Un anillo que tiende a un poligono regular de [sides] lados: entre mas
     * suena su familia, mas afilado; callado se redondea. [orientation] fija
     * hacia donde apunta (en radianes, 0 = derecha, y crece hacia abajo) y
     * solo se balancea [wobble] radianes, para que la forma no pierda
     * identidad girando.
     */
    private class Ring(
        val sides: Int,
        val orientation: Float,
        val wobble: Float,
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
        var rotation = orientation
        /** Compresion para caber dentro de un anillo mas grande (1 = tamano libre). */
        var squeeze = 1f

        private val segment = TWO_PI / sides
        private val apothem = cos(PI_F / sides)   // radio al centro de un lado, con vertices a 1

        /** 0 = circulo, 1 = poligono con lados rectos. */
        val sharpness get() = 0.7f + 0.3f * energy

        /** Radio a los vertices. */
        val base get() = minRadius + (maxRadius - minRadius) * energy
        private val lobeReach get() = 0.25f * lobes.indices.sumOf { (lobeAmp[it] * lobeLevel[it]).toDouble() }.toFloat()
        val outer get() = (base + lobeReach) * squeeze
        val inner get() = ((base * (1f + (apothem - 1f) * sharpness) - lobeReach) * squeeze).coerceAtLeast(0.5f)

        fun radius(theta: Float): Float {
            // Poligono regular en polares: apotema / cos(distancia angular al centro del lado)
            val a = ((theta - rotation) % segment + segment) % segment
            val polygon = apothem / cos(a - segment / 2f)
            var r = base * (1f + (polygon - 1f) * sharpness)
            // Lobulos organicos a la mitad de fuerza para que la forma se siga leyendo
            for (i in lobes.indices) r += 0.5f * lobeAmp[i] * lobeLevel[i] * (0.5f * cos(lobes[i] * (theta - phase[i])))
            return (r * squeeze).coerceAtLeast(0.6f)
        }
    }

    private val n = GlyphFrames.SIZE
    private val center = n / 2f
    private val edge = GlyphFrames.LED_RADIUS - 0.2f

    // Graves: hexagono con lado plano arriba (vertice a la derecha)
    private val low = Ring(sides = 6, orientation = 0f, wobble = 0.12f,
        minRadius = 1.2f, maxRadius = 12.2f, lobes = intArrayOf(1, 2), lobeAmp = floatArrayOf(1.4f, 1.8f),
        spin = floatArrayOf(0.13f, -0.21f), drift = 1.2f, driftSpeed = 0.35f, thickness = 1.0f, seed = 0f)
    // Voces: diamante, vertices sobre los ejes
    private val mid = Ring(sides = 4, orientation = 0f, wobble = 0.18f,
        minRadius = 1.2f, maxRadius = 10.5f, lobes = intArrayOf(3, 4), lobeAmp = floatArrayOf(1.2f, 1.6f),
        spin = floatArrayOf(0.4f, -0.55f), drift = 2.0f, driftSpeed = 0.5f, thickness = 0.9f, seed = 2.1f)
    // Agudos: triangulo apuntando hacia arriba (y crece hacia abajo, arriba es -90 grados)
    private val high = Ring(sides = 3, orientation = -PI_F / 2f, wobble = 0.25f,
        minRadius = 1.0f, maxRadius = 9.0f, lobes = intArrayOf(5, 7), lobeAmp = floatArrayOf(0.45f, 0.35f),
        spin = floatArrayOf(0.8f, -1.2f), drift = 1.5f, driftSpeed = 0.9f, thickness = 0.85f, seed = 4.3f)
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
            ring.rotation = ring.orientation + ring.wobble * ring.energy * sin(ring.clock * 0.8f + ring.seed)
            val amp = ring.drift * ring.energy
            val tx = amp * sin(ring.clock + ring.seed)
            val ty = amp * sin(ring.clock * 1.3f + 2f * ring.seed) * cos(ring.clock * 0.7f)
            ring.x += (tx - ring.x) * follow
            ring.y += (ty - ring.y) * follow
        }
        // Temblor de los agudos
        high.x += 0.12f * high.energy * sin(time * 11f)
        high.y += 0.12f * high.energy * cos(time * 13f)

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
