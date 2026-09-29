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
import kotlinx.coroutines.isActive

/**
 * Vista previa en pantalla del agua: el mismo FLIP y el mismo mapeo del
 * acelerometro que corren en la Glyph Matrix via FluidGlyphToyService.
 */
@Composable
fun FluidMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors
    val sim = remember { FlipFluidSimulation() }

    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    DisposableEffect(Unit) {
        val listener = gravityListener(sim)
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }

    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val dt = ((currentTime - lastTime) / 1000f).coerceIn(0.005f, 0.05f)
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
 * Pasa el acelerometro a la gravedad del agua. Los ejes van sin negar: la
 * Glyph Matrix esta en la espalda del telefono y al inclinarlo a la
 * izquierda el agua debe caer hacia ese lado (verificado en un Phone 3 real).
 */
internal fun gravityListener(sim: FlipFluidSimulation) = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        sim.setGravity(event.values[0], event.values[1])
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
