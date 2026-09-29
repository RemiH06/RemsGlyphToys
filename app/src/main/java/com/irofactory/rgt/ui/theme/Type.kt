package com.irofactory.rgt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.irofactory.rgt.R

// sherry_theme: --mono JetBrains Mono para cuerpo, --display VT323 para titulos.
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold)
)

val Vt323 = FontFamily(Font(R.font.vt323_regular, FontWeight.Normal))

private val base = Typography()

val Typography = Typography(
    displayLarge   = base.displayLarge.copy(fontFamily = Vt323),
    displayMedium  = base.displayMedium.copy(fontFamily = Vt323),
    displaySmall   = base.displaySmall.copy(fontFamily = Vt323),
    headlineLarge  = base.headlineLarge.copy(fontFamily = Vt323, fontSize = 40.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Vt323, fontSize = 34.sp, letterSpacing = 1.sp),
    headlineSmall  = base.headlineSmall.copy(fontFamily = Vt323),
    titleLarge     = base.titleLarge.copy(fontFamily = Vt323),
    titleMedium    = base.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
    titleSmall     = base.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
    bodyLarge      = TextStyle(fontFamily = JetBrainsMono, fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium     = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 22.sp),
    bodySmall      = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 18.sp),
    labelLarge     = base.labelLarge.copy(fontFamily = JetBrainsMono),
    labelMedium    = base.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall     = base.labelSmall.copy(fontFamily = JetBrainsMono, letterSpacing = 1.sp)
)
