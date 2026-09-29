package com.irofactory.rgt.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irofactory.rgt.ui.theme.sherryColors

/** El `.btn` del sherry_theme: borde neon de 1dp, texto mono en negritas, radio 2dp. */
@Composable
fun SherryButton(text: String, neon: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sc = sherryColors
    val shape = RoundedCornerShape(2.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            shadow = if (sc.glow) Shadow(color = neon.copy(alpha = 0.5f), blurRadius = 16f) else null
        ),
        color = neon,
        modifier = modifier
            .clip(shape)
            .background(sc.bg2)
            .border(BorderStroke(1.dp, neon), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}
