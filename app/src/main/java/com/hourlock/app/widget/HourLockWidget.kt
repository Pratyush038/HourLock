package com.hourlock.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hourlock.app.DEFAULT_LIMIT_MINUTES
import com.hourlock.app.MainActivity
import com.hourlock.app.PrefsRepository
import kotlinx.coroutines.flow.first

/**
 * HourLockWidget
 * ──────────────
 * Jetpack Glance Home Screen Widget.
 * Displays:
 *  - Real-time remaining minutes this clock hour
 *  - Monochrome card aesthetic matching app DesignTokens
 *  - Tapping opens the app directly
 */
class HourLockWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = PrefsRepository(context)
        val monitored = repo.getMonitoredPackages()
        val isEnabled = repo.blockingEnabledFlow.first()
        val pauseUntil = repo.pauseUntilFlow.first()
        val isPaused = System.currentTimeMillis() < pauseUntil

        var maxLimit = DEFAULT_LIMIT_MINUTES
        var maxUsed = 0

        for (pkg in monitored) {
            val limit = repo.getLimitSeconds(pkg) / 60
            val used = repo.getUsedSeconds(pkg) / 60
            if (limit > maxLimit) maxLimit = limit
            if (used > maxUsed) maxUsed = used
        }

        val remainingMins = (maxLimit - maxUsed).coerceAtLeast(0)
        val isBlocked = (maxUsed >= maxLimit) && isEnabled && !isPaused

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
                    .cornerRadius(20.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header Row with App Tag
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HOURLOCK",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF8E8E93)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = if (isBlocked) "LOCKED" else if (isPaused) "PAUSED" else "ACTIVE",
                            style = TextStyle(
                                color = ColorProvider(if (isBlocked) Color(0xFFD97706) else Color(0xFFFFFFFF)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(GlanceModifier.height(6.dp))

                    // Large Remaining Minutes Number
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (isBlocked) "0" else "$remainingMins",
                            style = TextStyle(
                                color = ColorProvider(if (isBlocked) Color(0xFFD97706) else Color(0xFFFFFFFF)),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "min left",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFA1A1AA)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(GlanceModifier.height(2.dp))

                    Text(
                        text = "Resets at top of hour",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF6B6B6B)),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
