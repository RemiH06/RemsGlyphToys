package com.irofactory.rgt.audio

import android.content.Context
import androidx.core.content.edit

/** Como se ve pulse en silencio. [label] es el texto del selector en la app. */
enum class RestPose(val label: String, val usesGravity: Boolean = false) {
    /** Las tres figuras se juntan al centro. */
    CENTER("centro"),
    /** Ojo entrecerrado: el diamante hace de parpados, el hexagono de iris y el triangulo de pupila. */
    HUMAN_EYE("ojo"),
    /** Ojo de gato del tamano de la matriz: rendija vertical que mira de un lado a otro. */
    CAT_EYE("gato"),
    /** Las tres figuras regulares, una dentro de otra como el logo, girando lento. */
    LOGO("logo"),
    /** El anillo del bombo quieto en su maximo, girando lento; las figuras camufladas en el. */
    BOOM("boom"),
    /** El triangulo, grande, apunta siempre al suelo; hexagono y diamante quedan tenues como marco. */
    PLUMB("plomada", usesGravity = true),
    /** Las figuras se derriten en agua (la FLIP de fluid) que cae con la gravedad. */
    MELT("derretir", usesGravity = true),
    /** Las figuras como poligonos solidos que caen con la gravedad y chocan entre si. */
    PIECES("piezas", usesGravity = true);

    companion object {
        private const val PREFS = "pulse"
        private const val KEY = "rest_pose"

        /** La elegida en la app; la leen la vista previa y el toy. */
        fun load(context: Context): RestPose {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == name } ?: CAT_EYE
        }

        fun save(context: Context, pose: RestPose) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, pose.name) }
        }
    }
}
