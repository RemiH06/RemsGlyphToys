package com.irofactory.rgt.fluid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.theme.sherryColors
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.isActive

/**
 * FluidMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa en pantalla de la simulacion de fluidos, misma resolucion
 * (25x25) y misma logica de gravedad/dispersion que corre en la Glyph Matrix
 * fisica via [com.irofactory.rgt.glyph.toy.FluidGlyphToyService]. Sirve para
 * ajustar parametros sin depender del carrusel del boton Glyph.
 */
@Composable
fun FluidMatrixView(
    params:    FluidParams = FluidParams(),
    modifier:  Modifier    = Modifier
) {
    val context = LocalContext.current
    val sc      = sherryColors

    // ── Simulacion ────────────────────────────────────────────────────────────
    val sim = remember {
        FluidSimulation(cols = 25, rows = 25, circularBounds = true).also { params.applyTo(it) }
    }

    LaunchedEffect(params) { params.applyTo(sim) }

    // ── Acelerometro ──────────────────────────────────────────────────────────
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val accelerometer  = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                applyAccelerometerGravity(sim, event.values[0], event.values[1], event.values[2])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // ── Grid rasterizado ──────────────────────────────────────────────────────
    var grid by remember { mutableStateOf(Array(25) { FloatArray(25) }) }

    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val dt = ((currentTime - lastTime) / 1000f).coerceIn(0.005f, 0.08f)
            lastTime = currentTime
            sim.step(dt)
            grid = sim.rasterize()
        }
    }

    GlyphMatrixCanvas(
        grid     = grid,
        neon     = sc.cyan,
        modifier = modifier,
        onClick  = { sim.splash() }
    )
}

/**
 * Deriva la gravedad 2D efectiva desde el acelerometro de 3 ejes.
 * Cuando el telefono esta acostado boca arriba/abajo (az domina), la
 * gravedad lateral se apaga y se aplica dispersion radial para que el
 * fluido no se quede estatico en el centro.
 */
internal fun applyAccelerometerGravity(sim: FluidSimulation, ax: Float, ay: Float, az: Float) {
    val hMag = sqrt(ax * ax + ay * ay)
    val vMag = abs(az)
    val totalMag = sqrt(hMag * hMag + vMag * vMag).coerceAtLeast(0.1f)
    val horizFactor = (vMag / totalMag).coerceIn(0f, 1f)

    // gravX sin negar: la Glyph Matrix esta en la espalda del telefono, al
    // girar a la izquierda (borde izquierdo abajo) el fluido debe caer hacia
    // ese lado. Con -ax se iba al lado contrario (verificado en Phone 3 real).
    sim.gravX = ax * (1f - horizFactor) * 2.0f
    sim.gravY = ay * (1f - horizFactor) * 2.0f

    if (horizFactor > 0.3f) {
        applyDispersionForce(sim, horizFactor)
    }
}

/** Empuja las particulas hacia un anillo intermedio cuando el fluido no tiene gravedad lateral. */
internal fun applyDispersionForce(sim: FluidSimulation, intensity: Float) {
    val strength = intensity * 0.8f
    for (p in sim.particles) {
        val dx = p.x - sim.centerX
        val dy = p.y - sim.centerY
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)
        val targetDist = sim.boundsRadius * 0.7f
        val forceMag = (targetDist - dist) * strength * 0.1f
        p.vx += (dx / dist) * forceMag
        p.vy += (dy / dist) * forceMag
    }
}
