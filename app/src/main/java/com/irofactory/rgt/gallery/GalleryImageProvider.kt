package com.irofactory.rgt.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.irofactory.rgt.glyphs.MyGlyphs

/**
 * GalleryImageProvider
 * ───────────────────────────────────────────────────────────────────────────
 * Elige al azar que mostrar en el toy gallery: una de las fotos de
 * [GallerySelection] o uno de [MyGlyphs], todos con la misma probabilidad.
 * Las fotos se decodifican ya reducidas (evita cargar el bitmap a
 * resolucion completa solo para terminar promediandolo a 25x25).
 */
object GalleryImageProvider {

    /** [key] identifica lo elegido para no repetirlo; [stats] solo existe en fotos. */
    class Pick(val key: String, val grid: Array<FloatArray>, val stats: GalleryBitmapRenderer.Stats?)

    /** Fotos y glifos que puede mostrar el toy. */
    fun count(context: Context) = GallerySelection.load(context).size + MyGlyphs.load(context).size

    /**
     * Foto o glifo al azar, ya como grilla 25x25 de brillo perceptual. Evita
     * repetir [avoid] si hay mas de uno. Salta las fotos que ya no se pueden
     * leer (borradas del telefono). Null si no hay nada.
     */
    fun randomPick(context: Context, avoid: String? = null): Pick? {
        val photos = GallerySelection.load(context).map { uri ->
            uri.toString() to { photoGrid(context, uri) }
        }
        val glyphs = MyGlyphs.load(context).map { glyph ->
            "glyph:${glyph.id}" to { glyph.toGrid() to null }
        }
        val candidates = (photos + glyphs).shuffled().sortedBy { if (it.first == avoid) 1 else 0 }
        for ((key, render) in candidates) {
            val (grid, stats) = render() ?: continue
            return Pick(key, grid, stats)
        }
        return null
    }

    private fun photoGrid(context: Context, uri: Uri): Pair<Array<FloatArray>, GalleryBitmapRenderer.Stats>? {
        val source = try {
            loadDownsampled(context, uri)
        } catch (e: Exception) {
            Log.w("GalleryImageProvider", "No se pudo leer $uri: ${e.message}")
            null
        } ?: return null
        val result = GalleryBitmapRenderer.toGrid(source)
        source.recycle()
        return result
    }

    /** Decodifica el URI ya submuestreado a ~[targetSize]px de lado. */
    private fun loadDownsampled(context: Context, uri: Uri, targetSize: Int = 200): Bitmap? {
        val resolver = context.contentResolver

        // Con inJustDecodeBounds, decodeStream siempre regresa null: solo llena outWidth/outHeight
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= targetSize && h / 2 >= targetSize) {
            w /= 2; h /= 2; sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
