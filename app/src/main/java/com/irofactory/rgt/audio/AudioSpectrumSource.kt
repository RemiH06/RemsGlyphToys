package com.irofactory.rgt.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.exp
import kotlin.math.hypot

/**
 * AudioSpectrumSource
 * ───────────────────────────────────────────────────────────────────────────
 * Lee la mezcla de audio de SALIDA del sistema con Visualizer(0) (antes del
 * ruteo a bocina, cable o Bluetooth) y la separa por FFT en [BAND_COUNT]
 * bandas, como las barras de Crisantemo pero agrupadas por "elemento" del
 * sonido en vez de bins parejos:
 *
 *   0 sub      20-90 Hz      patada, sub-bajo
 *   1 bass     90-250 Hz     bajo
 *   2 lowMid   250-500 Hz    cuerpo de instrumentos
 *   3 vocal    500-2000 Hz   voces, melodia
 *   4 presence 2-5 kHz       ataque, consonantes
 *   5 air      5-14 kHz      platillos, brillo
 *
 * Cada banda lleva su propio control de ganancia (pico con caida lenta),
 * asi una voz se mueve aunque el bajo domine la mezcla. El volumen real
 * (getMeasurementPeakRms, en mB) aparte, para distinguir silencio de musica.
 *
 * Permisos (Javadoc de Visualizer en Android 16): RECORD_AUDIO para usarlo,
 * MODIFY_AUDIO_SETTINGS para la session 0.
 */
class AudioSpectrumSource(private val context: Context) {

    companion object {
        const val BAND_COUNT = 6
        private val BAND_EDGES_HZ = floatArrayOf(20f, 90f, 250f, 500f, 2000f, 5000f, 14000f)
        // Piso de cada banda para el control de ganancia: evita amplificar ruido.
        private val BAND_FLOOR = floatArrayOf(6f, 6f, 4f, 3f, 2f, 1.5f)
    }

    private val tag = "AudioSpectrumSource"
    private var visualizer: Visualizer? = null
    private var fft: ByteArray? = null
    private var binHz = 0f
    private val measurement = Visualizer.MeasurementPeakRms()
    private var hasMeasurement = false

    private val peaks = FloatArray(BAND_COUNT) { BAND_FLOOR[it] }

    /** Energia relativa por banda, 0f..1f, suavizada (subida rapida, bajada lenta). */
    val bands = FloatArray(BAND_COUNT)

    /** Volumen real de salida, 0f (silencio) .. 1f (fuerte). */
    var loudness = 0f
        private set

    val isActive: Boolean get() = visualizer != null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (visualizer != null) return true
        if (!hasPermission()) {
            Log.w(tag, "Sin permiso RECORD_AUDIO, Visualizer no se inicia")
            return false
        }
        return try {
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            val v = Visualizer(0)
            v.captureSize = captureSize
            hasMeasurement = try {
                v.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                true
            } catch (e: Exception) { false }
            v.enabled = true
            visualizer = v
            fft = ByteArray(captureSize)
            binHz = v.samplingRate / 1000f / captureSize
            true
        } catch (e: Exception) {
            Log.e(tag, "No se pudo inicializar Visualizer: ${e.message}")
            stop()
            false
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) { }
        visualizer = null
        fft = null
        bands.fill(0f)
        loudness = 0f
    }

    /**
     * Lee un frame nuevo y actualiza [bands] y [loudness]. Si el efecto de
     * audio se invalida (cambio de cancion, de app o de salida), Visualizer
     * lanza IllegalStateException: se suelta y [isActive] queda en false para
     * que quien lo use lo vuelva a arrancar, en vez de tumbar el proceso.
     */
    fun update(dt: Float) {
        try {
            read(dt)
        } catch (e: RuntimeException) {
            Log.w(tag, "Visualizer invalido, se reinicia: ${e.message}")
            stop()
        }
    }

    private fun read(dt: Float) {
        val v = visualizer ?: return decay(dt)
        val buf = fft ?: return decay(dt)
        if (v.getFft(buf) != Visualizer.SUCCESS || binHz <= 0f) return decay(dt)

        val rawLoud = if (hasMeasurement && v.getMeasurementPeakRms(measurement) == Visualizer.SUCCESS) {
            // -5500 mB (~ -55 dB) es silencio practico, -1000 mB ya es fuerte.
            ((measurement.mRms + 5500) / 4500f).coerceIn(0f, 1f)
        } else {
            1f
        }
        loudness = smooth(loudness, rawLoud, dt, attack = 0.05f, release = 0.3f)

        // Formato de getFft: [Re0, Re(n/2), Re1, Im1, Re2, Im2, ...]
        val half = buf.size / 2
        val peakDecay = exp(-dt / 3f)
        for (b in 0 until BAND_COUNT) {
            val from = (BAND_EDGES_HZ[b] / binHz).toInt().coerceIn(1, half - 1)
            val to = (BAND_EDGES_HZ[b + 1] / binHz).toInt().coerceIn(from + 1, half)
            var sum = 0f
            for (k in from until to) {
                sum += hypot(buf[2 * k].toFloat(), buf[2 * k + 1].toFloat())
            }
            val energy = sum / (to - from)

            peaks[b] = maxOf(energy, peaks[b] * peakDecay, BAND_FLOOR[b])
            val target = (energy / peaks[b]).coerceIn(0f, 1f) * rawLoud.coerceAtLeast(0.15f)
            bands[b] = smooth(bands[b], if (rawLoud < 0.02f) 0f else target, dt, attack = 0.04f, release = 0.22f)
        }
    }

    private fun decay(dt: Float) {
        loudness = smooth(loudness, 0f, dt, 0.05f, 0.3f)
        for (b in 0 until BAND_COUNT) bands[b] = smooth(bands[b], 0f, dt, 0.04f, 0.22f)
    }

    private fun smooth(current: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
        val tau = if (target > current) attack else release
        return current + (target - current) * (1f - exp(-dt / tau))
    }
}
