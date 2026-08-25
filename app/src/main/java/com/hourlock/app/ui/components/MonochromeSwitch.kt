package com.hourlock.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.hourlock.app.ui.theme.DesignTokens

/**
 * MonochromeSwitch
 * ────────────────
 * Custom-drawn pill toggle switch with smooth thumb animation,
 * inverted black/white contrast when active, and built-in haptic feedback.
 */
@Composable
fun MonochromeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    val trackWidth = 48.dp
    val trackHeight = 28.dp
    val thumbSize = 20.dp
    val thumbPadding = 4.dp

    val trackColor by animateColorAsState(
        targetValue = if (checked) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkElevated,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "switchTrackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) DesignTokens.Palette.PureBlack else DesignTokens.Palette.GrayMuted,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "switchThumbColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkBorder,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "switchBorderColor"
    )

    val maxOffset = trackWidth - thumbSize - (thumbPadding * 2)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "switchThumbOffset"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                } catch (_: Exception) {}
                onCheckedChange(!checked)
            }
            .padding(thumbPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
