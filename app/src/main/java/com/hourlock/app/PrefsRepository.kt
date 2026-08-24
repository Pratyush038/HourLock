package com.hourlock.app

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * PrefsRepository
 * ────────────────
 * Single source of truth for all persisted state in HourLock.
 *
 * Uses Jetpack DataStore (Preferences) instead of raw SharedPreferences because:
 *  - DataStore is coroutine-safe: reads/writes never block the main thread.
 *  - It uses a Flow-based API, so UI can observe changes reactively.
 *  - It handles concurrent writes correctly with atomic transactions.
 *
 * All state is scoped to a single DataStore instance (created by the
 * [dataStore] extension delegate in HourLockApplication). We pass the
 * Context lazily to avoid leaking the Application context into the
 * repository constructor.
 */

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── DataStore singleton (extension on Context, created once per process) ──────
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hourlock_prefs")

// ── Key definitions ────────────────────────────────────────────────────────────

/** Package names the user has chosen to monitor. Stored as a Set<String>. */
val KEY_MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")

/** Master on/off switch for blocking behavior. */
val KEY_BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")

/**
 * Pause-until epoch millis. If current time < this value, blocking is paused.
 * Set to 0 (or past) when not paused.
 */
val KEY_PAUSE_UNTIL_MILLIS = longPreferencesKey("pause_until_millis")

// ── Per-package dynamic keys (generated at runtime) ────────────────────────────

/**
 * Returns the DataStore key that stores how many seconds [pkg] has been
 * in the foreground during the current rolling hour window.
 * Key format: "used_seconds_<packageName>"
 */
fun usedSecondsKey(pkg: String) = intPreferencesKey("used_seconds_$pkg")

/**
 * Returns the DataStore key for the start-of-hour epoch millis for [pkg].
 * When currentTime >= hourStart + 3600_000 ms, we reset usedSeconds to 0
 * and update this value.
 */
fun hourStartKey(pkg: String) = longPreferencesKey("hour_start_$pkg")

/**
 * Per-package limit in minutes. Key format: "limit_minutes_<packageName>".
 * Defaults to [DEFAULT_LIMIT_MINUTES] if not set.
 */
fun limitMinutesKey(pkg: String) = intPreferencesKey("limit_minutes_$pkg")

// ── Constants ──────────────────────────────────────────────────────────────────

const val DEFAULT_LIMIT_MINUTES = 10

/**
 * Packages that can NEVER be blocked, regardless of what the user configures.
 * These cover the most common system-critical apps; the list is intentionally
 * conservative. We'd rather under-block than accidentally lock someone out of
 * their phone dialer or system settings.
 */
val NEVER_BLOCK_PACKAGES = setOf(
    "com.android.settings",            // Android Settings
    "com.android.dialer",              // AOSP dialer
    "com.google.android.dialer",       // Google Phone
    "com.samsung.android.dialer",      // Samsung Phone
    "com.android.phone",               // Telephony framework UI
    "com.android.camera",              // AOSP Camera
    "com.android.camera2",
    "com.google.android.GoogleCamera", // Pixel Camera
    "com.samsung.android.app.camera",  // Samsung Camera
    // Home launchers — blocking these would trap the user
    "com.android.launcher",
    "com.android.launcher2",
    "com.android.launcher3",
    "com.google.android.apps.nexuslauncher",
    "com.samsung.android.app.spage",   // Samsung One UI Home
    "com.miui.home",
    "com.sec.android.app.launcher",
    // HourLock itself — never block our own package
    "com.hourlock.app",
)

/**
 * Packages representing system UI, keyboards, or input methods that should
 * NOT trigger an app switch or cancel tracking when they appear over a monitored app.
 */
val TRANSIENT_SYSTEM_PACKAGES = setOf(
    "android",
    "com.android.systemui",
    "com.samsung.android.honeyboard",       // Samsung Keyboard
    "com.google.android.inputmethod.latin", // Gboard
    "com.touchtype.swiftkey",               // SwiftKey
)

// ── Repository class ───────────────────────────────────────────────────────────

class PrefsRepository(private val context: Context) {

    private val dataStore = context.dataStore

    // ─── Monitored packages ────────────────────────────────────────────────

    /** Flow of the current set of monitored package names. */
    val monitoredPackagesFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_MONITORED_PACKAGES] ?: setOf("com.instagram.android")
    }

    /** Read current monitored packages synchronously (for use inside services). */
    suspend fun getMonitoredPackages(): Set<String> =
        monitoredPackagesFlow.first()

    suspend fun setMonitoredPackages(packages: Set<String>) {
        // Filter out any never-block packages that might have been added
        val safe = packages - NEVER_BLOCK_PACKAGES
        dataStore.edit { prefs -> prefs[KEY_MONITORED_PACKAGES] = safe }
    }

    // ─── Master toggle ─────────────────────────────────────────────────────

    val blockingEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BLOCKING_ENABLED] ?: true
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_BLOCKING_ENABLED] = enabled }
    }

    // ─── Pause until ───────────────────────────────────────────────────────

    val pauseUntilFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_PAUSE_UNTIL_MILLIS] ?: 0L
    }

    suspend fun pauseForOneHour() {
        val until = System.currentTimeMillis() + 3_600_000L
        dataStore.edit { prefs -> prefs[KEY_PAUSE_UNTIL_MILLIS] = until }
    }

    suspend fun clearPause() {
        dataStore.edit { prefs -> prefs[KEY_PAUSE_UNTIL_MILLIS] = 0L }
    }

    // ─── Per-package usage ─────────────────────────────────────────────────

    /**
     * Flow of used-seconds for [pkg] in the current rolling hour.
     * Automatically handles hour boundary resets by comparing [hourStartKey]
     * against current system time.
     */
    fun usedSecondsFlow(pkg: String): Flow<Int> = dataStore.data.map { prefs ->
        val hourStart = prefs[hourStartKey(pkg)] ?: 0L
        val now = System.currentTimeMillis()
        val hourDuration = 3_600_000L
        return@map if (now - hourStart >= hourDuration) {
            // Hour has rolled over — report 0 (actual write happens in service)
            0
        } else {
            prefs[usedSecondsKey(pkg)] ?: 0
        }
    }

    /**
     * Get used seconds and limit for [pkg]. Handles hour rollover:
     * if the stored hourStart is from a previous hour, resets usedSeconds to 0
     * and writes the new hourStart before returning.
     *
     * This is the primary function called by UsageTrackerService on each tick.
     */
    suspend fun getUsedSeconds(pkg: String): Int {
        val prefs = dataStore.data.first()
        val hourStart = prefs[hourStartKey(pkg)] ?: 0L
        val currentHour = currentHourStartMillis()
        return if (hourStart != currentHour) {
            // New clock hour — reset
            dataStore.edit { p ->
                p[usedSecondsKey(pkg)] = 0
                p[hourStartKey(pkg)] = currentHour
            }
            0
        } else {
            prefs[usedSecondsKey(pkg)] ?: 0
        }
    }

    /**
     * Atomically increment usedSeconds for [pkg] by 1 and persist.
     * Called on every 1-second tick while the monitored app is in foreground.
     * We write on EVERY tick (not just on stop) so that if the process is
     * killed by the OS, we don't lose the accumulated count for the current hour.
     */
    suspend fun incrementUsedSeconds(pkg: String): Int {
        var newValue = 0
        dataStore.edit { prefs ->
            val hourStart = prefs[hourStartKey(pkg)] ?: 0L
            val currentHour = currentHourStartMillis()
            val current = if (hourStart != currentHour) {
                // Hour rolled over mid-session — reset before incrementing
                prefs[hourStartKey(pkg)] = currentHour
                0
            } else {
                prefs[usedSecondsKey(pkg)] ?: 0
            }
            newValue = current + 1
            prefs[usedSecondsKey(pkg)] = newValue
        }
        return newValue
    }

    /**
     * Returns the epoch millis of the start of the current local clock hour.
     * E.g. at 14:37:22, this returns the millis for 14:00:00.
     */
    fun currentHourStartMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns the epoch millis of the START of the NEXT clock hour.
     * Used by BlockedActivity to display "unlocks at XX:00".
     */
    fun nextHourStartMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
        return cal.timeInMillis
    }

    // ─── Per-package limit ─────────────────────────────────────────────────

    fun limitMinutesFlow(pkg: String): Flow<Int> = dataStore.data.map { prefs ->
        prefs[limitMinutesKey(pkg)] ?: DEFAULT_LIMIT_MINUTES
    }

    suspend fun getLimitSeconds(pkg: String): Int {
        val prefs = dataStore.data.first()
        val minutes = prefs[limitMinutesKey(pkg)] ?: DEFAULT_LIMIT_MINUTES
        return minutes * 60
    }

    suspend fun setLimitMinutes(pkg: String, minutes: Int) {
        dataStore.edit { prefs -> prefs[limitMinutesKey(pkg)] = minutes.coerceIn(1, 60) }
    }

    // ─── Unlock options ────────────────────────────────────────────────────

    /** Key for the unlock challenge type: "none" | "phrase" | "wait" */
    private val KEY_UNLOCK_MODE = stringPreferencesKey("unlock_mode")
    val unlockModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_UNLOCK_MODE] ?: "none"
    }

    suspend fun setUnlockMode(mode: String) {
        dataStore.edit { prefs -> prefs[KEY_UNLOCK_MODE] = mode }
    }

    /**
     * Grant 2 extra minutes of emergency access for [pkg].
     * The 120 seconds are added to usedSeconds (still count against the hour)
     * so the user can use them exactly once per hour.
     */
    suspend fun grantEmergencyAccess(pkg: String) {
        dataStore.edit { prefs ->
            val current = prefs[usedSecondsKey(pkg)] ?: 0
            // Subtract 120 s from usedSeconds so the block triggers again
            // 2 min later. We never go below 0.
            prefs[usedSecondsKey(pkg)] = (current - 120).coerceAtLeast(0)
        }
    }

    // ─── Today's total usage (calculated from local midnight 00:00:00) ──────

    /**
     * Exact total foreground usage today in seconds for [pkg].
     * Computed using UsageStatsManager.queryEvents() from midnight (00:00:00)
     * in the device's local timezone (e.g. IST GMT+5:30) to now.
     * This avoids coarse daily bucket inaccuracies.
     */
    suspend fun getTodayTotalSeconds(pkg: String, context: Context): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return@withContext 0L

            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val now = System.currentTimeMillis()

            if (now <= startOfDay) return@withContext 0L

            val events = usm.queryEvents(startOfDay, now)
            var totalTimeMs = 0L
            var lastResumeTime = 0L
            var isForeground = false

            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == pkg) {
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            lastResumeTime = event.timeStamp
                            isForeground = true
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (isForeground && lastResumeTime > 0L) {
                                totalTimeMs += (event.timeStamp - lastResumeTime).coerceAtLeast(0L)
                            }
                            isForeground = false
                            lastResumeTime = 0L
                        }
                    }
                }
            }

            if (isForeground && lastResumeTime > 0L && lastResumeTime < now) {
                totalTimeMs += (now - lastResumeTime).coerceAtLeast(0L)
            }

            totalTimeMs / 1000L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Exact total foreground usage today in seconds across ALL [pkgs] combined.
     */
    suspend fun getTodayTotalSecondsAll(pkgs: Set<String>, context: Context): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return@withContext 0L

            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val now = System.currentTimeMillis()

            if (now <= startOfDay || pkgs.isEmpty()) return@withContext 0L

            val events = usm.queryEvents(startOfDay, now)
            var totalTimeMs = 0L
            val resumeTimes = mutableMapOf<String, Long>()
            val foregroundState = mutableMapOf<String, Boolean>()

            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val p = event.packageName
                if (p != null && p in pkgs) {
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            resumeTimes[p] = event.timeStamp
                            foregroundState[p] = true
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (foregroundState[p] == true) {
                                val lastResume = resumeTimes[p] ?: 0L
                                if (lastResume > 0L) {
                                    totalTimeMs += (event.timeStamp - lastResume).coerceAtLeast(0L)
                                }
                            }
                            foregroundState[p] = false
                            resumeTimes[p] = 0L
                        }
                    }
                }
            }

            for ((p, isFg) in foregroundState) {
                if (isFg) {
                    val lastResume = resumeTimes[p] ?: 0L
                    if (lastResume > 0L && lastResume < now) {
                        totalTimeMs += (now - lastResume).coerceAtLeast(0L)
                    }
                }
            }

            totalTimeMs / 1000L
        } catch (e: Exception) {
            0L
        }
    }
}
