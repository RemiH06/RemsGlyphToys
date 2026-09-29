package com.irofactory.rgt.glyphs

import kotlin.math.abs

/**
 * Celdas entre dos puntos de la grilla (Bresenham), ambos incluidos. Un
 * trazo rapido salta varias celdas entre dos eventos del touch; sin esto el
 * pincel dejaria huecos.
 */
fun cellLine(r0: Int, c0: Int, r1: Int, c1: Int): List<Pair<Int, Int>> {
    val cells = ArrayList<Pair<Int, Int>>()
    val dr = abs(r1 - r0)
    val dc = abs(c1 - c0)
    val sr = if (r1 > r0) 1 else -1
    val sc = if (c1 > c0) 1 else -1
    var r = r0
    var c = c0
    var err = dc - dr
    while (true) {
        cells += r to c
        if (r == r1 && c == c1) return cells
        val e2 = 2 * err
        if (e2 > -dr) { err -= dr; c += sc }
        if (e2 < dc) { err += dc; r += sr }
    }
}
