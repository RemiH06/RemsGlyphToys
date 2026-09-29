package com.irofactory.rgt.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dureza con audio sintetico que imita al Visualizer: bloques de 1024
 * muestras a 44.1 kHz, FFT sin ventana, normalizada y cuantizada a 8 bits
 * como getFft. Deja la tabla y los componentes crudos en build/harshness.txt.
 */
class SpectrumAnalysisTest {

    private val rate = 44100f
    private val size = 1024
    private val hop = (rate / 30f).toInt()      // un cuadro del toy
    private val binHz = rate / size

    private class Result(val harshness: FloatArray, val flatness: FloatArray, val flux: FloatArray, val roughness: FloatArray)

    private fun harmonics(vararg fundamentals: Float): (Double) -> Double = { t ->
        fundamentals.sumOf { f -> (1..4).sumOf { h -> sin(2 * PI * f * h * t) / h } }
    }

    private fun run(signal: (Double) -> Double, gate: (Int) -> Boolean = { true }, seconds: Float = 8f): Result {
        val analysis = SpectrumAnalysis()
        val frames = (seconds * 30).toInt()
        // Dureza promediada en el ultimo segundo: lo que la figura muestra en conjunto
        val average = FloatArray(SpectrumAnalysis.FAMILY_COUNT)
        for (frame in 0 until frames) {
            val start = frame * hop
            val on = gate(frame)
            val block = DoubleArray(size) { n -> if (on) signal((start + n) / rate.toDouble()) else 0.0 }
            analysis.analyze(visualizerMagnitudes(block), binHz, loudness = 0.8f, dt = 1f / 30f)
            if (frame >= frames - 30) for (f in average.indices) average[f] += analysis.harshness[f] / 30f
        }
        return Result(average, analysis.flatness.copyOf(), analysis.flux.copyOf(), analysis.roughness.copyOf())
    }

    /** FFT de la señal escalada y cuantizada a bytes, como Visualizer.getFft. */
    private fun visualizerMagnitudes(block: DoubleArray): FloatArray {
        val re = block.copyOf()
        val im = DoubleArray(size)
        fft(re, im)
        var peak = 1e-9
        for (k in 1 until size / 2) peak = maxOf(peak, hypot(re[k], im[k]))
        val scale = if (block.all { it == 0.0 }) 0.0 else 100.0 / peak
        return FloatArray(size / 2) { k ->
            val qr = (re[k] * scale).roundToInt().coerceIn(-128, 127)
            val qi = (im[k] * scale).roundToInt().coerceIn(-128, 127)
            hypot(qr.toFloat(), qi.toFloat())
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) for (k in 0 until len / 2) {
                val wr = cos(ang * k); val wi = sin(ang * k)
                val ur = re[i + k]; val ui = im[i + k]
                val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                re[i + k] = ur + vr; im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
            }
            len = len shl 1
        }
    }

    private fun FloatArray.fmt() = joinToString(" ") { "%.2f".format(it) }

    @Test
    fun harmonySoftensAndRoughnessHardens() {
        val chord = harmonics(261.6f, 329.6f, 392.0f)                 // do mayor
        val cluster = harmonics(440f, 466.2f, 493.9f, 523.3f)          // semitonos pegados
        val highCluster = harmonics(1000f, 1060f, 1120f, 1190f)        // disonancia en medios-agudos
        val random = Random(3)
        val noise: (Double) -> Double = { random.nextDouble() * 2 - 1 }
        // Bajo: notas de 1 s (la, mi) con armonicos; bombo: seno de 55 Hz que cae y se apaga, dos por segundo
        val bassLine: (Double) -> Double = { t -> harmonics(if ((t % 2.0) < 1.0) 55f else 82.4f)(t) }
        val kick: (Double) -> Double = { t ->
            val local = t % 0.5
            val pitch = 55 + 90 * kotlin.math.exp(-local * 30)
            3.0 * kotlin.math.exp(-local * 12) * sin(2 * PI * pitch * local)
        }

        val results = linkedMapOf(
            "tono puro 440" to run({ t -> sin(2 * PI * 440 * t) }),
            "acorde mayor" to run(chord),
            "cluster de semitonos" to run(cluster),
            "cluster agudo" to run(highCluster),
            "ruido blanco" to run(noise),
            "acorde en golpes" to run(chord, gate = { it % 5 < 2 }),
            "bajo sostenido" to run(bassLine),
            "bombo" to run(kick),
            "bajo con bombo" to run({ t -> bassLine(t) + kick(t) }),
        )
        val table = StringBuilder("escenario              dureza (graves medios agudos)   planitud          brusquedad        aspereza\n")
        for ((name, r) in results) {
            table.append("%-22s %-31s %-17s %-17s %s\n".format(name, r.harshness.fmt(), r.flatness.fmt(), r.flux.fmt(), r.roughness.fmt()))
        }
        File("build/harshness.txt").apply { parentFile.mkdirs() }.writeText(table.toString())

        val mid = { name: String -> results.getValue(name).harshness[1] }
        val high = { name: String -> results.getValue(name).harshness[2] }
        val low = { name: String -> results.getValue(name).harshness[0] }
        assertTrue("el tono puro debe ser lo mas suave", mid("tono puro 440") < 0.15f)
        assertTrue("el acorde debe ser suave", mid("acorde mayor") < 0.2f)
        assertTrue("el cluster debe ser mas aspero que el acorde", mid("cluster de semitonos") > mid("acorde mayor") + 0.1f)
        assertTrue("el ruido debe ser aspero", mid("ruido blanco") > 0.35f && high("ruido blanco") > 0.35f)
        assertTrue("los golpes deben ser mas bruscos que el acorde sostenido", mid("acorde en golpes") > mid("acorde mayor") + 0.1f)
        assertTrue("un bajo sostenido debe ser suave", low("bajo sostenido") < 0.25f)
        assertTrue("el bombo debe ser mas brusco que el bajo sostenido", low("bombo") > low("bajo sostenido") + 0.1f)
    }
}
