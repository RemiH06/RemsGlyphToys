package com.irofactory.rgt.audio

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.fluid.gravityListener
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.components.SherryButton
import com.irofactory.rgt.ui.theme.sherryColors
import kotlinx.coroutines.isActive

/**
 * Vista previa en pantalla del toy pulse. Aqui se pide RECORD_AUDIO: el toy
 * corre en segundo plano y no puede mostrar el dialogo de permisos. Doble
 * toque la reinicia: figuras desde cero y el audio vuelto a abrir. Abajo
 * se elige el reposo ([RestPose]); el toy toma la misma eleccion.
 *
 * El audio solo se lee con la app al frente: el Visualizer de la salida se
 * comparte dentro del proceso, y si la vista previa lo dejara encendido en
 * segundo plano, el toy no podria abrir el suyo.
 */
@Composable
fun AudioSphereMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors

    var sim by remember { mutableStateOf(AudioBlobSimulation()) }
    var restPose by remember { mutableStateOf(RestPose.load(context)) }
    val audioSource = remember { AudioSpectrumSource(context.applicationContext) }
    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var hasPermission by remember { mutableStateOf(audioSource.hasPermission()) }
    // Gravedad aparte de la simulacion: el doble toque la reemplaza
    val gravity = remember { floatArrayOf(0f, 9.81f) }

    DisposableEffect(restPose.usesGravity) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val listener = gravityListener(viewedFromBack = false) { x, y -> gravity[0] = x; gravity[1] = y }
        if (restPose.usesGravity) {
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(hasPermission, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (hasPermission) audioSource.start()
                Lifecycle.Event.ON_PAUSE -> audioSource.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioSource.stop()
        }
    }

    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val dt = ((currentTime - lastTime) / 1000f).coerceIn(0.005f, 0.08f)
            lastTime = currentTime
            audioSource.update(dt)
            sim.restPose = restPose
            sim.setGravity(gravity[0], gravity[1])
            sim.step(dt, audioSource.bands, audioSource.loudness, audioSource.harshness,
                audioSource.kick, audioSource.kickPresence)
            grid = sim.rasterize()
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlyphMatrixCanvas(
                grid    = grid,
                neon    = sc.magenta,
                onClick = if (hasPermission) null else {
                    { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                },
                onDoubleClick = if (!hasPermission) null else {
                    {
                        sim = AudioBlobSimulation()
                        audioSource.restart()
                    }
                }
            )
            if (!hasPermission) {
                Text(
                    text = "Toca para dar permiso de audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(56.dp)
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "reposo", style = MaterialTheme.typography.labelSmall, color = sc.text3)
            for (pose in RestPose.entries) {
                SherryButton(
                    text = if (pose == restPose) "> ${pose.label}" else pose.label,
                    neon = if (pose == restPose) sc.magenta else sc.text3,
                    onClick = {
                        restPose = pose
                        RestPose.save(context, pose)
                    }
                )
            }
        }
    }
}
