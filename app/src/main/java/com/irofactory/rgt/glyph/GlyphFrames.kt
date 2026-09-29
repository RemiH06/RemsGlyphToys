package com.irofactory.rgt.glyph

import kotlin.math.sqrt

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

    fun circularMask(size: Int = SIZE): Array<BooleanArray> {
        val center = size / 2f
        val radius = size / 2f - 0.5f
        return Array(size) { r ->
            BooleanArray(size) { c ->
                val dx = c + 0.5f - center
                val dy = r + 0.5f - center
                sqrt(dx * dx + dy * dy) <= radius
            }
        }
    }

    /** Grilla de brillo 0f..1f → frame. Las celdas fuera de [mask] quedan apagadas. */
    fun fromGrid(grid: Array<FloatArray>, mask: Array<BooleanArray>): IntArray {
        val frame = IntArray(SIZE * SIZE)
        for (r in 0 until minOf(SIZE, grid.size)) {
            val row = grid[r]
            for (c in 0 until minOf(SIZE, row.size)) {
                if (!mask[r][c]) continue
                val b = row[c]
                if (b.isNaN() || b <= 0f) continue
                frame[r * SIZE + c] = (b.coerceAtMost(1f) * MAX_BRIGHTNESS).toInt()
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
