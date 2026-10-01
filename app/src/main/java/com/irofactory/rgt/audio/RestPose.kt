package com.irofactory.rgt.audio

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.irofactory.rgt.R

/** Como se ve pulse en silencio. [label] es el texto del selector en la app. */
enum class RestPose(@StringRes val label: Int, val usesGravity: Boolean = false) {
    /** Las tres figuras se juntan al centro. */
    CENTER(R.string.rest_center),
    /** Ojo de gato del tamano de la matriz: rendija vertical que mira de un lado a otro. */
    CAT_EYE(R.string.rest_cat_eye),
    /** Las tres figuras regulares, una dentro de otra como el logo, girando lento. */
    LOGO(R.string.rest_logo),
    /** El anillo del bombo quieto en su maximo, girando lento; las figuras camufladas en el. */
    BOOM(R.string.rest_boom),
    /** El triangulo, grande, apunta siempre al suelo; hexagono y diamante quedan tenues como marco. */
    PLUMB(R.string.rest_plumb, usesGravity = true),
    /** Las figuras se derriten en agua (la FLIP de fluid) que cae con la gravedad. */
    MELT(R.string.rest_melt, usesGravity = true),
    /** Las figuras como poligonos solidos que caen con la gravedad y chocan entre si. */
    PIECES(R.string.rest_pieces, usesGravity = true),
    /** Oscuridad: todo se apaga en silencio y vuelve a aparecer desvaneciendose. */
    VANISH(R.string.rest_vanish),
    /** La hora actual: cada figura traza los digitos de la hora o los minutos. */
    CLOCK(R.string.rest_clock);

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
