package com.irofactory.rgt.glyph.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.irofactory.rgt.gallery.GalleryBitmapRenderer
import com.irofactory.rgt.gallery.GalleryImageProvider
import com.irofactory.rgt.glyph.GlyphFrames
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GalleryGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy que muestra una de las fotos elegidas en la app (Photo Picker),
 * reducida a la Glyph Matrix 25x25 por average pooling, o uno de "Mis
 * glifos" dibujados en la app.
 *
 * Interaccion:
 *   - Al seleccionar el toy → muestra una foto o glifo al azar.
 *   - Long-press (evento "change") → cambia a otro, sin repetir el actual.
 *
 * Sin fotos ni glifos muestra un anillo tenue, para distinguir "no hay nada
 * que mostrar" de "el toy no corre".
 */
class GalleryGlyphToyService : Service() {

    private val tag = "GalleryGlyphToy"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var loadJob: Job? = null

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private val mask = GlyphFrames.circularMask()
    @Volatile private var current: String? = null
    @Volatile private var lastFrame: IntArray? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) { super.handleMessage(msg); return }
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> showRandomPhoto()
                // Como toy de AOD el sistema avisa cada minuto: se repinta la misma foto
                GlyphToy.EVENT_AOD -> lastFrame?.let(::draw)
            }
        }
    }
    private val messenger = Messenger(handler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            try {
                glyphMatrixManager?.register(Glyph.DEVICE_23112)
            } catch (e: Exception) {
                Log.e(tag, "Error al registrar: ${e.message}")
            }
            showRandomPhoto()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onBind(intent: Intent?): IBinder {
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager?.init(callback)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        loadJob?.cancel()
        loadJob = null
        try { glyphMatrixManager?.turnOff(); glyphMatrixManager?.unInit() } catch (e: Exception) { }
        glyphMatrixManager = null
        return false
    }

    private fun showRandomPhoto() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val pick = GalleryImageProvider.randomPick(applicationContext, avoid = current)
            val s = pick?.stats
            when {
                pick == null -> Log.w(tag, "Sin fotos ni glifos: se eligen y dibujan en la app")
                s == null -> Log.i(tag, "Glifo ${pick.key}")
                else -> Log.i(tag, "Foto ${pick.key}: media=%.2f niveles=[%.2f, %.2f]".format(s.mean, s.low, s.high))
            }
            current = pick?.key ?: current
            val frame = pick?.let { GlyphFrames.fromGrid(it.grid, mask, GalleryBitmapRenderer.LED_GAMMA) }
                ?: GlyphFrames.idleRing(mask)
            lastFrame = frame
            withContext(Dispatchers.Main) { draw(frame) }
        }
    }

    private fun draw(frame: IntArray) {
        try {
            glyphMatrixManager?.setMatrixFrame(frame)
        } catch (e: GlyphException) {
            Log.e(tag, "Error al dibujar: ${e.message}")
        }
    }
}
