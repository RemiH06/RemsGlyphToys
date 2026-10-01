package com.irofactory.rgt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.audio.AudioSphereMatrixView
import com.irofactory.rgt.audio.PulseSettingsScreen
import com.irofactory.rgt.audio.PulseStyle
import com.irofactory.rgt.audio.RestPose
import com.irofactory.rgt.fluid.FluidMatrixView
import com.irofactory.rgt.gallery.GalleryMatrixView
import com.irofactory.rgt.glyphs.GlyphEditor
import com.irofactory.rgt.ui.theme.RemsGlyphToysTheme
import com.irofactory.rgt.ui.theme.crtScanlines
import com.irofactory.rgt.ui.theme.sherryColors
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemsGlyphToysTheme {
                val sc = sherryColors
                // CRT del sherry_theme sobre toda la app: blancas al 3% en oscuro, oscuras al 1.8% en claro
                val scanline = if (sc.isDark) sc.text.copy(alpha = 0.03f) else sc.text.copy(alpha = 0.018f)
                Scaffold(
                    modifier = Modifier.fillMaxSize().crtScanlines(scanline),
                    containerColor = sc.bg
                ) { innerPadding ->
                    AppScreens(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Inicio, editor de glifos o pantalla de pulse. [NEW_GLYPH] abre un glifo
 * nuevo; cualquier otro valor es el id del glifo a editar. El inicio conserva
 * su scroll mientras las otras estan abiertas.
 */
@Composable
private fun AppScreens(modifier: Modifier = Modifier) {
    val holder = rememberSaveableStateHolder()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var customizingPulse by rememberSaveable { mutableStateOf(false) }
    val target = editing
    when {
        target != null -> GlyphEditor(
            glyphId = target.takeIf { it != NEW_GLYPH },
            onClose = { editing = null },
            modifier = modifier
        )
        customizingPulse -> PulseSettingsScreen(onClose = { customizingPulse = false }, modifier = modifier)
        else -> holder.SaveableStateProvider("home") {
            HomeScreen(
                onEditGlyph = { editing = it ?: NEW_GLYPH },
                onCustomizePulse = { customizingPulse = true },
                modifier = modifier
            )
        }
    }
}

private const val NEW_GLYPH = ""

@Composable
fun HomeScreen(
    onEditGlyph: (String?) -> Unit = {},
    onCustomizePulse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sc = sherryColors
    val context = LocalContext.current
    // Se leen cada vez que se vuelve al inicio, por si cambiaron en la pantalla de pulse
    val restPose = remember { RestPose.load(context) }
    val pulseStyle = remember { PulseStyle.load(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Header()

        ToySection(
            index = "01",
            name = "fluid",
            neon = sc.cyan,
            description = stringResource(R.string.home_fluid_description)
        ) { FluidMatrixView() }

        ToySection(
            index = "02",
            name = "gallery",
            neon = sc.lime,
            description = stringResource(R.string.home_gallery_description)
        ) { GalleryMatrixView(onEditGlyph = onEditGlyph) }

        ToySection(
            index = "03",
            name = "pulse",
            neon = sc.magenta,
            description = stringResource(R.string.home_pulse_description)
        ) { AudioSphereMatrixView(restPose = restPose, style = pulseStyle, onCustomize = onCustomizePulse) }

        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.labelSmall,
            color = sc.text3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Header() {
    val sc = sherryColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "REM'S GLYPH TOYS",
                style = MaterialTheme.typography.headlineLarge,
                color = sc.text
            )
            Text(
                text = "glyph matrix 25x25 · nothing phone (3)",
                style = MaterialTheme.typography.bodySmall,
                color = sc.text2
            )
        }
    }
}

@Composable
private fun ToySection(
    index: String,
    name: String,
    neon: Color,
    description: String,
    preview: @Composable () -> Unit
) {
    val sc = sherryColors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(sc.border2)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$index ",
                style = MaterialTheme.typography.labelSmall,
                color = sc.text3,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "> $name",
                style = MaterialTheme.typography.headlineMedium,
                color = neon
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { preview() }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = sc.text2
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080808)
@Composable
fun HomeScreenPreview() {
    RemsGlyphToysTheme(darkTheme = true) {
        HomeScreen()
    }
}
