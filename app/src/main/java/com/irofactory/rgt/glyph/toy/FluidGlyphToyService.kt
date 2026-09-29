package com.irofactory.rgt.glyph.toy

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.irofactory.rgt.fluid.FluidParams
import com.irofactory.rgt.fluid.FluidSimulation
import com.irofactory.rgt.fluid.applyAccelerometerGravity
import com.irofactory.rgt.glyph.GlyphFrames
import com.nothing.ketchum.GlyphToy

/**
 * FluidGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy "fluid": simulacion SPH que reacciona al acelerometro y se
 * dibuja en tiempo real sobre la Glyph Matrix del Phone (3).
 *
 * Interaccion:
 *   - Touch-down (mantener presionado) → splash: empuja las particulas
 *     hacia afuera, como agitar el recipiente.
 *   - Long-press (evento "change") → reinicia la simulacion.
 */
class FluidGlyphToyService : AnimatedGlyphToyService("FluidGlyphToy") {

    private val sim = FluidSimulation(cols = 25, rows = 25, circularBounds = true).also {
        FluidParams().applyTo(it)
    }
    private val mask = GlyphFrames.circularMask()

    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            applyAccelerometerGravity(sim, event.values[0], event.values[1], event.values[2])
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onToyStart() {
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onToyStop() {
        sensorManager.unregisterListener(sensorListener)
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
