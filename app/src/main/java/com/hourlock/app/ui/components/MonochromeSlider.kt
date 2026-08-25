package com.hourlock.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hourlock.app.ui.theme.DesignTokens

/**
 * MonochromeSlider
 * ────────────────
 * Minimalist slider component restyled to match the strict monochrome palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonochromeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 1f..60f,
    steps: Int = 58,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = DesignTokens.Palette.PureWhite,
    inactiveColor: Color = DesignTokens.Palette.DarkElevated
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}
