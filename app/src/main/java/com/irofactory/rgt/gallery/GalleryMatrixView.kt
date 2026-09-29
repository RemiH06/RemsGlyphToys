package com.irofactory.rgt.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.irofactory.rgt.glyphs.MyGlyphs
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.components.GlyphThumbnail
import com.irofactory.rgt.ui.components.SherryButton
import com.irofactory.rgt.ui.theme.sherryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GalleryMatrixView
 * ───────────────────────────────────────────────────────────────────────────
 * Vista previa del toy de galeria y lugar donde se eligen sus fotos con el
 * Photo Picker del sistema (sin permisos de almacenamiento) y se dibujan
 * "Mis glifos". Tocar la matriz muestra otra foto o glifo.
 *
 * [onEditGlyph] abre el editor: null para uno nuevo, o el id de uno guardado.
 */
@Composable
fun GalleryMatrixView(onEditGlyph: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors
    val scope = rememberCoroutineScope()

    val glyphs by MyGlyphs.observe(context).collectAsState()
    var photoCount by remember { mutableIntStateOf(GallerySelection.load(context).size) }
    val count = photoCount + glyphs.size
    var grid by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var current by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun showNext() {
        if (loading) return
        loading = true
        scope.launch {
            val pick = withContext(Dispatchers.IO) {
                runCatching { GalleryImageProvider.randomPick(context, avoid = current) }.getOrNull()
            }
            grid = pick?.grid
            current = pick?.key
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { GallerySelection.replace(context, uris) }
            photoCount = GallerySelection.load(context).size
            current = null
            showNext()
        }
    }
    val openPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Al abrir y cada vez que cambian los glifos (volviendo del editor) se muestra otro
    LaunchedEffect(glyphs) { if (count > 0) showNext() else grid = null }

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
                    text = "Elige fotos o dibuja un glifo",
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(56.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SherryButton(
                text = if (photoCount > 0) "> elegir fotos · $photoCount" else "> elegir fotos",
                neon = sc.lime,
                onClick = openPicker
            )
            SherryButton(text = "> dibujar", neon = sc.lime, onClick = { onEditGlyph(null) })
        }
        if (glyphs.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "mis glifos · ${glyphs.size} · toca uno para editarlo",
                    style = MaterialTheme.typography.labelSmall,
                    color = sc.text3
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (glyph in glyphs) {
                        GlyphThumbnail(
                            grid = remember(glyph) { glyph.toGrid() },
                            neon = sc.text,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onEditGlyph(glyph.id) }
                        )
                    }
                }
            }
        }
    }
}
