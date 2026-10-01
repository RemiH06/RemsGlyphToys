package com.irofactory.rgt.audio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.R
import com.irofactory.rgt.ui.components.SherryButton
import com.irofactory.rgt.ui.theme.sherryColors

/**
 * PulseSettingsScreen
 * ───────────────────────────────────────────────────────────────────────────
 * Pantalla de pulse: vista previa en vivo, el reposo ([RestPose]) en una
 * tabla y la figura de cada familia mas el estilo del bombo ([PulseStyle]).
 * Cada eleccion se guarda al tocarla; el toy la toma al instante. Atras
 * regresa al inicio.
 */
@Composable
fun PulseSettingsScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sc = sherryColors
    var restPose by remember { mutableStateOf(RestPose.load(context)) }
    var style by remember { mutableStateOf(PulseStyle.load(context)) }

    BackHandler(onBack = onClose)

    fun update(new: PulseStyle) {
        style = new
        PulseStyle.save(context, new)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(text = "> pulse", style = MaterialTheme.typography.headlineMedium, color = sc.magenta)

        AudioSphereMatrixView(
            restPose = restPose,
            style = style,
            modifier = Modifier.fillMaxWidth()
        )

        SectionTitle(stringResource(R.string.pulse_rest))
        ChoiceTable(
            options = RestPose.entries,
            selected = restPose,
            label = { it.label },
            columns = 3
        ) {
            restPose = it
            RestPose.save(context, it)
        }

        SectionTitle(stringResource(R.string.pulse_shapes))
        Family(stringResource(R.string.family_high)) {
            ChoiceTable(HighShape.entries, style.high, { it.label }, columns = 2) { update(style.copy(high = it)) }
        }
        Family(stringResource(R.string.family_mid)) {
            ChoiceTable(MidShape.entries, style.mid, { it.label }, columns = 2) { update(style.copy(mid = it)) }
        }
        Family(stringResource(R.string.family_low)) {
            ChoiceTable(LowShape.entries, style.low, { it.label }, columns = 2) { update(style.copy(low = it)) }
        }
        Family(stringResource(R.string.family_kick)) {
            ChoiceTable(KickStyle.entries, style.kick, { it.label }, columns = 3) { update(style.copy(kick = it)) }
        }

        SherryButton(text = stringResource(R.string.settings_back), neon = sc.text2, onClick = onClose)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, color = sherryColors.text)
}

@Composable
private fun Family(name: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = name, style = MaterialTheme.typography.labelSmall, color = sherryColors.text3)
        options()
    }
}

/** Opciones en filas de [columns] celdas del mismo ancho; la elegida va en magenta y con ">". */
@Composable
private fun <T> ChoiceTable(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    columns: Int,
    onSelect: (T) -> Unit
) {
    val sc = sherryColors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in options.chunked(columns)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (option in row) {
                    val chosen = option == selected
                    val text = stringResource(label(option))
                    SherryButton(
                        text = if (chosen) "> $text" else text,
                        neon = if (chosen) sc.magenta else sc.text3,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Celdas vacias para que la ultima fila conserve el ancho de las demas
                repeat(columns - row.size) {
                    Text(text = "", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
