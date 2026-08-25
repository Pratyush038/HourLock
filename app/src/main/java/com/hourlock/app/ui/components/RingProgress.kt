package com.hourlock.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hourlock.app.ui.theme.DesignTokens

/**
 * RingProgress
 * ────────────
 * Custom Canvas-drawn circular progress ring with smooth animation,
 * rounded caps, and slot for center content (e.g. big Display-type number).
 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = DesignTokens.Palette.DarkElevated,
    progressColor: Color = DesignTokens.Palette.PureWhite,
    warningColor: Color = DesignTokens.Palette.WarningAccent,
    isWarningOrBlocked: Boolean = false,
    content: @Composable (() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 650,
            easing = FastOutSlowInEasing
        ),
        label = "ringProgressAnim"
    )

    val activeColor = if (isWarningOrBlocked) warningColor else progressColor

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth / 2)
        ) {
            val canvasSize = this.size.minDimension
            val strokePx = strokeWidth.toPx()
            val radius = (canvasSize - strokePx) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Background Track Arc (360 degrees)
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )

            // Progress Arc (Starting from top: -90 degrees)
            if (animatedProgress > 0.001f) {
                val sweepAngle = animatedProgress * 360f
                drawArc(
                    color = activeColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Center Composable Slot (e.g. remaining minutes + label)
        if (content != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(strokeWidth + 8.dp)
            ) {
                content()
            }
        }
    }
}
