package com.hourlock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo

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
 *  - We do NOT duplicate timers here — the AccessibilityService owns the timer.
 *    This service is purely a "keep-alive" vessel.
 */
class HourLockForegroundService : Service() {

    companion object {
        private const val TAG = "HourLock.FgService"
        const val CHANNEL_ID = "hourlock_protection"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "HourLockForegroundService created")
        createNotificationChannel()
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
        super.onDestroy()
        Log.i(TAG, "HourLockForegroundService destroyed")
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
