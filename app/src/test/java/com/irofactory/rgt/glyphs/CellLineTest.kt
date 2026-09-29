package com.irofactory.rgt.glyphs

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class CellLineTest {

    @Test
    fun samePointIsOneCell() {
        assertEquals(listOf(3 to 4), cellLine(3, 4, 3, 4))
    }

    @Test
    fun lineIsContinuousAndHitsBothEnds() {
        val cases = listOf(
            intArrayOf(0, 0, 24, 24), intArrayOf(24, 0, 0, 24), intArrayOf(5, 20, 7, 2),
            intArrayOf(12, 12, 0, 13), intArrayOf(3, 3, 3, 18)
        )
        for ((r0, c0, r1, c1) in cases.map { it.toList() }) {
            val line = cellLine(r0, c0, r1, c1)
            assertEquals(r0 to c0, line.first())
            assertEquals(r1 to c1, line.last())
            // Sin huecos: cada paso avanza a lo mas una celda por eje
            for ((a, b) in line.zipWithNext()) {
                assertEquals(1, max(abs(a.first - b.first), abs(a.second - b.second)))
            }
            assertEquals(max(abs(r1 - r0), abs(c1 - c0)) + 1, line.size)
        }
    }
}
