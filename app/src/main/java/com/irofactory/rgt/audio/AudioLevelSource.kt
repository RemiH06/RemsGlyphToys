package com.irofactory.rgt.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.sqrt

/**
 * AudioLevelSource
 * ───────────────────────────────────────────────────────────────────────────
 * Envuelve Visualizer(0), que capta la mezcla de audio de SALIDA del
 * sistema (session 0 = master mix) antes de que se rutee a bocina,
 * audifonos con cable o Bluetooth. Por eso reacciona igual sin importar
 * por donde este saliendo el sonido.
 *
 * No requiere RECORD_AUDIO: a diferencia de AudioRecord/MediaRecorder,
 * Visualizer no lee del microfono, lee del pipeline de salida.
 */
class AudioLevelSource {

    private val tag = "AudioLevelSource"
    private var visualizer: Visualizer? = null
    private var waveform: ByteArray? = null

    fun start() {
        if (visualizer != null) return
        try {
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer = Visualizer(0).apply {
                setCaptureSize(captureSize)
                enabled = true
            }
            waveform = ByteArray(captureSize)
        } catch (e: Exception) {
            Log.e(tag, "No se pudo inicializar Visualizer: ${e.message}")
            visualizer = null
            waveform = null
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) { }
        visualizer = null
        waveform = null
    }

    /** RMS normalizado 0f..1f del audio de salida actual. 0 si no hay Visualizer activo o silencio. */
    fun currentLevel(): Float {
        val v = visualizer ?: return 0f
        val buf = waveform ?: return 0f
        if (v.getWaveForm(buf) != Visualizer.SUCCESS) return 0f

        var sumSq = 0.0
        for (b in buf) {
            val sample = (b.toInt() and 0xFF) - 128
            sumSq += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSq / buf.size)
        return (rms / 128.0).toFloat().coerceIn(0f, 1f)
    }
}
