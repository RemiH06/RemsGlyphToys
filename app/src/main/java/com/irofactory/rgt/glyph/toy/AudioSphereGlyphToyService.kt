package com.irofactory.rgt.glyph.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.Log
import com.irofactory.rgt.audio.AudioLevelSource
import com.irofactory.rgt.audio.AudioSphereSimulation
import com.irofactory.rgt.glyph.GlyphDotRenderer
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AudioSphereGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy: esfera pulsante que respira con el volumen de salida del
 * sistema y dispara ondas expansivas en los picos (estilo NCS). Mientras
 * el toy esta seleccionado corre un loop propio a ~30fps leyendo
 * Visualizer(0) y dibujando cada frame con setMatrixFrame.
 *
 * Sin interaccion por touch: es puramente reactivo al audio, no necesita
 * mensajes del boton Glyph mas alla del binder que el sistema espera.
 */
class AudioSphereGlyphToyService : Service() {

    private val tag = "AudioSphereGlyphToy"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var loopJob: Job? = null

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var registered = false

    private val sim = AudioSphereSimulation(cols = 25, rows = 25)
    private val mask by lazy { sim.circularMask() }
    private val audioSource = AudioLevelSource()

    private val messenger = Messenger(Handler(Looper.getMainLooper()))

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            try {
                registered = glyphMatrixManager?.register(Glyph.DEVICE_23112) ?: false
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
        audioSource.start()
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
            while (isActive) {
                if (registered) {
                    sim.step(dt = 0.033f, rawLevel = audioSource.currentLevel())
                    val bitmap = GlyphDotRenderer.render(
                        grid      = sim.rasterize(),
                        mask      = mask,
                        sizePx    = 32,
                        colorArgb = Color.WHITE
                    )
                    try {
                        val frame = GlyphMatrixFrame.Builder()
                            .addTop(
                                GlyphMatrixObject.Builder()
                                    .setImageSource(bitmap)
                                    .setScale(100)
                                    .setPosition(0, 0)
                                    .setBrightness(255)
                                    .build()
                            )
                            .build(applicationContext)
                        glyphMatrixManager?.setMatrixFrame(frame.render())
                    } catch (e: GlyphException) {
                        Log.e(tag, "Error al dibujar: ${e.message}")
                    }
                }
                delay(33)
            }
        }
    }
}
