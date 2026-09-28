package com.irofactory.rgt.fluid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.ui.theme.metroColors
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
    val mc      = metroColors

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
    val mask  = remember { sim.circularMask() }

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

    val fluidColor  = mc.accent
    val emptyColor  = mc.surface2
    val borderColor = mc.border

    Box(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .border(0.5.dp, borderColor, CircleShape)
                .clickable { sim.splash() }
                .padding(4.dp)
        ) {
            val cols  = 25
            val rows  = 25
            val cellW = size.width / cols
            val cellH = size.height / rows
            val dotR  = cellW * 0.38f

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!mask[r][c]) continue

                    val brightness = grid[r][c]
                    val cx = c * cellW + cellW / 2f
                    val cy = r * cellH + cellH / 2f

                    val dotColor = if (brightness > 0.01f) {
                        fluidColor.copy(alpha = brightness.coerceIn(0.05f, 1f))
                    } else {
                        emptyColor.copy(alpha = 0.4f)
                    }

                    drawCircle(color = dotColor, radius = dotR, center = Offset(cx, cy))
                }
            }
        }
    }
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

    sim.gravX = -ax * (1f - horizFactor) * 2.0f
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
