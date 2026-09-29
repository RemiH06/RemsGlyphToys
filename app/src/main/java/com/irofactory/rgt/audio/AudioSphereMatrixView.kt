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
 * AudioSphereMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa en pantalla de la esfera de audio. Aqui se pide RECORD_AUDIO:
 * el toy corre en segundo plano y no puede mostrar el dialogo de permisos.
 */
@Composable
fun AudioSphereMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors

    val sim = remember { AudioSphereSimulation(cols = 25, rows = 25) }
    val audioSource = remember { AudioLevelSource(context.applicationContext) }
    val mask = remember { sim.circularMask() }
    var grid by remember { mutableStateOf(Array(25) { FloatArray(25) }) }
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
            sim.step(dt, audioSource.currentLevel())
            grid = sim.rasterize()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        GlyphMatrixCanvas(
            grid    = grid,
            mask    = mask,
            neon    = sc.magenta,
            onClick = if (hasPermission) null else {
                { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            }
        )
        if (!hasPermission) {
            Text(
                text = "Toca para dar permiso de audio",
                style = MaterialTheme.typography.bodySmall,
                color = sc.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(48.dp)
            )
        }
    }
}
