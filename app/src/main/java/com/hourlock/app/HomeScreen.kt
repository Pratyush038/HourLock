package com.hourlock.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * HomeScreen
 * ──────────
 * The main dashboard. Shows:
 *  1. Permission status banners (a11y + usage access) with quick-open links.
 *  2. Per-monitored-app progress ring (used / limit this hour).
 *  3. Master on/off toggle.
 *  4. Today's total usage for the primary app.
 *
 * The ring updates every second via a LaunchedEffect ticker so the user
 * sees live progress without the complexity of a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()

    // ── Observe live state ─────────────────────────────────────────────────
    val monitoredPackages by repo.monitoredPackagesFlow.collectAsState(initial = setOf("com.instagram.android"))
    val blockingEnabled by repo.blockingEnabledFlow.collectAsState(initial = true)
    val pauseUntil by repo.pauseUntilFlow.collectAsState(initial = 0L)
    val isPaused = System.currentTimeMillis() < pauseUntil

    // ── Permission checks (re-evaluated on every recomposition / resume) ───
    val isA11yGranted = remember { mutableStateOf(false) }
    val isUsageGranted = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isA11yGranted.value = isAccessibilityServiceEnabled(context)
            isUsageGranted.value = isUsageAccessGranted(context)
            delay(2000L) // Recheck every 2s (user might grant mid-session)
        }
    }

    // ── Primary app for the big ring ───────────────────────────────────────
    val primaryPkg = monitoredPackages.firstOrNull() ?: "com.instagram.android"
    var usedSeconds by remember { mutableIntStateOf(0) }
    var limitSeconds by remember { mutableIntStateOf(DEFAULT_LIMIT_MINUTES * 60) }
    var todaySeconds by remember { mutableLongStateOf(0L) }

    // Refresh every second — keeps ring live
    LaunchedEffect(primaryPkg) {
        while (true) {
            usedSeconds = repo.getUsedSeconds(primaryPkg)
            limitSeconds = repo.getLimitSeconds(primaryPkg)
            todaySeconds = repo.getTodayTotalSeconds(primaryPkg, context)
            delay(1000L)
        }
    }

    val progress = if (limitSeconds > 0) (usedSeconds.toFloat() / limitSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800),
        label = "ringProgress"
    )
    val appLabel = getAppLabelPublic(context, primaryPkg)
    val usedMin = usedSeconds / 60
    val usedSec = usedSeconds % 60
    val limitMin = limitSeconds / 60

    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0A0A1A), Color(0xFF0F0A20), Color(0xFF080815))
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "HourLock",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Permission warnings ─────────────────────────────────────────
                if (!isA11yGranted.value) {
                    PermissionBanner(
                        icon = Icons.Filled.Warning,
                        message = "Accessibility permission needed — HourLock can't detect app switches",
                        actionLabel = "Enable",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── Master toggle card ──────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16102E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Blocking",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = when {
                                    !blockingEnabled -> "Off"
                                    isPaused -> "Paused for 1 hour"
                                    else -> "Active"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = when {
                                        !blockingEnabled -> Color(0xFF9B7FCA)
                                        isPaused -> Color(0xFFFFA726)
                                        else -> Color(0xFF4CAF50)
                                    }
                                )
                            )
                        }
                        Switch(
                            checked = blockingEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { repo.setBlockingEnabled(enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF7C3AED),
                                uncheckedThumbColor = Color(0xFF9B7FCA),
                                uncheckedTrackColor = Color(0xFF2D1B69)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Usage ring ──────────────────────────────────────────────────
                Text(
                    text = "This Hour",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFF9B7FCA),
                        letterSpacing = 2.sp
                    )
                )

                Spacer(Modifier.height(20.dp))

                Box(contentAlignment = Alignment.Center) {
                    // Outer glow ring (decorative)
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(220.dp),
                        strokeWidth = 4.dp,
                        color = Color(0xFF2D1B69),
                        trackColor = Color.Transparent,
                    )
                    // Live progress ring
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(220.dp),
                        strokeWidth = 14.dp,
                        color = if (progress >= 0.9f) Color(0xFFEF5350) else Color(0xFF7C3AED),
                        trackColor = Color(0xFF1E1040),
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFFBB86FC),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "%d:%02d".format(usedMin, usedSec),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "of $limitMin min",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF9B7FCA)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Today stat ──────────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF120D28))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2D1B69)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = Color(0xFFBB86FC),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Today's total",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF9B7FCA)
                                )
                            )
                            Text(
                                text = formatDuration(todaySeconds),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF6B4FA8)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Settings shortcut ───────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF120D28)),
                    onClick = onNavigateToSettings
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2D1B69)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = Color(0xFFBB86FC),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Settings & Permissions",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(Modifier.weight(1f))
                        Text("›", color = Color(0xFF6B4FA8), fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

// ── Reusable permission banner ──────────────────────────────────────────────────

@Composable
fun PermissionBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1B00)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFFA726)),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFFFFA726),
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// ── Permission helpers ──────────────────────────────────────────────────────────

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
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            now - 86_400_000L,
            now
        )
        // If we get a non-empty result, permission is granted
        !stats.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }
}

// ── Formatting helpers ──────────────────────────────────────────────────────────

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

fun getAppLabelPublic(context: Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.')
    }
}
