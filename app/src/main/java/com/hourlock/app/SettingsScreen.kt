package com.hourlock.app

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SettingsScreen
 * ──────────────
 * Three logical sections:
 *  1. Permissions — live status for Accessibility, Usage Access, Battery Optimization,
 *     Notification permission; each with a direct deep link to the system settings page.
 *  2. App Management — list of monitored apps with per-app minute slider,
 *     add-app dialog showing installed apps, remove button.
 *  3. Safety controls — Pause for 1h, Disable HourLock entirely, Unlock challenge mode.
 *
 * Note: We deliberately do NOT show a global "Disable" button that removes the
 * a11y service — we instead guide the user to the system settings for that.
 * This prevents HourLock from being a one-tap bypass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    // ── Observe state ──────────────────────────────────────────────────────
    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))
    val blockingEnabled by repo.blockingEnabledFlow.collectAsState(initial = true)
    val pauseUntil by repo.pauseUntilFlow.collectAsState(initial = 0L)
    val unlockMode by repo.unlockModeFlow.collectAsState(initial = "none")
    val isPaused = System.currentTimeMillis() < pauseUntil

    // ── Permission states (re-checked every 2s) ────────────────────────────
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

    // ── Add-app dialog ─────────────────────────────────────────────────────
    var showAddDialog by remember { mutableStateOf(false) }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A0A1A), Color(0xFF0F0A20), Color(0xFF080815))
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ── SECTION: Permissions ──────────────────────────────────────
                item {
                    SectionHeader("Permissions")
                }

                item {
                    PermissionRow(
                        label = "Accessibility Service",
                        description = "Required to detect app switches in real time",
                        icon = Icons.Filled.Lock,
                        granted = a11yGranted,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            )
                        }
                    )
                }

                item {
                    PermissionRow(
                        label = "Usage Access",
                        description = "Required for today's total usage stat",
                        icon = Icons.Filled.Timer,
                        granted = usageGranted,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            )
                        }
                    )
                }

                item {
                    PermissionRow(
                        label = "Battery Optimization Exempt",
                        description = "Prevents Samsung/Xiaomi from killing HourLock",
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

                item { Spacer(Modifier.height(8.dp)) }

                // ── SECTION: Monitored Apps ────────────────────────────────────
                item {
                    SectionHeader("Monitored Apps")
                }

                items(monitoredPackages.toList()) { pkg ->
                    MonitoredAppRow(
                        pkg = pkg,
                        repo = repo,
                        onRemove = {
                            scope.launch {
                                val updated = monitoredPackages - pkg
                                repo.setMonitoredPackages(updated)
                            }
                        }
                    )
                }

                item {
                    // Add app button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16102E)),
                        onClick = { showAddDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2D1B69)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFBB86FC))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Add app to monitor",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFFBB86FC),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }

                // ── SECTION: Unlock Challenge ──────────────────────────────────
                item {
                    SectionHeader("Emergency Unlock Mode")
                }

                item {
                    UnlockModeSelector(
                        current = unlockMode,
                        onSelect = { scope.launch { repo.setUnlockMode(it) } }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }

                // ── SECTION: Safety Controls ───────────────────────────────────
                item {
                    SectionHeader("Safety Controls")
                }

                item {
                    SafetyControlCard(
                        icon = Icons.Filled.Pause,
                        title = if (isPaused) "Resume blocking now" else "Pause blocking for 1 hour",
                        subtitle = if (isPaused) "Blocking is currently paused" else "Take a guilt-free break",
                        tint = Color(0xFFFFA726),
                        onClick = {
                            scope.launch {
                                if (isPaused) repo.clearPause() else repo.pauseForOneHour()
                            }
                        }
                    )
                }

                item {
                    SafetyControlCard(
                        icon = if (blockingEnabled) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        title = if (blockingEnabled) "Disable HourLock entirely" else "Re-enable HourLock",
                        subtitle = if (blockingEnabled) "All blocking suspended until you re-enable"
                        else "Blocking is currently off",
                        tint = if (blockingEnabled) Color(0xFFEF5350) else Color(0xFF4CAF50),
                        onClick = {
                            scope.launch { repo.setBlockingEnabled(!blockingEnabled) }
                        }
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }

        // ── Add App Dialog ─────────────────────────────────────────────────────
        if (showAddDialog) {
            AddAppDialog(
                existingPackages = monitoredPackages,
                onAdd = { pkg ->
                    scope.launch {
                        repo.setMonitoredPackages(monitoredPackages + pkg)
                    }
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

// ── Component: Permission Row ───────────────────────────────────────────────────

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16102E)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0xFF1B3A1B) else Color(0xFF3A1B1B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF4CAF50) else Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9B7FCA))
                )
            }
            Spacer(Modifier.width(8.dp))
            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (granted) Color(0xFF1B3A1B) else Color(0xFF3A1B1B)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (granted) "Granted" else "Required",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (granted) Color(0xFF4CAF50) else Color(0xFFEF5350),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ── Component: Monitored App Row with slider ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitoredAppRow(
    pkg: String,
    repo: PrefsRepository,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limitFlow = repo.limitMinutesFlow(pkg).collectAsState(initial = DEFAULT_LIMIT_MINUTES)
    var sliderValue by remember(limitFlow.value) { mutableFloatStateOf(limitFlow.value.toFloat()) }
    val label = getAppLabelPublic(context, pkg)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16102E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF6B4FA8),
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = "${sliderValue.toInt()} min/h",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFFBB86FC),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.width(8.dp))
                // Expand toggle
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.Close else Icons.Filled.Settings,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Color(0xFF9B7FCA),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Limit: ${sliderValue.toInt()} minutes per hour",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9B7FCA))
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            scope.launch { repo.setLimitMinutes(pkg, sliderValue.toInt()) }
                        },
                        valueRange = 1f..60f,
                        steps = 58, // 1-minute steps (60 - 1 - 1 = 58 interior steps)
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C3AED),
                            activeTrackColor = Color(0xFF7C3AED),
                            inactiveTrackColor = Color(0xFF2D1B69)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 min", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B4FA8)))
                        Text("60 min", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B4FA8)))
                    }
                }
            }
        }
    }
}

// ── Component: Unlock mode selector ────────────────────────────────────────────

@Composable
private fun UnlockModeSelector(current: String, onSelect: (String) -> Unit) {
    val modes = listOf(
        Triple("none", "No challenge", "Emergency access granted immediately"),
        Triple("phrase", "Type a phrase", "Must type \"I need a break\" to unlock 2 min"),
        Triple("wait", "Wait 30 seconds", "Breathing exercise before 2-min access")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16102E))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            modes.forEach { (id, title, subtitle) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(id) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = current == id,
                        onClick = { onSelect(id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF7C3AED),
                            unselectedColor = Color(0xFF4A2D82)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9B7FCA))
                        )
                    }
                }
            }
        }
    }
}

// ── Component: Safety control card ─────────────────────────────────────────────

@Composable
private fun SafetyControlCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16102E)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = tint,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9B7FCA))
                )
            }
        }
    }
}

// ── Component: Add App Dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppDialog(
    existingPackages: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // Enumerate installed apps (excluding system apps and never-block packages)
    val installedApps = remember {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            packages
                .filter { info ->
                    // Include only user-installed or user-visible apps
                    (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0
                            || info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
                        && info.packageName !in NEVER_BLOCK_PACKAGES
                        && info.packageName != "com.hourlock.app"
                }
                .map { info ->
                    val label = try { pm.getApplicationLabel(info).toString() } catch (e: Exception) { info.packageName }
                    Pair(label, info.packageName)
                }
                .sortedBy { it.first.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val filtered = installedApps.filter { (label, pkg) ->
        searchQuery.isBlank() ||
            label.contains(searchQuery, ignoreCase = true) ||
            pkg.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1030),
        title = {
            Text(
                "Add App to Monitor",
                style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps…", color = Color(0xFF6B4FA8)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF16102E),
                        unfocusedContainerColor = Color(0xFF16102E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color(0xFF7C3AED),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(filtered) { (label, pkg) ->
                        val alreadyAdded = pkg in existingPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyAdded) { onAdd(pkg) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (alreadyAdded) Color(0xFF6B4FA8) else Color.White
                                    )
                                )
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF4A2D82),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            if (alreadyAdded) {
                                Text(
                                    "Added",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF7C3AED)
                                    )
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E1040), thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF7C3AED))
            }
        }
    )
}

// ── Section Header ──────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            color = Color(0xFF6B4FA8),
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}
