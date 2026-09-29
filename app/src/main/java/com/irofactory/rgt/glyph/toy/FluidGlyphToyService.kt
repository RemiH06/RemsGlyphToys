package com.irofactory.rgt.glyph.toy

import android.hardware.Sensor
import android.hardware.SensorManager
import com.irofactory.rgt.fluid.FlipFluidSimulation
import com.irofactory.rgt.fluid.gravityListener
import com.irofactory.rgt.glyph.GlyphFrames
import com.nothing.ketchum.GlyphToy

/**
 * FluidGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy "fluid": agua simulada con FLIP (como la fluid pendant de
 * mitxela) que cae hacia donde inclinas el telefono.
 *
 * Interaccion:
 *   - Touch-down (mantener presionado) → agita el agua.
 *   - Long-press (evento "change") → la reinicia en reposo.
 */
class FluidGlyphToyService : AnimatedGlyphToyService("FluidGlyphToy") {

    private val sim = FlipFluidSimulation()
    private val mask = GlyphFrames.circularMask()
    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val listener by lazy { gravityListener(sim, viewedFromBack = true) }

    override fun onToyStart() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onToyStop() {
        sensorManager.unregisterListener(listener)
    }

    override fun onGlyphEvent(event: String) {
        when (event) {
            GlyphToy.EVENT_ACTION_DOWN -> sim.splash()
            GlyphToy.EVENT_CHANGE      -> sim.reset()
        }
    }

    override fun nextFrame(dt: Float): IntArray {
        sim.step(dt)
        return GlyphFrames.fromGrid(sim.rasterize(), mask)
    }
}
