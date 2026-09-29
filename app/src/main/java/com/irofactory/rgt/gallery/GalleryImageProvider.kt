package com.irofactory.rgt.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * GalleryImageProvider
 * ───────────────────────────────────────────────────────────────────────────
 * Consulta MediaStore para elegir una foto al azar de la galeria del
 * dispositivo y la decodifica ya reducida (evita cargar el bitmap a
 * resolucion completa solo para terminar promediandolo a 25x25).
 */
object GalleryImageProvider {

    /** Pedir ambos: con "seleccionar fotos" Android 14+ concede solo el segundo. */
    val PERMISSIONS = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun pickRandomUri(context: Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val ids = ArrayList<Long>(cursor.count)
            while (cursor.moveToNext()) ids.add(cursor.getLong(idCol))
            val id = ids.randomOrNull() ?: return null
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
        return null
    }

    /** Decodifica el URI ya submuestreado a ~[targetSize]px de lado. */
    fun loadDownsampled(context: Context, uri: Uri, targetSize: Int = 200): Bitmap? {
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

    /** Foto al azar ya convertida a grilla de brillo 25x25, o null si no hay permiso/fotos. */
    fun randomGrid(context: Context): Array<FloatArray>? {
        if (!hasPermission(context)) return null
        val uri = pickRandomUri(context) ?: return null
        val source = loadDownsampled(context, uri) ?: return null
        return GalleryBitmapRenderer.toGrid(source).also { source.recycle() }
    }
}
