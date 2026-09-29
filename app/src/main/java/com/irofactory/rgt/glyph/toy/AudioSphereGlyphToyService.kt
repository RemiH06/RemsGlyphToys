package com.irofactory.rgt.glyph.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.Log
import com.irofactory.rgt.audio.AudioBlobSimulation
import com.irofactory.rgt.audio.AudioSpectrumSource
import com.irofactory.rgt.glyph.GlyphFrames
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AudioSphereGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy "pulse": tres anillos que respiran con el audio de salida del
 * sistema, uno por familia de frecuencias (graves, voces, agudos). Mientras
 * el toy esta seleccionado corre un loop propio a ~30fps.
 *
 * Si el Visualizer no arranca (permiso RECORD_AUDIO aun no concedido), el
 * loop lo reintenta cada ~2s y mientras tanto muestra un anillo tenue.
 */
class AudioSphereGlyphToyService : Service() {

    private val tag = "AudioSphereGlyphToy"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var loopJob: Job? = null

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var registered = false

    private val sim = AudioBlobSimulation()
    private val mask = GlyphFrames.circularMask()
    private val idleFrame by lazy { GlyphFrames.idleRing(mask) }
    private val audioSource by lazy { AudioSpectrumSource(applicationContext) }

    private val messenger = Messenger(Handler(Looper.getMainLooper()))

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            try {
                registered = glyphMatrixManager?.register(Glyph.DEVICE_23112) ?: false
                glyphMatrixManager?.setGlyphMatrixTimeout(false)
            } catch (e: Exception) {
                Log.e(tag, "Error al registrar: ${e.message}")
            }
            startLoop()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            registered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager?.init(callback)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        loopJob?.cancel()
        loopJob = null
        audioSource.stop()
        try { glyphMatrixManager?.turnOff(); glyphMatrixManager?.unInit() } catch (e: Exception) { }
        glyphMatrixManager = null
        registered = false
        return false
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            var retryIn = 0f
            while (isActive) {
                if (!audioSource.isActive) {
                    retryIn -= 0.033f
                    if (retryIn <= 0f) {
                        audioSource.start()
                        retryIn = 2f
                    }
                }

                if (registered) {
                    val frame = if (audioSource.isActive) {
                        audioSource.update(0.033f)
                        sim.step(0.033f, audioSource.bands, audioSource.loudness)
                        GlyphFrames.fromGrid(sim.rasterize(), mask)
                    } else {
                        idleFrame
                    }
                    withContext(Dispatchers.Main) {
                        try {
                            glyphMatrixManager?.setMatrixFrame(frame)
                        } catch (e: GlyphException) {
                            Log.e(tag, "Error al dibujar: ${e.message}")
                        }
                    }
                }
                delay(33)
            }
        }
    }
}
