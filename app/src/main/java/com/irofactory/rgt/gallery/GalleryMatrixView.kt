package com.irofactory.rgt.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.glyph.GlyphFrames
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.theme.sherryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GalleryMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa en pantalla del toy de galeria. Aqui se piden los permisos
 * de fotos (el toy corre en segundo plano y no puede mostrar el dialogo).
 * Toca para pedir permiso o cambiar de foto.
 */
@Composable
fun GalleryMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors
    val scope = rememberCoroutineScope()
    val mask = remember { GlyphFrames.circularMask() }

    var hasPermission by remember { mutableStateOf(GalleryImageProvider.hasPermission(context)) }
    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPermission = GalleryImageProvider.hasPermission(context) }

    fun loadRandom() {
        if (loading) return
        loading = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { GalleryImageProvider.randomGrid(context) }.getOrNull()
            }
            empty = next == null
            if (next != null) grid = next
            loading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && grid == null) loadRandom()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        GlyphMatrixCanvas(
            grid    = grid,
            mask    = mask,
            neon    = sc.text,
            onClick = {
                if (hasPermission) loadRandom()
                else permissionLauncher.launch(GalleryImageProvider.PERMISSIONS)
            }
        )

        val message = when {
            !hasPermission -> "Toca para dar permiso de fotos"
            empty && grid == null -> "No hay fotos accesibles"
            else -> null
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = sc.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(48.dp)
            )
        }
    }
}
