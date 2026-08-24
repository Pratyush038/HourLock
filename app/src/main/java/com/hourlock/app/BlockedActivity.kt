package com.hourlock.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.ui.theme.AccentOrange
import com.hourlock.app.ui.theme.AccentRed
import com.hourlock.app.ui.theme.DarkBackground
import com.hourlock.app.ui.theme.DarkBorder
import com.hourlock.app.ui.theme.DarkBorderSubtle
import com.hourlock.app.ui.theme.DarkSurfaceCard
import com.hourlock.app.ui.theme.DarkSurfaceElevated
import com.hourlock.app.ui.theme.HourLockTheme
import com.hourlock.app.ui.theme.PureBlack
import com.hourlock.app.ui.theme.PureWhite
import com.hourlock.app.ui.theme.TextMutedDark
import com.hourlock.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BlockedActivity
 * ────────────────
 * Minimalist, high-contrast lock screen overlay displayed when an app's
 * hourly quota is reached.
 */
class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Unknown App"
        setContent {
            HourLockTheme(darkTheme = true) {
                BlockedScreen(
                    blockedPackage = pkg,
                    onGoHome = { navigateHome() },
                    onEmergencyAccess = { handleEmergencyAccess(pkg) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Unknown App"
        setContent {
            HourLockTheme(darkTheme = true) {
                BlockedScreen(
                    blockedPackage = pkg,
                    onGoHome = { navigateHome() },
                    onEmergencyAccess = { handleEmergencyAccess(pkg) }
                )
            }
        }
    }

    private fun navigateHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun handleEmergencyAccess(pkg: String) {
        finish()
    }
}

// ─── COMPOSE UI ────────────────────────────────────────────────────────────────

private const val UNLOCK_PHRASE = "I need a break"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(
    blockedPackage: String,
    onGoHome: () -> Unit,
    onEmergencyAccess: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { PrefsRepository(context) }
    val scope = rememberCoroutineScope()
    val appLabel = remember(blockedPackage) { getAppLabel(context, blockedPackage) }

    // ── Countdown to next hour ─────────────────────────────────────────────
    var secondsUntilUnlock by remember { mutableIntStateOf(0) }
    var nextHourFormatted by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("h:00 a", Locale.getDefault())
        while (true) {
            val nextHourMillis = repo.nextHourStartMillis()
            nextHourFormatted = sdf.format(Date(nextHourMillis))
            secondsUntilUnlock = ((nextHourMillis - System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L).toInt()
            delay(1000L)
        }
    }

    val unlockMode by repo.unlockModeFlow.collectAsState(initial = "none")

    // UI state: "blocked" | "challenge" | "waiting" | "granted"
    var screenState by remember { mutableStateOf("blocked") }
    var phraseInput by remember { mutableStateOf("") }
    var waitProgress by remember { mutableStateOf(0f) }
    var waitSecondsLeft by remember { mutableIntStateOf(30) }
    var phraseError by remember { mutableStateOf(false) }

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockPulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Lock Icon Container
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale),
                    shape = CircleShape,
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = PureWhite,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Blocked App Pill Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkBorderSubtle)
                ) {
                    Text(
                        text = appLabel.uppercase(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Hour Limit Reached",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = "Take a breath. You've reached your screen budget for this clock hour.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(Modifier.height(24.dp))

                // Countdown Pill Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "NEXT RESET AT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMutedDark,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                nextHourFormatted,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            )
                        }

                        // Time remaining
                        val minutesLeft = secondsUntilUnlock / 60
                        val secondsLeft = secondsUntilUnlock % 60
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkBorderSubtle)
                        ) {
                            Text(
                                text = String.format("%02d:%02d", minutesLeft, secondsLeft),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Primary Return Home Button
                Button(
                    onClick = onGoHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            "Return to Home",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Secondary Emergency Access Trigger
                when (screenState) {
                    "blocked" -> {
                        TextButton(
                            onClick = {
                                when (unlockMode) {
                                    "none" -> {
                                        scope.launch {
                                            repo.grantEmergencyAccess(blockedPackage)
                                            onEmergencyAccess()
                                        }
                                    }
                                    "phrase" -> screenState = "challenge"
                                    "wait" -> {
                                        screenState = "waiting"
                                        scope.launch {
                                            for (i in 30 downTo 0) {
                                                waitSecondsLeft = i
                                                waitProgress = (30 - i) / 30f
                                                delay(1000L)
                                            }
                                            repo.grantEmergencyAccess(blockedPackage)
                                            onEmergencyAccess()
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                "Emergency 2-min access",
                                color = TextMutedDark,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }

                    "waiting" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { waitProgress },
                                color = PureWhite,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "Unlocking in ${waitSecondsLeft}s...",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                            )
                        }
                    }

                    "challenge" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Type \"$UNLOCK_PHRASE\" to unlock:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                            )
                            OutlinedTextField(
                                value = phraseInput,
                                onValueChange = {
                                    phraseInput = it
                                    phraseError = false
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated,
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite,
                                    focusedIndicatorColor = PureWhite,
                                    unfocusedIndicatorColor = DarkBorder
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (phraseInput.trim().equals(UNLOCK_PHRASE, ignoreCase = true)) {
                                        scope.launch {
                                            repo.grantEmergencyAccess(blockedPackage)
                                            onEmergencyAccess()
                                        }
                                    } else {
                                        phraseError = true
                                    }
                                })
                            )

                            if (phraseError) {
                                Text("Phrase doesn't match.", color = AccentRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
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
