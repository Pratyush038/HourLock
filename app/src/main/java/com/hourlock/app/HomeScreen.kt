package com.hourlock.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.ui.theme.AccentGreen
import com.hourlock.app.ui.theme.AccentOrange
import com.hourlock.app.ui.theme.AccentRed
import com.hourlock.app.ui.theme.DarkBorder
import com.hourlock.app.ui.theme.DarkBorderSubtle
import com.hourlock.app.ui.theme.DarkSurface
import com.hourlock.app.ui.theme.DarkSurfaceCard
import com.hourlock.app.ui.theme.DarkSurfaceElevated
import com.hourlock.app.ui.theme.PureBlack
import com.hourlock.app.ui.theme.PureWhite
import com.hourlock.app.ui.theme.TextMutedDark
import com.hourlock.app.ui.theme.TextPrimaryDark
import com.hourlock.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * HomeScreen
 * ──────────
 * Generalized multi-app dashboard with a minimalist Black & White aesthetic.
 * Displays overall hourly screen time budget, 2-column quick metric cards,
 * and individual status cards for all monitored apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    // ── Live state ─────────────────────────────────────────────────────────
    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))
    val blockingEnabled by repo.blockingEnabledFlow.collectAsState(initial = true)
    val pauseUntil by repo.pauseUntilFlow.collectAsState(initial = 0L)
    val isPaused = System.currentTimeMillis() < pauseUntil

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
    val todaySecondsMap = remember { mutableStateMapOf<String, Long>() }
    var totalTodaySecondsAll by remember { mutableLongStateOf(0L) }

    LaunchedEffect(monitoredPackages) {
        while (true) {
            for (pkg in monitoredPackages) {
                usedSecondsMap[pkg] = repo.getUsedSeconds(pkg)
                limitMinutesMap[pkg] = repo.getLimitSeconds(pkg) / 60
                todaySecondsMap[pkg] = repo.getTodayTotalSeconds(pkg, context)
            }
            totalTodaySecondsAll = repo.getTodayTotalSecondsAll(monitoredPackages, context)
            delay(1000L)
        }
    }

    // ── Dialog state ───────────────────────────────────────────────────────
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<String?>(null) }

    // ── Time-based greeting ────────────────────────────────────────────────
    val greeting = remember { getGreeting() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── TOP HEADER ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    align = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = "HourLock • Rolling screen budget",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMutedDark,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    // Settings Button Pill
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateToSettings() },
                        color = DarkSurfaceCard,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = PureWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── PERMISSION WARNING BANNER (If missing) ─────────────────────────
            if (!a11yGranted || !usageGranted) {
                item {
                    PermissionAlertBanner(
                        a11yGranted = a11yGranted,
                        usageGranted = usageGranted,
                        onFix = onNavigateToSettings
                    )
                }
            }

            // ── HERO CARD: MASTER OVERVIEW ─────────────────────────────────────
            item {
                HeroOverviewCard(
                    blockingEnabled = blockingEnabled,
                    isPaused = isPaused,
                    pauseUntil = pauseUntil,
                    monitoredCount = monitoredPackages.size,
                    onToggleBlocking = { scope.launch { repo.setBlockingEnabled(!blockingEnabled) } },
                    onTogglePause = {
                        scope.launch {
                            if (isPaused) repo.clearPause() else repo.pauseForOneHour()
                        }
                    }
                )
            }

            // ── 2-COLUMN METRIC CARDS (Inspo from Image 1) ─────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Available time
                    val maxLimit = monitoredPackages.maxOfOrNull { limitMinutesMap[it] ?: 10 } ?: 10
                    val maxUsed = monitoredPackages.maxOfOrNull { (usedSecondsMap[it] ?: 0) / 60 } ?: 0
                    val remainingMins = (maxLimit - maxUsed).coerceAtLeast(0)

                    MetricPillCard(
                        modifier = Modifier.weight(1f),
                        title = "Time available",
                        subtitle = "this hour",
                        value = "${remainingMins}m",
                        progress = if (maxLimit > 0) (remainingMins.toFloat() / maxLimit.toFloat()) else 1f
                    )

                    // Card 2: Today's exact screen time
                    MetricPillCard(
                        modifier = Modifier.weight(1f),
                        title = "Today's total",
                        subtitle = "from 12:00 AM",
                        value = formatDurationHoursMinutes(totalTodaySecondsAll),
                        progress = ((totalTodaySecondsAll % 3600) / 3600f).coerceIn(0f, 1f)
                    )
                }
            }

            // ── MONITORED APPS SECTION ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    align = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE APP LOCKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMutedDark,
                            letterSpacing = 1.5.sp
                        )
                    )

                    // "+ Add" Pill Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showAddDialog = true },
                        color = PureWhite,
                        contentColor = PureBlack
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Text(
                                "Add App",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // ── APP CARDS LIST ─────────────────────────────────────────────────
            if (monitoredPackages.isEmpty()) {
                item {
                    EmptyAppsState(onAddClick = { showAddDialog = true })
                }
            } else {
                items(monitoredPackages.toList()) { pkg ->
                    val usedSec = usedSecondsMap[pkg] ?: 0
                    val limitMin = limitMinutesMap[pkg] ?: DEFAULT_LIMIT_MINUTES
                    val todaySec = todaySecondsMap[pkg] ?: 0L

                    AppLockCard(
                        pkg = pkg,
                        usedSeconds = usedSec,
                        limitMinutes = limitMin,
                        todaySeconds = todaySec,
                        isBlocked = (usedSec >= limitMin * 60) && blockingEnabled && !isPaused,
                        onClick = { editingPkg = pkg }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ── ADD APP DIALOG ─────────────────────────────────────────────────────
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

    // ── EDIT APP LIMIT DIALOG ──────────────────────────────────────────────
    editingPkg?.let { pkg ->
        val currentLimit = limitMinutesMap[pkg] ?: DEFAULT_LIMIT_MINUTES
        EditLimitDialog(
            pkg = pkg,
            currentLimitMinutes = currentLimit,
            onSaveLimit = { newLimit ->
                scope.launch {
                    repo.setLimitMinutes(pkg, newLimit)
                    editingPkg = null
                }
            },
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

// ─── HERO OVERVIEW CARD ────────────────────────────────────────────────────────

@Composable
private fun HeroOverviewCard(
    blockingEnabled: Boolean,
    isPaused: Boolean,
    pauseUntil: Long,
    monitoredCount: Int,
    onToggleBlocking: () -> Unit,
    onTogglePause: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                align = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Focus Lock",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )

                        // Status Badge
                        val badgeColor = when {
                            !blockingEnabled -> Color(0xFF3F3F46)
                            isPaused -> AccentOrange
                            else -> AccentGreen
                        }
                        val badgeText = when {
                            !blockingEnabled -> "Disabled"
                            isPaused -> "Paused"
                            else -> "Active"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = "$monitoredCount ${if (monitoredCount == 1) "app" else "apps"} guarded • Rolling 60m resets",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                    )
                }

                // Sleek Switch
                Switch(
                    checked = blockingEnabled,
                    onCheckedChange = { onToggleBlocking() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureBlack,
                        checkedTrackColor = PureWhite,
                        uncheckedThumbColor = TextMutedDark,
                        uncheckedTrackColor = DarkSurfaceElevated,
                        uncheckedBorderColor = DarkBorder
                    )
                )
            }

            // Quick Pause Action Bar
            AnimatedVisibility(visible = blockingEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTogglePause() },
                    color = if (isPaused) AccentOrange.copy(alpha = 0.12f) else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (isPaused) AccentOrange.copy(alpha = 0.4f) else DarkBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                                tint = if (isPaused) AccentOrange else TextSecondaryDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isPaused) "Resume blocking now" else "Take a 1-hour focus pause",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isPaused) AccentOrange else TextSecondaryDark,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Text(
                            text = if (isPaused) "RESUME" else "PAUSE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isPaused) AccentOrange else PureWhite,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── 2-COLUMN METRIC PILL CARD (Inspiration from Smart Home card) ─────────────

@Composable
private fun MetricPillCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    value: String,
    progress: Float
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMutedDark,
                            fontSize = 10.sp
                        )
                    )
                }

                // Micro circular indicator
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = DarkSurfaceElevated,
                        strokeWidth = 2.5.dp
                    )
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = PureWhite,
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    letterSpacing = (-0.5).sp
                )
            )
        }
    }
}

// ─── APP LOCK CARD (Generalized for all apps) ──────────────────────────────────

@Composable
private fun AppLockCard(
    pkg: String,
    usedSeconds: Int,
    limitMinutes: Int,
    todaySeconds: Long,
    isBlocked: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appLabel = remember(pkg) { getAppLabel(context, pkg) }
    val limitSeconds = limitMinutes * 60
    val progress = if (limitSeconds > 0) (usedSeconds.toFloat() / limitSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val usedMin = usedSeconds / 60
    val usedSec = usedSeconds % 60
    val remainingSec = (limitSeconds - usedSeconds).coerceAtLeast(0)
    val remainingMin = remainingSec / 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, if (isBlocked) AccentRed.copy(alpha = 0.5f) else DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Avatar Pill with Initial
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isBlocked) AccentRed.copy(alpha = 0.15f) else DarkSurfaceElevated,
                border = BorderStroke(1.dp, if (isBlocked) AccentRed.copy(alpha = 0.4f) else DarkBorderSubtle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = appLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isBlocked) AccentRed else PureWhite
                        )
                    )
                }
            }

            // Middle info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isBlocked) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentRed.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "LOCKED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "${String.format("%d:%02d", usedMin, usedSec)} of $limitMinutes min/h • Today: ${formatDurationHoursMinutes(todaySeconds)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                )

                Spacer(Modifier.height(8.dp))

                // Progress line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isBlocked) AccentRed else PureWhite)
                    )
                }
            }

            // Right remaining badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isBlocked) "0m left" else "${remainingMin}m left",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isBlocked) AccentRed else PureWhite
                    )
                )
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Options",
                    tint = TextMutedDark,
                    modifier = Modifier.size(16.dp)
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
            .clip(RoundedCornerShape(18.dp))
            .clickable { onFix() },
        color = AccentOrange.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = AccentOrange)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Setup Incomplete",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                )
                val missing = buildList {
                    if (!a11yGranted) add("Accessibility")
                    if (!usageGranted) add("Usage Access")
                }.joinToString(" & ")
                Text(
                    "Grant $missing to enable lock enforcement",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                )
            }
            Text(
                "FIX",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

// ─── EMPTY STATE ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyAppsState(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterAlignment,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Apps,
                contentDescription = null,
                tint = TextMutedDark,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No Apps Monitored",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            )
            Text(
                "Add your social media, games, or video apps to limit hourly usage.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondaryDark,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onAddClick() },
                color = PureWhite,
                contentColor = PureBlack
            ) {
                Text(
                    "Add Your First App",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

// ─── ADD APP DIALOG (Search & Add any installed app) ───────────────────────────

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
        containerColor = DarkSurfaceCard,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Add App to Guard",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = TextMutedDark) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMutedDark) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedIndicatorColor = PureWhite,
                        unfocusedIndicatorColor = DarkBorder
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
                                .clip(RoundedCornerShape(12.dp))
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = PureWhite
                                    )
                                )
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMutedDark)
                                )
                            }

                            if (isAdded) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurfaceElevated
                                ) {
                                    Text(
                                        "Added",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextMutedDark)
                                    )
                                }
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = "Add", tint = PureWhite)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = PureWhite, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ─── EDIT APP LIMIT DIALOG ─────────────────────────────────────────────────────

@Composable
private fun EditLimitDialog(
    pkg: String,
    currentLimitMinutes: Int,
    onSaveLimit: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appLabel = remember(pkg) { getAppLabel(context, pkg) }
    var limitSlider by remember { mutableIntStateOf(currentLimitMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceCard,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                appLabel,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Set hourly screen time limit:",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondaryDark)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${limitSlider} minutes / hour",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    )
                }

                Slider(
                    value = limitSlider.toFloat(),
                    onValueChange = { limitSlider = it.toInt() },
                    valueRange = 1f..60f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = PureWhite,
                        activeTrackColor = PureWhite,
                        inactiveTrackColor = DarkSurfaceElevated
                    )
                )

                Spacer(Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRemove() },
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Remove from Guard List",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveLimit(limitSlider) }) {
                Text("Save", color = PureWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMutedDark)
            }
        }
    )
}

// ─── UTILITY HELPERS ───────────────────────────────────────────────────────────

private fun getGreeting(): String {
    val cal = Calendar.getInstance()
    return when (cal.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

private fun formatDurationHoursMinutes(totalSeconds: Long): String {
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
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val componentName = "${context.packageName}/${UsageTrackerService::class.java.canonicalName}"
        enabledServices.split(":").any { it.equals(componentName, ignoreCase = true) }
    } catch (e: Exception) {
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
    } catch (e: Exception) {
        false
    }
}
