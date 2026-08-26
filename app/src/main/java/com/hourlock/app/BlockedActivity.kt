package com.hourlock.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hourlock.app.ui.components.RingProgress
import com.hourlock.app.ui.components.RoundedCard
import com.hourlock.app.ui.theme.DesignTokens
import com.hourlock.app.ui.theme.HourLockTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Monitored App"
        setContent {
            HourLockTheme(darkTheme = true) {
                BlockedScreen(
                    blockedPackage = pkg,
                    onGoHome = { navigateHome() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Monitored App"
        setContent {
            HourLockTheme(darkTheme = true) {
                BlockedScreen(
                    blockedPackage = pkg,
                    onGoHome = { navigateHome() }
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
}

@Composable
fun BlockedScreen(
    blockedPackage: String,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repo = remember { PrefsRepository(context) }
    val appLabel = remember(blockedPackage) { getAppLabel(context, blockedPackage) }

    var secondsUntilUnlock by remember { mutableIntStateOf(0) }
    var unlockTimeFormatted by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(blockedPackage) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Exception) {
        }

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        while (true) {
            val status = repo.getCurrentLimitStatus(blockedPackage)
            val now = System.currentTimeMillis()
            val unlockMillis = status.unlockAtMillis
            unlockTimeFormatted = timeFormat.format(Date(unlockMillis))
            secondsUntilUnlock = ((unlockMillis - now) / 1000L).coerceAtLeast(0L).toInt()

            val windowDuration = (unlockMillis - status.activeBlock.blockStartMillis).coerceAtLeast(1L)
            val remaining = (unlockMillis - now).coerceAtLeast(0L)
            progressFraction = (remaining.toFloat() / windowDuration.toFloat()).coerceIn(0f, 1f)
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockPulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DesignTokens.Palette.DarkBackground
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
                RingProgress(
                    progress = progressFraction,
                    size = 130.dp,
                    strokeWidth = 8.dp,
                    trackColor = DesignTokens.Palette.DarkElevated,
                    progressColor = DesignTokens.Palette.WarningAccent,
                    warningColor = DesignTokens.Palette.WarningAccent,
                    isWarningOrBlocked = true,
                    content = {
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulseScale),
                            shape = CircleShape,
                            color = DesignTokens.Palette.DarkCard,
                            border = BorderStroke(1.dp, DesignTokens.Palette.WarningAccentBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = DesignTokens.Palette.WarningAccent,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.height(28.dp))

                Surface(
                    shape = DesignTokens.Shapes.Pill,
                    color = DesignTokens.Palette.DarkElevated,
                    border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorder)
                ) {
                    Text(
                        text = appLabel.uppercase(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = DesignTokens.Typography.caption().copy(
                            color = DesignTokens.Palette.PureWhite,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Usage Locked",
                    style = DesignTokens.Typography.display().copy(
                        fontWeight = FontWeight.Bold,
                        color = DesignTokens.Palette.PureWhite,
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp
                    )
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Current schedule block has no remaining allowance.",
                    style = DesignTokens.Typography.body().copy(
                        color = DesignTokens.Palette.GraySecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(28.dp))

                RoundedCard(
                    shape = DesignTokens.Shapes.Card,
                    containerColor = DesignTokens.Palette.DarkCard,
                    borderColor = DesignTokens.Palette.DarkBorder,
                    contentPadding = 20.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "UNLOCKS AT",
                                style = DesignTokens.Typography.caption().copy(
                                    color = DesignTokens.Palette.GrayMuted,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 10.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                unlockTimeFormatted,
                                style = DesignTokens.Typography.title().copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DesignTokens.Palette.PureWhite,
                                    fontSize = 20.sp
                                )
                            )
                        }

                        val minutesLeft = secondsUntilUnlock / 60
                        val secondsLeft = secondsUntilUnlock % 60
                        Surface(
                            shape = DesignTokens.Shapes.Button,
                            color = DesignTokens.Palette.DarkElevated,
                            border = BorderStroke(1.dp, DesignTokens.Palette.DarkBorderSubtle)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", minutesLeft, secondsLeft),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = DesignTokens.Typography.monospacedNumber(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ).copy(color = DesignTokens.Palette.PureWhite)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = {
                        try {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } catch (_: Exception) {
                        }
                        onGoHome()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = DesignTokens.Shapes.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.Palette.PureWhite,
                        contentColor = DesignTokens.Palette.PureBlack
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            "Return to Home",
                            style = DesignTokens.Typography.subtitle().copy(
                                fontWeight = FontWeight.Bold,
                                color = DesignTokens.Palette.PureBlack
                            )
                        )
                    }
                }
            }
        }
    }
}

