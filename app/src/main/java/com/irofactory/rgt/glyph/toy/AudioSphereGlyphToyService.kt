package com.irofactory.rgt.glyph.toy

import android.hardware.SensorManager
import com.irofactory.rgt.audio.AudioBlobSimulation
import com.irofactory.rgt.audio.AudioSpectrumSource
import com.irofactory.rgt.audio.PulseStyle
import com.irofactory.rgt.audio.RestPose
import com.irofactory.rgt.fluid.GravitySampler
import com.irofactory.rgt.glyph.GlyphFrames

/**
 * AudioSphereGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy "pulse": tres anillos que respiran con el audio de salida del
 * sistema, uno por familia de frecuencias (graves, voces, agudos).
 *
 * Si el Visualizer no arranca o se invalida (pasa al pausar la musica), se
 * reintenta cada ~1 s y mientras tanto las figuras siguen como en silencio,
 * para que el reposo se termine de formar. El anillo tenue queda solo para
 * cuando falta el permiso RECORD_AUDIO.
 *
 * El acelerometro solo se escucha mientras el reposo elegido usa la gravedad.
 */
class AudioSphereGlyphToyService : AnimatedGlyphToyService("AudioSphereGlyphToy") {

    private val sim = AudioBlobSimulation()
    private val mask = GlyphFrames.circularMask()
    private val idleFrame by lazy { GlyphFrames.idleRing(mask) }
    private val audioSource by lazy { AudioSpectrumSource(applicationContext) }
    private var retryIn = 0f
    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val gravity = GravitySampler(viewedFromBack = true)
    private var listening = false

    override fun nextFrame(dt: Float): IntArray {
        if (!audioSource.isActive) {
            retryIn -= dt
            if (retryIn <= 0f) {
                audioSource.start()
                retryIn = 1f   // crear el Visualizer puede fallar justo al arrancar; reintentar pronto
            }
            if (!audioSource.isActive && !audioSource.hasPermission()) return idleFrame
        }
        audioSource.update(dt)
        // Se lee en cada cuadro (SharedPreferences ya lo tiene en memoria) para tomar el cambio al instante
        sim.restPose = RestPose.load(applicationContext)
        sim.style = PulseStyle.load(applicationContext)
        listenGravity(sim.restPose.usesGravity)
        if (listening) gravity.drain(sim::setGravity)
        val now = java.time.LocalTime.now()
        sim.step(dt, audioSource.bands, audioSource.loudness, audioSource.harshness,
                audioSource.kick, audioSource.kickPresence, audioSource.melody,
                now.hour, now.minute)
        return GlyphFrames.fromGrid(sim.rasterize(), mask)
    }

    override fun onToyStop() {
        audioSource.stop()
        listenGravity(false)
    }

    private fun listenGravity(on: Boolean) {
        if (on == listening) return
        listening = on
        if (on) {
            gravity.register(sensorManager)
        } else {
            sensorManager.unregisterListener(gravity)
        }
    }
}
