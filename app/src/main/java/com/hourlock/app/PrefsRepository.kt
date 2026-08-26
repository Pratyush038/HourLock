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

/** Epoch millis when Commitment Lock expires (0 when not active). */
val KEY_COMMITMENT_LOCK_UNTIL_MILLIS = longPreferencesKey("commitment_lock_until_millis")

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
fun quotaWindowStartKey(pkg: String) = longPreferencesKey("quota_window_start_$pkg")

/**
 * Signature of the active schedule block/rule used to account [usedSecondsKey].
 * If this changes, usage is reset for the new block window.
 */
fun quotaWindowSignatureKey(pkg: String) = stringPreferencesKey("quota_window_signature_$pkg")

/** Serialized per-package schedule block list. */
fun scheduleBlocksKey(pkg: String) = stringPreferencesKey("schedule_blocks_$pkg")

/** Per-package toggle for informational session check-in notifications. */
fun sessionCheckInEnabledKey(pkg: String) = booleanPreferencesKey("session_checkin_enabled_$pkg")

/** Per-package check-in interval in minutes. Allowed values: 3, 5, 10. */
fun sessionCheckInIntervalMinutesKey(pkg: String) = intPreferencesKey("session_checkin_interval_minutes_$pkg")

// ── Constants ──────────────────────────────────────────────────────────────────

const val DEFAULT_LIMIT_MINUTES = 10
const val DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES = 5

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

    // ─── Commitment lock ─────────────────────────────────────────────────

    val commitmentLockUntilFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_COMMITMENT_LOCK_UNTIL_MILLIS] ?: 0L
    }

    val isCommitmentLockActiveFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        val until = prefs[KEY_COMMITMENT_LOCK_UNTIL_MILLIS] ?: 0L
        until > System.currentTimeMillis()
    }

    suspend fun getCommitmentLockUntilMillis(): Long {
        val prefs = dataStore.data.first()
        val until = prefs[KEY_COMMITMENT_LOCK_UNTIL_MILLIS] ?: 0L
        return if (until > System.currentTimeMillis()) until else 0L
    }

    suspend fun isCommitmentLockActive(): Boolean {
        return getCommitmentLockUntilMillis() > System.currentTimeMillis()
    }

    suspend fun startCommitmentLock(durationMillis: Long) {
        val until = System.currentTimeMillis() + durationMillis.coerceAtLeast(0L)
        dataStore.edit { prefs -> prefs[KEY_COMMITMENT_LOCK_UNTIL_MILLIS] = until }
    }

    suspend fun clearCommitmentLock() {
        dataStore.edit { prefs -> prefs[KEY_COMMITMENT_LOCK_UNTIL_MILLIS] = 0L }
    }

    // ─── Per-package usage ─────────────────────────────────────────────────

    /**
     * Flow of used-seconds for [pkg] in the current active quota window.
     * The actual reset/write is performed by [getUsedSeconds]/[incrementUsedSeconds].
     */
    fun usedSecondsFlow(pkg: String): Flow<Int> = dataStore.data.map { prefs ->
        prefs[usedSecondsKey(pkg)] ?: 0
    }

    /**
     * Get used seconds for [pkg] in its active schedule quota window.
     * The usage counter resets when either:
     *  - The quota window boundary changes (hour boundary for hourly quota).
     *  - The active schedule block/rule changes.
     */
    suspend fun getUsedSeconds(pkg: String): Int {
        val schedule = getScheduleForPackage(pkg)
        val active = resolveActiveBlock(schedule)
        val expectedWindowStart = expectedQuotaWindowStartMillis(active)
        val expectedSignature = blockSignature(active)

        val prefs = dataStore.data.first()
        val storedWindowStart = prefs[quotaWindowStartKey(pkg)] ?: Long.MIN_VALUE
        val storedSignature = prefs[quotaWindowSignatureKey(pkg)] ?: ""

        return if (storedWindowStart != expectedWindowStart || storedSignature != expectedSignature) {
            dataStore.edit { p ->
                p[usedSecondsKey(pkg)] = 0
                p[quotaWindowStartKey(pkg)] = expectedWindowStart
                p[quotaWindowSignatureKey(pkg)] = expectedSignature
            }
            0
        } else {
            prefs[usedSecondsKey(pkg)] ?: 0
        }
    }

    /**
     * Atomically increment usedSeconds for [pkg] by 1 and persist.
     * Accounting follows the active schedule block and its rule type.
     */
    suspend fun incrementUsedSeconds(pkg: String): Int {
        val schedule = getScheduleForPackage(pkg)
        val active = resolveActiveBlock(schedule)
        val expectedWindowStart = expectedQuotaWindowStartMillis(active)
        val expectedSignature = blockSignature(active)

        var newValue = 0
        dataStore.edit { prefs ->
            val storedWindowStart = prefs[quotaWindowStartKey(pkg)] ?: Long.MIN_VALUE
            val storedSignature = prefs[quotaWindowSignatureKey(pkg)] ?: ""
            val current = if (storedWindowStart != expectedWindowStart || storedSignature != expectedSignature) {
                prefs[quotaWindowStartKey(pkg)] = expectedWindowStart
                prefs[quotaWindowSignatureKey(pkg)] = expectedSignature
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

    // ─── Per-package schedule limits ──────────────────────────────────────

    fun scheduleFlow(pkg: String): Flow<List<ScheduleBlock>> = dataStore.data.map { prefs ->
        val encoded = prefs[scheduleBlocksKey(pkg)]
        val parsed = decodeSchedule(encoded)
        if (parsed.isEmpty()) defaultSimpleSchedule() else parsed
    }

    suspend fun getScheduleForPackage(pkg: String): List<ScheduleBlock> {
        val prefs = dataStore.data.first()
        val parsed = decodeSchedule(prefs[scheduleBlocksKey(pkg)])
        if (parsed.isNotEmpty()) return parsed

        val fallback = defaultSimpleSchedule()
        setScheduleForPackage(pkg, fallback)
        return fallback
    }

    suspend fun setScheduleForPackage(pkg: String, blocks: List<ScheduleBlock>): ScheduleValidationResult {
        val validation = validateSchedule(blocks)
        if (!validation.isValid) return validation
        val encoded = encodeSchedule(blocks)
        dataStore.edit { prefs -> prefs[scheduleBlocksKey(pkg)] = encoded }
        return ScheduleValidationResult(isValid = true)
    }

    suspend fun copySchedule(sourcePkg: String, targetPkgs: Set<String>) {
        val source = getScheduleForPackage(sourcePkg)
        for (pkg in targetPkgs) {
            setScheduleForPackage(pkg, source)
        }
    }

    suspend fun getActiveScheduleBlock(pkg: String): ActiveScheduleBlock {
        val schedule = getScheduleForPackage(pkg)
        return resolveActiveBlock(schedule)
    }

    suspend fun getLimitSeconds(pkg: String): Int {
        val active = getActiveScheduleBlock(pkg)
        return (active.block.limitMinutes.coerceAtLeast(0)) * 60
    }

    suspend fun getCurrentLimitStatus(pkg: String): CurrentLimitStatus {
        val active = getActiveScheduleBlock(pkg)
        val used = getUsedSeconds(pkg)
        val limitSeconds = active.block.limitMinutes.coerceAtLeast(0) * 60
        val blocked = used >= limitSeconds
        return CurrentLimitStatus(
            limitSeconds = limitSeconds,
            usedSeconds = used,
            isBlocked = blocked,
            activeBlock = active,
            unlockAtMillis = calculateUnlockAtMillis(active)
        )
    }

    suspend fun nextUnlockMillisForPackage(pkg: String): Long {
        val active = getActiveScheduleBlock(pkg)
        return calculateUnlockAtMillis(active)
    }

    suspend fun resetCurrentQuotaCounters(pkgs: Set<String>) {
        for (pkg in pkgs) {
            val schedule = getScheduleForPackage(pkg)
            val active = resolveActiveBlock(schedule)
            dataStore.edit { prefs ->
                prefs[usedSecondsKey(pkg)] = 0
                prefs[quotaWindowStartKey(pkg)] = expectedQuotaWindowStartMillis(active)
                prefs[quotaWindowSignatureKey(pkg)] = blockSignature(active)
            }
        }
    }

    // ─── Per-package session check-ins ───────────────────────────────────

    fun sessionCheckInEnabledFlow(pkg: String): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[sessionCheckInEnabledKey(pkg)] ?: true
    }

    suspend fun isSessionCheckInEnabled(pkg: String): Boolean {
        val prefs = dataStore.data.first()
        return prefs[sessionCheckInEnabledKey(pkg)] ?: true
    }

    suspend fun setSessionCheckInEnabled(pkg: String, enabled: Boolean) {
        dataStore.edit { prefs -> prefs[sessionCheckInEnabledKey(pkg)] = enabled }
    }

    fun sessionCheckInIntervalMinutesFlow(pkg: String): Flow<Int> = dataStore.data.map { prefs ->
        val raw = prefs[sessionCheckInIntervalMinutesKey(pkg)] ?: DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES
        raw.coerceIn(3, 10).let { if (it in setOf(3, 5, 10)) it else DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES }
    }

    suspend fun getSessionCheckInIntervalMinutes(pkg: String): Int {
        val prefs = dataStore.data.first()
        val raw = prefs[sessionCheckInIntervalMinutesKey(pkg)] ?: DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES
        return if (raw in setOf(3, 5, 10)) raw else DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES
    }

    suspend fun setSessionCheckInIntervalMinutes(pkg: String, minutes: Int) {
        val normalized = when (minutes) {
            3, 5, 10 -> minutes
            else -> DEFAULT_SESSION_CHECK_IN_INTERVAL_MINUTES
        }
        dataStore.edit { prefs -> prefs[sessionCheckInIntervalMinutesKey(pkg)] = normalized }
    }

    // ─── Schedule helpers ─────────────────────────────────────────────────

    fun validateSchedule(blocks: List<ScheduleBlock>): ScheduleValidationResult {
        if (blocks.isEmpty()) {
            return ScheduleValidationResult(false, "Schedule cannot be empty")
        }

        val sorted = blocks.sortedBy { it.startMinuteOfDay }
        if (sorted.first().startMinuteOfDay != 0) {
            return ScheduleValidationResult(false, "Schedule must start at 12:00 AM")
        }

        var expectedStart = 0
        for ((index, block) in sorted.withIndex()) {
            if (block.startMinuteOfDay != expectedStart) {
                return ScheduleValidationResult(
                    false,
                    "Gap or overlap near block ${index + 1}; each block must start where the previous ends"
                )
            }
            if (block.endMinuteOfDay <= block.startMinuteOfDay) {
                return ScheduleValidationResult(false, "Each block must end after it starts")
            }
            if (block.endMinuteOfDay > 24 * 60) {
                return ScheduleValidationResult(false, "Schedule cannot extend past 12:00 AM")
            }
            if (block.limitMinutes < 0) {
                return ScheduleValidationResult(false, "Limit minutes cannot be negative")
            }
            expectedStart = block.endMinuteOfDay
        }

        if (expectedStart != 24 * 60) {
            return ScheduleValidationResult(false, "Schedule must cover full 24 hours")
        }

        return ScheduleValidationResult(isValid = true)
    }

    private fun encodeSchedule(blocks: List<ScheduleBlock>): String {
        return blocks.joinToString(separator = "|") { block ->
            "${block.startMinuteOfDay},${block.endMinuteOfDay},${block.ruleType.name},${block.limitMinutes}"
        }
    }

    private fun decodeSchedule(encoded: String?): List<ScheduleBlock> {
        if (encoded.isNullOrBlank()) return emptyList()

        val blocks = mutableListOf<ScheduleBlock>()
        val parts = encoded.split("|")
        for (part in parts) {
            val fields = part.split(",")
            if (fields.size != 4) return emptyList()

            val start = fields[0].toIntOrNull() ?: return emptyList()
            val end = fields[1].toIntOrNull() ?: return emptyList()
            val type = runCatching { ScheduleRuleType.valueOf(fields[2]) }.getOrNull() ?: return emptyList()
            val minutes = fields[3].toIntOrNull() ?: return emptyList()

            blocks += ScheduleBlock(
                startMinuteOfDay = start,
                endMinuteOfDay = end,
                ruleType = type,
                limitMinutes = minutes
            )
        }

        return if (validateSchedule(blocks).isValid) blocks.sortedBy { it.startMinuteOfDay } else emptyList()
    }

    private fun resolveActiveBlock(blocks: List<ScheduleBlock>): ActiveScheduleBlock {
        val now = java.util.Calendar.getInstance()
        val minuteOfDay = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

        val sorted = blocks.sortedBy { it.startMinuteOfDay }
        val index = sorted.indexOfFirst { minuteOfDay >= it.startMinuteOfDay && minuteOfDay < it.endMinuteOfDay }
            .let { if (it == -1) sorted.lastIndex else it }
        val block = sorted[index]

        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val blockStartMillis = midnight + block.startMinuteOfDay * 60_000L
        val blockEndMillis = midnight + block.endMinuteOfDay * 60_000L

        return ActiveScheduleBlock(
            block = block,
            blockIndex = index,
            blockStartMillis = blockStartMillis,
            blockEndMillis = blockEndMillis
        )
    }

    private fun expectedQuotaWindowStartMillis(active: ActiveScheduleBlock): Long {
        return when (active.block.ruleType) {
            ScheduleRuleType.HOURLY_QUOTA -> {
                val currentHour = currentHourStartMillis()
                maxOf(currentHour, active.blockStartMillis)
            }
            ScheduleRuleType.FLAT_ALLOWANCE -> active.blockStartMillis
        }
    }

    private fun blockSignature(active: ActiveScheduleBlock): String {
        val b = active.block
        return "${active.blockIndex}:${b.startMinuteOfDay}-${b.endMinuteOfDay}:${b.ruleType.name}:${b.limitMinutes}"
    }

    private fun calculateUnlockAtMillis(active: ActiveScheduleBlock): Long {
        return when (active.block.ruleType) {
            ScheduleRuleType.HOURLY_QUOTA -> {
                if (active.block.limitMinutes <= 0) {
                    active.blockEndMillis
                } else {
                    val nextHour = currentHourStartMillis() + 3_600_000L
                    minOf(nextHour, active.blockEndMillis)
                }
            }
            ScheduleRuleType.FLAT_ALLOWANCE -> active.blockEndMillis
        }
    }

    // ─── Today's total usage (calculated from local midnight 00:00:00) ──────

    /**
     * Exact total foreground usage today in seconds for [pkg].
     * Computed using UsageStatsManager.queryEvents() from midnight (00:00:00)
     * in the device's local timezone (e.g. IST GMT+5:30) to now.
     * Accurately tracks app switches, screen lock / off transitions, and pauses.
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
                val p = event.packageName
                val type = event.eventType

                // Screen turned off, phone locked, or shutdown -> end active foreground session
                if (type == 16 /* SCREEN_NON_INTERACTIVE */ ||
                    type == 17 /* KEYGUARD_SHOWN */ ||
                    type == 26 /* DEVICE_SHUTDOWN */) {
                    if (isForeground && lastResumeTime > 0L) {
                        totalTimeMs += (event.timeStamp - lastResumeTime).coerceAtLeast(0L)
                    }
                    isForeground = false
                    lastResumeTime = 0L
                    continue
                }

                if (p == pkg) {
                    when (type) {
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
                } else if (isForeground && p != null && p !in TRANSIENT_SYSTEM_PACKAGES) {
                    // Switched to another app
                    if (type == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                        type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        if (lastResumeTime > 0L) {
                            totalTimeMs += (event.timeStamp - lastResumeTime).coerceAtLeast(0L)
                        }
                        isForeground = false
                        lastResumeTime = 0L
                    }
                }
            }

            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isScreenOn = pm?.isInteractive ?: true
            if (isForeground && isScreenOn && lastResumeTime > 0L && lastResumeTime < now) {
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
                val type = event.eventType

                if (type == 16 /* SCREEN_NON_INTERACTIVE */ ||
                    type == 17 /* KEYGUARD_SHOWN */ ||
                    type == 26 /* DEVICE_SHUTDOWN */) {
                    for ((pkgName, isFg) in foregroundState) {
                        if (isFg) {
                            val lastResume = resumeTimes[pkgName] ?: 0L
                            if (lastResume > 0L) {
                                totalTimeMs += (event.timeStamp - lastResume).coerceAtLeast(0L)
                            }
                        }
                    }
                    foregroundState.clear()
                    resumeTimes.clear()
                    continue
                }

                if (p != null && p in pkgs) {
                    when (type) {
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
                } else if (p != null && p !in TRANSIENT_SYSTEM_PACKAGES) {
                    if (type == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                        type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        for ((pkgName, isFg) in foregroundState) {
                            if (isFg) {
                                val lastResume = resumeTimes[pkgName] ?: 0L
                                if (lastResume > 0L) {
                                    totalTimeMs += (event.timeStamp - lastResume).coerceAtLeast(0L)
                                }
                            }
                        }
                        foregroundState.clear()
                        resumeTimes.clear()
                    }
                }
            }

            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isScreenOn = pm?.isInteractive ?: true
            if (isScreenOn) {
                for ((p, isFg) in foregroundState) {
                    if (isFg) {
                        val lastResume = resumeTimes[p] ?: 0L
                        if (lastResume > 0L && lastResume < now) {
                            totalTimeMs += (now - lastResume).coerceAtLeast(0L)
                        }
                    }
                }
            }

            totalTimeMs / 1000L
        } catch (e: Exception) {
            0L
        }
    }
}
