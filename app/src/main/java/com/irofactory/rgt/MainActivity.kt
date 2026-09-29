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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.audio.AudioSphereMatrixView
import com.irofactory.rgt.fluid.FluidMatrixView
import com.irofactory.rgt.gallery.GalleryMatrixView
import com.irofactory.rgt.ui.theme.RemsGlyphToysTheme
import com.irofactory.rgt.ui.theme.crtScanlines
import com.irofactory.rgt.ui.theme.sherryColors

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
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val sc = sherryColors

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
            description = "Agua simulada con FLIP, como la fluid pendant de mitxela: cae hacia donde inclines el telefono. Toca para agitarla; en la matriz fisica manten presionado el boton Glyph. Pulsacion larga reinicia."
        ) { FluidMatrixView() }

        ToySection(
            index = "02",
            name = "gallery",
            neon = sc.lime,
            description = "Elige las fotos que quieres ver; se promedian a 25x25. Toca la matriz para cambiar de foto; en la matriz fisica, pulsacion larga del boton Glyph."
        ) { GalleryMatrixView() }

        ToySection(
            index = "03",
            name = "pulse",
            neon = sc.magenta,
            description = "Tres figuras anidadas que respiran con lo que se este reproduciendo, por bocina o audifonos: un hexagono para los graves, un diamante para las voces y un triangulo para los agudos. La que mas suena queda afuera, las calladas se quedan quietas en el centro, y solo se cruzan cuando dos familias suenan igual de fuerte."
        ) { AudioSphereMatrixView() }

        Text(
            text = "elige cada toy en el carrusel del boton Glyph",
            style = MaterialTheme.typography.labelSmall,
            color = sc.text3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Header() {
    val sc = sherryColors
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
