package com.hourlock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.ui.theme.HourLockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * BlockedActivity
 * ────────────────
 * Shown as a full-screen overlay when the user has exhausted their hourly
 * quota for a monitored app.
 *
 * INTENT CONTRACT:
 *  - Must be started with FLAG_ACTIVITY_NEW_TASK (done by UsageTrackerService).
 *  - Receives the blocked package name via [EXTRA_BLOCKED_PACKAGE].
 *
 * DESIGN NOTES:
 *  - launchMode="singleInstance" in the manifest ensures only one instance
 *    exists at a time; subsequent startActivity calls update the existing one
 *    via onNewIntent.
 *  - The "Take me home" button navigates to the launcher without finishing
 *    this activity first, then finishes — ensuring the monitored app is
 *    pushed behind the home screen.
 *  - Emergency access is grantEmergencyAccess() on the repo, which decrements
 *    usedSeconds by 120 so the timer (in UsageTrackerService) naturally resumes
 *    and will re-block after those 2 minutes.
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
            HourLockTheme {
                BlockedScreen(
                    blockedPackage = pkg,
                    onGoHome = { navigateHome() },
                    onEmergencyAccess = { handleEmergencyAccess(pkg) }
                )
            }
        }
    }

    // Handle the case where a new block event fires while this activity is
    // already showing (singleInstance means onNewIntent is called instead of onCreate)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Unknown App"
        setContent {
            HourLockTheme {
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
        // Just finish — UsageTrackerService will pick up the next foreground
        // event from the app and start ticking again from the decremented value.
        // The repo.grantEmergencyAccess call is done in the Compose screen's
        // coroutine scope so the UI can show a loading state.
        finish()
    }
}

// ── Compose UI ─────────────────────────────────────────────────────────────────

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

    // ── Countdown to next hour ─────────────────────────────────────────────
    var secondsUntilUnlock by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            val nextHour = repo.nextHourStartMillis()
            secondsUntilUnlock = ((nextHour - System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L).toInt()
            delay(1000L)
        }
    }

    // ── Unlock mode ────────────────────────────────────────────────────────
    val unlockMode by repo.unlockModeFlow.collectAsState(initial = "none")

    // UI state machine: "blocked" | "challenge" | "waiting" | "granted"
    var screenState by remember { mutableStateOf("blocked") }
    var phraseInput by remember { mutableStateOf("") }
    var waitProgress by remember { mutableStateOf(0f) }
    var waitSecondsLeft by remember { mutableIntStateOf(30) }
    var phraseError by remember { mutableStateOf(false) }

    // ── Pulsing animation for the lock icon ────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockPulse"
    )

    // ── Wait screen countdown ──────────────────────────────────────────────
    LaunchedEffect(screenState) {
        if (screenState == "waiting") {
            waitSecondsLeft = 30
            waitProgress = 0f
            repeat(30) { i ->
                delay(1000L)
                waitSecondsLeft = 29 - i
                waitProgress = (i + 1) / 30f
            }
            // After 30s — grant emergency access
            scope.launch {
                repo.grantEmergencyAccess(blockedPackage)
                onEmergencyAccess()
            }
        }
    }

    // ── Background gradient ────────────────────────────────────────────────
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0A0A1A),
            Color(0xFF0D0D2B),
            Color(0xFF100820),
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        when (screenState) {
            "blocked" -> BlockedContent(
                blockedPackage = blockedPackage,
                secondsUntilUnlock = secondsUntilUnlock,
                pulseScale = pulseScale,
                unlockMode = unlockMode,
                onGoHome = onGoHome,
                onTryEmergency = {
                    screenState = when (unlockMode) {
                        "phrase" -> "challenge"
                        "wait" -> "waiting"
                        else -> { // "none" — grant immediately
                            scope.launch { repo.grantEmergencyAccess(blockedPackage) }
                            onEmergencyAccess()
                            "blocked"
                        }
                    }
                }
            )

            "challenge" -> PhraseChallenge(
                input = phraseInput,
                error = phraseError,
                onInputChange = { phraseInput = it; phraseError = false },
                onSubmit = {
                    if (phraseInput.trim().equals(UNLOCK_PHRASE, ignoreCase = true)) {
                        scope.launch {
                            repo.grantEmergencyAccess(blockedPackage)
                            onEmergencyAccess()
                        }
                    } else {
                        phraseError = true
                    }
                },
                onCancel = { screenState = "blocked" }
            )

            "waiting" -> BreathingWaitScreen(
                progress = waitProgress,
                secondsLeft = waitSecondsLeft
            )
        }
    }
}

// ── Sub-screens ────────────────────────────────────────────────────────────────

@Composable
private fun BlockedContent(
    blockedPackage: String,
    secondsUntilUnlock: Int,
    pulseScale: Float,
    unlockMode: String,
    onGoHome: () -> Unit,
    onTryEmergency: () -> Unit
) {
    val appLabel = getAppLabel(LocalContext.current, blockedPackage)
    val mm = secondsUntilUnlock / 60
    val ss = secondsUntilUnlock % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Lock icon ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── App name ───────────────────────────────────────────────────────
        Text(
            text = appLabel,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 28.sp
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "is locked for this hour",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color(0xFFBB86FC),
                fontSize = 16.sp
            )
        )

        Spacer(Modifier.height(40.dp))

        // ── Countdown card ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1040))
                .border(1.dp, Color(0xFF7C3AED), RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = Color(0xFFBB86FC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Unlocks in",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFFBB86FC)
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "%02d:%02d".format(mm, ss),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 52.sp,
                        letterSpacing = 4.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "at the top of the next hour",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF9B7FCA)
                    )
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        // ── Take me home button ────────────────────────────────────────────
        Button(
            onClick = onGoHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C3AED)
            )
        ) {
            Icon(Icons.Filled.Home, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Take me home",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Emergency access (only if unlock mode is set) ──────────────────
        OutlinedButton(
            onClick = onTryEmergency,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF9B7FCA)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A2D82))
        ) {
            Text(
                text = when (unlockMode) {
                    "phrase" -> "Unlock with a phrase (+2 min)"
                    "wait" -> "Wait 30 seconds (+2 min)"
                    else -> "Emergency access (+2 min)"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhraseChallenge(
    input: String,
    error: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Type to unlock",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Type the phrase to confirm intentional use:",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFBB86FC)),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "\"$UNLOCK_PHRASE\"",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7C3AED),
                fontSize = 20.sp
            )
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text("Your phrase") },
            isError = error,
            supportingText = if (error) {
                { Text("Phrase doesn't match. Try again.") }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1040),
                unfocusedContainerColor = Color(0xFF1E1040),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color(0xFF7C3AED),
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
        ) {
            Text("Unlock", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Cancel", color = Color(0xFF9B7FCA))
        }
    }
}

@Composable
private fun BreathingWaitScreen(progress: Float, secondsLeft: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900),
        label = "waitProgress"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Take a breath",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Wait 30 seconds to confirm you really need it.",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFBB86FC)),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(140.dp),
                strokeWidth = 8.dp,
                color = Color(0xFF7C3AED),
                trackColor = Color(0xFF2D1B69),
                strokeCap = StrokeCap.Round,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$secondsLeft",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "seconds",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9B7FCA))
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Breathe in… breathe out…",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color(0xFF7C3AED),
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun getAppLabel(context: android.content.Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.')
    }
}
