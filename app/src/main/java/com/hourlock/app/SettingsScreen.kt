package com.hourlock.app

import android.app.TimePickerDialog
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
import androidx.compose.runtime.mutableIntStateOf
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
                    val defaultLimit = inferDefaultLimitMinutes(schedule)
                    val customWindowCount = scheduleOverrides(schedule, defaultLimit).size
                    Text(
                        text = if (customWindowCount == 0) {
                            "Default ${defaultLimit} min/hour all day"
                        } else {
                            "Default ${defaultLimit} min/hour + $customWindowCount custom window${if (customWindowCount == 1) "" else "s"}"
                        },
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

        val defaultLimit = inferDefaultLimitMinutes(schedule)
        Text(
            text = "Default: ${defaultLimit} min/hour",
            style = DesignTokens.Typography.bodySmall().copy(
                color = DesignTokens.Palette.PureWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
        Spacer(Modifier.height(4.dp))

        val customWindows = scheduleOverrides(schedule, defaultLimit)
        if (customWindows.isEmpty()) {
            Text(
                text = "No custom windows. This app uses the default limit all day.",
                style = DesignTokens.Typography.bodySmall().copy(
                    color = DesignTokens.Palette.GraySecondary,
                    fontSize = 12.sp
                )
            )
        } else {
            customWindows.forEach { window ->
                val block = ScheduleBlock(
                    startMinuteOfDay = window.startMinuteOfDay,
                    endMinuteOfDay = window.endMinuteOfDay,
                    ruleType = window.ruleType,
                    limitMinutes = window.limitMinutes
                )
                Text(
                    text = formatBlockLabel(block),
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = DesignTokens.Palette.GraySecondary,
                        fontSize = 12.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
            }
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
                text = "Edit Limits",
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
    onSave: (List<ScheduleBlock>) -> Unit,
    onDismiss: () -> Unit
) {
    val initialDefaultLimit = remember(initialSchedule) { inferDefaultLimitMinutes(initialSchedule) }
    var defaultLimitMinutes by remember(initialSchedule) { mutableIntStateOf(initialDefaultLimit) }
    var customWindows by remember(initialSchedule) {
        mutableStateOf(scheduleOverrides(initialSchedule, initialDefaultLimit))
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val conflictMessage = scheduleOverrideConflictMessage(customWindows)
    val candidateSchedule = buildScheduleFromDefault(defaultLimitMinutes, customWindows)
    val isTightening = isScheduleTighteningOrEqual(initialSchedule, candidateSchedule)
    val canSave = conflictMessage == null && (!isCommitmentLockActive || isTightening)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Column {
                Text(
                    text = "$appLabel Schedule",
                    style = DesignTokens.Typography.title().copy(
                        color = DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Set a simple default, then add exceptions only when needed",
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 12.sp
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "DEFAULT LIMIT",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                com.hourlock.app.ui.components.LimitSelector(
                    limitMinutes = defaultLimitMinutes,
                    onLimitMinutesChanged = { defaultLimitMinutes = it }
                )

                HorizontalDivider(color = DesignTokens.Palette.DarkBorderSubtle, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Custom Windows",
                            style = DesignTokens.Typography.bodyMedium().copy(
                                color = DesignTokens.Palette.PureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            "Everything else keeps the default automatically",
                            style = DesignTokens.Typography.bodySmall().copy(
                                color = DesignTokens.Palette.GrayMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (customWindows.isEmpty()) {
                    Text(
                        "No custom windows. The default limit applies all day.",
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.GraySecondary,
                            fontSize = 12.sp
                        )
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height((customWindows.size * 58).coerceIn(64, 190).dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(customWindows.size) { index ->
                            CustomWindowRow(
                                window = customWindows[index],
                                onEdit = { editingIndex = index },
                                onDelete = {
                                    customWindows = customWindows.filterIndexed { i, _ -> i != index }
                                },
                                deleteEnabled = !isCommitmentLockActive
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DesignTokens.Shapes.Button)
                        .clickable {
                            val start = nextSuggestedOverrideStart(customWindows)
                            val end = (start + 60).coerceAtMost(24 * 60)
                            customWindows = customWindows + ScheduleOverride(
                                startMinuteOfDay = start,
                                endMinuteOfDay = end,
                                ruleType = ScheduleRuleType.HOURLY_QUOTA,
                                limitMinutes = 0
                            )
                            editingIndex = customWindows.lastIndex
                        },
                    shape = DesignTokens.Shapes.Button,
                    color = DesignTokens.Palette.PureWhite,
                    contentColor = DesignTokens.Palette.PureBlack
                ) {
                    Text(
                        text = "+ Add Custom Window",
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = DesignTokens.Typography.caption().copy(
                            color = DesignTokens.Palette.PureBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                if (conflictMessage != null) {
                    Text(
                        text = conflictMessage,
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.WarningAccent,
                            fontSize = 11.sp
                        )
                    )
                } else if (isCommitmentLockActive && !isTightening) {
                    Text(
                        text = "Commitment Lock active until ${formatLockUntil(commitmentLockUntilMillis)}. Only stricter schedule changes are allowed.",
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.WarningAccent,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(candidateSchedule) }, enabled = canSave) {
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
        val window = customWindows.getOrNull(index)
        if (window != null) {
            EditCustomWindowDialog(
                window = window,
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    customWindows = customWindows.mapIndexed { i, existing ->
                        if (i == index) updated else existing
                    }
                    editingIndex = null
                }
            )
        }
    }
}

@Composable
private fun CustomWindowRow(
    window: ScheduleOverride,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean
) {
    val ruleText = when (window.ruleType) {
        ScheduleRuleType.HOURLY_QUOTA -> if (window.limitMinutes == 0) "Locked" else "${window.limitMinutes} min/hour"
        ScheduleRuleType.FLAT_ALLOWANCE -> if (window.limitMinutes == 0) "Locked" else "${window.limitMinutes} min total"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DesignTokens.Shapes.Button)
            .clickable { onEdit() },
        shape = DesignTokens.Shapes.Button,
        color = DesignTokens.Palette.DarkElevated,
        border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${formatMinuteOfDayCasual(window.startMinuteOfDay)} - ${formatMinuteOfDayCasual(window.endMinuteOfDay)}",
                    style = DesignTokens.Typography.bodyMedium().copy(
                        color = DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
                Text(
                    ruleText,
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = if (window.limitMinutes == 0) DesignTokens.Palette.WarningAccent else DesignTokens.Palette.GraySecondary,
                        fontSize = 11.sp
                    )
                )
            }

            IconButton(
                onClick = onDelete,
                enabled = deleteEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete custom window",
                    tint = if (deleteEnabled) DesignTokens.Palette.GrayMuted else DesignTokens.Palette.DarkBorderSubtle,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EditCustomWindowDialog(
    window: ScheduleOverride,
    onSave: (ScheduleOverride) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var startMinute by remember(window) { mutableIntStateOf(window.startMinuteOfDay) }
    var endMinute by remember(window) { mutableIntStateOf(window.endMinuteOfDay) }
    var ruleType by remember(window) { mutableStateOf(window.ruleType) }
    var limitMinutes by remember(window) { mutableIntStateOf(window.limitMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Palette.DarkCard,
        shape = DesignTokens.Shapes.Card,
        title = {
            Column {
                Text(
                    "Custom Window",
                    style = DesignTokens.Typography.subtitle().copy(
                        color = DesignTokens.Palette.PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
                Text(
                    "${formatMinuteOfDayCasual(startMinute)} - ${formatMinuteOfDayCasual(endMinute)}",
                    style = DesignTokens.Typography.bodySmall().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 12.sp
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "WHEN DOES THIS APPLY?",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeChoiceButton(
                        label = "From",
                        timeText = formatMinuteOfDayCasual(startMinute),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showNativeTimePicker(
                                context = context,
                                minuteOfDay = startMinute,
                                minMinute = 0,
                                maxMinute = (endMinute - 5).coerceAtLeast(0),
                                onSelected = { selected -> startMinute = selected }
                            )
                        }
                    )
                    TimeChoiceButton(
                        label = "Until",
                        timeText = formatMinuteOfDayCasual(endMinute),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showNativeTimePicker(
                                context = context,
                                minuteOfDay = endMinute,
                                minMinute = (startMinute + 5).coerceAtMost(24 * 60),
                                maxMinute = 24 * 60,
                                allowMidnight = true,
                                onSelected = { selected -> endMinute = selected }
                            )
                        }
                    )
                }

                Text(
                    "LIMIT FOR THIS WINDOW",
                    style = DesignTokens.Typography.caption().copy(
                        color = DesignTokens.Palette.GrayMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntervalChip(label = "Per hour", selected = ruleType == ScheduleRuleType.HOURLY_QUOTA) {
                        ruleType = ScheduleRuleType.HOURLY_QUOTA
                    }
                    IntervalChip(label = "Total", selected = ruleType == ScheduleRuleType.FLAT_ALLOWANCE) {
                        ruleType = ScheduleRuleType.FLAT_ALLOWANCE
                    }
                }
                com.hourlock.app.ui.components.LimitSelector(
                    limitMinutes = limitMinutes,
                    onLimitMinutesChanged = { limitMinutes = it }
                )

            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScheduleOverride(
                            startMinuteOfDay = startMinute,
                            endMinuteOfDay = endMinute,
                            ruleType = ruleType,
                            limitMinutes = limitMinutes
                        )
                    )
                },
                enabled = endMinute > startMinute
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
private fun TimeChoiceButton(
    label: String,
    timeText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(DesignTokens.Shapes.Button)
            .clickable(onClick = onClick),
        shape = DesignTokens.Shapes.Button,
        color = DesignTokens.Palette.DarkElevated,
        border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = DesignTokens.Typography.caption().copy(
                    color = DesignTokens.Palette.GrayMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                timeText,
                style = DesignTokens.Typography.bodyMedium().copy(
                    color = DesignTokens.Palette.PureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }
    }
}

private fun showNativeTimePicker(
    context: Context,
    minuteOfDay: Int,
    minMinute: Int,
    maxMinute: Int,
    allowMidnight: Boolean = false,
    onSelected: (Int) -> Unit
) {
    val pickerMinute = minuteOfDay
        .coerceIn(minMinute, maxMinute)
        .let { if (allowMidnight && it == 24 * 60) 0 else it }
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val rawSelected = hour * 60 + minute
            val selected = if (allowMidnight && rawSelected == 0) {
                24 * 60
            } else {
                rawSelected.coerceIn(minMinute, maxMinute)
            }
            onSelected(selected)
        },
        pickerMinute / 60,
        pickerMinute % 60,
        false
    ).show()
}

private fun nextSuggestedOverrideStart(windows: List<ScheduleOverride>): Int {
    val latestEnd = windows.maxOfOrNull { it.endMinuteOfDay } ?: (9 * 60)
    return latestEnd.coerceAtMost(23 * 60)
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
                                selected = if (targetPkg in selected) selected.minus(targetPkg) else selected.plus(targetPkg)
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
                                selected = if (it) selected.plus(targetPkg) else selected.minus(targetPkg)
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
