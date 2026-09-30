package com.irofactory.rgt.audio

import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AudioBlobSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Tres contornos huecos anidados, uno por familia del sonido de
 * AudioSpectrumSource. La matriz solo distingue brillo, no color, asi que
 * cada familia se reconoce por su silueta: un blob que tiende a un poligono
 * redondeado (el armonico principal del poligono, no sus lados rectos):
 *
 *   graves  (sub, bass)        hexagono
 *   voces   (lowMid, vocal)    diamante
 *   agudos  (presence, air)    triangulo
 *
 * Capas en escala logaritmica: el radio de cada figura depende de su energia
 * relativa a la familia mas fuerte, log(r) = log(rExterior) + LOG_SPREAD *
 * log(e / eMax). La que mas suena siempre queda afuera, la que menos adentro;
 * familias parecidas quedan con radios parecidos y se solapan, y una que casi
 * no suena colapsa al centro.
 *
 * Dureza (SpectrumAnalysis.harshness, armonia y no calidad): con sonido
 * suave la figura se redondea; con sonido aspero o brusco sus vertices se
 * afilan y se estiran en puntas cada vez mas delgadas.
 *
 * Las figuras giran lento, flotan con un resorte hacia el centro (cuanto
 * flotan depende de cuanto suenan) y sus bordes respiran con lobulos
 * organicos. Los cruces suman brillo. Motor puro, sin dependencias de Android.
 *
 * Experimento, cuarto anillo (KICK_RING): un circulo para el bombo, casi
 * biestable. Sin bombo marcado es una bolita en el centro que solo se
 * sobresalta con golpes sueltos; con bombo marcado se abre hasta el borde de
 * la matriz, destella con cada golpe y las otras figuras se encogen para
 * caber adentro.
 */
class AudioBlobSimulation {

    private companion object {
        const val PI_F = 3.1415927f
        // Capas: exponente de la escala logaritmica y piso de energia (evita log 0)
        const val LOG_SPREAD = 1.5f
        const val ENERGY_FLOOR = 0.02f
        // Radio de la capa exterior: en reposo y cuanto crece con la familia mas fuerte
        const val REST_RADIUS = 3.0f
        const val OUTER_RANGE = 8.6f
        const val MIN_RADIUS = 1.0f
        // Radios mas parecidos que CROSS (celdas) se pueden cruzar; a partir de
        // CROSS + CROSS_BLEND se anidan por completo, con transicion suave.
        const val CROSS = 0.6f
        const val CROSS_BLEND = 1.6f
        // Hueco visible entre contornos anidados, ademas del grosor de ambos
        const val CLEARANCE = 0.8f
        // Largo maximo de las puntas con dureza total, relativo al tamano de la figura
        const val SPIKE = 0.45f

        // Experimento: anillo del bombo. false lo quita por completo
        const val KICK_RING = true
        const val KICK_BALL = 0.8f
        const val KICK_THICKNESS = 1.1f
        // Espacio que deja a las otras figuras cuando esta abierto
        const val KICK_RESERVE = KICK_THICKNESS + CLEARANCE + 0.4f
    }

    /**
     * Una figura: circulo deformado por el armonico de [sides] lobulos (su
     * poligono redondeado) mas lobulos organicos que la hacen respirar.
     */
    private class Ring(
        val sides: Int,
        val shapeAmp: Float,
        orientation: Float,
        val turnRate: Float,
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
        /** 0 = sonido suave (redondeada) .. 1 = aspero o brusco (puntas). */
        var harshness = 0f
        var size = REST_RADIUS
        var rotation = orientation
        var x = 0f
        var y = 0f
        var clock = seed
        /** Compresion para caber dentro de otra figura o de la matriz (1 = libre). */
        var squeeze = 1f

        /** Que tan marcada esta la forma: mas definida entre mas suena; mas redonda si es suave. */
        private val definition get() = shapeAmp * (0.6f + 0.4f * energy) * (0.4f + 0.6f * harshness)
        /** Largo de las puntas: solo aparecen con dureza, y crecen mas rapido que ella. */
        private val spikeLength get() = SPIKE * harshness * sqrt(harshness)
        /** Que tan delgadas son las puntas: exponente del perfil del vertice. */
        private val spikeSharpness get() = 1f + 10f * harshness
        private val lobeReach get() = 0.5f * lobes.indices.sumOf { (lobeAmp[it] * lobeLevel[it]).toDouble() }.toFloat()
        val outer get() = (size * (1f + definition + spikeLength) + lobeReach) * squeeze
        val inner get() = ((size * (1f - definition) - lobeReach) * squeeze).coerceAtLeast(0.5f)

        fun radius(theta: Float): Float {
            val wave = cos(sides * (theta - rotation))
            // Punta: el perfil 0..1 del vertice elevado a una potencia alta queda
            // angosto, asi que solo la zona del vertice se estira
            val spike = spikeLength * ((0.5f + 0.5f * wave).pow(spikeSharpness))
            var r = size * (1f + definition * wave + spike)
            for (i in lobes.indices) r += lobeAmp[i] * lobeLevel[i] * 0.5f * cos(lobes[i] * (theta - phase[i]))
            return (r * squeeze).coerceAtLeast(0.6f)
        }
    }

    private val n = GlyphFrames.SIZE
    private val center = n / 2f
    private val edge = GlyphFrames.LED_RADIUS - 0.2f

    // Graves: hexagono, gira lento a la derecha
    private val low = Ring(sides = 6, shapeAmp = 0.07f, orientation = 0f, turnRate = 0.06f,
        lobes = intArrayOf(2, 3), lobeAmp = floatArrayOf(1.4f, 1.2f), spin = floatArrayOf(0.13f, -0.21f),
        drift = 1.2f, driftSpeed = 0.35f, thickness = 1.0f, seed = 0f)
    // Voces: diamante (vertices en los ejes), gira a la izquierda
    private val mid = Ring(sides = 4, shapeAmp = 0.14f, orientation = 0f, turnRate = -0.09f,
        lobes = intArrayOf(2, 5), lobeAmp = floatArrayOf(1.2f, 1.0f), spin = floatArrayOf(0.4f, -0.55f),
        drift = 2.0f, driftSpeed = 0.5f, thickness = 0.9f, seed = 2.1f)
    // Agudos: triangulo apuntando hacia arriba (y crece hacia abajo), gira a la derecha mas rapido
    private val high = Ring(sides = 3, shapeAmp = 0.24f, orientation = -PI_F / 2f, turnRate = 0.13f,
        lobes = intArrayOf(5, 7), lobeAmp = floatArrayOf(0.6f, 0.5f), spin = floatArrayOf(0.8f, -1.2f),
        drift = 1.5f, driftSpeed = 0.9f, thickness = 0.85f, seed = 4.3f)
    private val rings = arrayOf(low, mid, high)

    private var loudness = 0f
    private var time = 0f

    // Anillo del bombo: 0 bolita .. 1 abierto al borde, y destello del golpe
    private var kickOpen = 0f
    private var kickFlash = 0f
    private var kickRadius = KICK_BALL

    /**
     * [bands] son las 6 bandas y [harshness] la dureza de graves, medios y
     * agudos, ambas de SpectrumAnalysis; [loud] el volumen real 0..1.
     * [kick] y [kickPresence] mueven el anillo del bombo.
     */
    fun step(dt: Float, bands: FloatArray, loud: Float, harshness: FloatArray,
             kick: Float = 0f, kickPresence: Float = 0f) {
        time += dt
        loudness = loud

        if (KICK_RING) {
            // Suavizado en S para que casi siempre este cerrado o abierto del todo
            kickOpen = smoothstep(0f, 1f, kickPresence)
            kickFlash = kick
            val open = KICK_BALL + (edge - KICK_THICKNESS * 0.6f - KICK_BALL) * kickOpen
            // Cerrado, cada golpe suelto infla un poco la bolita
            kickRadius = open + (1f - kickOpen) * 1.2f * kick
        }
        // Lo que queda para las demas figuras dentro del anillo abierto
        val limit = edge - kickOpen * KICK_RESERVE

        low.harshness = harshness[0]
        mid.harshness = harshness[1]
        high.harshness = harshness[2]

        low.energy = 0.6f * bands[1] + 0.4f * bands[0]
        low.lobeLevel[0] = bands[0]; low.lobeLevel[1] = bands[1]
        mid.energy = 0.4f * bands[2] + 0.6f * bands[3]
        mid.lobeLevel[0] = bands[2]; mid.lobeLevel[1] = bands[3]
        high.energy = 0.55f * bands[4] + 0.45f * bands[5]
        high.lobeLevel[0] = bands[4]; high.lobeLevel[1] = bands[5]

        // Capas logaritmicas relativas a la familia mas fuerte
        val maxEnergy = rings.maxOf { it.energy }
        val outerSize = REST_RADIUS + OUTER_RANGE * maxEnergy.pow(0.7f)
        val sizeFollow = 1f - exp(-dt / 0.12f)
        val follow = 1f - exp(-dt / 0.4f)
        for (ring in rings) {
            val ratio = (ring.energy + ENERGY_FLOOR) / (maxEnergy + ENERGY_FLOOR)
            val target = (outerSize * ratio.pow(LOG_SPREAD)).coerceAtLeast(MIN_RADIUS)
            ring.size += (target - ring.size) * sizeFollow

            ring.rotation += ring.turnRate * (1f + 0.5f * ring.energy) * dt
            for (i in ring.phase.indices) ring.phase[i] += ring.spin[i] * (1f + 1.5f * ring.lobeLevel[i]) * dt

            // Flotar: deriva lenta proporcional a su energia, resorte al centro
            ring.clock += dt * ring.driftSpeed * (0.3f + ring.energy)
            val amp = ring.drift * ring.energy
            val tx = amp * sin(ring.clock + ring.seed)
            val ty = amp * sin(ring.clock * 1.3f + 2f * ring.seed) * cos(ring.clock * 0.7f)
            ring.x += (tx - ring.x) * follow
            ring.y += (ty - ring.y) * follow
        }
        // Temblor leve de los agudos
        high.x += 0.12f * high.energy * sin(time * 11f)
        high.y += 0.12f * high.energy * cos(time * 13f)

        // Anidado, de afuera hacia adentro: la mas chica queda dentro de la mas
        // grande con hueco visible (comprimiendose si no cabe) salvo que sus
        // tamanos se parezcan, que es cuando se cruzan. Luego, todas en la matriz.
        for (ring in rings) ring.squeeze = 1f
        for (ring in rings) if (ring.outer > limit) ring.squeeze = limit / ring.outer
        val bySize = rings.sortedByDescending { it.size }
        for (i in bySize.indices) for (j in i + 1 until bySize.size) {
            val big = bySize[i]
            val small = bySize[j]
            val nest = smoothstep(CROSS, CROSS + CROSS_BLEND, big.size - small.size)
            if (nest <= 0f) continue
            val gap = big.thickness + small.thickness + CLEARANCE

            val maxOuter = big.inner - gap
            if (small.outer > maxOuter) {
                val fit = (maxOuter / small.outer * small.squeeze).coerceIn(0.2f, 1f)
                small.squeeze += (fit - small.squeeze) * nest
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
            val room = (limit - ring.outer).coerceAtLeast(0f)
            if (d > room && d > 1e-3f) { ring.x = ring.x / d * room; ring.y = ring.y / d * room }
        }
    }

    fun rasterize(): Array<FloatArray> {
        val grid = Array(n) { FloatArray(n) }
        val light = 0.5f + 0.5f * loudness
        val levels = FloatArray(rings.size) { (0.4f + 0.6f * rings[it].energy) * light }
        val kickLevel = (0.35f + 0.65f * kickFlash) * light
        // Con el golpe el anillo abierto engrosa hacia adentro
        val kickThickness = KICK_THICKNESS * (1f + 0.6f * kickFlash * kickOpen)

        for (row in 0 until n) {
            for (col in 0 until n) {
                val x = col + 0.5f - center
                val y = row + 0.5f - center
                if (hypot(x, y) > GlyphFrames.LED_RADIUS) continue

                // Mezcla tipo "screen": los cruces brillan mas sin saturar de golpe
                var dark = 1f
                for (i in rings.indices) dark *= 1f - rim(x, y, rings[i]) * levels[i]
                if (KICK_RING) dark *= 1f - kickShape(hypot(x, y), kickThickness) * kickLevel
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

    /** Contorno del anillo del bombo; cerrado se rellena para verse como bolita y no como aro. */
    private fun kickShape(d: Float, thickness: Float): Float {
        val rim = (1f - abs(d - kickRadius) / thickness).coerceIn(0f, 1f)
        val ball = (kickRadius + 0.6f - d).coerceIn(0f, 1f) * (1f - kickOpen)
        return maxOf(rim, ball)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
