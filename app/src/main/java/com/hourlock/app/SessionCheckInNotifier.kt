package com.hourlock.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Lightweight informational notifications for active app sessions.
 * These notifications never enforce blocking; they only prompt reflection.
 */
class SessionCheckInNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "hourlock_session_checkins"
        private const val CHANNEL_NAME = "Session Check-ins"
        private const val CHANNEL_DESCRIPTION = "Gentle reminders during active app sessions"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    fun notifySessionCheckIn(packageName: String, appLabel: String, elapsedSessionMinutes: Int) {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$elapsedSessionMinutes min on $appLabel")
            .setContentText("Want to take a break?")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(notificationIdForPackage(packageName), notification)
    }

    fun cancelForPackage(packageName: String) {
        notificationManager.cancel(notificationIdForPackage(packageName))
    }

    private fun notificationIdForPackage(packageName: String): Int {
        return 20_000 + packageName.hashCode().absoluteValue % 10_000
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(channel)
    }
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this
