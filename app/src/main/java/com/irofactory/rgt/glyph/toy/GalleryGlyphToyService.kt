package com.irofactory.rgt.glyph.toy

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.content.ContextCompat
import com.irofactory.rgt.gallery.GalleryBitmapRenderer
import com.irofactory.rgt.gallery.GalleryImageProvider
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * GalleryGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy que muestra una foto al azar de la galeria del dispositivo,
 * reducida a la Glyph Matrix 25x25 por average pooling.
 *
 * Interaccion:
 *   - Al seleccionar el toy → carga y muestra una foto al azar.
 *   - Long-press (evento "change") → cambia a otra foto al azar.
 */
class GalleryGlyphToyService : Service() {

    private val tag = "GalleryGlyphToy"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var loadJob: Job? = null

    private var glyphMatrixManager: GlyphMatrixManager? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) { super.handleMessage(msg); return }
            if (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) == GlyphToy.EVENT_CHANGE) {
                showRandomPhoto()
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(tag, "Sin permiso de galeria (READ_MEDIA_IMAGES)")
            return
        }

        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val uri = GalleryImageProvider.pickRandomUri(applicationContext) ?: run {
                    Log.w(tag, "Galeria vacia")
                    return@launch
                }
                val source = GalleryImageProvider.loadDownsampled(applicationContext, uri) ?: return@launch
                val matrixBitmap = GalleryBitmapRenderer.toMatrix(source)
                source.recycle()
                render(matrixBitmap)
            } catch (e: Exception) {
                Log.e(tag, "Error al cargar foto: ${e.message}")
            }
        }
    }

    private fun render(bitmap: Bitmap) {
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
}
