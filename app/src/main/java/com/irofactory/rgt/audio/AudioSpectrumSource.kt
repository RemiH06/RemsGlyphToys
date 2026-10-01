package com.irofactory.rgt.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * AudioSpectrumSource
 * ───────────────────────────────────────────────────────────────────────────
 * Lee la mezcla de audio de SALIDA del sistema con Visualizer(0) (antes del
 * ruteo a bocina, cable o Bluetooth): su FFT pasa a [SpectrumAnalysis], que
 * saca las 6 bandas y la dureza de cada familia. El volumen real
 * (getMeasurementPeakRms, en mB) va aparte, para distinguir silencio de musica.
 *
 * Permisos (Javadoc de Visualizer en Android 16): RECORD_AUDIO para usarlo,
 * MODIFY_AUDIO_SETTINGS para la session 0.
 */
class AudioSpectrumSource(private val context: Context) {

    private val tag = "AudioSpectrumSource"
    private var visualizer: Visualizer? = null
    private var fft: ByteArray? = null
    private var binHz = 0f
    private val measurement = Visualizer.MeasurementPeakRms()
    private var hasMeasurement = false

    private var analysis = SpectrumAnalysis()
    private var magnitudes = FloatArray(0)

    /** Energia relativa por banda, 0f..1f (ver [SpectrumAnalysis.bands]). */
    val bands: FloatArray get() = analysis.bands

    /** Dureza de graves, medios y agudos, 0f suave .. 1f aspero (ver [SpectrumAnalysis.harshness]). */
    val harshness: FloatArray get() = analysis.harshness

    /** Golpe de bombo, 1f al golpe y se apaga (ver [SpectrumAnalysis.kick]). */
    val kick: Float get() = analysis.kick

    /** Si la cancion tiene bombo, casi 0f o 1f (ver [SpectrumAnalysis.kickPresence]). */
    val kickPresence: Float get() = analysis.kickPresence

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
            val v = Visualizer(0)
            // Dentro de un proceso los Visualizer de la session 0 comparten el
            // mismo efecto: si otro ya esta encendido, este nace encendido y su
            // tamano de captura ya no se puede cambiar. Se usa el que trae.
            if (!v.enabled) v.captureSize = Visualizer.getCaptureSizeRange()[1]
            val captureSize = v.captureSize
            hasMeasurement = try {
                v.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                true
            } catch (e: Exception) { false }
            v.enabled = true
            visualizer = v
            fft = ByteArray(captureSize)
            magnitudes = FloatArray(captureSize / 2)
            binHz = v.samplingRate / 1000f / captureSize
            true
        } catch (e: Exception) {
            Log.e(tag, "No se pudo inicializar Visualizer: ${e.message}")
            stop()
            false
        }
    }

    /** Vuelve a abrir el Visualizer y olvida todo lo aprendido (ganancias, bases, bombo). */
    fun restart(): Boolean {
        stop()
        analysis = SpectrumAnalysis()
        return start()
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) { }
        visualizer = null
        fft = null
        analysis.decay(1f)
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

        // Formato de getFft: [Re0, Re(n/2), Re1, Im1, Re2, Im2, ...]
        magnitudes[0] = abs(buf[0].toFloat())
        var any = magnitudes[0] > 0f
        for (k in 1 until magnitudes.size) {
            magnitudes[k] = hypot(buf[2 * k].toFloat(), buf[2 * k + 1].toFloat())
            if (magnitudes[k] > 0f) any = true
        }

        // Al pausar, la salida deja de procesar audio: la captura llega en cero
        // pero la medicion se queda con el ultimo volumen. Captura en cero es
        // silencio, diga lo que diga la medicion.
        val rawLoud = when {
            !any -> 0f
            hasMeasurement && v.getMeasurementPeakRms(measurement) == Visualizer.SUCCESS ->
                // -5500 mB (~ -55 dB) es silencio practico, -1000 mB ya es fuerte.
                ((measurement.mRms + 5500) / 4500f).coerceIn(0f, 1f)
            else -> 1f
        }
        loudness = smooth(loudness, rawLoud, dt, attack = 0.05f, release = 0.3f)
        analysis.analyze(magnitudes, binHz, rawLoud, dt)
    }

    private fun decay(dt: Float) {
        loudness = smooth(loudness, 0f, dt, 0.05f, 0.3f)
        analysis.decay(dt)
    }

    private fun smooth(current: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
        val tau = if (target > current) attack else release
        return current + (target - current) * (1f - exp(-dt / tau))
    }
}
