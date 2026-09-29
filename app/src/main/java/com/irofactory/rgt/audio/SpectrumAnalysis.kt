package com.irofactory.rgt.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * SpectrumAnalysis
 * ───────────────────────────────────────────────────────────────────────────
 * Todo lo que el toy pulse lee del espectro, sin dependencias de Android
 * (se prueba en la JVM con audio sintetico). Recibe magnitudes de la FFT y
 * produce:
 *
 *   [bands]      energia relativa de 6 bandas, cada una contra su propio pico
 *                reciente (control de ganancia), subida rapida y bajada lenta:
 *                0 sub 20-90 Hz · 1 bass 90-250 · 2 lowMid 250-500 ·
 *                3 vocal 500-2000 · 4 presence 2-5 kHz · 5 air 5-14 kHz
 *
 *   [harshness]  dureza de cada familia (graves, medios, agudos), 0 suave ..
 *                1 aspero. No es calidad de audio sino armonia, combinando:
 *                - planitud: un sonido tonal concentra su potencia en picos;
 *                  el ruido la reparte (media geometrica / aritmetica sobre
 *                  la potencia, donde la fuga de la FFT sin ventana casi no pesa).
 *                - brusquedad: cuanto sube el espectro de un cuadro al
 *                  siguiente (ataques, golpes; tambien el batido de notas
 *                  muy cercanas, que la resolucion no separa en picos).
 *                - aspereza: disonancia entre los picos mas fuertes con la
 *                  curva de Plomp-Levelt (ajuste de Sethares).
 *                En graves la FFT del Visualizer (~45 Hz por bin) casi no
 *                separa notas, asi que ahi pesa sobre todo la brusquedad. Una
 *                familia casi sin energia no tiene dureza: lo poco que hay es
 *                fuga de la FFT y se leeria como ruido.
 *
 *                Base por familia: cada una aprende su dureza habitual (sube
 *                lento, baja mas rapido) y cuenta sobre todo lo que la rebasa.
 *                Sin esto los graves siempre salian asperos: en musica el bajo
 *                cambia de nota todo el tiempo y cada cambio parece un golpe.
 */
class SpectrumAnalysis {

    companion object {
        const val BAND_COUNT = 6
        const val FAMILY_COUNT = 3
        private val BAND_EDGES_HZ = floatArrayOf(20f, 90f, 250f, 500f, 2000f, 5000f, 14000f)
        // Piso de cada banda para el control de ganancia: evita amplificar ruido.
        private val BAND_FLOOR = floatArrayOf(6f, 6f, 4f, 3f, 2f, 1.5f)
        // Familias para la dureza: graves, medios, agudos. Los graves paran en
        // 200 Hz (no 250) para no recoger la fuga de notas medias justo arriba.
        private val FAMILY_EDGES_HZ = floatArrayOf(20f, 200f, 2000f, 14000f)
        // Brusquedad minima y plena por familia: en graves un cambio de nota
        // normal no debe contar como golpe
        private val FLUX_FROM = floatArrayOf(0.12f, 0.05f, 0.05f)
        private val FLUX_TO = floatArrayOf(0.40f, 0.20f, 0.20f)
        // Pesos de planitud, brusquedad y aspereza por familia
        private val W_FLAT = floatArrayOf(0.30f, 0.30f, 0.45f)
        private val W_FLUX = floatArrayOf(0.70f, 0.40f, 0.30f)
        private val W_ROUGH = floatArrayOf(0.00f, 0.30f, 0.25f)
        private const val MAX_PEAKS = 10
        // Cuanto de la dureza es absoluta; el resto es lo que rebasa la base de la familia
        private val ABSOLUTE_WEIGHT = floatArrayOf(0.2f, 0.6f, 0.6f)
    }

    val bands = FloatArray(BAND_COUNT)
    val harshness = FloatArray(FAMILY_COUNT)

    /** Ultimas lecturas crudas por familia (para diagnostico y pruebas). */
    val flatness = FloatArray(FAMILY_COUNT)
    val flux = FloatArray(FAMILY_COUNT)
    val roughness = FloatArray(FAMILY_COUNT)

    private val peaks = FloatArray(BAND_COUNT) { BAND_FLOOR[it] }
    private val familyPower = FloatArray(FAMILY_COUNT)
    private val absolute = FloatArray(FAMILY_COUNT)
    private val baseline = FloatArray(FAMILY_COUNT)
    private var previous = FloatArray(0)

    /**
     * Un cuadro nuevo. [magnitudes] indexadas por bin (0 = DC), [binHz] el
     * ancho de cada bin y [loudness] el volumen real 0..1 (0 = silencio).
     */
    fun analyze(magnitudes: FloatArray, binHz: Float, loudness: Float, dt: Float) {
        val bins = magnitudes.size
        val peakDecay = exp(-dt / 3f)
        for (b in 0 until BAND_COUNT) {
            val (from, to) = range(BAND_EDGES_HZ[b], BAND_EDGES_HZ[b + 1], binHz, bins)
            var sum = 0f
            for (k in from until to) sum += magnitudes[k]
            val energy = sum / (to - from)
            peaks[b] = maxOf(energy, peaks[b] * peakDecay, BAND_FLOOR[b])
            val target = (energy / peaks[b]).coerceIn(0f, 1f) * loudness.coerceAtLeast(0.15f)
            bands[b] = smooth(bands[b], if (loudness < 0.02f) 0f else target, dt, attack = 0.04f, release = 0.22f)
        }

        val prev = if (previous.size == bins) previous else magnitudes
        for (f in 0 until FAMILY_COUNT) {
            val (from, to) = range(FAMILY_EDGES_HZ[f], FAMILY_EDGES_HZ[f + 1], binHz, bins)
            var power = 0f
            for (k in from until to) power += magnitudes[k] * magnitudes[k]
            familyPower[f] = power / (to - from)
        }
        val strongest = familyPower.max().coerceAtLeast(1e-6f)
        for (f in 0 until FAMILY_COUNT) {
            val (from, to) = range(FAMILY_EDGES_HZ[f], FAMILY_EDGES_HZ[f + 1], binHz, bins)
            flatness[f] = flatness(magnitudes, from, to)
            flux[f] = flux(magnitudes, prev, from, to)
            roughness[f] = if (W_ROUGH[f] > 0f) roughness(magnitudes, from, to, binHz) else 0f

            val raw = W_FLAT[f] * smoothstep(0.10f, 0.50f, flatness[f]) +
                W_FLUX[f] * smoothstep(FLUX_FROM[f], FLUX_TO[f], flux[f]) +
                W_ROUGH[f] * smoothstep(0.05f, 0.35f, roughness[f])
            val relevance = smoothstep(0.05f, 0.20f, familyPower[f] / strongest)
            val target = if (loudness < 0.02f) 0f else (raw * relevance).coerceIn(0f, 1f)
            // Dureza absoluta: subida casi inmediata para que un golpe se note, bajada lenta para que "resuene"
            absolute[f] = smooth(absolute[f], target, dt, attack = 0.03f, release = 0.6f)

            // Base: el nivel habitual de la familia, aprendido de la dureza ya suavizada
            if (loudness >= 0.02f) {
                val follow = if (absolute[f] > baseline[f]) 8f else 3f
                baseline[f] += (absolute[f] - baseline[f]) * (1f - exp(-dt / follow))
            }
            val above = smoothstep(0f, 0.35f, absolute[f] - baseline[f])
            harshness[f] = ABSOLUTE_WEIGHT[f] * absolute[f] + (1f - ABSOLUTE_WEIGHT[f]) * above
        }
        previous = magnitudes.copyOf()
    }

    /** Sin audio: todo se desvanece. */
    fun decay(dt: Float) {
        for (b in 0 until BAND_COUNT) bands[b] = smooth(bands[b], 0f, dt, 0.04f, 0.22f)
        for (f in 0 until FAMILY_COUNT) {
            absolute[f] = smooth(absolute[f], 0f, dt, 0.03f, 0.6f)
            harshness[f] = smooth(harshness[f], 0f, dt, 0.03f, 0.6f)
        }
        previous = FloatArray(0)
    }

    private fun range(lowHz: Float, highHz: Float, binHz: Float, bins: Int): Pair<Int, Int> {
        val from = (lowHz / binHz).toInt().coerceIn(1, bins - 1)
        val to = (highHz / binHz).toInt().coerceIn(from + 1, bins)
        return from to to
    }

    /** Media geometrica / aritmetica de la potencia: ~0 si hay pocos picos (tonal), alto si es ruido. */
    private fun flatness(m: FloatArray, from: Int, to: Int): Float {
        var logSum = 0.0
        var sum = 0.0
        for (k in from until to) {
            val power = m[k] * m[k] + 1f
            logSum += ln(power.toDouble())
            sum += power
        }
        val n = to - from
        val arithmetic = sum / n
        if (arithmetic <= 2.0) return 0f   // casi silencio en esta familia
        return (exp(logSum / n) / arithmetic).toFloat().coerceIn(0f, 1f)
    }

    /** Subida del espectro respecto al cuadro anterior, normalizada. */
    private fun flux(m: FloatArray, prev: FloatArray, from: Int, to: Int): Float {
        var rise = 0f
        var total = 0f
        for (k in from until to) {
            rise += max(0f, m[k] - prev[k])
            total += m[k] + prev[k]
        }
        return if (total < 1f) 0f else rise / total
    }

    /**
     * Disonancia sensorial entre los picos mas fuertes de la familia, con la
     * curva de Plomp-Levelt en la forma de Sethares, normalizada a 0..1.
     */
    private fun roughness(m: FloatArray, from: Int, to: Int, binHz: Float): Float {
        var top = 0f
        for (k in from until to) top = max(top, m[k])
        if (top < 3f) return 0f
        val found = ArrayList<Pair<Float, Float>>()   // (frecuencia, amplitud)
        for (k in max(from, 1) until min(to, m.size - 1)) {
            if (m[k] > m[k - 1] && m[k] >= m[k + 1] && m[k] > 0.15f * top) found += (k * binHz) to m[k]
        }
        val strongest = found.sortedByDescending { it.second }.take(MAX_PEAKS)
        var dissonance = 0f
        var weight = 0f
        for (i in strongest.indices) for (j in i + 1 until strongest.size) {
            val (f1, a1) = strongest[i]
            val (f2, a2) = strongest[j]
            val amp = min(a1, a2)
            val s = 0.24f / (0.0207f * min(f1, f2) + 18.96f)
            val x = s * abs(f2 - f1)
            dissonance += amp * (exp(-3.51f * x) - exp(-5.75f * x))
            weight += amp
        }
        // El maximo de la curva es ~0.18: se normaliza a 0..1
        return if (weight <= 0f) 0f else (dissonance / weight / 0.18f).coerceIn(0f, 1f)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun smooth(current: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
        val tau = if (target > current) attack else release
        return current + (target - current) * (1f - exp(-dt / tau))
    }
}
