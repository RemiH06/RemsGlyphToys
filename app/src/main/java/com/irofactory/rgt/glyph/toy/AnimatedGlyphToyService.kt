package com.irofactory.rgt.glyph.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AnimatedGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Base de los Glyph Toys animados (fluid, pulse), mismo patron que la clase
 * base del proyecto de ejemplo de Nothing. Se encarga de:
 *
 *   - enlazar y registrar la Glyph Matrix, y soltarla al desenlazar;
 *   - un loop a ~30fps que pide [nextFrame] y lo entrega en el hilo principal;
 *   - mantener el toy en pantalla: desactiva el timeout de la matriz al
 *     conectar y lo vuelve a desactivar cada [KEEP_AWAKE_SECONDS] por si el
 *     sistema lo restablece;
 *   - que ningun error de un frame tumbe el proceso (eso haria que el sistema
 *     regrese al toy por default): se registra y el loop sigue;
 *   - registrar en logcat cuanto vivio el toy y por que termino.
 */
abstract class AnimatedGlyphToyService(private val tag: String) : Service() {

    private companion object {
        const val FRAME_SECONDS = 0.033f
        const val KEEP_AWAKE_SECONDS = 10f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var boundAt = 0L

    private var glyphMatrixManager: GlyphMatrixManager? = null
    @Volatile private var registered = false

    /** Frame siguiente: IntArray de 25x25 con valores 0..4095. Corre en el hilo del loop. */
    protected abstract fun nextFrame(dt: Float): IntArray

    /** Al enlazarse el toy (antes del primer frame). */
    protected open fun onToyStart() {}

    /** Al desenlazarse el toy. */
    protected open fun onToyStop() {}

    /** Eventos del boton Glyph (GlyphToy.EVENT_*), en el hilo principal. */
    protected open fun onGlyphEvent(event: String) {}

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) { super.handleMessage(msg); return }
            val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) ?: return
            // Como toy de AOD el sistema manda EVENT_AOD cada minuto; el loop ya sigue dibujando.
            if (event == GlyphToy.EVENT_AOD) Log.d(tag, "EVENT_AOD tras ${aliveSeconds()}s") else onGlyphEvent(event)
        }
    }
    private val messenger = Messenger(handler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            try {
                registered = glyphMatrixManager?.register(Glyph.DEVICE_23112) ?: false
            } catch (e: Exception) {
                Log.e(tag, "Error al registrar: ${e.message}")
            }
            Log.i(tag, "Glyph Matrix conectada, registrada=$registered")
            keepAwake()
            startLoop()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            registered = false
            Log.w(tag, "Servicio de la Glyph Matrix desconectado tras ${aliveSeconds()}s")
        }
    }

    final override fun onBind(intent: Intent?): IBinder {
        boundAt = SystemClock.elapsedRealtime()
        Log.i(tag, "onBind")
        onToyStart()
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager?.init(callback)
        return messenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        Log.i(tag, "onUnbind tras ${aliveSeconds()}s (el sistema retiro el toy)")
        loopJob?.cancel()
        loopJob = null
        onToyStop()
        try { glyphMatrixManager?.turnOff(); glyphMatrixManager?.unInit() } catch (e: Exception) { }
        glyphMatrixManager = null
        registered = false
        return false
    }

    private fun aliveSeconds() = (SystemClock.elapsedRealtime() - boundAt) / 1000

    private fun keepAwake() {
        try {
            glyphMatrixManager?.setGlyphMatrixTimeout(false)
        } catch (e: Exception) {
            Log.w(tag, "No se pudo desactivar el timeout: ${e.message}")
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            var sinceKeepAwake = 0f
            while (isActive) {
                if (registered) {
                    val frame = try {
                        nextFrame(FRAME_SECONDS)
                    } catch (e: Exception) {
                        Log.e(tag, "Error calculando el frame, se omite", e)
                        null
                    }
                    sinceKeepAwake += FRAME_SECONDS
                    val refresh = sinceKeepAwake >= KEEP_AWAKE_SECONDS
                    if (refresh) sinceKeepAwake = 0f

                    withContext(Dispatchers.Main) {
                        if (refresh) keepAwake()
                        if (frame != null) {
                            try {
                                glyphMatrixManager?.setMatrixFrame(frame)
                            } catch (e: Exception) {
                                Log.e(tag, "Error al dibujar: ${e.message}")
                            }
                        }
                    }
                }
                delay(33)
            }
        }
    }
}
