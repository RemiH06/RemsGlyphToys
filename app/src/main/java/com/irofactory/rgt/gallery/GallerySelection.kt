package com.irofactory.rgt.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit

/**
 * GallerySelection
 * ───────────────────────────────────────────────────────────────────────────
 * Fotos elegidas con el Photo Picker del sistema para el toy de galeria.
 * El picker no requiere permisos de almacenamiento; para que el toy pueda
 * seguir leyendolas en segundo plano (y tras reiniciar) se toma permiso
 * de lectura persistente sobre cada URI.
 */
object GallerySelection {

    private const val TAG = "GallerySelection"
    private const val PREFS = "gallery"
    private const val KEY_URIS = "uris"

    fun load(context: Context): List<Uri> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_URIS, emptySet())
            .orEmpty()
            .map(Uri::parse)

    /** Reemplaza la seleccion: toma permiso de las nuevas y libera el de las que salen. */
    fun replace(context: Context, uris: List<Uri>) {
        val resolver = context.contentResolver
        val previous = load(context).toSet()
        val kept = mutableListOf<Uri>()

        for (uri in uris.distinct()) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                kept += uri
            } catch (e: SecurityException) {
                Log.w(TAG, "Sin permiso persistente para $uri: ${e.message}")
            }
        }
        for (uri in previous - kept.toSet()) {
            try {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) { }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putStringSet(KEY_URIS, kept.map(Uri::toString).toSet())
        }
    }
}
