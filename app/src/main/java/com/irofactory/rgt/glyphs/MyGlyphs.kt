package com.irofactory.rgt.glyphs

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.irofactory.rgt.glyph.GlyphFrames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Un glifo dibujado: 25x25 niveles 0..[MyGlyphs.LEVELS] (0 = apagado), fila por fila. */
class MyGlyph(val id: String, val levels: ByteArray) {

    val isEmpty: Boolean get() = levels.all { it.toInt() == 0 }

    /** Brillo perceptual 0..1 por celda, igual que la grilla de una foto. */
    fun toGrid(): Array<FloatArray> = Array(GlyphFrames.SIZE) { r ->
        FloatArray(GlyphFrames.SIZE) { c -> levels[r * GlyphFrames.SIZE + c] / MyGlyphs.LEVELS.toFloat() }
    }
}

/**
 * MyGlyphs
 * ───────────────────────────────────────────────────────────────────────────
 * "Mis glifos": lo que se dibuja en la app. El toy gallery los muestra en la
 * misma rotacion que las fotos. Se guardan en un JSON privado de la app, con
 * los niveles como una cadena de 625 digitos.
 *
 * Todo pasa por este objeto, asi que la lista en memoria es la del disco:
 * la UI la observa y el toy la lee (mismo proceso).
 */
object MyGlyphs {

    /** Intensidades del pincel, sin contar el borrador. */
    const val LEVELS = 5

    private const val TAG = "MyGlyphs"
    private const val FILE = "my_glyphs.json"
    private const val CELLS = GlyphFrames.SIZE * GlyphFrames.SIZE

    private val all = MutableStateFlow<List<MyGlyph>>(emptyList())
    @Volatile private var loaded = false

    fun observe(context: Context): StateFlow<List<MyGlyph>> {
        ensureLoaded(context)
        return all
    }

    fun load(context: Context): List<MyGlyph> {
        ensureLoaded(context)
        return all.value
    }

    fun blank() = MyGlyph(UUID.randomUUID().toString(), ByteArray(CELLS))

    /** Guarda uno nuevo o reemplaza el del mismo id. */
    @Synchronized
    fun save(context: Context, glyph: MyGlyph) {
        ensureLoaded(context)
        val list = all.value.toMutableList()
        val i = list.indexOfFirst { it.id == glyph.id }
        if (i >= 0) list[i] = glyph else list += glyph
        write(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        ensureLoaded(context)
        write(context, all.value.filter { it.id != id })
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        all.value = read(file(context))
        loaded = true
    }

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, FILE))

    private fun read(file: AtomicFile): List<MyGlyph> {
        if (!file.baseFile.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readFully().decodeToString())
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                decode(o.getString("levels"))?.let { MyGlyph(o.getString("id"), it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudieron leer los glifos: ${e.message}")
            emptyList()
        }
    }

    private fun write(context: Context, list: List<MyGlyph>) {
        val array = JSONArray()
        for (g in list) array.put(JSONObject().put("id", g.id).put("levels", encode(g.levels)))
        val file = file(context)
        val out = file.startWrite()
        try {
            out.write(array.toString().encodeToByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "No se pudieron guardar los glifos: ${e.message}")
            return
        }
        all.value = list
    }

    private fun encode(levels: ByteArray) = levels.joinToString("") { it.toString() }

    private fun decode(text: String): ByteArray? {
        if (text.length != CELLS) return null
        return ByteArray(CELLS) { i ->
            val level = text[i] - '0'
            if (level !in 0..LEVELS) return null
            level.toByte()
        }
    }
}
