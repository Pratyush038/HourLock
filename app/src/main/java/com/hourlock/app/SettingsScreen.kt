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
    val isPaused = System.currentTimeMillis() < pauseUntil

    var a11yGranted by remember { mutableStateOf(false) }
    var usageGranted by remember { mutableStateOf(false) }
    var batteryOptExempt by remember { mutableStateOf(false) }

    var showResetDialog by remember { mutableStateOf(false) }

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

            // ── 2. MONITORED APPS BUDGETS ──────────────────────────────────────
            item {
                SectionHeader("MONITORED APPS BUDGETS")
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
                            onCheckedChange = { scope.launch { repo.setBlockingEnabled(it) } }
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
                            for (pkg in monitoredPackages) {
                                repo.setLimitMinutes(pkg, repo.getLimitSeconds(pkg) / 60)
                            }
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
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limitMinFlow = remember(pkg) { repo.limitMinutesFlow(pkg) }
    val limitMin by limitMinFlow.collectAsState(initial = DEFAULT_LIMIT_MINUTES)
    val appLabel = remember(pkg) { getAppLabel(context, pkg) }

    var sliderValue by remember(limitMin) { mutableFloatStateOf(limitMin.toFloat()) }

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
                        text = "${sliderValue.toInt()} min / hour budget",
                        style = DesignTokens.Typography.bodySmall().copy(
                            color = DesignTokens.Palette.GraySecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = DesignTokens.Palette.GrayMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        MonochromeSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                scope.launch {
                    repo.setLimitMinutes(pkg, sliderValue.toInt())
                }
            },
            valueRange = 1f..60f,
            steps = 58
        )
    }
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

private fun getAppLabel(context: Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
