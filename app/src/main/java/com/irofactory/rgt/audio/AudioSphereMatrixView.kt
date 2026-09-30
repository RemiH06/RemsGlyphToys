package com.irofactory.rgt.audio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.theme.sherryColors
import kotlinx.coroutines.isActive

/**
 * Vista previa en pantalla del toy pulse. Aqui se pide RECORD_AUDIO: el toy
 * corre en segundo plano y no puede mostrar el dialogo de permisos. Doble
 * toque la reinicia: figuras desde cero y el audio vuelto a abrir.
 */
@Composable
fun AudioSphereMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors

    var sim by remember { mutableStateOf(AudioBlobSimulation()) }
    val audioSource = remember { AudioSpectrumSource(context.applicationContext) }
    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var hasPermission by remember { mutableStateOf(audioSource.hasPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    DisposableEffect(hasPermission) {
        if (hasPermission) audioSource.start()
        onDispose { audioSource.stop() }
    }

    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val dt = ((currentTime - lastTime) / 1000f).coerceIn(0.005f, 0.08f)
            lastTime = currentTime
            audioSource.update(dt)
            sim.step(dt, audioSource.bands, audioSource.loudness, audioSource.harshness,
                audioSource.kick, audioSource.kickPresence)
            grid = sim.rasterize()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
}
