package com.irofactory.rgt.glyph.toy

import com.irofactory.rgt.audio.AudioBlobSimulation
import com.irofactory.rgt.audio.AudioSpectrumSource
import com.irofactory.rgt.glyph.GlyphFrames

/**
 * AudioSphereGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy "pulse": tres anillos que respiran con el audio de salida del
 * sistema, uno por familia de frecuencias (graves, voces, agudos).
 *
 * Si el Visualizer no arranca (permiso RECORD_AUDIO aun no concedido) o se
 * invalida a media cancion, se reintenta cada ~0.5 s y mientras tanto se
 * muestra un anillo tenue.
 */
class AudioSphereGlyphToyService : AnimatedGlyphToyService("AudioSphereGlyphToy") {

    private val sim = AudioBlobSimulation()
    private val mask = GlyphFrames.circularMask()
    private val idleFrame by lazy { GlyphFrames.idleRing(mask) }
    private val audioSource by lazy { AudioSpectrumSource(applicationContext) }
    private var retryIn = 0f

    override fun nextFrame(dt: Float): IntArray {
        if (!audioSource.isActive) {
            retryIn -= dt
            if (retryIn <= 0f) {
                audioSource.start()
                retryIn = 0.5f   // crear el Visualizer puede fallar justo al arrancar; reintentar pronto
            }
            if (!audioSource.isActive) return idleFrame
        }
        audioSource.update(dt)
        sim.step(dt, audioSource.bands, audioSource.loudness, audioSource.harshness)
        return GlyphFrames.fromGrid(sim.rasterize(), mask)
    }

    override fun onToyStop() {
        audioSource.stop()
    }
}
