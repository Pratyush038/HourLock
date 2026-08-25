package com.hourlock.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.data.DailyUsage
import com.hourlock.app.data.DayCompliance
import com.hourlock.app.data.HourlyIntensity
import com.hourlock.app.data.InsightSummary
import com.hourlock.app.data.StreakSummary
import com.hourlock.app.data.UsageLogRepository
import com.hourlock.app.ui.components.RoundedCard
import com.hourlock.app.ui.components.StatChip
import com.hourlock.app.ui.theme.DesignTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * StatsScreen
 * ───────────
 * Analytics & Insights destination featuring:
 *  1. Auto-generated smart insight statement banner
 *  2. Weekly 7-day monochrome vertical bar chart (Mon-Sun)
 *  3. 24-hour heatmap intensity grid for hourly pacing
 *  4. Focus streak count + 14-day compliance calendar dots
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val analyticsRepo = remember { UsageLogRepository(context) }

    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))

    var weeklyUsage by remember { mutableStateOf<List<DailyUsage>>(emptyList()) }
    var hourlyHeatmap by remember { mutableStateOf<List<HourlyIntensity>>(emptyList()) }
    var streakSummary by remember { mutableStateOf<StreakSummary?>(null) }
    var insightSummary by remember { mutableStateOf<InsightSummary?>(null) }
    var selectedDateStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    LaunchedEffect(monitoredPackages, selectedDateStr) {
        weeklyUsage = analyticsRepo.getWeeklyUsage(monitoredPackages)
        hourlyHeatmap = analyticsRepo.getHourlyHeatmap(selectedDateStr, monitoredPackages)
        streakSummary = analyticsRepo.getStreakSummary(monitoredPackages)
        insightSummary = analyticsRepo.getInsightSummary(monitoredPackages)
    }

    Scaffold(
        containerColor = DesignTokens.Palette.DarkBackground,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Insights & Trends",
                        style = DesignTokens.Typography.title().copy(
                            fontWeight = FontWeight.Bold,
                            color = DesignTokens.Palette.PureWhite,
                            fontSize = 24.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DesignTokens.Palette.PureWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = DesignTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg)
        ) {
            // ── 1. SMART INSIGHT BANNER ────────────────────────────────────────
            item {
                insightSummary?.let { insight ->
                    RoundedCard(
                        shape = DesignTokens.Shapes.Card,
                        containerColor = DesignTokens.Palette.DarkCard,
                        borderColor = DesignTokens.Palette.DarkBorder,
                        contentPadding = 18.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = DesignTokens.Shapes.Pill,
                                color = DesignTokens.Palette.DarkElevated,
                                border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorderSubtle)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = DesignTokens.Palette.PureWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = insight.headline,
                                    style = DesignTokens.Typography.subtitle().copy(
                                        fontWeight = FontWeight.Bold,
                                        color = DesignTokens.Palette.PureWhite,
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = insight.detail,
                                    style = DesignTokens.Typography.bodySmall().copy(
                                        color = DesignTokens.Palette.GraySecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── 2. FOCUS STREAK & CALENDAR DOTS ────────────────────────────────
            item {
                streakSummary?.let { streak ->
                    RoundedCard(
                        shape = DesignTokens.Shapes.Card,
                        containerColor = DesignTokens.Palette.DarkCard,
                        borderColor = DesignTokens.Palette.DarkBorder,
                        contentPadding = 20.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "FOCUS STREAK",
                                    style = DesignTokens.Typography.caption().copy(
                                        color = DesignTokens.Palette.GrayMuted,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.2.sp
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${streak.currentStreak}",
                                        style = DesignTokens.Typography.display().copy(
                                            fontWeight = FontWeight.Bold,
                                            color = DesignTokens.Palette.PureWhite,
                                            fontSize = 36.sp
                                        )
                                    )
                                    Text(
                                        text = "DAYS",
                                        style = DesignTokens.Typography.caption().copy(
                                            color = DesignTokens.Palette.GraySecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            // Best streak pill
                            Surface(
                                shape = DesignTokens.Shapes.Chip,
                                color = DesignTokens.Palette.DarkElevated,
                                border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = DesignTokens.Palette.PureWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Best: ${streak.longestStreak}d",
                                        style = DesignTokens.Typography.caption().copy(
                                            color = DesignTokens.Palette.PureWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // 14-day Calendar Dots Row
                        Text(
                            text = "PAST 14 DAYS COMPLIANCE",
                            style = DesignTokens.Typography.caption().copy(
                                color = DesignTokens.Palette.GrayMuted,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            streak.recent14Days.forEach { day ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Dot
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (day.stayedUnderLimit) {
                                                    Modifier.background(DesignTokens.Palette.PureWhite)
                                                } else {
                                                    Modifier
                                                        .background(DesignTokens.Palette.DarkElevated)
                                                        .border(1.5.dp, DesignTokens.Palette.WarningAccent, CircleShape)
                                                }
                                            )
                                    )

                                    // Day number
                                    Text(
                                        text = day.dayLabel,
                                        style = DesignTokens.Typography.caption().copy(
                                            color = DesignTokens.Palette.GrayMuted,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. WEEKLY BAR CHART (Mon - Sun) ────────────────────────────────
            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 20.dp
                ) {
                    val maxMins = (weeklyUsage.maxOfOrNull { it.totalMinutes } ?: 60).coerceAtLeast(30)
                    val totalWeeklyMins = weeklyUsage.sumOf { it.totalMinutes }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "THIS WEEK'S USAGE",
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GrayMuted,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${totalWeeklyMins / 60}h ${totalWeeklyMins % 60}m total",
                                style = DesignTokens.Typography.subtitle().copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DesignTokens.Palette.PureWhite
                                )
                            )
                        }

                        Surface(
                            shape = DesignTokens.Shapes.Badge,
                            color = DesignTokens.Palette.DarkElevated
                        ) {
                            Text(
                                text = "MON - SUN",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GraySecondary,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 7 Bar Columns
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyUsage.forEach { day ->
                            val heightFraction = (day.totalMinutes.toFloat() / maxMins.toFloat()).coerceIn(0.04f, 1f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .clickable {
                                        selectedDateStr = day.date
                                    }
                            ) {
                                // Value label above bar
                                if (day.totalMinutes > 0) {
                                    Text(
                                        text = "${day.totalMinutes}m",
                                        style = DesignTokens.Typography.caption().copy(
                                            color = if (day.isCurrentDay) DesignTokens.Palette.PureWhite else DesignTokens.Palette.GrayMuted,
                                            fontSize = 9.sp,
                                            fontWeight = if (day.isCurrentDay) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                } else {
                                    Spacer(Modifier.height(16.dp))
                                }

                                // Bar
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .then(
                                            if (day.isCurrentDay) {
                                                Modifier.background(DesignTokens.Palette.PureWhite)
                                            } else {
                                                Modifier
                                                    .background(DesignTokens.Palette.DarkElevated)
                                                    .border(1.dp, DesignTokens.Palette.DarkBorder, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                            }
                                        )
                                )

                                Spacer(Modifier.height(8.dp))

                                // Day label
                                Text(
                                    text = day.dayOfWeek.uppercase(),
                                    style = DesignTokens.Typography.caption().copy(
                                        color = if (day.isCurrentDay) DesignTokens.Palette.PureWhite else DesignTokens.Palette.GrayMuted,
                                        fontWeight = if (day.isCurrentDay) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── 4. HOURLY HEATMAP ROW (24 Squares) ─────────────────────────────
            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 20.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "HOURLY HEATMAP",
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GrayMuted,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "24-Hour Pacing ($selectedDateStr)",
                                style = DesignTokens.Typography.subtitle().copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DesignTokens.Palette.PureWhite,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        // Legend
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "0m",
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GrayMuted,
                                    fontSize = 9.sp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DesignTokens.Palette.DarkElevated)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DesignTokens.Palette.GraySecondary)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DesignTokens.Palette.PureWhite)
                            )
                            Text(
                                text = "Max",
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GrayMuted,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 24 Hour blocks grid (4 rows of 6 blocks or 2 rows of 12)
                    val rows = hourlyHeatmap.chunked(12)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowItems.forEach { hourData ->
                                    val blockColor = when {
                                        hourData.usedMinutes == 0 -> DesignTokens.Palette.DarkElevated
                                        hourData.intensity < 0.35f -> DesignTokens.Palette.GrayMuted
                                        hourData.intensity < 0.70f -> DesignTokens.Palette.GraySecondary
                                        else -> DesignTokens.Palette.PureWhite
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(blockColor)
                                                .border(1.dp, DesignTokens.Palette.DarkBorderSubtle, RoundedCornerShape(4.dp))
                                        )
                                        Text(
                                            text = "${hourData.hour}",
                                            style = DesignTokens.Typography.caption().copy(
                                                color = DesignTokens.Palette.GrayMuted,
                                                fontSize = 8.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(DesignTokens.Spacing.xxl)) }
        }
    }
}
