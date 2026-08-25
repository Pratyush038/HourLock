package com.hourlock.app.data

/**
 * UsageLogEntry
 * ─────────────
 * Represents a single hourly snapshot of app usage and limit enforcement.
 * Persisted in rolling log (capped to 30 days).
 */
data class UsageLogEntry(
    val date: String,         // Format: "yyyy-MM-dd" (e.g. "2026-08-25")
    val hour: Int,            // 0..23
    val packageName: String,
    val usedSeconds: Int,
    val wasBlocked: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/** Daily total aggregate for weekly chart */
data class DailyUsage(
    val dayOfWeek: String,    // "Mon", "Tue", "Wed", etc.
    val date: String,         // "yyyy-MM-dd"
    val totalMinutes: Int,
    val isCurrentDay: Boolean
)

/** Hourly intensity for 24-hour heatmap */
data class HourlyIntensity(
    val hour: Int,            // 0..23
    val hourLabel: String,    // "00", "01", ..., "23" or "12 AM", etc.
    val usedMinutes: Int,
    val intensity: Float      // 0.0f (none) to 1.0f (maximum usage in day)
)

/** Day compliance for 14-day streak calendar dots */
data class DayCompliance(
    val date: String,
    val dayLabel: String,
    val stayedUnderLimit: Boolean
)

/** Streak summary */
data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val recent14Days: List<DayCompliance>
)

/** Computed high-level insight sentence */
data class InsightSummary(
    val headline: String,
    val detail: String
)
