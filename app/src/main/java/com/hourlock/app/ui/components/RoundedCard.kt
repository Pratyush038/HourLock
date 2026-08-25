package com.hourlock.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hourlock.app.ui.theme.DesignTokens

/**
 * RoundedCard
 * ───────────
 * Sleek, flat, border-based card container matching the monochrome design system.
 * Zero drop shadows — uses subtle 1dp hairline/subtle borders and grayscale fills.
 */
@Composable
fun RoundedCard(
    modifier: Modifier = Modifier,
    shape: Shape = DesignTokens.Shapes.Card,
    containerColor: Color = DesignTokens.Palette.DarkCard,
    borderColor: Color = DesignTokens.Palette.DarkBorder,
    borderWidth: Dp = DesignTokens.Elevation.borderWidth,
    contentPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}
