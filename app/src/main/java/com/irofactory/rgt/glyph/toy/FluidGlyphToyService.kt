package com.irofactory.rgt.glyph.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.irofactory.rgt.fluid.FluidParams
import com.irofactory.rgt.fluid.FluidSimulation
import com.irofactory.rgt.fluid.applyAccelerometerGravity
import com.irofactory.rgt.glyph.GlyphFrames
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FluidGlyphToyService
 * ───────────────────────────────────────────────────────────────────────────
 * Glyph Toy para el Nothing Phone 3 (DEVICE_23112, Glyph Matrix 25x25):
 * una simulacion SPH de fluidos que reacciona al acelerometro y se dibuja
 * en tiempo real sobre la matriz fisica.
 *
 * A diferencia de un Glyph Toy estatico (icono + texto), este mantiene un
 * loop propio a ~30fps mientras el sistema lo tiene enlazado (toy activo
 * en el carrusel del boton Glyph), dibujando cada frame con setMatrixFrame.
 *
 * Interaccion:
 *   - Touch-down (mantener presionado) → splash: empuja las particulas
 *     hacia afuera, como agitar el recipiente.
 *   - Long-press (evento "change") → reinicia la simulacion.
 */
class FluidGlyphToyService : Service() {

    private val tag = "FluidGlyphToy"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var loopJob: Job? = null

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var registered = false

    private val sim = FluidSimulation(cols = 25, rows = 25, circularBounds = true).also {
        FluidParams().applyTo(it)
    }
    private val mask by lazy { sim.circularMask() }

    // ── Acelerometro ──────────────────────────────────────────────────────────
    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            applyAccelerometerGravity(sim, event.values[0], event.values[1], event.values[2])
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ── Mensajeria del Glyph Toy ────────────────────────────────────────────
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) { super.handleMessage(msg); return }
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_ACTION_DOWN -> sim.splash()
                GlyphToy.EVENT_CHANGE      -> sim.reset()
            }
        }
    }
    private val messenger = Messenger(handler)

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            try {
                registered = glyphMatrixManager?.register(Glyph.DEVICE_23112) ?: false
                // Sin esto el sistema regresa al toy por default (reloj) a media partida.
                glyphMatrixManager?.setGlyphMatrixTimeout(false)
            } catch (e: Exception) {
                Log.e(tag, "Error al registrar: ${e.message}")
            }
            startLoop()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            registered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        if (!Common.is23112()) {
            Log.w(tag, "Dispositivo no soportado: ${android.os.Build.MODEL}")
        }
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager?.init(callback)
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        loopJob?.cancel()
        loopJob = null
        sensorManager.unregisterListener(sensorListener)
        try { glyphMatrixManager?.turnOff(); glyphMatrixManager?.unInit() } catch (e: Exception) { }
        glyphMatrixManager = null
        registered = false
        return false
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                if (registered) {
                    sim.step(dt = 0.033f)
                    val frame = GlyphFrames.fromGrid(sim.rasterize(), mask)
                    // El ejemplo oficial de Nothing entrega cada frame en el hilo principal.
                    withContext(Dispatchers.Main) {
                        try {
                            glyphMatrixManager?.setMatrixFrame(frame)
                        } catch (e: GlyphException) {
                            Log.e(tag, "Error al dibujar: ${e.message}")
                        }
                    }
                }
                delay(33)
            }
        }
    }
}
