package com.hourlock.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.ui.theme.DesignTokens

/**
 * StatChip
 * ────────
 * Compact, rounded metric container for displaying key insights
 * such as Today's Total, Streak count, and Week-over-Week delta.
 */
@Composable
fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlightValue: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .clip(DesignTokens.Shapes.Chip)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = DesignTokens.Shapes.Chip,
        color = DesignTokens.Palette.DarkCard,
        border = BorderStroke(DesignTokens.Elevation.borderWidth, DesignTokens.Palette.DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = DesignTokens.Palette.GrayMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label.uppercase(),
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = value,
                style = DesignTokens.Typography.subtitle().copy(
                    fontWeight = FontWeight.Bold,
                    color = if (highlightValue) DesignTokens.Palette.PureWhite else DesignTokens.Palette.PureWhite,
                    fontSize = 16.sp
                )
            )
        }
    }
}
