package com.irofactory.rgt.audio

import android.Manifest
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.irofactory.rgt.fluid.GravitySampler
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.components.SherryButton
import com.irofactory.rgt.ui.theme.sherryColors
import kotlinx.coroutines.isActive
import androidx.compose.ui.res.stringResource
import com.irofactory.rgt.R

/**
 * Vista previa en pantalla del toy pulse. Aqui se pide RECORD_AUDIO: el toy
 * corre en segundo plano y no puede mostrar el dialogo de permisos. Doble
 * toque la reinicia: figuras desde cero y el audio vuelto a abrir. Muestra
 * el [restPose] y el [style] que se le pasen (se eligen en
 * [PulseSettingsScreen]); con [onCustomize] agrega el boton para ir ahi.
 *
 * El audio solo se lee con la app al frente: el Visualizer de la salida se
 * comparte dentro del proceso, y si la vista previa lo dejara encendido en
 * segundo plano, el toy no podria abrir el suyo.
 */
@Composable
fun AudioSphereMatrixView(
    restPose: RestPose,
    style: PulseStyle,
    modifier: Modifier = Modifier,
    onCustomize: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sc = sherryColors

    var sim by remember { mutableStateOf(AudioBlobSimulation()) }
    // El ciclo de cuadros arranca una vez: lee siempre lo ultimo que se eligio
    val currentPose by rememberUpdatedState(restPose)
    val currentStyle by rememberUpdatedState(style)
    val audioSource = remember { AudioSpectrumSource(context.applicationContext) }
    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var hasPermission by remember { mutableStateOf(audioSource.hasPermission()) }
    // Gravedad aparte de la simulacion: el doble toque la reemplaza
    val gravity = remember { GravitySampler(viewedFromBack = false) }

    DisposableEffect(restPose.usesGravity) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        if (restPose.usesGravity) gravity.register(sensorManager)
        onDispose { sensorManager.unregisterListener(gravity) }
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
            sim.restPose = currentPose
            sim.style = currentStyle
            gravity.drain(sim::setGravity)
            val now = java.time.LocalTime.now()
            sim.step(dt, audioSource.bands, audioSource.loudness, audioSource.harshness,
                audioSource.kick, audioSource.kickPresence, audioSource.melody,
                now.hour, now.minute)
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
                    text = stringResource(R.string.pulse_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(56.dp)
                )
            }
        }
        if (onCustomize != null) {
            SherryButton(text = stringResource(R.string.pulse_customize), neon = sc.magenta, onClick = onCustomize)
        }
    }
}
