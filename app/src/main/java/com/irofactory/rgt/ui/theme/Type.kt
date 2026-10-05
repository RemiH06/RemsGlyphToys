package com.irofactory.rgt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.irofactory.rgt.R

// sherry_theme: --mono JetBrains Mono para cuerpo, --display Doto para
// titulos (matriz de puntos, OFL; instancia fija en negrita, solo latin).
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold)
)

val Doto = FontFamily(Font(R.font.doto_bold, FontWeight.Bold))

// Doto es mas grande que VT323 al mismo tamaño (altura de mayuscula 0.69
// contra 0.56 del em, y mas ancha): los tamaños cuidan que "REM'S GLYPH TOYS"
// quepa junto al logo en un telefono.
private fun doto(size: Int) = TextStyle(
    fontFamily = Doto,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * 1.15f).sp,
    letterSpacing = 0.sp
)

private val base = Typography()

val Typography = Typography(
    displayLarge   = doto(44),
    displayMedium  = doto(36),
    displaySmall   = doto(29),
    headlineLarge  = doto(28),
    headlineMedium = doto(26),
    headlineSmall  = doto(20),
    titleLarge     = doto(18),
    titleMedium    = base.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
    titleSmall     = base.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
    bodyLarge      = TextStyle(fontFamily = JetBrainsMono, fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium     = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 22.sp),
    bodySmall      = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 18.sp),
    labelLarge     = base.labelLarge.copy(fontFamily = JetBrainsMono),
    labelMedium    = base.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall     = base.labelSmall.copy(fontFamily = JetBrainsMono, letterSpacing = 1.sp)
)
