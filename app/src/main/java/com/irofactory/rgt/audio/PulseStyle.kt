package com.irofactory.rgt.audio

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.irofactory.rgt.R

/** Figura de los agudos. */
enum class HighShape(@StringRes val label: Int) {
    TRIANGLE(R.string.shape_triangle),
    /** Sin vertices; con sonido aspero le salen puntas cortas alrededor. */
    CIRCLE(R.string.shape_circle)
}

/** Figura de las voces. */
enum class MidShape(@StringRes val label: Int) {
    DIAMOND(R.string.shape_diamond),
    /** Almendra horizontal con puntas, siempre del mismo largo: lo que suena solo la abre. */
    ALMOND(R.string.shape_almond)
}

/** Figura de los graves. */
enum class LowShape(@StringRes val label: Int) {
    HEXAGON(R.string.shape_hexagon),
    PENTAGON(R.string.shape_pentagon)
}

/** Como se ve el bombo. */
enum class KickStyle(@StringRes val label: Int) {
    /** Banda irregular que entra desde fuera de la matriz con cada golpe y se retira. */
    OUTSIDE(R.string.kick_outside),
    /** Bolita en el centro que cada golpe lanza al borde y regresa tenue. */
    INSIDE(R.string.kick_inside),
    /** Anillo que se abre hasta el borde mientras la cancion tiene bombo y destella con cada golpe. */
    WAVE(R.string.kick_wave)
}

/**
 * PulseStyle
 * ───────────────────────────────────────────────────────────────────────────
 * Las figuras elegidas en la app para pulse. La leen la vista previa y el
 * toy; se guarda junto al reposo ([RestPose]).
 */
data class PulseStyle(
    val high: HighShape = HighShape.TRIANGLE,
    val mid: MidShape = MidShape.DIAMOND,
    val low: LowShape = LowShape.HEXAGON,
    val kick: KickStyle = KickStyle.OUTSIDE
) {
    companion object {
        private const val PREFS = "pulse"

        fun load(context: Context): PulseStyle {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return PulseStyle(
                high = prefs.getString("high", null).toEntry(HighShape.TRIANGLE),
                mid = prefs.getString("mid", null).toEntry(MidShape.DIAMOND),
                low = prefs.getString("low", null).toEntry(LowShape.HEXAGON),
                kick = prefs.getString("kick", null).toEntry(KickStyle.OUTSIDE)
            )
        }

        fun save(context: Context, style: PulseStyle) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString("high", style.high.name)
                putString("mid", style.mid.name)
                putString("low", style.low.name)
                putString("kick", style.kick.name)
            }
        }

        private inline fun <reified E : Enum<E>> String?.toEntry(default: E): E =
            enumValues<E>().firstOrNull { it.name == this } ?: default
    }
}
