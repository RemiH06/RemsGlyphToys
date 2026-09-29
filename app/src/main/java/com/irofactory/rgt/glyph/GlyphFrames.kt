package com.irofactory.rgt.glyph

import kotlin.math.pow

/**
 * GlyphFrames
 * ───────────────────────────────────────────────────────────────────────────
 * Arma el IntArray de 25x25 que recibe GlyphMatrixManager.setMatrixFrame(int[]),
 * igual que el proyecto de ejemplo oficial de Nothing. Cada valor va de 0 a
 * 4095: el propio SDK convierte su gris 0..255 multiplicando por 16.
 */
object GlyphFrames {

    const val SIZE = 25
    const val MAX_BRIGHTNESS = 4095

    /**
     * LEDs por fila de la matriz real del Phone (3), centrados: 489 en total.
     * Medido de la imagen oficial de Nothing; equivale a las celdas cuyo
     * centro queda a <= 12.5 celdas del centro de la grilla.
     */
    val ROW_SPANS = intArrayOf(
        7, 11, 15, 17, 19, 21, 21, 23, 23,
        25, 25, 25, 25, 25, 25, 25,
        23, 23, 21, 21, 19, 17, 15, 11, 7
    )

    /** Radio (en celdas) del disco real de LEDs, medido desde el centro de la grilla. */
    const val LED_RADIUS = 12.5f

    fun circularMask(): Array<BooleanArray> = Array(SIZE) { r ->
        val start = (SIZE - ROW_SPANS[r]) / 2
        BooleanArray(SIZE) { c -> c >= start && c < start + ROW_SPANS[r] }
    }

    /**
     * Grilla de brillo 0f..1f → frame. Las celdas fuera de [mask] quedan
     * apagadas. [gamma] > 1 pasa de brillo percibido a PWM lineal de los
     * LEDs (fotos); los efectos sinteticos se dejan en 1.
     */
    fun fromGrid(grid: Array<FloatArray>, mask: Array<BooleanArray>, gamma: Float = 1f): IntArray {
        val frame = IntArray(SIZE * SIZE)
        for (r in 0 until minOf(SIZE, grid.size)) {
            val row = grid[r]
            for (c in 0 until minOf(SIZE, row.size)) {
                if (!mask[r][c]) continue
                val b = row[c]
                if (b.isNaN() || b <= 0f) continue
                val linear = if (gamma == 1f) b.coerceAtMost(1f) else b.coerceAtMost(1f).pow(gamma)
                frame[r * SIZE + c] = (linear * MAX_BRIGHTNESS).toInt()
            }
        }
        return frame
    }

    /** Anillo tenue en el borde: indica que el toy esta vivo pero no tiene nada que mostrar. */
    fun idleRing(mask: Array<BooleanArray>, brightness: Float = 0.25f): IntArray {
        val grid = Array(SIZE) { r ->
            FloatArray(SIZE) { c ->
                val edge = mask[r][c] && (
                    r == 0 || c == 0 || r == SIZE - 1 || c == SIZE - 1 ||
                        !mask[r - 1][c] || !mask[r + 1][c] || !mask[r][c - 1] || !mask[r][c + 1]
                    )
                if (edge) brightness else 0f
            }
        }
        return fromGrid(grid, mask)
    }
}
