package com.hourlock.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.data.UsageLogRepository
import com.hourlock.app.ui.components.MonochromeSwitch
import com.hourlock.app.ui.components.RingProgress
import com.hourlock.app.ui.components.RoundedCard
import com.hourlock.app.ui.theme.DesignTokens
import com.hourlock.app.ui.theme.WordmarkFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HomeScreen
 * ──────────
 * Premium monochromatic dashboard featuring:
 *  - Large circular progress ring with animated arc and center countdown
 *  - Quick stat chips (Today's total, Streak, Week-over-week delta)
 *  - Full-width interactive master toggle card with inverted contrast
 *  - Per-app cards with mini progress rings and limit management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repo = remember { PrefsRepository(context) }
    val analyticsRepo = remember { UsageLogRepository(context) }
    val scope = rememberCoroutineScope()

    // ── Live preferences state ─────────────────────────────────────────────
    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))
    val blockingEnabled by repo.blockingEnabledFlow.collectAsState(initial = true)
    val pauseUntil by repo.pauseUntilFlow.collectAsState(initial = 0L)
    val commitmentLockUntil by repo.commitmentLockUntilFlow.collectAsState(initial = 0L)
    val isPaused = System.currentTimeMillis() < pauseUntil
    val isCommitmentLockActive = commitmentLockUntil > System.currentTimeMillis()

    // ── Permission state ───────────────────────────────────────────────────
    var a11yGranted by remember { mutableStateOf(false) }
    var usageGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            a11yGranted = isAccessibilityServiceEnabled(context)
            usageGranted = isUsageAccessGranted(context)
            delay(2000L)
        }
    }

    // ── Per-app live usage ticker ──────────────────────────────────────────
    val usedSecondsMap = remember { mutableStateMapOf<String, Int>() }
    val limitMinutesMap = remember { mutableStateMapOf<String, Int>() }
    val activeBlockLabelMap = remember { mutableStateMapOf<String, String>() }
    val todaySecondsMap = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(monitoredPackages) {
        while (true) {
            for (pkg in monitoredPackages) {
                usedSecondsMap[pkg] = repo.getUsedSeconds(pkg)
                limitMinutesMap[pkg] = repo.getLimitSeconds(pkg) / 60
                activeBlockLabelMap[pkg] = formatBlockLabel(repo.getActiveScheduleBlock(pkg).block)
                todaySecondsMap[pkg] = repo.getTodayTotalSeconds(pkg, context)
            }
            delay(1000L)
        }
    }

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = DesignTokens.Palette.DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = DesignTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg)
        ) {
            item { Spacer(Modifier.height(DesignTokens.Spacing.xs)) }

            // ── TOP APP BAR ────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = DesignTokens.Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HOURLOCK",
                            fontFamily = WordmarkFont,
                            style = DesignTokens.Typography.title().copy(
                                fontWeight = FontWeight.Thin,
                                color = DesignTokens.Palette.PureWhite,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = "ROLLING HOURLY SCREEN BUDGET",
                            style = DesignTokens.Typography.caption().copy(
                                color = DesignTokens.Palette.GrayMuted,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    // Action Icons (Stats + Settings)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(DesignTokens.Shapes.Pill)
                                .clickable {
                                    try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                    onNavigateToStats()
                                },
                            shape = DesignTokens.Shapes.Pill,
                            color = DesignTokens.Palette.DarkCard,
                            border = BorderStroke(DesignTokens.Elevation.borderWidth, DesignTokens.Palette.DarkBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.BarChart,
                                    contentDescription = "Stats & Insights",
                                    tint = DesignTokens.Palette.PureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(DesignTokens.Shapes.Pill)
                                .clickable {
                                    try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                    onNavigateToSettings()
                                },
                            shape = DesignTokens.Shapes.Pill,
                            color = DesignTokens.Palette.DarkCard,
                            border = BorderStroke(DesignTokens.Elevation.borderWidth, DesignTokens.Palette.DarkBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = DesignTokens.Palette.PureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── PERMISSION WARNING BANNER (If setup incomplete) ────────────────
            if (!a11yGranted || !usageGranted) {
                item {
                    PermissionAlertBanner(
                        a11yGranted = a11yGranted,
                        usageGranted = usageGranted,
                        onFix = onNavigateToSettings
                    )
                }
            }

            if (isCommitmentLockActive) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignTokens.Shapes.Card,
                        color = DesignTokens.Palette.DarkCard,
                        border = BorderStroke(1.dp, DesignTokens.Palette.WarningAccentBorder)
                    ) {
                        Text(
                            text = "Committed until ${formatLockUntil(commitmentLockUntil)}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = DesignTokens.Typography.bodySmall().copy(
                                color = DesignTokens.Palette.WarningAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }



            // ── 3. FULL-WIDTH MASTER ON/OFF TOGGLE CARD ────────────────────────
            item {
                val isOn = blockingEnabled
                val cardBg = DesignTokens.Palette.DarkCard
                val cardBorder = if (isOn) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkBorder
                val textPrimary = DesignTokens.Palette.PureWhite
                val textSecondary = if (isOn) DesignTokens.Palette.GrayMuted else DesignTokens.Palette.GraySecondary

                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = cardBg,
                    borderColor = cardBorder,
                    contentPadding = 20.dp,
                    onClick = {
                        if (isCommitmentLockActive && blockingEnabled) return@RoundedCard
                        try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                        scope.launch { repo.setBlockingEnabled(!blockingEnabled) }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = DesignTokens.Shapes.Pill,
                                color = if (isOn) DesignTokens.Palette.PureBlack else DesignTokens.Palette.DarkElevated
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isOn) Icons.Filled.Lock else Icons.Filled.Pause,
                                        contentDescription = null,
                                        tint = if (isOn) DesignTokens.Palette.PureWhite else DesignTokens.Palette.GrayMuted,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = if (isOn) "FOCUS LOCK ACTIVE" else "LOCK DISABLED",
                                    style = DesignTokens.Typography.subtitle().copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 16.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Text(
                                    text = if (isOn) {
                                        if (isPaused) "Temporarily paused for 1 hour" else "${monitoredPackages.size} guarded • Resets every hour"
                                    } else "Tap card to resume protection",
                                    style = DesignTokens.Typography.bodySmall().copy(
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        MonochromeSwitch(
                            checked = blockingEnabled,
                            onCheckedChange = { checked ->
                                if (isCommitmentLockActive && !checked) return@MonochromeSwitch
                                scope.launch { repo.setBlockingEnabled(checked) }
                            },
                            enabled = !(isCommitmentLockActive && blockingEnabled)
                        )
                    }

                    // 1-Hour Pause quick bar if active
                    AnimatedVisibility(visible = blockingEnabled) {
                        Column(modifier = Modifier.padding(top = DesignTokens.Spacing.md)) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DesignTokens.Shapes.Button)
                                    .clickable {
                                        try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                        scope.launch {
                                            if (isPaused) repo.clearPause() else repo.pauseForOneHour()
                                        }
                                    },
                                color = DesignTokens.Palette.DarkElevated,
                                border = BorderStroke(1.dp, if (isPaused) DesignTokens.Palette.WarningAccentBorder else DesignTokens.Palette.DarkBorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                            contentDescription = null,
                                            tint = if (isPaused) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isPaused) "Pause active (resumes at top of hour)" else "Need emergency access? Take 1-hr pause",
                                            style = DesignTokens.Typography.bodySmall().copy(
                                                color = if (isPaused) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = if (isPaused) "RESUME" else "PAUSE",
                                        modifier = Modifier.wrapContentWidth().padding(start = 12.dp),
                                        style = DesignTokens.Typography.caption().copy(
                                            color = if (isPaused) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. MONITORED APPS LIST HEADER ──────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DesignTokens.Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MONITORED APPS",
                        style = DesignTokens.Typography.caption().copy(
                            color = DesignTokens.Palette.GrayMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )

                    // "+ Add App" Pill
                    Surface(
                        modifier = Modifier
                            .clip(DesignTokens.Shapes.Pill)
                            .clickable {
                                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                showAddDialog = true
                            },
                        shape = DesignTokens.Shapes.Pill,
                        color = DesignTokens.Palette.PureWhite,
                        contentColor = DesignTokens.Palette.PureBlack
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(15.dp))
                            Text(
                                "Add App",
                                style = DesignTokens.Typography.caption().copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
            }

            // ── 5. PER-APP CARDS WITH MINI PROGRESS RINGS ──────────────────────
            if (monitoredPackages.isEmpty()) {
                item {
                    EmptyAppsState(onAddClick = { showAddDialog = true })
                }
            } else {
                items(monitoredPackages.toList()) { pkg ->
                    val usedSec = usedSecondsMap[pkg] ?: 0
                    val limitMin = limitMinutesMap[pkg] ?: DEFAULT_LIMIT_MINUTES
                    val todaySec = todaySecondsMap[pkg] ?: 0L
                    val isAppBlocked = (usedSec >= limitMin * 60) && blockingEnabled && !isPaused

                    AppLockRowCard(
                        pkg = pkg,
                        usedSeconds = usedSec,
                        limitMinutes = limitMin,
                        activeBlockLabel = activeBlockLabelMap[pkg] ?: "",
                        todaySeconds = todaySec,
                        isBlocked = isAppBlocked,
                        onClick = {
                            try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                            editingPkg = pkg
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(DesignTokens.Spacing.xxl)) }
        }
    }

    // ── ADD APP MODAL DIALOG ───────────────────────────────────────────────
    if (showAddDialog) {
        AddAppDialog(
            currentlyMonitored = monitoredPackages,
            onAdd = { pkg ->
                scope.launch {
                    repo.setMonitoredPackages(monitoredPackages + pkg)
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // ── EDIT APP LIMIT MODAL DIALOG ────────────────────────────────────────
    editingPkg?.let { pkg ->
        EditLimitDialog(
            pkg = pkg,
            canRemove = !isCommitmentLockActive,
            commitmentLockUntilMillis = commitmentLockUntil,
            onRemove = {
                scope.launch {
                    repo.setMonitoredPackages(monitoredPackages - pkg)
                    editingPkg = null
                }
            },
            onDismiss = { editingPkg = null }
        )
    }
}

// ─── PER-APP CARD ROW ──────────────────────────────────────────────────────────

@Composable
private fun AppLockRowCard(
    pkg: String,
    usedSeconds: Int,
    limitMinutes: Int,
    activeBlockLabel: String,
    todaySeconds: Long,
    isBlocked: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appLabel = remember(pkg) { getAppLabel(context, pkg) }
    val limitSeconds = limitMinutes * 60
    val progress = if (limitSeconds > 0) (usedSeconds.toFloat() / limitSeconds.toFloat()).coerceIn(0f, 1f) else 0f

    val usedMin = usedSeconds / 60
    val usedSec = usedSeconds % 60
    val remainingSec = (limitSeconds - usedSeconds).coerceAtLeast(0)
    val remainingMin = remainingSec / 60

    RoundedCard(
        shape = DesignTokens.Shapes.Card,
        containerColor = DesignTokens.Palette.DarkCard,
        borderColor = if (isBlocked) DesignTokens.Palette.WarningAccentBorder else DesignTokens.Palette.DarkBorder,
        contentPadding = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mini Circular Progress Ring with App Initial
            RingProgress(
                progress = progress,
                size = 46.dp,
                strokeWidth = 4.dp,
                trackColor = DesignTokens.Palette.DarkElevated,
                progressColor = DesignTokens.Palette.PureWhite,
                warningColor = DesignTokens.Palette.WarningAccent,
                isWarningOrBlocked = isBlocked,
                content = {
                    Text(
                        text = appLabel.take(1).uppercase(),
                        style = DesignTokens.Typography.bodyMedium().copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isBlocked) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                            fontSize = 15.sp
                        )
                    )
                }
            )

            // Center details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = appLabel,
                        style = DesignTokens.Typography.subtitle().copy(
                            fontWeight = FontWeight.Bold,
                            color = DesignTokens.Palette.PureWhite,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isBlocked) {
                        Surface(
                            shape = DesignTokens.Shapes.Badge,
                            color = DesignTokens.Palette.WarningAccentMuted,
                            border = BorderStroke(1.dp, DesignTokens.Palette.WarningAccentBorder)
                        ) {
                            Text(
                                "LOCKED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.WarningAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = "${String.format("%d:%02d", usedMin, usedSec)} of $limitMinutes min/h • Today: ${formatDuration(todaySeconds)}",
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = DesignTokens.Palette.GraySecondary,
                        fontSize = 12.sp
                    )
                )

                if (activeBlockLabel.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = activeBlockLabel,
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.GrayMuted,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right remaining pill
            Surface(
                shape = DesignTokens.Shapes.Chip,
                color = if (isBlocked) DesignTokens.Palette.WarningAccentMuted else DesignTokens.Palette.DarkElevated,
                border = BorderStroke(1.dp, if (isBlocked) DesignTokens.Palette.WarningAccentBorder else DesignTokens.Palette.DarkBorderSubtle)
            ) {
                Text(
                    text = if (isBlocked) "0m left" else "${remainingMin}m left",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = DesignTokens.Typography.caption().copy(
                        color = if (isBlocked) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

// ─── PERMISSION ALERT BANNER ───────────────────────────────────────────────────

@Composable
private fun PermissionAlertBanner(
    a11yGranted: Boolean,
    usageGranted: Boolean,
    onFix: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DesignTokens.Shapes.Card)
            .clickable { onFix() },
        shape = DesignTokens.Shapes.Card,
        color = DesignTokens.Palette.WarningAccentMuted,
        border = BorderStroke(1.dp, DesignTokens.Palette.WarningAccentBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = DesignTokens.Palette.WarningAccent)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Setup Incomplete",
                    style = DesignTokens.Typography.subtitle().copy(
                        fontWeight = FontWeight.Bold,
                        color = DesignTokens.Palette.WarningAccent,
                        fontSize = 15.sp
                    )
                )
                val missing = buildList {
                    if (!a11yGranted) add("Accessibility")
                    if (!usageGranted) add("Usage Access")
                }.joinToString(" & ")
                Text(
                    "Grant $missing to enable lock enforcement",
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = DesignTokens.Palette.GraySecondary,
                        fontSize = 12.sp
                    )
                )
            }
            Text(
                "FIX",
                style = DesignTokens.Typography.caption().copy(
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.Palette.WarningAccent,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

// ─── EMPTY APPS STATE ──────────────────────────────────────────────────────────

@Composable
private fun EmptyAppsState(onAddClick: () -> Unit) {
    RoundedCard(
        shape = DesignTokens.Shapes.Card,
        containerColor = DesignTokens.Palette.DarkCard,
        borderColor = DesignTokens.Palette.DarkBorder,
        contentPadding = 32.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.HourglassEmpty,
                contentDescription = null,
                tint = DesignTokens.Palette.GrayMuted,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No Apps Monitored",
                style = DesignTokens.Typography.subtitle().copy(
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.Palette.PureWhite
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Add your social media, video, or distraction apps to enforce rolling hourly limits.",
                style = DesignTokens.Typography.bodySmall().copy(
                    color = DesignTokens.Palette.GraySecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .clip(DesignTokens.Shapes.Button)
                    .clickable { onAddClick() },
                shape = DesignTokens.Shapes.Button,
                color = DesignTokens.Palette.PureWhite,
                contentColor = DesignTokens.Palette.PureBlack
            ) {
                Text(
                    "Add Your First App",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = DesignTokens.Typography.caption().copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

// ─── ADD APP SEARCH & PICKER DIALOG ───────────────────────────────────────────

@Composable
private fun AddAppDialog(
    currentlyMonitored: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val list = resolveInfos.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg !in NEVER_BLOCK_PACKAGES && pkg != context.packageName) {
                    val label = resolveInfo.loadLabel(pm).toString()
                    label to pkg
                } else null
            }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
            installedApps = list
        }
    }

    val filtered = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.first.contains(searchQuery, ignoreCase = true) ||
            it.second.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Text(
                "Add App to Guard",
                style = DesignTokens.Typography.title().copy(
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.Palette.PureWhite
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = DesignTokens.Palette.GrayMuted) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DesignTokens.Palette.GrayMuted) },
                    singleLine = true,
                    shape = DesignTokens.Shapes.Button,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DesignTokens.Palette.DarkElevated,
                        unfocusedContainerColor = DesignTokens.Palette.DarkElevated,
                        focusedTextColor = DesignTokens.Palette.PureWhite,
                        unfocusedTextColor = DesignTokens.Palette.PureWhite,
                        focusedIndicatorColor = DesignTokens.Palette.PureWhite,
                        unfocusedIndicatorColor = DesignTokens.Palette.DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered) { (label, pkg) ->
                        val isAdded = pkg in currentlyMonitored
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DesignTokens.Shapes.Button)
                                .clickable {
                                    if (!isAdded) onAdd(pkg)
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = DesignTokens.Typography.bodyMedium().copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = DesignTokens.Palette.PureWhite
                                    )
                                )
                                Text(
                                    text = pkg,
                                    style = DesignTokens.Typography.bodySmall().copy(
                                        color = DesignTokens.Palette.GrayMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            if (isAdded) {
                                Surface(
                                    shape = DesignTokens.Shapes.Badge,
                                    color = DesignTokens.Palette.DarkElevated
                                ) {
                                    Text(
                                        "Added",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = DesignTokens.Typography.caption().copy(
                                            color = DesignTokens.Palette.GrayMuted,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = "Add", tint = DesignTokens.Palette.PureWhite)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ─── EDIT APP LIMIT DIALOG ─────────────────────────────────────────────────────

@Composable
private fun EditLimitDialog(
    pkg: String,
    canRemove: Boolean,
    commitmentLockUntilMillis: Long,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appLabel = remember(pkg) { getAppLabel(context, pkg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Text(
                appLabel,
                style = DesignTokens.Typography.title().copy(
                    fontWeight = FontWeight.Bold,
                    color = DesignTokens.Palette.PureWhite
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Schedule limits are managed in Settings > Monitored App Schedules.",
                    style = DesignTokens.Typography.body().copy(color = DesignTokens.Palette.GraySecondary)
                )

                if (!canRemove) {
                    Text(
                        "Removing this app is locked until ${formatLockUntil(commitmentLockUntilMillis)}.",
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.WarningAccent,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DesignTokens.Shapes.Button)
                        .clickable(enabled = canRemove) { onRemove() },
                    shape = DesignTokens.Shapes.Button,
                    color = if (canRemove) DesignTokens.Palette.StatusErrorMuted else DesignTokens.Palette.DarkElevated,
                    border = BorderStroke(
                        1.dp,
                        if (canRemove) DesignTokens.Palette.StatusError.copy(alpha = 0.4f) else DesignTokens.Palette.DarkBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Remove from Guard List",
                            style = DesignTokens.Typography.caption().copy(
                                color = if (canRemove) DesignTokens.Palette.StatusError else DesignTokens.Palette.GrayMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DesignTokens.Palette.GrayMuted)
            }
        }
    )
}

// ─── HELPERS ───────────────────────────────────────────────────────────────────

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${totalSeconds}s"
    }
}

private fun getAppLabel(context: Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

private fun formatLockUntil(untilMillis: Long): String {
    if (untilMillis <= 0L) return "-"
    val sdf = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
    return sdf.format(Date(untilMillis))
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val componentName = "${context.packageName}/${UsageTrackerService::class.java.canonicalName}"
        enabledServices.split(":").any { it.equals(componentName, ignoreCase = true) }
    } catch (_: Exception) {
        false
    }
}

fun isUsageAccessGranted(context: Context): Boolean {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return false
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            now - 86_400_000L,
            now
        )
        !stats.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }
}
