package com.irofactory.rgt.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.irofactory.rgt.ui.theme.metroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GalleryMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa en pantalla del toy de galeria: pide el permiso de fotos si
 * falta y muestra, con el mismo estilo de puntos que [FluidMatrixView], la
 * ultima foto promediada a 25x25. Toca para pedir permiso o cambiar de foto.
 */
@Composable
fun GalleryMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mc = metroColors
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var matrixBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    fun loadRandom() {
        if (loading) return
        loading = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val uri = GalleryImageProvider.pickRandomUri(context) ?: return@withContext null
                val source = GalleryImageProvider.loadDownsampled(context, uri) ?: return@withContext null
                GalleryBitmapRenderer.toMatrix(source).also { source.recycle() }
            }
            empty = bitmap == null
            if (bitmap != null) matrixBitmap = bitmap
            loading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && matrixBitmap == null) loadRandom()
    }

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
                .clickable {
                    if (hasPermission) loadRandom() else permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
                .padding(4.dp)
        ) {
            val bitmap = matrixBitmap ?: return@Canvas
            val cols = bitmap.width
            val rows = bitmap.height
            val cellW = size.width / cols
            val cellH = size.height / rows
            val dotR = cellW * 0.38f

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val argb = bitmap.getPixel(c, r)
                    val alpha = (argb ushr 24) and 0xFF
                    if (alpha < 8) continue
                    val brightness = ((argb ushr 16) and 0xFF) / 255f

                    val cx = c * cellW + cellW / 2f
                    val cy = r * cellH + cellH / 2f
                    val dotColor = mc.accent.copy(alpha = brightness.coerceIn(0.06f, 1f))
                    drawCircle(color = dotColor, radius = dotR, center = Offset(cx, cy))
                }
            }
        }

        if (!hasPermission) {
            Text(
                text = "Toca para dar permiso de galeria",
                style = MaterialTheme.typography.bodySmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        } else if (empty && matrixBitmap == null) {
            Text(
                text = "No hay fotos en la galeria",
                style = MaterialTheme.typography.bodySmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
