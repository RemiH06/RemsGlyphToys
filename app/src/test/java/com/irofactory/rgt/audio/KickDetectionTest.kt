package com.irofactory.rgt.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bombo con audio sintetico que pasa por [VisualizerModel.normalizedMagnitudes]
 * (la ganancia por pasos del Visualizer). Deja la tabla en build/kick.txt.
 */
class KickDetectionTest {

    private val rate = 44100f
    private val size = 1024
    private val hop = (rate / 30f).toInt()      // un cuadro del toy
    private val binHz = rate / size

    private class Result(val hits: Int, val kicks: Int, val presence: Float, val minPresence: Float, val seconds: Float)

    /**
     * [expected] golpes reales en la señal, solo para la tabla. [minPresence]
     * es el minimo en los ultimos 3 s: que no parpadee estando encendido.
     */
    private fun run(signal: (Double) -> Double, seconds: Float = 10f): Result {
        val analysis = SpectrumAnalysis()
        val frames = (seconds * 30).toInt()
        var minPresence = 1f
        for (frame in 0 until frames) {
            val start = frame * hop
            val block = DoubleArray(size) { n -> signal((start + n) / rate.toDouble()) }
            analysis.analyze(VisualizerModel.normalizedMagnitudes(block), binHz, loudness = 0.8f, dt = 1f / 30f)
            if (frame >= frames - 90) minPresence = minOf(minPresence, analysis.kickPresence)
        }
        return Result(analysis.kickHits, analysis.kickCount, analysis.kickPresence, minPresence, seconds)
    }

    private fun tone(f: Double, t: Double, harmonics: Int = 4) = (1..harmonics).sumOf { h -> sin(2 * PI * f * h * t) / h }

    /** Bombo: seno que cae de 145 a 55 Hz y se apaga, cada [period] s desde [offset]. */
    private fun kick(t: Double, period: Double, offset: Double = 0.0): Double {
        if (t < offset) return 0.0
        val local = (t - offset) % period
        val pitch = 55 + 90 * exp(-local * 30)
        return exp(-local * 12) * sin(2 * PI * pitch * local)
    }

    private val random = Random(7)
    private fun hat(t: Double, period: Double): Double {
        val local = (t + period / 2) % period
        return 0.15 * exp(-local * 60) * (random.nextDouble() * 2 - 1)
    }

    private fun bass(t: Double) = 0.35 * tone(if ((t % 2.0) < 1.0) 55.0 else 82.4, t)
    private fun pad(t: Double) = 0.12 * (tone(261.6, t) + tone(329.6, t) + tone(392.0, t))

    @Test
    fun kickRingFollowsPronouncedKicks() {
        val results = linkedMapOf(
            "house 120 (bombo cada tiempo)" to run({ t -> kick(t, 0.5) + bass(t) + pad(t) + hat(t, 0.25) }),
            "bombo y bajo" to run({ t -> kick(t, 0.5) + bass(t) }),
            "bombo solo" to run({ t -> kick(t, 0.5) }),
            "hip hop 90 (tiempos 1 y 3)" to run({ t -> kick(t, 1.333) + kick(t, 1.333, 0.999) + bass(t) + pad(t) + hat(t, 0.333) }),
            "bombo suave bajo la mezcla" to run({ t -> 0.25 * kick(t, 0.5) + bass(t) + 2 * pad(t) + hat(t, 0.25) }),
            "balada (bajo y acordes)" to run({ t -> bass(t) + pad(t) }),
            "bajo sostenido" to run({ t -> bass(t) }),
            "acordes en golpes" to run({ t -> if ((t % 0.5) < 0.2) 2 * pad(t) else 0.0 }),
            "hi-hats solos" to run({ t -> hat(t, 0.25) }),
            "bajo staccato" to run({ t -> if ((t % 0.5) < 0.2) bass(t) else 0.0 }),
            "house y luego balada" to run({ t ->
                if (t < 6) kick(t, 0.5) + bass(t) + pad(t) + hat(t, 0.25) else bass(t) + pad(t)
            }, seconds = 16f),
        )
        val table = StringBuilder("escenario                        golpes/s  confirmados/s  presencia  minima 3 s\n")
        for ((name, r) in results) {
            table.append("%-32s %8.2f  %13.2f  %9.2f  %10.2f\n".format(name, r.hits / r.seconds, r.kicks / r.seconds, r.presence, r.minPresence))
        }
        File("build/kick.txt").apply { parentFile.mkdirs() }.writeText(table.toString())

        val on = { name: String -> results.getValue(name).minPresence > 0.9f }
        val off = { name: String -> results.getValue(name).presence < 0.1f }
        for (name in listOf("house 120 (bombo cada tiempo)", "bombo y bajo", "hip hop 90 (tiempos 1 y 3)")) {
            assertTrue("con bombo marcado el anillo debe quedar abierto sin parpadear: $name", on(name))
        }
        for (name in listOf("bombo suave bajo la mezcla", "balada (bajo y acordes)", "bajo sostenido",
                "acordes en golpes", "hi-hats solos", "house y luego balada")) {
            assertTrue("sin bombo marcado el anillo debe quedar cerrado: $name", off(name))
        }
    }
}
