package com.hourlock.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.formatMinuteOfDay
import com.hourlock.app.ui.theme.DesignTokens
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Generic vertical scrolling wheel drum picker with snapping and haptic ticks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 3,
    itemHeight: Dp = 38.dp,
    formatItem: (T) -> String = { it.toString() }
) {
    val view = LocalView.current
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    )
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Sync external index changes
    LaunchedEffect(currentSelectedIndex) {
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != currentSelectedIndex) {
            listState.scrollToItem(currentSelectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        }
    }

    // Report center index on scroll end or item settle
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices && index != currentSelectedIndex) {
                    try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                    onSelectedIndexChanged(index)
                }
            }
    }

    val totalHeight = itemHeight * visibleItemCount

    Box(
        modifier = modifier
            .height(totalHeight)
            .clip(DesignTokens.Shapes.Button),
        contentAlignment = Alignment.Center
    ) {
        // Selection highlight bar in middle
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
            shape = DesignTokens.Shapes.Badge,
            color = DesignTokens.Palette.DarkElevated,
            border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorder)
        ) {}

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top spacer so first item can sit in center
            item { Spacer(Modifier.height(itemHeight * (visibleItemCount / 2))) }

            items(items.size) { index ->
                val isSelected by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex == index
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            onSelectedIndexChanged(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatItem(items[index]),
                        style = DesignTokens.Typography.bodyMedium().copy(
                            fontSize = if (isSelected) 16.sp else 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.GrayMuted,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            // Bottom spacer so last item can sit in center
            item { Spacer(Modifier.height(itemHeight * (visibleItemCount / 2))) }
        }

        // Top and bottom subtle gradient masks
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * (visibleItemCount / 2))
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DesignTokens.Palette.DarkCard,
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * (visibleItemCount / 2))
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DesignTokens.Palette.DarkCard
                        )
                    )
                )
        )
    }
}

/**
 * Interactive Time Wheel Picker with Hours, Minutes (5m increments), and AM/PM.
 */
@Composable
fun TimeWheelPicker(
    minuteOfDay: Int,
    onMinuteOfDayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    allow24HourEnd: Boolean = true,
    minMinute: Int = 0,
    maxMinute: Int = 24 * 60
) {
    val clamped = minuteOfDay.coerceIn(minMinute, maxMinute)
    val is24Hundred = allow24HourEnd && clamped == 24 * 60

    val rawHour = if (is24Hundred) 24 else (clamped / 60)
    val rawMinute = if (is24Hundred) 0 else (clamped % 60)

    val hour12 = when {
        is24Hundred -> 12
        rawHour == 0 -> 12
        rawHour > 12 -> rawHour - 12
        else -> rawHour
    }
    val isPm = rawHour >= 12 && !is24Hundred

    val hoursList = remember { (1..12).toList() }
    val minutesList = remember { (0..55 step 5).toList() }
    val amPmList = remember { listOf("AM", "PM") }

    val currentHourIndex = hoursList.indexOf(hour12).coerceAtLeast(0)
    val currentMinuteIndex = (minutesList.indexOfFirst { it >= rawMinute }.let {
        if (it == -1) minutesList.lastIndex else it
    }).coerceAtLeast(0)
    val currentAmPmIndex = if (isPm) 1 else 0

    fun updateTime(h12: Int, m: Int, pm: Boolean) {
        val calculatedHour = when {
            h12 == 12 && !pm -> 0
            h12 == 12 && pm -> 12
            pm -> h12 + 12
            else -> h12
        }
        val total = (calculatedHour * 60 + m).coerceIn(minMinute, maxMinute)
        onMinuteOfDayChanged(total)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Formatted Time Header Display
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = DesignTokens.Shapes.Badge,
            color = DesignTokens.Palette.DarkElevated,
            border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Time",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = if (is24Hundred) "12:00 AM (Next Day / 24:00)" else formatMinuteOfDay(clamped),
                    style = DesignTokens.Typography.bodyMedium().copy(
                        color = DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
        }

        // 3-Column Wheel (Hour, Minute, AM/PM)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hour Column
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "HOUR",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                WheelPicker(
                    items = hoursList,
                    selectedIndex = currentHourIndex,
                    onSelectedIndexChanged = { idx ->
                        val selectedH = hoursList[idx]
                        updateTime(selectedH, minutesList[currentMinuteIndex], currentAmPmIndex == 1)
                    },
                    formatItem = { String.format("%d", it) }
                )
            }

            Text(
                ":",
                style = DesignTokens.Typography.title().copy(
                    color = DesignTokens.Palette.GrayMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                modifier = Modifier.padding(top = 16.dp)
            )

            // Minute Column
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "MIN",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                WheelPicker(
                    items = minutesList,
                    selectedIndex = currentMinuteIndex,
                    onSelectedIndexChanged = { idx ->
                        val selectedM = minutesList[idx]
                        updateTime(hoursList[currentHourIndex], selectedM, currentAmPmIndex == 1)
                    },
                    formatItem = { String.format("%02d", it) }
                )
            }

            // AM / PM Column
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "PERIOD",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                WheelPicker(
                    items = amPmList,
                    selectedIndex = currentAmPmIndex,
                    onSelectedIndexChanged = { idx ->
                        val isSelectedPm = idx == 1
                        updateTime(hoursList[currentHourIndex], minutesList[currentMinuteIndex], isSelectedPm)
                    }
                )
            }
        }

        // Quick Preset Chips for Easy Jumping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "9 AM" to 9 * 60,
                "12 PM" to 12 * 60,
                "5 PM" to 17 * 60,
                "10 PM" to 22 * 60
            ) + if (allow24HourEnd) listOf("End Day" to 24 * 60) else listOf("12 AM" to 0)

            presets.forEach { (label, minVal) ->
                val isSelected = clamped == minVal
                Surface(
                    shape = DesignTokens.Shapes.Chip,
                    color = if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkBorderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(DesignTokens.Shapes.Chip)
                        .clickable {
                            onMinuteOfDayChanged(minVal.coerceIn(minMinute, maxMinute))
                        }
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 5.dp),
                        style = DesignTokens.Typography.caption().copy(
                            color = if (isSelected) DesignTokens.Palette.PureBlack else DesignTokens.Palette.GraySecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Interactive Limit Selector with Quick Chips and Stepper buttons.
 */
@Composable
fun LimitSelector(
    limitMinutes: Int,
    onLimitMinutesChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxLimit: Int = 180
) {
    val quickLimits = listOf(0, 5, 10, 15, 20, 30, 45, 60)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Presets Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickLimits.take(4).forEach { minVal ->
                val isSelected = limitMinutes == minVal
                val label = if (minVal == 0) "Locked" else "${minVal}m"
                Surface(
                    shape = DesignTokens.Shapes.Chip,
                    color = if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkBorderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(DesignTokens.Shapes.Chip)
                        .clickable { onLimitMinutesChanged(minVal) }
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = DesignTokens.Typography.caption().copy(
                            color = if (isSelected) DesignTokens.Palette.PureBlack else DesignTokens.Palette.GraySecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Quick Presets Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickLimits.drop(4).forEach { minVal ->
                val isSelected = limitMinutes == minVal
                val label = "${minVal}m"
                Surface(
                    shape = DesignTokens.Shapes.Chip,
                    color = if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkBorderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(DesignTokens.Shapes.Chip)
                        .clickable { onLimitMinutesChanged(minVal) }
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 6.dp),
                        style = DesignTokens.Typography.caption().copy(
                            color = if (isSelected) DesignTokens.Palette.PureBlack else DesignTokens.Palette.GraySecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Stepper Bar for Fine-tuning
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = DesignTokens.Shapes.Button,
            color = DesignTokens.Palette.DarkElevated,
            border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onLimitMinutesChanged((limitMinutes - 5).coerceAtLeast(0)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = "Minus 5m",
                            tint = DesignTokens.Palette.PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onLimitMinutesChanged((limitMinutes - 1).coerceAtLeast(0)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "-1",
                            style = DesignTokens.Typography.caption().copy(
                                color = DesignTokens.Palette.GrayMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Text(
                    text = if (limitMinutes == 0) "Locked (0 min)" else "$limitMinutes minutes",
                    style = DesignTokens.Typography.bodyMedium().copy(
                        color = if (limitMinutes == 0) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onLimitMinutesChanged((limitMinutes + 1).coerceAtMost(maxLimit)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "+1",
                            style = DesignTokens.Typography.caption().copy(
                                color = DesignTokens.Palette.GrayMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                    IconButton(
                        onClick = { onLimitMinutesChanged((limitMinutes + 5).coerceAtMost(maxLimit)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Plus 5m",
                            tint = DesignTokens.Palette.PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
