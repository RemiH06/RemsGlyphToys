package com.irofactory.rgt.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.ui.theme.metroColors
import kotlinx.coroutines.isActive

/**
 * AudioSphereMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa en pantalla de la esfera de audio, mismo lenguaje visual de
 * puntos que [com.irofactory.rgt.fluid.FluidMatrixView]. Usa su propia
 * instancia de [AudioLevelSource]; correr esta junto con el toy fisico al
 * mismo tiempo no choca, cada Visualizer lee la mezcla de forma independiente.
 */
@Composable
fun AudioSphereMatrixView(modifier: Modifier = Modifier) {
    val mc = metroColors

    val sim = remember { AudioSphereSimulation(cols = 25, rows = 25) }
    val audioSource = remember { AudioLevelSource() }
    val mask = remember { sim.circularMask() }
    var grid by remember { mutableStateOf(Array(25) { FloatArray(25) }) }

    DisposableEffect(Unit) {
        audioSource.start()
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

    val activeColor = mc.accent
    val emptyColor  = mc.surface2

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
                .border(0.5.dp, mc.border, CircleShape)
                .padding(4.dp)
        ) {
            val cols = 25
            val rows = 25
            val cellW = size.width / cols
            val cellH = size.height / rows
            val dotR = cellW * 0.38f

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!mask[r][c]) continue

                    val brightness = grid[r][c]
                    val cx = c * cellW + cellW / 2f
                    val cy = r * cellH + cellH / 2f

                    val dotColor = if (brightness > 0.01f) {
                        activeColor.copy(alpha = brightness.coerceIn(0.05f, 1f))
                    } else {
                        emptyColor.copy(alpha = 0.4f)
                    }

                    drawCircle(color = dotColor, radius = dotR, center = Offset(cx, cy))
                }
            }
        }
    }
}
