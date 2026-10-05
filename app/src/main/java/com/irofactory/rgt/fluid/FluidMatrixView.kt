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
import kotlin.math.hypot

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
    val gravity = remember { GravitySampler(viewedFromBack = false) }

    DisposableEffect(Unit) {
        gravity.register(sensorManager)
        onDispose { sensorManager.unregisterListener(gravity) }
    }

    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }

    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val dt = ((currentTime - lastTime) / 1000f).coerceIn(0.005f, 0.05f)
            lastTime = currentTime
            gravity.drain(sim::setGravity)
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
 * Pasa el acelerometro a una gravedad (la del agua, o la de los reposos de
 * pulse que caen), en m/s² con la y hacia abajo. La pantalla se ve de frente y
 * la Glyph Matrix por detras, asi que su izquierda y derecha estan
 * invertidas entre si: la vista previa niega el eje X y la matriz no
 * (ambos verificados en un Phone 3 real). El eje Y va igual en los dos.
 *
 * Lee el sensor a 200 Hz y, en cada cuadro, [drain] entrega el
 * promedio de todo lo que llego desde el anterior: asi una sacudida conserva
 * su impulso completo. Con solo la ultima lectura (cientos por segundo contra
 * ~30 cuadros) casi siempre caia en un punto cualquiera de la sacudida y el
 * agua temblaba en vez de chapotear. Tope de 3 g para que un golpe no la
 * vuelva loca.
 */
internal class GravitySampler(private val viewedFromBack: Boolean) : SensorEventListener {

    private companion object {
        const val MAX_ACCEL = 3f * 9.81f
        // 200 Hz: el maximo sin el permiso HIGH_SAMPLING_RATE_SENSORS. Con
        // SENSOR_DELAY_FASTEST (mas rapido) Android 12+ lanza SecurityException
        const val SAMPLING_PERIOD_US = 5_000
    }

    private var sumX = 0f
    private var sumY = 0f
    private var count = 0
    private var lastX = 0f
    private var lastY = 9.81f

    fun register(sensorManager: SensorManager) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SAMPLING_PERIOD_US)
    }

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        var x = if (viewedFromBack) event.values[0] else -event.values[0]
        var y = event.values[1]
        val g = hypot(x, y)
        if (g > MAX_ACCEL) { x *= MAX_ACCEL / g; y *= MAX_ACCEL / g }
        sumX += x; sumY += y; count++
        lastX = x; lastY = y
    }

    /** Entrega el promedio desde la llamada anterior (o la ultima lectura si no llego nada). */
    @Synchronized
    fun drain(onGravity: (x: Float, y: Float) -> Unit) {
        if (count == 0) {
            onGravity(lastX, lastY)
            return
        }
        onGravity(sumX / count, sumY / count)
        sumX = 0f; sumY = 0f; count = 0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
