package com.irofactory.rgt.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 *
 *   [kick]       golpes de bombo (35-180 Hz): un golpe es cuando el contraste
 *                de esa banda contra el resto del espectro brinca sobre su valle
 *                reciente mientras domina el cuadro. Se usa el contraste y no la
 *                potencia porque el Visualizer normaliza cada captura en pasos
 *                de x2: cada paso sube todo el espectro por igual y pareceria un
 *                golpe. Ademas debe subir la potencia propia de la banda, que
 *                un bajo parejo no sube. Vale 1 al golpe y se apaga en ~0.1 s.
 *   [melody]     si hay una linea melodica (voz, silbido, viento): que parte
 *                de la energia de 150 Hz a 2.5 kHz pertenece a una sola serie
 *                armonica (una nota con sus armonicos). Un acorde la reparte
 *                entre varias notas y el ruido no tiene armonicos. Se compara
 *                contra lo que esa serie capturaria de un espectro plano, para
 *                que el ruido de 0. Aproximado: la FFT del Visualizer tiene
 *                ~43 Hz por bin, y una guitarra o un piano tocando la melodia
 *                nota por nota tambien cuentan.
 *   [kickPresence] si la cancion "tiene bombo", casi biestable: cuenta golpes
 *                con memoria corta y se enciende con varios seguidos (histeresis:
 *                se apaga solo cuando ya casi no hay), asi que se queda en 0 o
 *                en 1 salvo al cambiar. Solo cuentan golpes confirmados, que
 *                vuelven a caer poco despues: una nota nueva del bajo tambien
 *                brinca, pero se queda arriba.
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

        // Bombo: la banda de su golpe (el "click" agudo no se usa)
        // Linea melodica: banda, fundamental minima (voz grave) y umbral de puntaje
        private const val MELODY_LOW_HZ = 150f
        private const val MELODY_HIGH_HZ = 2500f
        private const val MELODY_MIN_F0_HZ = 100f
        // La fundamental debe tener al menos esta fraccion del pico mas fuerte
        private const val MELODY_FUNDAMENTAL = 0.2f
        private const val MELODY_FROM = 0.32f
        private const val MELODY_TO = 0.46f

        private const val KICK_LOW_HZ = 35f
        private const val KICK_HIGH_HZ = 180f
        // Cuanto deben brincar el contraste y la potencia propia de la banda
        // (ln, 1.0 = x2.7 = ~4.3 dB) y que parte del cuadro debe ser suya
        private const val KICK_RISE = 1.0f
        private const val KICK_POWER_RISE = 0.5f
        private const val CONTRAST_FLOOR = 0.05f
        private const val KICK_SHARE = 0.35f
        // Minimo entre golpes: 0.15 s = 400 BPM, para no contar dos veces el mismo
        private const val KICK_REFRACTORY = 0.15f
        // Confirmacion: en KICK_CONFIRM s el contraste debe caer KICK_DROP (ln, 0.7 = a la mitad)
        private const val KICK_CONFIRM = 0.25f
        private const val KICK_DROP = 0.7f
        // Conteo de golpes: memoria (s) y umbrales de encendido y apagado
        private const val KICK_MEMORY = 2.5f
        private const val KICK_ON = 2.0f
        private const val KICK_OFF = 1.3f
    }

    val bands = FloatArray(BAND_COUNT)
    val harshness = FloatArray(FAMILY_COUNT)

    /** Ultimas lecturas crudas por familia (para diagnostico y pruebas). */
    val flatness = FloatArray(FAMILY_COUNT)
    val flux = FloatArray(FAMILY_COUNT)
    val roughness = FloatArray(FAMILY_COUNT)

    var melody = 0f
        private set
    /** Puntaje crudo de la linea melodica del ultimo cuadro (diagnostico y pruebas). */
    var melodyScore = 0f
        private set
    private var melodyAverage = 0f

    var kick = 0f
        private set
    var kickPresence = 0f
        private set
    /** Golpes confirmados con memoria corta; totales de golpes y de confirmados (diagnostico y pruebas). */
    var kickRate = 0f
        private set
    var kickHits = 0
        private set
    var kickCount = 0
        private set

    private var contrastValley = 0f
    private var powerValley = 0f
    private var previousContrast = 0f
    private var sinceKick = 1f
    private var kickOn = false
    private var pendingContrast = 0f
    private var pendingAge = -1f   // < 0: ningun golpe esperando confirmacion

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
        detectKick(magnitudes, binHz, loudness, dt)

        // Primero el promedio de medio segundo y despues el umbral: decide la
        // tendencia, no los cuadros sueltos que brincan en un acorde
        melodyScore = melodyScore(magnitudes, binHz)
        val score = if (loudness < 0.02f) 0f else melodyScore
        melodyAverage = smooth(melodyAverage, score, dt, attack = 0.5f, release = 0.5f)
        melody = smoothstep(MELODY_FROM, MELODY_TO, melodyAverage)
    }

    /** Sin audio: todo se desvanece. */
    fun decay(dt: Float) {
        for (b in 0 until BAND_COUNT) bands[b] = smooth(bands[b], 0f, dt, 0.04f, 0.22f)
        for (f in 0 until FAMILY_COUNT) {
            absolute[f] = smooth(absolute[f], 0f, dt, 0.03f, 0.6f)
            harshness[f] = smooth(harshness[f], 0f, dt, 0.03f, 0.6f)
        }
        previous = FloatArray(0)
        pendingAge = -1f
        updateKickState(hit = false, confirmed = false, strength = 0f, dt = dt)
        melodyAverage = smooth(melodyAverage, 0f, dt, 0.5f, 0.5f)
        melody = smoothstep(MELODY_FROM, MELODY_TO, melodyAverage)
    }

    private fun detectKick(m: FloatArray, binHz: Float, loudness: Float, dt: Float) {
        val (from, to) = range(KICK_LOW_HZ, KICK_HIGH_HZ, binHz, m.size)
        var kickPower = 0f
        for (k in from until to) kickPower += m[k] * m[k]
        var total = 0f
        for (k in 1 until m.size) total += m[k] * m[k]
        val share = if (total > 0f) kickPower / total else 0f
        // Contraste con piso: sin el, cuando casi nada suena fuera de la banda
        // cualquier fuga de la FFT lo hace brincar
        val floor = CONTRAST_FLOOR * total + 1f
        val contrast = ln((kickPower + floor) / (total - kickPower + floor))
        val power = ln(kickPower + 1f)
        val rise = contrast - contrastValley

        sinceKick += dt
        val hit = loudness >= 0.02f && rise > KICK_RISE && power - powerValley > KICK_POWER_RISE &&
            contrast > previousContrast && share > KICK_SHARE && sinceKick > KICK_REFRACTORY
        var confirmed = false
        if (pendingAge >= 0f) {
            pendingAge += dt
            if (contrast < pendingContrast - KICK_DROP) { confirmed = true; pendingAge = -1f }
            else if (pendingAge > KICK_CONFIRM) pendingAge = -1f
        }
        if (hit) {
            sinceKick = 0f
            kickHits++
            pendingContrast = contrast
            pendingAge = 0f
        }
        if (confirmed) kickCount++
        updateKickState(hit, confirmed, strength = smoothstep(KICK_RISE, KICK_RISE + 1.5f, rise), dt = dt)

        // Valles: bajan rapido con la cola del golpe y suben lento con lo
        // sostenido, asi un bajo largo deja de contar como golpe en ~1 s
        contrastValley = follow(contrastValley, contrast, dt)
        powerValley = follow(powerValley, power, dt)
        previousContrast = contrast
    }

    private fun follow(valley: Float, value: Float, dt: Float): Float {
        val tau = if (value > valley) 0.5f else 0.06f
        return valley + (value - valley) * (1f - exp(-dt / tau))
    }

    /** El destello sale con el golpe; el conteo espera a que se confirme. */
    private fun updateKickState(hit: Boolean, confirmed: Boolean, strength: Float, dt: Float) {
        kick *= exp(-dt / 0.12f)
        kickRate *= exp(-dt / KICK_MEMORY)
        if (hit) kick = max(kick, 0.5f + 0.5f * strength)
        if (confirmed) kickRate += 1f
        if (kickRate > KICK_ON) kickOn = true
        if (kickRate < KICK_OFF) kickOn = false
        kickPresence = smooth(kickPresence, if (kickOn) 1f else 0f, dt, attack = 0.25f, release = 1.0f)
    }

    /**
     * Que tanto de la potencia de la banda melodica cae en los armonicos de una
     * sola fundamental, menos lo que caeria de un espectro plano, sobre lo que
     * falta para 1. Candidatas: el pico mas fuerte como fundamental o como su
     * 2o a 6o armonico (en una voz grave el pico suele ser un armonico), cada
     * una afinada en ±3 %. Una candidata cuenta solo si su fundamental tiene
     * energia: un acorde mayor cae casi entero en los armonicos de una
     * fundamental imaginaria una octava abajo, pero ahi no suena nada.
     */
    private fun melodyScore(m: FloatArray, binHz: Float): Float {
        val (from, to) = range(MELODY_LOW_HZ, MELODY_HIGH_HZ, binHz, m.size)
        var total = 0f
        var peak = from
        for (k in from until to) {
            total += m[k] * m[k]
            if (m[k] > m[peak]) peak = k
        }
        if (total <= 0f || m[peak] < 3f) return 0f
        var best = 0f
        for (divisor in 1..6) {
            val guess = peak.toFloat() / divisor
            if (guess * binHz < MELODY_MIN_F0_HZ) break
            val fundamental = guess.roundToInt()
            val around = max(m[fundamental], max(m[fundamental - 1], m.getOrElse(fundamental + 1) { 0f }))
            if (around < MELODY_FUNDAMENTAL * m[peak]) continue
            for (step in -6..6) {
                best = max(best, combScore(m, guess * (1f + step * 0.005f), from, to, total))
            }
        }
        return best
    }

    /** Puntaje de la serie armonica de [f0] (en bins) en la banda [from, to). */
    private fun combScore(m: FloatArray, f0: Float, from: Int, to: Int, total: Float): Float {
        // Ventana de ±1 bin si los armonicos estan separados; si no, solo el bin mas cercano
        val reach = if (f0 >= 5f) 1 else 0
        var captured = 0f
        var covered = 0
        var last = -1
        var h = 1
        while (true) {
            val center = (h * f0).roundToInt()
            if (center - reach >= to) break
            for (k in center - reach..center + reach) {
                if (k < from || k >= to || k <= last) continue
                captured += m[k] * m[k]
                covered++
                last = k
            }
            h++
        }
        val coverage = covered.toFloat() / (to - from)
        if (coverage >= 0.95f) return 0f
        return ((captured / total - coverage) / (1f - coverage)).coerceIn(0f, 1f)
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
