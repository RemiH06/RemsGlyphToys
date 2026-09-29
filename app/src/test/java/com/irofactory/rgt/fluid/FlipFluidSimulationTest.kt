package com.irofactory.rgt.fluid

import com.irofactory.rgt.glyph.GlyphFrames
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Corre el FLIP por escenas (reposo, inclinado, de cabeza, agitado) y
 * comprueba que siga siendo agua: sin NaN y conservando el volumen visible.
 * Deja los cuadros en build/flip_frames.txt para revisarlos a ojo.
 */
class FlipFluidSimulationTest {

    private val mask = GlyphFrames.circularMask()
    private val shades = " .:-=+*#%@"

    @Test
    fun keepsVolumeAndStaysFinite() {
        val sim = FlipFluidSimulation()
        val dt = 1f / 30f
        val out = StringBuilder()
        var baseline = -1f
        var steps = 0
        var nanos = 0L

        fun run(label: String, seconds: Float, gx: Float, gy: Float, snapshots: List<Float>, splashAt: Float = -1f) {
            sim.setGravity(gx, gy)
            var t = 0f
            var next = 0
            while (t < seconds - 1e-4f) {
                if (splashAt >= 0f && t < splashAt + 1e-4f && t + dt >= splashAt) sim.splash()
                val start = System.nanoTime()
                sim.step(dt)
                nanos += System.nanoTime() - start
                steps++
                t += dt
                if (next < snapshots.size && t >= snapshots[next] - 1e-4f) {
                    val grid = sim.rasterize()
                    val volume = grid.withIndex().sumOf { (r, row) ->
                        row.withIndex().sumOf { (c, b) -> if (mask[r][c]) b.toDouble() else 0.0 }
                    }.toFloat()
                    grid.forEach { row -> row.forEach { assertFalse("NaN en $label", it.isNaN()) } }
                    if (baseline < 0f) baseline = volume
                    out.append("== $label t=%.2fs volumen=%.0f\n".format(t, volume))
                    for (r in 0 until GlyphFrames.SIZE) {
                        for (c in 0 until GlyphFrames.SIZE) {
                            out.append(if (!mask[r][c]) ' ' else shades[(grid[r][c] * (shades.length - 1)).toInt()])
                        }
                        out.append('\n')
                    }
                    next++
                }
            }
        }

        run("reposo", 2.0f, 0f, 9.81f, listOf(0.1f, 1.0f, 2.0f))
        run("inclinado a la derecha", 1.5f, 6.9f, 6.9f, listOf(0.3f, 0.7f, 1.5f))
        run("de lado a la izquierda", 1.5f, -9.81f, 0f, listOf(0.3f, 0.7f, 1.5f))
        run("de cabeza", 1.2f, 0f, -9.81f, listOf(0.15f, 0.35f, 0.6f, 1.2f))
        run("agitado", 1.2f, 0f, 9.81f, listOf(0.1f, 0.3f, 0.6f, 1.2f), splashAt = 0f)
        run("reposo final", 2.0f, 0f, 9.81f, listOf(2.0f))

        val finalVolume = out.lines().last { it.startsWith("==") }.substringAfter("volumen=").toFloat()
        out.append("pasos=$steps  promedio=%.2f ms/paso\n".format(nanos / 1e6 / steps))
        File("build/flip_frames.txt").apply { parentFile.mkdirs() }.writeText(out.toString())

        assertEquals("el volumen visible debe conservarse", baseline, finalVolume, baseline * 0.25f)
    }
}
