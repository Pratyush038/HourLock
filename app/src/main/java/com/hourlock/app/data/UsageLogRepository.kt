package com.hourlock.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hourlock.app.DEFAULT_LIMIT_MINUTES
import com.hourlock.app.TRANSIENT_SYSTEM_PACKAGES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Context.analyticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hourlock_analytics")

val KEY_USAGE_LOGS_JSON = stringPreferencesKey("usage_logs_json")

class UsageLogRepository(private val context: Context) {

    private val dataStore = context.analyticsDataStore
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    companion object {
        private const val MAX_DAYS_TO_KEEP = 30
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val SCREEN_NON_INTERACTIVE = 16
        private const val KEYGUARD_SHOWN = 17
        private const val DEVICE_SHUTDOWN = 26
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US)

    val allLogsFlow: Flow<List<UsageLogEntry>> = dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_USAGE_LOGS_JSON] ?: "[]"
        parseEntriesFromJson(jsonStr)
    }

    /**
     * Records an hourly snapshot or breach event.
     */
    suspend fun logHourSnapshot(
        packageName: String,
        usedSeconds: Int,
        wasBlocked: Boolean
    ) = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        val dateStr = dateFormat.format(cal.time)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val now = System.currentTimeMillis()
        val cutoff = now - (MAX_DAYS_TO_KEEP * MILLIS_PER_DAY)

        val newEntry = UsageLogEntry(
            date = dateStr,
            hour = hour,
            packageName = packageName,
            usedSeconds = usedSeconds,
            wasBlocked = wasBlocked,
            timestamp = now
        )

        dataStore.edit { prefs ->
            val existing = parseEntriesFromJson(prefs[KEY_USAGE_LOGS_JSON] ?: "[]")
            val filtered = existing.filter { entry ->
                entry.timestamp >= cutoff && !(entry.date == dateStr && entry.hour == hour && entry.packageName == packageName)
            }
            prefs[KEY_USAGE_LOGS_JSON] = serializeEntriesToJson(filtered + newEntry)
        }
    }

    /**
     * Monday-to-Sunday weekly usage breakdown matching Digital Wellbeing.
     */
    suspend fun getWeeklyUsage(monitoredPackages: Set<String>): List<DailyUsage> = withContext(Dispatchers.IO) {
        val todayCal = Calendar.getInstance()
        val todayDateStr = dateFormat.format(todayCal.time)

        // Current week Monday 00:00:00
        val cal = Calendar.getInstance().apply {
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayLabelFormat = SimpleDateFormat("d", Locale.US)
        val list = mutableListOf<DailyUsage>()
        val now = System.currentTimeMillis()

        repeat(7) {
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + MILLIS_PER_DAY
            val dayDate = dateFormat.format(cal.time)
            val dayOfWeekStr = dayOfWeekFormat.format(cal.time)
            val dayNumStr = dayLabelFormat.format(cal.time)

            val isFuture = dayStart > now
            val minutes = if (isFuture || monitoredPackages.isEmpty()) {
                0
            } else {
                val rangeEnd = minOf(dayEnd, now)
                val seconds = getUsageSecondsInRange(dayStart, rangeEnd, monitoredPackages)
                (seconds / 60L).toInt()
            }

            list += DailyUsage(
                dayOfWeek = dayOfWeekStr,
                dayLabel = dayNumStr,
                date = dayDate,
                totalMinutes = minutes,
                isCurrentDay = (dayDate == todayDateStr),
                isFutureDay = isFuture
            )

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        list
    }

    /**
     * 24-Hour Heatmap Pacing for [dateStr].
     */
    suspend fun getHourlyHeatmap(
        dateStr: String,
        monitoredPackages: Set<String>
    ): List<HourlyIntensity> = withContext(Dispatchers.IO) {
        val dayStart = parseDateStart(dateStr) ?: return@withContext (0..23).map { hour ->
            HourlyIntensity(hour = hour, hourLabel = formatHourLabel(hour), usedMinutes = 0, intensity = 0f)
        }
        val dayEnd = dayStart + MILLIS_PER_DAY
        val now = System.currentTimeMillis()
        val rangeEnd = minOf(dayEnd, now)

        if (rangeEnd <= dayStart || monitoredPackages.isEmpty()) {
            return@withContext (0..23).map { hour ->
                HourlyIntensity(hour = hour, hourLabel = formatHourLabel(hour), usedMinutes = 0, intensity = 0f)
            }
        }

        val hourSeconds = getHourlyUsageSecondsForDate(dayStart, rangeEnd, monitoredPackages)
        val hourMinutes = hourSeconds.map { (it / 60L).toInt() }
        val maxMinutes = (hourMinutes.maxOrNull() ?: 0).coerceAtLeast(1)

        (0..23).map { hour ->
            val mins = hourMinutes[hour]
            val intensity = if (mins == 0) 0f else (mins.toFloat() / maxMinutes.toFloat()).coerceIn(0.15f, 1f)
            HourlyIntensity(
                hour = hour,
                hourLabel = formatHourLabel(hour),
                usedMinutes = mins,
                intensity = intensity
            )
        }
    }

    /**
     * Authentic 14-day and 30-day Streak & Compliance calculation.
     */
    suspend fun getStreakSummary(monitoredPackages: Set<String>): StreakSummary = withContext(Dispatchers.IO) {
        val entries = allLogsFlow.first()
        val blockedDateSet = entries
            .filter { it.wasBlocked && (monitoredPackages.isEmpty() || it.packageName in monitoredPackages) }
            .map { it.date }
            .toSet()

        val recent14 = mutableListOf<DayCompliance>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayDateStr = dateFormat.format(cal.time)
        val dayLabelFormat = SimpleDateFormat("d", Locale.US)
        val dayOfWeekFormatShort = SimpleDateFormat("EEE", Locale.US)
        val now = System.currentTimeMillis()

        for (i in 13 downTo 0) {
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dayStart = dayCal.timeInMillis
            val dayEnd = dayStart + MILLIS_PER_DAY
            val dayDate = dateFormat.format(dayCal.time)
            val isToday = (dayDate == todayDateStr)

            val usageMinutes = if (monitoredPackages.isEmpty()) {
                0
            } else {
                val rangeEnd = minOf(dayEnd, now)
                val sec = getUsageSecondsInRange(dayStart, rangeEnd, monitoredPackages)
                (sec / 60L).toInt()
            }

            val wasBlocked = dayDate in blockedDateSet

            val status = when {
                wasBlocked -> DayComplianceStatus.BREACHED
                usageMinutes > 0 -> DayComplianceStatus.UNDER_LIMIT
                else -> DayComplianceStatus.NO_DATA
            }

            recent14 += DayCompliance(
                date = dayDate,
                dayLabel = dayLabelFormat.format(dayCal.time),
                dayOfWeek = dayOfWeekFormatShort.format(dayCal.time),
                status = status,
                totalMinutes = usageMinutes,
                isToday = isToday
            )
        }

        // Current Streak: count backwards consecutive non-breached days starting from today
        var currentStreak = 0
        for (day in recent14.asReversed()) {
            if (day.status == DayComplianceStatus.BREACHED) {
                break
            } else {
                currentStreak++
            }
        }

        val longestStreak = computeLongestStreakLast30Days(monitoredPackages, blockedDateSet)
        val countedDays = recent14.count { it.status != DayComplianceStatus.BREACHED }

        StreakSummary(
            currentStreak = currentStreak,
            longestStreak = maxOf(longestStreak, currentStreak),
            countedDaysInWindow = countedDays,
            recent14Days = recent14
        )
    }

    /**
     * Smart High-Level Insight Summary.
     */
    suspend fun getInsightSummary(monitoredPackages: Set<String>): InsightSummary = withContext(Dispatchers.IO) {
        if (monitoredPackages.isEmpty()) {
            return@withContext InsightSummary(
                headline = "No apps currently guarded",
                detail = "Add apps from the home screen to start tracking focus budgets."
            )
        }

        val weekly = getWeeklyUsage(monitoredPackages)
        val pastAndToday = weekly.filter { !it.isFutureDay }
        val totalMinutes = pastAndToday.sumOf { it.totalMinutes }
        val daysCount = pastAndToday.size.coerceAtLeast(1)
        val avgPerDay = totalMinutes / daysCount
        val peakDay = pastAndToday.maxByOrNull { it.totalMinutes }
        val streak = getStreakSummary(monitoredPackages)

        val headline = if (totalMinutes >= 60) {
            "${totalMinutes / 60}h ${totalMinutes % 60}m guarded screen time this week"
        } else {
            "${totalMinutes}m guarded screen time this week"
        }

        val detail = buildString {
            append("Avg ${avgPerDay}m/day")
            if (peakDay != null && peakDay.totalMinutes > 0) {
                append(" • Peak ${peakDay.dayOfWeek} (${peakDay.totalMinutes}m)")
            }
            if (streak.currentStreak > 0) {
                append(" • ${streak.currentStreak}d Streak")
            }
        }

        InsightSummary(headline = headline, detail = detail)
    }

    /**
     * Percentage delta between current week pace and last week pace.
     */
    suspend fun getWeekVsLastWeekDelta(monitoredPackages: Set<String>): Int = withContext(Dispatchers.IO) {
        if (monitoredPackages.isEmpty()) return@withContext 0

        val cal = Calendar.getInstance().apply {
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentWeekStart = cal.timeInMillis
        val now = System.currentTimeMillis()
        val elapsedThisWeek = (now - currentWeekStart).coerceAtLeast(0L)

        val lastWeekStart = currentWeekStart - (7 * MILLIS_PER_DAY)
        val lastWeekSamePoint = lastWeekStart + elapsedThisWeek

        val currentSec = getUsageSecondsInRange(currentWeekStart, now, monitoredPackages)
        val previousSec = getUsageSecondsInRange(lastWeekStart, lastWeekSamePoint, monitoredPackages)

        if (previousSec <= 0L) {
            return@withContext if (currentSec <= 0L) 0 else 100
        }

        (((currentSec - previousSec).toDouble() / previousSec.toDouble()) * 100.0)
            .toInt()
            .coerceIn(-100, 999)
    }

    /**
     * Per-App Usage Breakdown (today's time, week's time, hourly limit).
     */
    suspend fun getAppBreakdowns(
        monitoredPackages: Set<String>,
        limits: Map<String, Int>
    ): List<AppUsageBreakdown> = withContext(Dispatchers.IO) {
        if (monitoredPackages.isEmpty()) return@withContext emptyList()

        val cal = Calendar.getInstance()
        val todayCal = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = todayCal.timeInMillis

        val weekCal = (todayCal.clone() as Calendar).apply {
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        }
        val weekStart = weekCal.timeInMillis

        val now = System.currentTimeMillis()

        val todayUsage = getPerAppUsageSecondsInRange(todayStart, now, monitoredPackages)
        val weekUsage = getPerAppUsageSecondsInRange(weekStart, now, monitoredPackages)

        val pm = context.packageManager

        monitoredPackages.map { pkg ->
            val appName = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }

            val todayMins = ((todayUsage[pkg] ?: 0L) / 60L).toInt()
            val weekMins = ((weekUsage[pkg] ?: 0L) / 60L).toInt()
            val limitMins = limits[pkg] ?: DEFAULT_LIMIT_MINUTES

            AppUsageBreakdown(
                packageName = pkg,
                appName = appName,
                todayMinutes = todayMins,
                weekMinutes = weekMins,
                hourlyLimitMinutes = limitMins
            )
        }.sortedByDescending { it.weekMinutes }
    }

    // ── High-Precision Event Processing Engine ────────────────────────────────

    @Suppress("DEPRECATION")
    private fun getUsageSecondsInRange(
        fromMillis: Long,
        toMillis: Long,
        monitoredPackages: Set<String>
    ): Long {
        if (monitoredPackages.isEmpty()) return 0L
        val usm = usageStatsManager ?: return 0L
        val now = System.currentTimeMillis()
        val boundedFrom = fromMillis.coerceAtMost(now)
        val boundedTo = toMillis.coerceAtMost(now)
        if (boundedTo <= boundedFrom) return 0L

        return try {
            val events = usm.queryEvents(boundedFrom, boundedTo)
            var totalTimeMs = 0L
            var activePkg: String? = null
            var activeStart = 0L

            fun closeActive(atMillis: Long) {
                val pkg = activePkg ?: return
                if (pkg in monitoredPackages && atMillis > activeStart) {
                    val clampedStart = maxOf(activeStart, boundedFrom)
                    val clampedEnd = minOf(atMillis, boundedTo)
                    if (clampedEnd > clampedStart) {
                        totalTimeMs += (clampedEnd - clampedStart)
                    }
                }
                activePkg = null
                activeStart = 0L
            }

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val type = event.eventType
                val ts = event.timeStamp

                if (type == SCREEN_NON_INTERACTIVE || type == KEYGUARD_SHOWN || type == DEVICE_SHUTDOWN) {
                    closeActive(ts)
                    continue
                }

                val isResume = type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND
                val isPause = type == UsageEvents.Event.ACTIVITY_PAUSED ||
                    type == UsageEvents.Event.ACTIVITY_STOPPED ||
                    type == UsageEvents.Event.MOVE_TO_BACKGROUND

                if (isResume) {
                    if (activePkg != null && pkg != activePkg && pkg !in TRANSIENT_SYSTEM_PACKAGES) {
                        closeActive(ts)
                    }

                    if (pkg in monitoredPackages) {
                        if (activePkg != pkg) {
                            activePkg = pkg
                            activeStart = ts
                        }
                    } else if (pkg !in TRANSIENT_SYSTEM_PACKAGES) {
                        closeActive(ts)
                    }
                }

                if (isPause && pkg == activePkg) {
                    closeActive(ts)
                }
            }

            val isScreenOn = (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: true
            if (activePkg != null && isScreenOn) {
                closeActive(boundedTo)
            }

            totalTimeMs / 1000L
        } catch (_: Exception) {
            0L
        }
    }

    @Suppress("DEPRECATION")
    private fun getHourlyUsageSecondsForDate(
        dayStart: Long,
        dayEnd: Long,
        monitoredPackages: Set<String>
    ): LongArray {
        val result = LongArray(24)
        if (monitoredPackages.isEmpty()) return result
        val usm = usageStatsManager ?: return result
        val now = System.currentTimeMillis()
        val boundedFrom = dayStart.coerceAtMost(now)
        val boundedTo = dayEnd.coerceAtMost(now)
        if (boundedTo <= boundedFrom) return result

        try {
            val events = usm.queryEvents(boundedFrom, boundedTo)
            var activePkg: String? = null
            var activeStart = 0L

            fun closeActive(atMillis: Long) {
                val pkg = activePkg ?: return
                if (pkg in monitoredPackages && atMillis > activeStart) {
                    val clampedStart = maxOf(activeStart, boundedFrom)
                    val clampedEnd = minOf(atMillis, boundedTo)
                    if (clampedEnd > clampedStart) {
                        sliceIntervalIntoHours(result, clampedStart, clampedEnd, dayStart)
                    }
                }
                activePkg = null
                activeStart = 0L
            }

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val type = event.eventType
                val ts = event.timeStamp

                if (type == SCREEN_NON_INTERACTIVE || type == KEYGUARD_SHOWN || type == DEVICE_SHUTDOWN) {
                    closeActive(ts)
                    continue
                }

                val isResume = type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND
                val isPause = type == UsageEvents.Event.ACTIVITY_PAUSED ||
                    type == UsageEvents.Event.ACTIVITY_STOPPED ||
                    type == UsageEvents.Event.MOVE_TO_BACKGROUND

                if (isResume) {
                    if (activePkg != null && pkg != activePkg && pkg !in TRANSIENT_SYSTEM_PACKAGES) {
                        closeActive(ts)
                    }

                    if (pkg in monitoredPackages) {
                        if (activePkg != pkg) {
                            activePkg = pkg
                            activeStart = ts
                        }
                    } else if (pkg !in TRANSIENT_SYSTEM_PACKAGES) {
                        closeActive(ts)
                    }
                }

                if (isPause && pkg == activePkg) {
                    closeActive(ts)
                }
            }

            val isScreenOn = (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: true
            if (activePkg != null && isScreenOn) {
                closeActive(boundedTo)
            }
        } catch (_: Exception) {
            return result
        }

        return result
    }

    @Suppress("DEPRECATION")
    private fun getPerAppUsageSecondsInRange(
        fromMillis: Long,
        toMillis: Long,
        monitoredPackages: Set<String>
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        monitoredPackages.forEach { result[it] = 0L }
        if (monitoredPackages.isEmpty()) return result
        val usm = usageStatsManager ?: return result
        val now = System.currentTimeMillis()
        val boundedFrom = fromMillis.coerceAtMost(now)
        val boundedTo = toMillis.coerceAtMost(now)
        if (boundedTo <= boundedFrom) return result

        try {
            val events = usm.queryEvents(boundedFrom, boundedTo)
            var activePkg: String? = null
            var activeStart = 0L

            fun closeActive(atMillis: Long) {
                val pkg = activePkg ?: return
                if (pkg in monitoredPackages && atMillis > activeStart) {
                    val clampedStart = maxOf(activeStart, boundedFrom)
                    val clampedEnd = minOf(atMillis, boundedTo)
                    if (clampedEnd > clampedStart) {
                        val sec = (clampedEnd - clampedStart) / 1000L
                        result[pkg] = (result[pkg] ?: 0L) + sec
                    }
                }
                activePkg = null
                activeStart = 0L
            }

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val type = event.eventType
                val ts = event.timeStamp

                if (type == SCREEN_NON_INTERACTIVE || type == KEYGUARD_SHOWN || type == DEVICE_SHUTDOWN) {
                    closeActive(ts)
                    continue
                }

                val isResume = type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND
                val isPause = type == UsageEvents.Event.ACTIVITY_PAUSED ||
                    type == UsageEvents.Event.ACTIVITY_STOPPED ||
                    type == UsageEvents.Event.MOVE_TO_BACKGROUND

                if (isResume) {
                    if (activePkg != null && pkg != activePkg && pkg !in TRANSIENT_SYSTEM_PACKAGES) {
                        closeActive(ts)
                    }

                    if (pkg in monitoredPackages) {
                        if (activePkg != pkg) {
                            activePkg = pkg
                            activeStart = ts
                        }
                    } else if (pkg !in TRANSIENT_SYSTEM_PACKAGES) {
                        closeActive(ts)
                    }
                }

                if (isPause && pkg == activePkg) {
                    closeActive(ts)
                }
            }

            val isScreenOn = (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: true
            if (activePkg != null && isScreenOn) {
                closeActive(boundedTo)
            }
        } catch (_: Exception) {
            return result
        }

        return result
    }

    private fun sliceIntervalIntoHours(
        resultSeconds: LongArray,
        startMillis: Long,
        endMillis: Long,
        dayStartMillis: Long
    ) {
        var cursor = startMillis
        while (cursor < endMillis) {
            val hourIndex = (((cursor - dayStartMillis) / 3600_000L).toInt()).coerceIn(0, 23)
            val nextHourMillis = dayStartMillis + ((hourIndex + 1) * 3600_000L)
            val segmentEnd = minOf(endMillis, nextHourMillis)
            val seconds = ((segmentEnd - cursor) / 1000L).coerceAtLeast(0L)
            resultSeconds[hourIndex] += seconds
            cursor = segmentEnd
        }
    }

    private fun computeLongestStreakLast30Days(
        monitoredPackages: Set<String>,
        blockedDateSet: Set<String>
    ): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var best = 0
        var running = 0

        for (i in 29 downTo 0) {
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dayDate = dateFormat.format(dayCal.time)
            val blocked = dayDate in blockedDateSet

            if (blocked) {
                running = 0
            } else {
                running += 1
                if (running > best) best = running
            }
        }

        return best
    }

    private fun formatHourLabel(hour: Int): String {
        return when (hour) {
            0 -> "12 AM"
            12 -> "12 PM"
            in 1..11 -> "$hour AM"
            else -> "${hour - 12} PM"
        }
    }

    private fun parseDateStart(dateStr: String): Long? {
        return try {
            val parsed = dateFormat.parse(dateStr) ?: return null
            Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEntriesFromJson(jsonStr: String): List<UsageLogEntry> {
        val list = mutableListOf<UsageLogEntry>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UsageLogEntry(
                        date = obj.getString("date"),
                        hour = obj.getInt("hour"),
                        packageName = obj.getString("packageName"),
                        usedSeconds = obj.getInt("usedSeconds"),
                        wasBlocked = obj.getBoolean("wasBlocked"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun serializeEntriesToJson(entries: List<UsageLogEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            val obj = JSONObject().apply {
                put("date", e.date)
                put("hour", e.hour)
                put("packageName", e.packageName)
                put("usedSeconds", e.usedSeconds)
                put("wasBlocked", e.wasBlocked)
                put("timestamp", e.timestamp)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}

