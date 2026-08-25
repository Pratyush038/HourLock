package com.hourlock.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * HourLockWidgetReceiver
 * ──────────────────────
 * BroadcastReceiver responsible for providing the GlanceAppWidget instance
 * and handling widget update broadcasts.
 */
class HourLockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourLockWidget()
}
