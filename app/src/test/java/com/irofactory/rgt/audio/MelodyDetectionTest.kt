package com.irofactory.rgt.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Linea melodica con audio sintetico por [VisualizerModel.normalizedMagnitudes].
 * Deja la tabla en build/melody.txt.
 */
class MelodyDetectionTest {

    private val rate = 44100f
    private val size = 1024
    private val hop = (rate / 30f).toInt()
    private val binHz = rate / size

    private class Result(val melody: Float, val score: Float)

    private fun run(signal: (Double) -> Double, seconds: Float = 6f): Result {
        val analysis = SpectrumAnalysis()
        val frames = (seconds * 30).toInt()
        var melody = 0f
        var score = 0f
        for (frame in 0 until frames) {
            val start = frame * hop
            val block = DoubleArray(size) { n -> signal((start + n) / rate.toDouble()) }
            analysis.analyze(VisualizerModel.normalizedMagnitudes(block), binHz, loudness = 0.8f, dt = 1f / 30f)
            if (frame >= frames - 60) { melody += analysis.melody / 60f; score += analysis.melodyScore / 60f }
        }
        return Result(melody, score)
    }

    /** Fase acumulada de una frecuencia con vibrato: f(t) = f0·(1 + depth·sin(2π·rate·t)). */
    private fun vibratoPhase(f0: Double, depth: Double, vibRate: Double, t: Double) =
        2 * PI * f0 * (t - depth / (2 * PI * vibRate) * kotlin.math.cos(2 * PI * vibRate * t))

    /** Voz: fuente glotal (armonicos que bajan como 1/h) reforzada por dos formantes, con vibrato. */
    private fun voice(f0: Double, f1: Double, f2: Double): (Double) -> Double = { t ->
        var v = 0.0
        val phase = vibratoPhase(f0, 0.02, 5.5, t)
        for (h in 1..30) {
            val f = f0 * h
            if (f > 4000) break
            val formant = 1 + 3 * exp(-((f - f1) / 150).let { it * it }) + 2 * exp(-((f - f2) / 200).let { it * it })
            v += formant / h * sin(h * phase)
        }
        0.25 * v
    }

    private fun harmonics(f0: Double, count: Int, t: Double, decay: (Int) -> Double) =
        (1..count).sumOf { h -> decay(h) * sin(2 * PI * f0 * h * t) }

    /** Nota pulsada: armonicos que se apagan, re-atacada cada [period] s. */
    private fun plucked(f0s: DoubleArray, period: Double): (Double) -> Double = { t ->
        val local = t % period
        val env = exp(-local * 3)
        f0s.sumOf { f0 -> env * harmonics(f0, 8, t) { h -> 1.0 / h } } * 0.25
    }

    @Test
    fun diamondFollowsMelodicLines() {
        val random = Random(9)
        val results = linkedMapOf(
            "voz aguda (220 Hz)" to run(voice(220.0, 700.0, 1200.0)),
            "voz grave (120 Hz)" to run(voice(120.0, 500.0, 1100.0)),
            "silbido (1500 Hz)" to run({ t -> 0.6 * sin(vibratoPhase(1500.0, 0.01, 5.0, t)) }),
            "flauta (600 Hz)" to run({ t -> 0.5 * harmonics(600.0, 4, t) { h -> 1.0 / (h * h) } }),
            "guitarra rasgueada (mi mayor)" to run(plucked(doubleArrayOf(82.4, 123.5, 164.8, 207.7, 246.9, 329.6), 0.5)),
            "piano (do mayor)" to run(plucked(doubleArrayOf(261.6, 329.6, 392.0), 1.0)),
            "pad sostenido (do mayor)" to run({ t -> 0.2 * (harmonics(261.6, 6, t) { 1.0 / it } + harmonics(329.6, 6, t) { 1.0 / it } + harmonics(392.0, 6, t) { 1.0 / it }) }),
            "ruido" to run({ random.nextDouble() * 2 - 1 }),
            "voz sobre acordes" to run({ t -> voice(220.0, 700.0, 1200.0)(t) + 0.5 * plucked(doubleArrayOf(261.6, 329.6, 392.0), 1.0)(t) }),
        )
        val table = StringBuilder("escenario                        linea melodica  puntaje crudo\n")
        for ((name, r) in results) table.append("%-32s %14.2f  %13.2f\n".format(name, r.melody, r.score))
        File("build/melody.txt").apply { parentFile.mkdirs() }.writeText(table.toString())

        val m = { name: String -> results.getValue(name).melody }
        for (name in listOf("voz aguda (220 Hz)", "voz grave (120 Hz)", "silbido (1500 Hz)", "flauta (600 Hz)")) {
            assertTrue("una linea melodica debe contar: $name", m(name) > 0.6f)
        }
        for (name in listOf("guitarra rasgueada (mi mayor)", "piano (do mayor)", "pad sostenido (do mayor)", "ruido")) {
            assertTrue("un acorde o ruido no es linea melodica: $name", m(name) < 0.35f)
        }
    }
}
