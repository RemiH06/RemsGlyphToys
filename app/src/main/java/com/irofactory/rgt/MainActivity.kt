package com.irofactory.rgt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.audio.AudioSphereMatrixView
import com.irofactory.rgt.fluid.FluidMatrixView
import com.irofactory.rgt.gallery.GalleryMatrixView
import com.irofactory.rgt.ui.theme.RemsGlyphToysTheme
import com.irofactory.rgt.ui.theme.metroColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemsGlyphToysTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val mc = metroColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "Fluid",
            style = MaterialTheme.typography.headlineMedium,
            color = mc.textPrimary
        )
        FluidMatrixView()
        Text(
            text = "Vista previa. Manten presionado el boton Glyph y elige \"Fluid\" en el carrusel para verlo en la matriz fisica.",
            style = MaterialTheme.typography.bodyMedium,
            color = mc.textSecondary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Gallery",
            style = MaterialTheme.typography.headlineMedium,
            color = mc.textPrimary
        )
        GalleryMatrixView()
        Text(
            text = "Toca para pedir permiso o cambiar de foto. En la matriz fisica, elige \"Gallery\" en el carrusel y manten presionado el boton Glyph para cambiarla.",
            style = MaterialTheme.typography.bodyMedium,
            color = mc.textSecondary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Pulse",
            style = MaterialTheme.typography.headlineMedium,
            color = mc.textPrimary
        )
        AudioSphereMatrixView()
        Text(
            text = "Reacciona a lo que se este reproduciendo en el telefono, sea por bocina o audifonos. Elige \"Pulse\" en el carrusel del boton Glyph para verlo en la matriz fisica.",
            style = MaterialTheme.typography.bodyMedium,
            color = mc.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    RemsGlyphToysTheme {
        HomeScreen()
    }
}
