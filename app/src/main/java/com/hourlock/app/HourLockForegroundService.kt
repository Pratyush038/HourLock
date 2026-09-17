package com.hourlock.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * HourLockForegroundService
 * ──────────────────────────
 * A persistent foreground service whose primary job is to KEEP THE PROCESS
 * ALIVE on OEM-modified Android builds (Samsung, Xiaomi, Oppo, etc.) that
 * aggressively kill background processes and AccessibilityServices.
 *
 * WHY A FOREGROUND SERVICE?
 * Android's AccessibilityService documentation recommends pairing it with a
 * foreground service when you need reliable long-running operation. Without it:
 *  - Samsung's "Sleeping apps" feature can kill the process within minutes.
 *  - The a11y service silently stops receiving events without any error.
 *  - The user loses blocking protection without knowing it.
 *
 * NOTIFICATION DESIGN:
 *  - LOW_IMPORTANCE channel: no sound, no heads-up, no badge.
 *  - Tapping the notification opens the MainActivity.
 *  - The notification text is deliberately minimal ("protecting your time").
 *
 * LIFECYCLE:
 *  - START_STICKY: if the OS kills the service for memory, it's restarted
 *    automatically. onStartCommand is called again with a null Intent; we
 *    handle this gracefully.
 *  - State (usedSeconds) is already persisted in DataStore on every tick by
 *    UsageTrackerService, so restart after kill loses at most 1 second of data.
 *  - If Accessibility is disabled, this service falls back to Usage Access
 *    polling so payment apps that reject accessibility services can still work.
 */
class HourLockForegroundService : Service() {

    companion object {
        private const val TAG = "HourLock.FgService"
        private const val POLL_INTERVAL_MS = 1_000L
        const val CHANNEL_ID = "hourlock_protection"
        const val NOTIFICATION_ID = 1001
        const val BLOCKING_CHANNEL_ID = "hourlock_blocking"
        const val BLOCKING_NOTIFICATION_ID = 1002
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: PrefsRepository
    private lateinit var analyticsRepo: com.hourlock.app.data.UsageLogRepository
    private var pollingJob: Job? = null
    private var trackedPkg: String? = null
    private var lastPollTimeMillis: Long = 0L
    private var wasAccessibilityEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "HourLockForegroundService created")
        repo = PrefsRepository(applicationContext)
        analyticsRepo = com.hourlock.app.data.UsageLogRepository(applicationContext)
        createNotificationChannel()
        createBlockingNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent may be null when the service is restarted after being killed
        // (START_STICKY behavior). We handle null gracefully.
        Log.i(TAG, "onStartCommand called (intent=${intent?.action ?: "null — restarted by OS"})")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                buildNotification(), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        startUsageAccessFallback()
        // START_STICKY: the OS will restart this service if killed. When
        // restarted, onStartCommand is called with intent=null. The
        // AccessibilityService (if still running) will call startForegroundService
        // again on its next event, ensuring everything stays in sync.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't support binding — this is a started service only.
        return null
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "HourLockForegroundService destroyed")
    }

    // ── Usage Access fallback ─────────────────────────────────────────────

    private fun startUsageAccessFallback() {
        if (pollingJob?.isActive == true) return

        pollingJob = serviceScope.launch {
            lastPollTimeMillis = System.currentTimeMillis() - 3_600_000L
            while (true) {
                try {
                    pollUsageAccessOnce()
                } catch (e: Exception) {
                    Log.e(TAG, "Usage Access fallback poll failed", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollUsageAccessOnce() {
        if (isAccessibilityServiceEnabled(applicationContext)) {
            stopFallbackTracking("accessibility service active")
            lastPollTimeMillis = System.currentTimeMillis()
            wasAccessibilityEnabled = true
            return
        }

        val now = System.currentTimeMillis()
        val fromMillis = if (wasAccessibilityEnabled) now - 3_600_000L else lastPollTimeMillis
        wasAccessibilityEnabled = false

        val foregroundPkg = findForegroundPackageSince(fromMillis, now)
        lastPollTimeMillis = now

        if (foregroundPkg == null || foregroundPkg in TRANSIENT_SYSTEM_PACKAGES || foregroundPkg in NEVER_BLOCK_PACKAGES) {
            if (foregroundPkg != null) stopFallbackTracking("safe or transient package: $foregroundPkg")
            return
        }

        val monitoredPackages = repo.getMonitoredPackages()
        if (foregroundPkg !in monitoredPackages) {
            stopFallbackTracking("non-monitored package: $foregroundPkg")
            return
        }

        if (trackedPkg != foregroundPkg) {
            stopFallbackTracking("switching to $foregroundPkg")
            trackedPkg = foregroundPkg
            checkInitialLimit(foregroundPkg)
            return
        }

        tickForPackage(foregroundPkg)
    }

    private fun findForegroundPackageSince(fromMillis: Long, toMillis: Long): String? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return trackedPkg
        val events = usm.queryEvents(fromMillis.coerceAtMost(toMillis), toMillis)
        val event = UsageEvents.Event()
        var activePkg = trackedPkg

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> activePkg = pkg
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (pkg == activePkg) activePkg = null
                }
            }
        }

        val isScreenOn = (getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: true
        return if (isScreenOn) activePkg else null
    }

    private suspend fun checkInitialLimit(pkg: String) {
        if (!repo.blockingEnabledFlow.first()) return
        if (System.currentTimeMillis() < repo.pauseUntilFlow.first()) return

        val used = repo.getUsedSeconds(pkg)
        val limitSec = repo.getLimitSeconds(pkg)
        if (used >= limitSec) {
            launchBlockedActivity(pkg)
            stopFallbackTracking("limit reached on fallback launch")
        }
    }

    private suspend fun tickForPackage(pkg: String) {
        if (pkg !in repo.getMonitoredPackages()) {
            stopFallbackTracking("$pkg removed from monitored list")
            return
        }

        val used = repo.incrementUsedSeconds(pkg)
        val limitSec = repo.getLimitSeconds(pkg)

        if (!repo.blockingEnabledFlow.first()) return
        if (System.currentTimeMillis() < repo.pauseUntilFlow.first()) return

        if (used >= limitSec) {
            try {
                analyticsRepo.logHourSnapshot(pkg, used, wasBlocked = true)
            } catch (_: Exception) {}
            launchBlockedActivity(pkg)
            stopFallbackTracking("limit reached for $pkg")
        }
    }

    private fun stopFallbackTracking(reason: String) {
        if (trackedPkg != null) {
            Log.d(TAG, "Stopping Usage Access fallback tracking. Reason: $reason")
        }
        trackedPkg = null
    }

    /**
     * Launches BlockedActivity using a multi-strategy approach:
     *  1. Full-screen intent notification (works like incoming calls — primary method)
     *  2. SYSTEM_ALERT_WINDOW direct startActivity (if user granted overlay permission)
     *  3. Direct startActivity fallback (works on older Android versions)
     *
     * This replaces the old approach that only used startActivity(), which Android 10+
     * silently blocks from background services. The full-screen intent mechanism is
     * the approved pattern and does NOT require Accessibility Service permission,
     * so BHIM and other payment apps continue to work normally.
     */
    private fun launchBlockedActivity(pkg: String) {
        val blockedIntent = Intent(applicationContext, BlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        }

        // Strategy 1: Full-screen intent notification
        // This is the primary mechanism. Android shows the activity immediately when
        // the screen is on, or on the lock screen if off — exactly like an incoming call.
        try {
            val fullScreenPendingIntent = PendingIntent.getActivity(
                applicationContext,
                BLOCKING_NOTIFICATION_ID,
                blockedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val appLabel = getAppLabel(applicationContext, pkg)

            val notification = NotificationCompat.Builder(applicationContext, BLOCKING_CHANNEL_ID)
                .setContentTitle("⏱ Time's up")
                .setContentText("$appLabel has reached its hourly limit")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(BLOCKING_NOTIFICATION_ID, notification)
            Log.i(TAG, "Posted full-screen intent notification for $pkg")
        } catch (e: Exception) {
            Log.e(TAG, "Full-screen intent notification failed for $pkg", e)
        }

        // Strategy 2: Direct startActivity — works if SYSTEM_ALERT_WINDOW is granted
        // or on older Android versions (pre-10). Harmless no-op if blocked by the OS.
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                Settings.canDrawOverlays(applicationContext)) {
                applicationContext.startActivity(blockedIntent)
                Log.i(TAG, "Direct startActivity succeeded for $pkg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed for $pkg (expected on Android 10+)", e)
        }
    }

    /**
     * Cancels the blocking notification. Called when:
     *  - The user navigates away from the blocked app
     *  - The hour resets and usage is below the limit again
     *  - BlockedActivity itself launches and takes over
     */
    fun cancelBlockingNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(BLOCKING_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel blocking notification", e)
        }
    }

    // ── Notification ───────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HourLock Protection",
                // IMPORTANCE_LOW: silent, no heads-up display, no badge dot.
                // This is the most unobtrusive level that still allows a
                // persistent foreground service notification.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while HourLock is actively protecting your screen time"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Creates the high-importance notification channel for blocking notifications.
     * IMPORTANCE_HIGH is required for full-screen intents to launch the activity.
     */
    private fun createBlockingNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BLOCKING_CHANNEL_ID,
                "App Blocking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows when a monitored app has exceeded its time limit"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Tapping the notification opens MainActivity on the Home screen.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HourLock")
            .setContentText("Protecting your screen time \uD83D\uDD12")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)           // Cannot be dismissed by the user
            .setShowWhen(false)         // Don't show timestamp — cleaner look
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
