package com.hourlock.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.ui.components.MonochromeSlider
import com.hourlock.app.ui.components.MonochromeSwitch
import com.hourlock.app.ui.components.RoundedCard
import com.hourlock.app.ui.theme.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SettingsScreen
 * ──────────────
 * Cleanly organized settings grouped into rounded cards:
 *  1. System Permissions (with green/red status dots & inline grant)
 *  2. Monitored Apps (with per-app limit sliders)
 *  3. Blocking Behavior (master toggle & rolling window info)
 *  4. Privacy & Safety Manifesto
 *  5. Danger Zone (pause, disable, reset data)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))
    val blockingEnabled by repo.blockingEnabledFlow.collectAsState(initial = true)
    val pauseUntil by repo.pauseUntilFlow.collectAsState(initial = 0L)
    val commitmentLockUntil by repo.commitmentLockUntilFlow.collectAsState(initial = 0L)
    val isPaused = System.currentTimeMillis() < pauseUntil
    val isCommitmentLockActive = commitmentLockUntil > System.currentTimeMillis()

    var a11yGranted by remember { mutableStateOf(false) }
    var usageGranted by remember { mutableStateOf(false) }
    var batteryOptExempt by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }
    var showCommitmentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            a11yGranted = isAccessibilityServiceEnabled(context)
            usageGranted = isUsageAccessGranted(context)
            batteryOptExempt = try {
                val pm = context.getSystemService(PowerManager::class.java)
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (_: Exception) { false }
            delay(2000L)
        }
    }

    Scaffold(
        containerColor = DesignTokens.Palette.DarkBackground,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
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
            // ── 1. SYSTEM PERMISSIONS ──────────────────────────────────────────
            item {
                SectionHeader("PERMISSIONS")
            }

            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 16.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PermissionRowItem(
                            label = "Accessibility Service",
                            description = "Foreground app detection & quota enforcement",
                            icon = Icons.Filled.Lock,
                            granted = a11yGranted,
                            onGrant = {
                                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            }
                        )

                        HorizontalDivider(color = DesignTokens.Palette.DarkBorderSubtle, thickness = 0.5.dp)

                        PermissionRowItem(
                            label = "Usage Access",
                            description = "Daily screen time calculation & statistics",
                            icon = Icons.Filled.Timer,
                            granted = usageGranted,
                            onGrant = {
                                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            }
                        )

                        HorizontalDivider(color = DesignTokens.Palette.DarkBorderSubtle, thickness = 0.5.dp)

                        PermissionRowItem(
                            label = "Battery Exemption",
                            description = "Prevents aggressive OS background killing",
                            icon = Icons.Filled.Battery4Bar,
                            granted = batteryOptExempt,
                            onGrant = {
                                try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    )
                                } catch (_: Exception) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ── 2. MONITORED APP SCHEDULES ─────────────────────────────────────
            item {
                SectionHeader("MONITORED APP SCHEDULES")
            }

            if (monitoredPackages.isEmpty()) {
                item {
                    Text(
                        "No apps monitored yet. Add apps from the Home screen.",
                        style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.GrayMuted),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                items(monitoredPackages.toList()) { pkg ->
                    MonitoredAppBudgetRowCard(
                        pkg = pkg,
                        repo = repo,
                        allMonitoredPackages = monitoredPackages,
                        isCommitmentLockActive = isCommitmentLockActive,
                        commitmentLockUntilMillis = commitmentLockUntil,
                        onRemove = {
                            scope.launch {
                                repo.setMonitoredPackages(monitoredPackages - pkg)
                            }
                        }
                    )
                }
            }

            // ── 3. BLOCKING BEHAVIOR ───────────────────────────────────────────
            item {
                SectionHeader("BLOCKING BEHAVIOR")
            }

            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 18.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enforce Hourly Limits",
                                style = DesignTokens.Typography.subtitle().copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DesignTokens.Palette.PureWhite,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Instantly overlays lock screen when an app hits its hourly limit",
                                style = DesignTokens.Typography.bodySmall().copy(
                                    color = DesignTokens.Palette.GraySecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        MonochromeSwitch(
                            checked = blockingEnabled,
                            onCheckedChange = {
                                if (isCommitmentLockActive && !it) {
                                    return@MonochromeSwitch
                                }
                                scope.launch { repo.setBlockingEnabled(it) }
                            },
                            enabled = !(isCommitmentLockActive && blockingEnabled)
                        )
                    }

                    if (isCommitmentLockActive && blockingEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Commitment Lock active until ${formatLockUntil(commitmentLockUntil)}",
                            style = DesignTokens.Typography.bodySmall().copy(
                                color = DesignTokens.Palette.WarningAccent,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            item {
                SectionHeader("COMMITMENT LOCK")
            }

            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 18.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Commitment Lock",
                                style = DesignTokens.Typography.subtitle().copy(
                                    color = DesignTokens.Palette.PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isCommitmentLockActive) {
                                    "Locked until ${formatLockUntil(commitmentLockUntil)}"
                                } else {
                                    "Prevent loosening restrictions for 24h, 72h, or 7 days"
                                },
                                style = DesignTokens.Typography.bodySmall().copy(
                                    color = DesignTokens.Palette.GraySecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        MonochromeSwitch(
                            checked = isCommitmentLockActive,
                            onCheckedChange = { checked ->
                                if (!checked && isCommitmentLockActive) {
                                    return@MonochromeSwitch
                                }
                                if (checked && !isCommitmentLockActive) {
                                    showCommitmentDialog = true
                                }
                            },
                            enabled = !isCommitmentLockActive
                        )
                    }
                }
            }

            // ── 4. OFFLINE & PRIVACY MANIFESTO ─────────────────────────────────
            item {
                SectionHeader("OFFLINE & PRIVACY MANIFESTO")
            }

            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 18.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = DesignTokens.Palette.PureWhite)
                            Text(
                                "100% Offline & Private",
                                style = DesignTokens.Typography.subtitle().copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DesignTokens.Palette.PureWhite,
                                    fontSize = 15.sp
                                )
                            )
                        }
                        Text(
                            "• Zero network permissions — no data can leave your device.\n• Window content inspection disabled — cannot read text or passwords.\n• Never blocks emergency phone dialers, camera, or system settings.",
                            style = DesignTokens.Typography.bodySmall().copy(
                                color = DesignTokens.Palette.GraySecondary,
                                lineHeight = 19.sp,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // ── 5. DANGER ZONE ─────────────────────────────────────────────────
            item {
                SectionHeader("DANGER ZONE")
            }

            item {
                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorderSubtle,
                    contentPadding = 18.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Pause Blocking Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        if (isPaused) repo.clearPause() else repo.pauseForOneHour()
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.PauseCircle,
                                    contentDescription = null,
                                    tint = if (isPaused) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.GrayMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isPaused) "Resume Blocking" else "Pause for 1 Hour",
                                        style = DesignTokens.Typography.bodyMedium().copy(
                                            color = if (isPaused) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = if (isPaused) "Pause currently active" else "Temporarily disable lock enforcement",
                                        style = DesignTokens.Typography.bodySmall().copy(
                                            color = DesignTokens.Palette.GrayMuted,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = if (isPaused) "RESUME" else "PAUSE",
                                style = DesignTokens.Typography.caption().copy(
                                    color = if (isPaused) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        HorizontalDivider(color = DesignTokens.Palette.DarkBorderSubtle, thickness = 0.5.dp)

                        // Reset All Usage Data Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showResetDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = DesignTokens.Palette.GrayMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Reset Current Hour Counters",
                                        style = DesignTokens.Typography.bodyMedium().copy(
                                            color = DesignTokens.Palette.PureWhite,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "Clears accumulated seconds for this clock hour",
                                        style = DesignTokens.Typography.bodySmall().copy(
                                            color = DesignTokens.Palette.GrayMuted,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "RESET",
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GrayMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(DesignTokens.Spacing.xxl)) }
        }
    }

    // ── RESET CONFIRMATION DIALOG ──────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = DesignTokens.Palette.DarkCard,
            shape = DesignTokens.Shapes.Card,
            title = {
                Text(
                    "Reset Usage Counters?",
                    style = DesignTokens.Typography.title().copy(
                        fontWeight = FontWeight.Bold,
                        color = DesignTokens.Palette.PureWhite
                    )
                )
            },
            text = {
                Text(
                    "This will reset your current hour usage counters back to 0 for all monitored apps.",
                    style = DesignTokens.Typography.body().copy(color = DesignTokens.Palette.GraySecondary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.resetCurrentQuotaCounters(monitoredPackages)
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = DesignTokens.Palette.GrayMuted)
                }
            }
        )
    }

    if (showCommitmentDialog) {
        var selectedDuration by remember { mutableStateOf(24L) }
        var confirmationText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCommitmentDialog = false },
            containerColor = DesignTokens.Palette.DarkCard,
            shape = DesignTokens.Shapes.Card,
            title = {
                Text(
                    "Enable Commitment Lock",
                    style = DesignTokens.Typography.title().copy(
                        color = DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose duration and type I commit to confirm. You will not be able to loosen restrictions until it expires.",
                        style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.GraySecondary)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntervalChip("24h", selectedDuration == 24L) { selectedDuration = 24L }
                        IntervalChip("72h", selectedDuration == 72L) { selectedDuration = 72L }
                        IntervalChip("7 days", selectedDuration == 168L) { selectedDuration = 168L }
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = confirmationText,
                        onValueChange = { confirmationText = it },
                        label = { Text("Type: I commit") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.startCommitmentLock(selectedDuration * 60L * 60L * 1000L)
                            showCommitmentDialog = false
                        }
                    },
                    enabled = confirmationText.trim() == "I commit"
                ) {
                    Text("Lock In", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitmentDialog = false }) {
                    Text("Cancel", color = DesignTokens.Palette.GrayMuted)
                }
            }
        )
    }
}

// ─── PERMISSION ROW ITEM ───────────────────────────────────────────────────────

@Composable
private fun PermissionRowItem(
    label: String,
    description: String,
    icon: ImageVector,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onGrant() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Surface(
            modifier = Modifier.size(36.dp),
            shape = DesignTokens.Shapes.Pill,
            color = DesignTokens.Palette.DarkElevated
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = DesignTokens.Palette.PureWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Status Dot (the only place where green/red dot is allowed)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (granted) DesignTokens.Palette.StatusSuccess else DesignTokens.Palette.StatusError)
                )

                Text(
                    text = label,
                    style = DesignTokens.Typography.bodyMedium().copy(
                        fontWeight = FontWeight.SemiBold,
                        color = DesignTokens.Palette.PureWhite,
                        fontSize = 14.sp
                    )
                )
            }

            Text(
                text = description,
                style = DesignTokens.Typography.bodySmall().copy(
                    color = DesignTokens.Palette.GrayMuted,
                    fontSize = 11.sp
                )
            )
        }

        // Grant / Granted Pill
        Surface(
            shape = DesignTokens.Shapes.Chip,
            color = if (granted) DesignTokens.Palette.StatusSuccessMuted else DesignTokens.Palette.PureWhite,
            border = BorderStroke(1.dp, if (granted) DesignTokens.Palette.StatusSuccess.copy(alpha = 0.3f) else DesignTokens.Palette.PureWhite)
        ) {
            Text(
                text = if (granted) "GRANTED" else "GRANT",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                style = DesignTokens.Typography.caption().copy(
                    color = if (granted) DesignTokens.Palette.StatusSuccess else DesignTokens.Palette.PureBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
            )
        }
    }
}

// ─── MONITORED APP BUDGET CARD ─────────────────────────────────────────────────

@Composable
private fun MonitoredAppBudgetRowCard(
    pkg: String,
    repo: PrefsRepository,
    allMonitoredPackages: Set<String>,
    isCommitmentLockActive: Boolean,
    commitmentLockUntilMillis: Long,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheduleFlow = remember(pkg) { repo.scheduleFlow(pkg) }
    val schedule by scheduleFlow.collectAsState(initial = defaultSimpleSchedule())
    val appLabel = remember(pkg) { getAppLabel(context, pkg) }
    val checkInEnabledFlow = remember(pkg) { repo.sessionCheckInEnabledFlow(pkg) }
    val checkInEnabled by checkInEnabledFlow.collectAsState(initial = true)
    val checkInIntervalFlow = remember(pkg) { repo.sessionCheckInIntervalMinutesFlow(pkg) }
    val checkInIntervalMinutes by checkInIntervalFlow.collectAsState(initial = DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES)

    var showScheduleEditor by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }

    RoundedCard(
        shape = DesignTokens.Shapes.Card,
        containerColor = DesignTokens.Palette.DarkCard,
        borderColor = DesignTokens.Palette.DarkBorder,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = DesignTokens.Shapes.Badge,
                    color = DesignTokens.Palette.DarkElevated
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = appLabel.take(1).uppercase(),
                            style = DesignTokens.Typography.caption().copy(
                                fontWeight = FontWeight.Bold,
                                color = DesignTokens.Palette.PureWhite
                            )
                        )
                    }
                }

                Column {
                    Text(
                        text = appLabel,
                        style = DesignTokens.Typography.subtitle().copy(
                            fontWeight = FontWeight.Bold,
                            color = DesignTokens.Palette.PureWhite,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = "${schedule.size} block schedule",
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.GraySecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { showCopyDialog = true }) {
                    Icon(Icons.Filled.Security, contentDescription = "Copy schedule", tint = DesignTokens.Palette.GrayMuted, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        if (!isCommitmentLockActive) onRemove()
                    },
                    enabled = !isCommitmentLockActive
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = if (isCommitmentLockActive) DesignTokens.Palette.DarkBorderSubtle else DesignTokens.Palette.GrayMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        schedule.forEach { block ->
            Text(
                text = formatBlockLabel(block),
                style = DesignTokens.Typography.bodySmall().copy(
                    color = DesignTokens.Palette.GraySecondary,
                    fontSize = 12.sp
                )
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DesignTokens.Shapes.Button)
                .clickable { showScheduleEditor = true },
            shape = DesignTokens.Shapes.Button,
            color = DesignTokens.Palette.DarkElevated,
            border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorder)
        ) {
            Text(
                text = "Edit Schedule",
                modifier = Modifier.padding(vertical = 10.dp),
                style = DesignTokens.Typography.caption().copy(
                    color = DesignTokens.Palette.PureWhite,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DesignTokens.Palette.DarkBorderSubtle, thickness = 0.5.dp)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Session check-ins",
                    style = DesignTokens.Typography.bodyMedium().copy(
                        color = DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Gentle reminders during active sessions",
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 11.sp
                    )
                )
            }

            MonochromeSwitch(
                checked = checkInEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        repo.setSessionCheckInEnabled(pkg, enabled)
                    }
                }
            )
        }

        if (checkInEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10).forEach { optionMinutes ->
                    IntervalChip(
                        label = "$optionMinutes min",
                        selected = checkInIntervalMinutes == optionMinutes,
                        onClick = {
                            scope.launch {
                                repo.setSessionCheckInIntervalMinutes(pkg, optionMinutes)
                            }
                        }
                    )
                }
            }
        }

        if (isCommitmentLockActive) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Loosening actions are locked until ${formatLockUntil(commitmentLockUntilMillis)}",
                style = DesignTokens.Typography.bodySmall().copy(
                    color = DesignTokens.Palette.WarningAccent,
                    fontSize = 11.sp
                )
            )
        }
    }

    if (showScheduleEditor) {
        ScheduleEditorDialog(
            appLabel = appLabel,
            initialSchedule = schedule,
            isCommitmentLockActive = isCommitmentLockActive,
            commitmentLockUntilMillis = commitmentLockUntilMillis,
            onDismiss = { showScheduleEditor = false },
            validateSchedule = { blocks -> repo.validateSchedule(blocks) },
            onSave = { blocks ->
                scope.launch {
                    val result = repo.setScheduleForPackage(pkg, blocks)
                    if (result.isValid) {
                        showScheduleEditor = false
                    }
                }
            }
        )
    }

    if (showCopyDialog) {
        CopyScheduleDialog(
            sourceAppLabel = appLabel,
            candidatePackages = allMonitoredPackages - pkg,
            onDismiss = { showCopyDialog = false },
            onCopy = { targets ->
                scope.launch {
                    repo.copySchedule(pkg, targets)
                    showCopyDialog = false
                }
            }
        )
    }
}

@Composable
private fun IntervalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = DesignTokens.Shapes.Chip,
        color = if (selected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) DesignTokens.Palette.PureWhite else DesignTokens.Palette.DarkBorder
        ),
        modifier = Modifier.clip(DesignTokens.Shapes.Chip).clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = DesignTokens.Typography.caption().copy(
                color = if (selected) DesignTokens.Palette.PureBlack else DesignTokens.Palette.GraySecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun ScheduleEditorDialog(
    appLabel: String,
    initialSchedule: List<ScheduleBlock>,
    isCommitmentLockActive: Boolean,
    commitmentLockUntilMillis: Long,
    validateSchedule: (List<ScheduleBlock>) -> ScheduleValidationResult,
    onSave: (List<ScheduleBlock>) -> Unit,
    onDismiss: () -> Unit
) {
    var blocks by remember(initialSchedule) { mutableStateOf(initialSchedule.sortedBy { it.startMinuteOfDay }) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val validation = validateSchedule(blocks)
    val isTightening = isScheduleTighteningOrEqual(initialSchedule, blocks)
    val canSave = validation.isValid && (!isCommitmentLockActive || isTightening)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Text(
                text = "$appLabel Schedule",
                style = DesignTokens.Typography.title().copy(
                    color = DesignTokens.Palette.PureWhite,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntervalChip(label = "Simple", selected = false) {
                        blocks = defaultSimpleSchedule()
                    }
                    IntervalChip(label = "Work Focus", selected = false) {
                        blocks = workFocusPreset()
                    }
                    IntervalChip(label = "Weekend", selected = false) {
                        blocks = weekendPreset()
                    }
                }

                blocks.forEachIndexed { index, block ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DesignTokens.Shapes.Button)
                            .clickable { editingIndex = index }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatBlockLabel(block),
                            style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.PureWhite)
                        )

                        IconButton(
                            onClick = {
                                blocks = blocks.filterIndexed { i, _ -> i != index }
                            },
                            enabled = !isCommitmentLockActive
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete block",
                                tint = if (isCommitmentLockActive) DesignTokens.Palette.DarkBorderSubtle else DesignTokens.Palette.GrayMuted
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DesignTokens.Shapes.Button)
                        .clickable {
                            val targetIndex = blocks.indices.maxByOrNull { i ->
                                blocks[i].endMinuteOfDay - blocks[i].startMinuteOfDay
                            }
                            if (targetIndex != null) {
                                val target = blocks[targetIndex]
                                val length = target.endMinuteOfDay - target.startMinuteOfDay
                                if (length >= 2) {
                                    val midpoint = target.startMinuteOfDay + (length / 2)
                                    val first = target.copy(endMinuteOfDay = midpoint)
                                    val second = target.copy(startMinuteOfDay = midpoint)
                                    blocks = buildList {
                                        addAll(blocks.take(targetIndex))
                                        add(first)
                                        add(second)
                                        addAll(blocks.drop(targetIndex + 1))
                                    }
                                }
                            }
                        },
                    shape = DesignTokens.Shapes.Button,
                    color = DesignTokens.Palette.DarkElevated,
                    border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorder)
                ) {
                    Text(
                        text = "+ Add Block",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = DesignTokens.Typography.caption().copy(
                            color = DesignTokens.Palette.PureWhite,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                if (!validation.isValid) {
                    Text(
                        text = validation.errorMessage ?: "Invalid schedule",
                        style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.WarningAccent)
                    )
                } else if (isCommitmentLockActive && !isTightening) {
                    Text(
                        text = "Commitment Lock active until ${formatLockUntil(commitmentLockUntilMillis)}. Only stricter schedule changes are allowed.",
                        style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.WarningAccent)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(blocks) }, enabled = canSave) {
                Text("Save", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DesignTokens.Palette.GrayMuted)
            }
        }
    )

    editingIndex?.let { index ->
        val block = blocks.getOrNull(index)
        if (block != null) {
            EditBlockDialog(
                block = block,
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    blocks = blocks.mapIndexed { i, existing ->
                        if (i == index) updated else existing
                    }
                    editingIndex = null
                }
            )
        }
    }
}

@Composable
private fun EditBlockDialog(
    block: ScheduleBlock,
    onSave: (ScheduleBlock) -> Unit,
    onDismiss: () -> Unit
) {
    var startText by remember(block) { mutableStateOf(toTimeInput(block.startMinuteOfDay)) }
    var endText by remember(block) { mutableStateOf(toTimeInput(block.endMinuteOfDay)) }
    var limitText by remember(block) { mutableStateOf(block.limitMinutes.toString()) }
    var ruleType by remember(block) { mutableStateOf(block.ruleType) }

    val parsedStart = parseTimeInput(startText)
    val parsedEnd = parseTimeInput(endText)
    val parsedLimit = limitText.toIntOrNull()
    val isValid = parsedStart != null && parsedEnd != null && parsedStart < parsedEnd && parsedLimit != null && parsedLimit >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Text(
                "Edit Block",
                style = DesignTokens.Typography.subtitle().copy(color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("Start (HH:MM)") },
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("End (HH:MM)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntervalChip(label = "Hourly", selected = ruleType == ScheduleRuleType.HOURLY_QUOTA) {
                        ruleType = ScheduleRuleType.HOURLY_QUOTA
                    }
                    IntervalChip(label = "Flat", selected = ruleType == ScheduleRuleType.FLAT_ALLOWANCE) {
                        ruleType = ScheduleRuleType.FLAT_ALLOWANCE
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Limit (minutes)") },
                    singleLine = true
                )
                if (!isValid) {
                    Text(
                        "Use valid times and non-negative minutes.",
                        style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.WarningAccent)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScheduleBlock(
                            startMinuteOfDay = parsedStart!!,
                            endMinuteOfDay = parsedEnd!!,
                            ruleType = ruleType,
                            limitMinutes = parsedLimit!!
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Apply", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DesignTokens.Palette.GrayMuted)
            }
        }
    )
}

@Composable
private fun CopyScheduleDialog(
    sourceAppLabel: String,
    candidatePackages: Set<String>,
    onCopy: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selected by remember(candidatePackages) { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Text(
                "Copy schedule from $sourceAppLabel",
                style = DesignTokens.Typography.subtitle().copy(color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (candidatePackages.isEmpty()) {
                    Text(
                        "No other monitored apps available.",
                        style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.GrayMuted)
                    )
                }
                candidatePackages.forEach { targetPkg ->
                    val label = getAppLabel(context, targetPkg)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DesignTokens.Shapes.Button)
                            .clickable {
                                selected = if (targetPkg in selected) selected - targetPkg else selected + targetPkg
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = DesignTokens.Typography.bodySmall().copy(color = DesignTokens.Palette.PureWhite)
                        )
                        MonochromeSwitch(
                            checked = targetPkg in selected,
                            onCheckedChange = {
                                selected = if (it) selected + targetPkg else selected - targetPkg
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(selected) }, enabled = selected.isNotEmpty()) {
                Text("Copy", color = DesignTokens.Palette.PureWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DesignTokens.Palette.GrayMuted)
            }
        }
    )
}

private fun parseTimeInput(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..24) return null
    if (minute !in 0..59) return null
    if (hour == 24 && minute != 0) return null
    return (hour * 60 + minute).coerceIn(0, 24 * 60)
}

private fun toTimeInput(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, 24 * 60)
    val hour = m / 60
    val minute = m % 60
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

// ─── SECTION HEADER ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = DesignTokens.Typography.caption().copy(
            color = DesignTokens.Palette.GrayMuted,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

private fun isScheduleTighteningOrEqual(
    original: List<ScheduleBlock>,
    candidate: List<ScheduleBlock>
): Boolean {
    val originalByMinute = scheduleStrictnessByMinute(original)
    val candidateByMinute = scheduleStrictnessByMinute(candidate)
    if (originalByMinute.size != 24 * 60 || candidateByMinute.size != 24 * 60) return false

    for (minute in 0 until 24 * 60) {
        if (candidateByMinute[minute] > originalByMinute[minute]) {
            return false
        }
    }
    return true
}

private fun scheduleStrictnessByMinute(blocks: List<ScheduleBlock>): FloatArray {
    val result = FloatArray(24 * 60) { Float.POSITIVE_INFINITY }
    for (block in blocks) {
        val durationMinutes = (block.endMinuteOfDay - block.startMinuteOfDay).coerceAtLeast(1)
        val score = when (block.ruleType) {
            ScheduleRuleType.HOURLY_QUOTA -> block.limitMinutes.toFloat()
            ScheduleRuleType.FLAT_ALLOWANCE -> (block.limitMinutes * 60f) / durationMinutes.toFloat()
        }
        val start = block.startMinuteOfDay.coerceIn(0, 24 * 60)
        val end = block.endMinuteOfDay.coerceIn(0, 24 * 60)
        for (m in start until end) {
            result[m] = score
        }
    }
    return result
}

