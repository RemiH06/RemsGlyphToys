package com.irofactory.rgt.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * AudioLevelSource
 * ───────────────────────────────────────────────────────────────────────────
 * Envuelve Visualizer(0), que capta la mezcla de audio de SALIDA del
 * sistema (session 0 = output mix) antes de que se rutee a bocina,
 * audifonos con cable o Bluetooth.
 *
 * Permisos (segun el Javadoc de Visualizer en Android 16): usarlo requiere
 * RECORD_AUDIO, y crearlo sobre la session 0 requiere MODIFY_AUDIO_SETTINGS.
 */
class AudioLevelSource(private val context: Context) {

    private val tag = "AudioLevelSource"
    private var visualizer: Visualizer? = null
    private var waveform: ByteArray? = null

    val isActive: Boolean get() = visualizer != null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Intenta arrancar el Visualizer. Regresa false si falta permiso o el sistema lo rechaza. */
    fun start(): Boolean {
        if (visualizer != null) return true
        if (!hasPermission()) {
            Log.w(tag, "Sin permiso RECORD_AUDIO, Visualizer no se inicia")
            return false
        }
        return try {
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer = Visualizer(0).apply {
                setCaptureSize(captureSize)
                enabled = true
            }
            waveform = ByteArray(captureSize)
            true
        } catch (e: Exception) {
            Log.e(tag, "No se pudo inicializar Visualizer: ${e.message}")
            visualizer = null
            waveform = null
            false
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
