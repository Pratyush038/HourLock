package com.hourlock.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SettingsScreen
 * ──────────────
 * Minimalist monochromatic settings:
 *  1. Permissions status (Accessibility, Usage Access, Battery, Notifications)
 *  2. Monitored apps with individual limit sliders
 *  3. Emergency unlock challenge configuration
 *  4. Privacy & Safety manifesto
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))
    val unlockMode by repo.unlockModeFlow.collectAsState(initial = "none")

    var a11yGranted by remember { mutableStateOf(false) }
    var usageGranted by remember { mutableStateOf(false) }
    var batteryOptExempt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            a11yGranted = isAccessibilityServiceEnabled(context)
            usageGranted = isUsageAccessGranted(context)
            batteryOptExempt = try {
                val pm = context.getSystemService(PowerManager::class.java)
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) { false }
            delay(2000L)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── PERMISSIONS SECTION ────────────────────────────────────────────
            item {
                SectionTitle("SYSTEM PERMISSIONS")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionItem(
                            label = "Accessibility Service",
                            description = "Real-time app detection",
                            icon = Icons.Filled.Lock,
                            granted = a11yGranted,
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            }
                        )

                        PermissionItem(
                            label = "Usage Access",
                            description = "Daily screen time aggregation",
                            icon = Icons.Filled.Timer,
                            granted = usageGranted,
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            }
                        )

                        PermissionItem(
                            label = "Battery Exemption",
                            description = "Prevents background process kill",
                            icon = Icons.Filled.Battery4Bar,
                            granted = batteryOptExempt,
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    )
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${context.packageName}")
                                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                        )
                                    } catch (e2: Exception) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ── MONITORED APPS BUDGET ──────────────────────────────────────────
            item {
                SectionTitle("HOURLY BUDGETS")
            }

            items(monitoredPackages.toList()) { pkg ->
                MonitoredAppBudgetCard(
                    pkg = pkg,
                    repo = repo,
                    onRemove = {
                        scope.launch {
                            repo.setMonitoredPackages(monitoredPackages - pkg)
                        }
                    }
                )
            }

            // ── EMERGENCY UNLOCK CHALLENGE ─────────────────────────────────────
            item {
                SectionTitle("EMERGENCY UNLOCK MODE")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UnlockModeOption(
                            title = "No challenge",
                            description = "Instant 2-minute emergency unlock",
                            selected = unlockMode == "none",
                            onSelect = { scope.launch { repo.setUnlockMode("none") } }
                        )

                        UnlockModeOption(
                            title = "30s Breathing timer",
                            description = "Requires a 30-second mindful wait",
                            selected = unlockMode == "wait",
                            onSelect = { scope.launch { repo.setUnlockMode("wait") } }
                        )

                        UnlockModeOption(
                            title = "Type phrase challenge",
                            description = "Requires typing 'I need a break'",
                            selected = unlockMode == "phrase",
                            onSelect = { scope.launch { repo.setUnlockMode("phrase") } }
                        )
                    }
                }
            }

            // ── PRIVACY & SAFETY CARD ──────────────────────────────────────────
            item {
                SectionTitle("SAFETY & OFFLINE PRIVACY")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = PureWhite)
                            Text(
                                "100% Offline & Private",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            )
                        }
                        Text(
                            "• Zero network permissions — no data can leave your device.\n• Window content inspection disabled — cannot read text or passwords.\n• Never blocks emergency dialers, camera, or system settings.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondaryDark,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── PERMISSION ITEM ───────────────────────────────────────────────────────────

@Composable
private fun PermissionItem(
    label: String,
    description: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkBorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (granted) PureBlack else AccentOrange.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (granted) PureWhite else AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMutedDark)
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (granted) AccentGreen.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (granted) AccentGreen.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f))
            ) {
                Text(
                    text = if (granted) "GRANTED" else "REQUIRED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (granted) AccentGreen else AccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

// ─── MONITORED APP BUDGET CARD ─────────────────────────────────────────────────

@Composable
private fun MonitoredAppBudgetCard(
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
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceElevated
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = appLabel.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            )
                        }
                    }

                    Column {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )
                        Text(
                            text = "${sliderValue.toInt()} min / hour",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextMutedDark)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    scope.launch {
                        repo.setLimitMinutes(pkg, sliderValue.toInt())
                    }
                },
                valueRange = 1f..60f,
                steps = 58,
                colors = SliderDefaults.colors(
                    thumbColor = PureWhite,
                    activeTrackColor = PureWhite,
                    inactiveTrackColor = DarkSurfaceElevated
                )
            )
        }
    }
}

// ─── UNLOCK MODE OPTION ────────────────────────────────────────────────────────

@Composable
private fun UnlockModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() },
        color = if (selected) DarkSurfaceElevated else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) PureWhite.copy(alpha = 0.3f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = PureWhite,
                    unselectedColor = TextMutedDark
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondaryDark)
                )
            }
        }
    }
}

// ─── SECTION TITLE ─────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            color = TextMutedDark,
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
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
