package com.irofactory.rgt.glyphs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.glyph.GlyphFrames
import com.irofactory.rgt.ui.components.GlyphMatrixCanvas
import com.irofactory.rgt.ui.components.SherryButton
import com.irofactory.rgt.ui.theme.sherryColors

/**
 * GlyphEditor
 * ───────────────────────────────────────────────────────────────────────────
 * Lienzo para "Mis glifos", como el de GlyphFactory: la matriz real y un
 * pincel de una celda con [MyGlyphs.LEVELS] intensidades mas borrador. Solo
 * se pinta dentro de los 489 LEDs.
 *
 * [glyphId] null dibuja uno nuevo. Guardar un glifo vacio no crea nada (y si
 * ya existia, lo elimina). Atras descarta los cambios.
 */
@Composable
fun GlyphEditor(glyphId: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors
    val mask = remember { GlyphFrames.circularMask() }

    val original = remember(glyphId) {
        glyphId?.let { id -> MyGlyphs.load(context).firstOrNull { it.id == id } }
    }
    val id = remember(glyphId) { original?.id ?: MyGlyphs.blank().id }
    var levels by remember(glyphId) {
        mutableStateOf(original?.levels?.copyOf() ?: ByteArray(GlyphFrames.SIZE * GlyphFrames.SIZE))
    }
    var brush by remember { mutableIntStateOf(MyGlyphs.LEVELS) }
    var last by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val grid = remember(levels) { MyGlyph(id, levels).toGrid() }

    BackHandler(onBack = onClose)

    fun paint(row: Int, col: Int, start: Boolean) {
        // Un toque nuevo empieza en su celda; un arrastre une con la anterior
        val from = if (start) row to col else last ?: (row to col)
        val next = levels.copyOf()
        for ((r, c) in cellLine(from.first, from.second, row, col)) {
            if (mask[r][c]) next[r * GlyphFrames.SIZE + c] = brush.toByte()
        }
        levels = next
        last = row to col
    }

    fun save() {
        val glyph = MyGlyph(id, levels)
        when {
            !glyph.isEmpty -> MyGlyphs.save(context, glyph)
            original != null -> MyGlyphs.delete(context, id)
        }
        onClose()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = if (original == null) "> nuevo glifo" else "> editar glifo",
            style = MaterialTheme.typography.headlineMedium,
            color = sc.lime,
            modifier = Modifier.fillMaxWidth()
        )

        GlyphMatrixCanvas(
            grid    = grid,
            neon    = sc.text,
            onPaint = ::paint
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrushSwatch(level = 0, selected = brush == 0, neon = sc.text) { brush = 0 }
            for (level in 1..MyGlyphs.LEVELS) {
                BrushSwatch(level = level, selected = brush == level, neon = sc.text) { brush = level }
            }
        }
        Text(
            text = if (brush == 0) "pincel · borrador" else "pincel · ${brush * 100 / MyGlyphs.LEVELS}%",
            style = MaterialTheme.typography.labelSmall,
            color = sc.text3
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SherryButton(text = "> guardar", neon = sc.lime, onClick = ::save)
            SherryButton(text = "> cancelar", neon = sc.text2, onClick = onClose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SherryButton(
                text = "> limpiar",
                neon = sc.text2,
                onClick = { levels = ByteArray(levels.size) }
            )
            if (original != null) {
                SherryButton(
                    text = if (confirmDelete) "> toca otra vez" else "> eliminar",
                    neon = sc.magenta,
                    onClick = {
                        if (confirmDelete) {
                            MyGlyphs.delete(context, id)
                            onClose()
                        } else confirmDelete = true
                    }
                )
            }
        }
    }
}

/** Muestra del pincel: el LED a esa intensidad; el borrador es un LED apagado con una diagonal. */
@Composable
private fun BrushSwatch(level: Int, selected: Boolean, neon: Color, onClick: () -> Unit) {
    val sc = sherryColors
    val shape = RoundedCornerShape(2.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(sc.bg2)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) sc.lime else sc.border2), shape)
            .clickable(onClick = onClick)
    ) {
        if (level == 0) {
            Text(text = "×", style = MaterialTheme.typography.labelLarge, color = sc.text3)
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(sc.border2)
                    .background(neon.copy(alpha = level / MyGlyphs.LEVELS.toFloat()))
            )
        }
    }
}
