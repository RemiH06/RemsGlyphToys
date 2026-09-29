package com.irofactory.rgt.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

/**
 * GalleryImageProvider
 * ───────────────────────────────────────────────────────────────────────────
 * Elige al azar una de las fotos de [GallerySelection] y la decodifica ya
 * reducida (evita cargar el bitmap a resolucion completa solo para
 * terminar promediandolo a 25x25).
 */
object GalleryImageProvider {

    class Pick(val uri: Uri, val grid: Array<FloatArray>, val stats: GalleryBitmapRenderer.Stats)

    /**
     * Foto al azar de la seleccion, ya convertida a grilla 25x25. Evita
     * repetir [avoid] si hay mas de una. Salta las que ya no se pueden leer
     * (borradas del telefono). Null si la seleccion esta vacia.
     */
    fun randomPick(context: Context, avoid: Uri? = null): Pick? {
        val candidates = GallerySelection.load(context).shuffled()
            .sortedBy { if (it == avoid) 1 else 0 }
        for (uri in candidates) {
            val source = try {
                loadDownsampled(context, uri)
            } catch (e: Exception) {
                Log.w("GalleryImageProvider", "No se pudo leer $uri: ${e.message}")
                null
            } ?: continue
            val (grid, stats) = GalleryBitmapRenderer.toGrid(source)
            source.recycle()
            return Pick(uri, grid, stats)
        }
        return null
    }

    /** Decodifica el URI ya submuestreado a ~[targetSize]px de lado. */
    private fun loadDownsampled(context: Context, uri: Uri, targetSize: Int = 200): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
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
