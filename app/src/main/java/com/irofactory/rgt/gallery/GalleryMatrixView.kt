package com.irofactory.rgt.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.components.SherryButton
import com.irofactory.rgt.ui.theme.sherryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GalleryMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa del toy de galeria y lugar donde se eligen sus fotos con el
 * Photo Picker del sistema (sin permisos de almacenamiento). Tocar la
 * matriz muestra otra foto de la seleccion.
 */
@Composable
fun GalleryMatrixView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors
    val scope = rememberCoroutineScope()

    var count by remember { mutableIntStateOf(GallerySelection.load(context).size) }
    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var current by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun showNext() {
        if (loading) return
        loading = true
        scope.launch {
            val pick = withContext(Dispatchers.IO) {
                runCatching { GalleryImageProvider.randomPick(context, avoid = current) }.getOrNull()
            }
            grid = pick?.grid
            current = pick?.uri
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { GallerySelection.replace(context, uris) }
            count = GallerySelection.load(context).size
            current = null
            showNext()
        }
    }
    val openPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(Unit) { if (count > 0) showNext() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlyphMatrixCanvas(
                grid    = grid,
                neon    = sc.text,
                onClick = { if (count > 0) showNext() else openPicker() }
            )
            if (count == 0) {
                Text(
                    text = "Toca para elegir fotos",
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(56.dp)
                )
            }
        }
        SherryButton(
            text = if (count > 0) "> elegir fotos · $count" else "> elegir fotos",
            neon = sc.lime,
            onClick = openPicker
        )
    }
}
