package com.hourlock.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UsageTrackerService — AccessibilityService
 * ──────────────────────────────────────────
 * This is the only component in HourLock that has elevated system access.
 * It subscribes to TYPE_WINDOW_STATE_CHANGED events and, for monitored
 * packages only, ticks a 1-second timer to accumulate foreground usage.
 *
 * CRITICAL SAFETY CONTRACT (do not weaken without thorough review):
 * 1. We check [NEVER_BLOCK_PACKAGES] before any action on [onAccessibilityEvent].
 * 2. We return immediately (fast-path) for any package not in the monitored list.
 * 3. We NEVER call performGlobalAction(), dispatchGesture(), or findAccessibilityNodeInfosByText()
 *    — no interception, no injection into other apps' UIs.
 * 4. canRetrieveWindowContent is false in the XML config, making the above
 *    point enforced at the framework level as well.
 * 5. All callbacks are wrapped in try/catch so a Kotlin exception here can
 *    NEVER propagate into the system UI or crash another app.
 *
 * LIFECYCLE NOTE:
 * AccessibilityService runs in the main process. Android may restart the
 * service after it's killed. We use a SupervisorJob + IO dispatcher so that
 * coroutine failures don't cascade. The CoroutineScope is cancelled in
 * [onUnbind] to prevent leaks.
 */
class UsageTrackerService : AccessibilityService() {

    companion object {
        private const val TAG = "HourLock.A11yService"
        private const val TICK_INTERVAL_MS = 1_000L
    }

    // ── Coroutine scope tied to service lifecycle ──────────────────────────
    // SupervisorJob: if the timer job for one app crashes, it doesn't cancel
    // the scope (other monitored apps keep tracking).
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // The package name currently in the foreground (null = no monitored app)
    @Volatile private var currentForegroundPkg: String? = null

    // The running timer job for the foreground app (null if idle)
    private var timerJob: Job? = null

    // Cached set of monitored packages. Updated each time a window event fires.
    // Volatile so reads from IO thread see the latest value.
    @Volatile private var cachedMonitoredPackages: Set<String> = emptySet()
    @Volatile private var cachedBlockingEnabled: Boolean = true
    @Volatile private var cachedPauseUntil: Long = 0L

    private lateinit var repo: PrefsRepository

    // ── Service lifecycle ──────────────────────────────────────────────────

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            repo = PrefsRepository(applicationContext)

            // Set service info dynamically as a safety belt alongside the XML
            // config — ensures we only receive the events we declared.
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.DEFAULT
                notificationTimeout = 100
            }
            serviceInfo = info

            // Start collecting state changes continuously so we instantly know
            // when the user adds/removes apps or toggles blocking.
            serviceScope.launch {
                repo.monitoredPackagesFlow.collect { pkgs ->
                    cachedMonitoredPackages = pkgs
                }
            }
            serviceScope.launch {
                repo.blockingEnabledFlow.collect { enabled ->
                    cachedBlockingEnabled = enabled
                }
            }
            serviceScope.launch {
                repo.pauseUntilFlow.collect { pauseTime ->
                    cachedPauseUntil = pauseTime
                }
            }

            // Start the foreground service so Samsung battery saver doesn't
            // kill us. We start it here rather than from Application.onCreate
            // so we know the a11y service is definitely active.
            startForegroundServiceCompat()

            Log.i(TAG, "UsageTrackerService connected")
        } catch (e: Exception) {
            // Do NOT rethrow — a crash here would kill the system a11y pipeline
            Log.e(TAG, "Error in onServiceConnected", e)
        }
    }

    // ── Main event handler ─────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // FAST PATH: null guard first — never trust that event is non-null
        if (event == null) return

        try {
            // Only handle window-state-changed events
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

            val pkg = event.packageName?.toString() ?: return

            // TRANSIENT SYSTEM PACKAGES: Keyboards, System UI notifications/panels.
            // When these appear over a monitored app, do NOT stop tracking the app.
            if (pkg in TRANSIENT_SYSTEM_PACKAGES) {
                return
            }

            // FAST PATH: never-block list takes absolute precedence
            if (pkg in NEVER_BLOCK_PACKAGES) {
                stopTimerIfRunning(reason = "never-block package came to foreground")
                return
            }

            // FAST PATH: if not monitored, stop any running timer and return
            if (pkg !in cachedMonitoredPackages) {
                if (currentForegroundPkg != null) {
                    stopTimerIfRunning(reason = "non-monitored package: $pkg")
                }
                return
            }

            // ── Monitored package came to foreground ───────────────────────

            // If it's the same package already being tracked, do nothing
            if (pkg == currentForegroundPkg) return

            // A different monitored package came to foreground — switch timers
            stopTimerIfRunning(reason = "switching to $pkg")
            currentForegroundPkg = pkg
            startTimerFor(pkg)

        } catch (e: Exception) {
            // Catch everything: a bug here must never surface to the framework
            Log.e(TAG, "Uncaught exception in onAccessibilityEvent", e)
        }
    }

    override fun onInterrupt() {
        // Called when the service is interrupted (e.g. a new a11y service connects).
        // Stop the timer gracefully; the service will reconnect automatically.
        try {
            stopTimerIfRunning(reason = "service interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onInterrupt", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            stopTimerIfRunning(reason = "service unbound")
            serviceScope.cancel()
            Log.i(TAG, "UsageTrackerService unbound, scope cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onUnbind", e)
        }
        return super.onUnbind(intent)
    }

    // ── Timer management ───────────────────────────────────────────────────

    /**
     * Starts a 1-second interval timer for [pkg].
     * On each tick:
     *  1. Refresh cached prefs (blocking enabled, pause state, monitored list)
     *  2. Check hour boundary; reset if needed (handled inside repo)
     *  3. Increment usedSeconds (written to DataStore on every tick)
     *  4. If usedSeconds >= limitSeconds, launch BlockedActivity
     */
    private fun startTimerFor(pkg: String) {
        timerJob = serviceScope.launch {
            Log.d(TAG, "Timer started for $pkg")
            // Immediate check: if already at limit, block immediately without waiting 1s
            try {
                checkInitialLimit(pkg)
            } catch (e: Exception) {
                Log.e(TAG, "Initial limit check error for $pkg", e)
            }

            while (true) {
                delay(TICK_INTERVAL_MS)
                try {
                    tickForPackage(pkg)
                } catch (e: Exception) {
                    Log.e(TAG, "Timer tick error for $pkg", e)
                    // Don't cancel — keep ticking on next second
                }
            }
        }
    }

    private suspend fun checkInitialLimit(pkg: String) {
        cachedBlockingEnabled = repo.blockingEnabledFlow.first()
        cachedPauseUntil = repo.pauseUntilFlow.first()
        cachedMonitoredPackages = repo.getMonitoredPackages()

        if (pkg !in cachedMonitoredPackages) return
        if (!cachedBlockingEnabled) return
        if (System.currentTimeMillis() < cachedPauseUntil) return

        val used = repo.getUsedSeconds(pkg)
        val limitSec = repo.getLimitSeconds(pkg)
        if (used >= limitSec) {
            Log.i(TAG, "$pkg already at limit ($used/$limitSec s) — launching BlockedActivity immediately")
            launchBlockedActivity(pkg)
            stopTimerIfRunning(reason = "limit reached on launch for $pkg")
        }
    }

    private suspend fun tickForPackage(pkg: String) {
        // Refresh state each tick — cheap DataStore reads are cached in memory
        cachedBlockingEnabled = repo.blockingEnabledFlow.first()
        cachedPauseUntil = repo.pauseUntilFlow.first()
        cachedMonitoredPackages = repo.getMonitoredPackages()

        // If the package was removed from the monitored list mid-session, stop
        if (pkg !in cachedMonitoredPackages) {
            stopTimerIfRunning(reason = "$pkg removed from monitored list")
            return
        }

        // Increment and persist (also handles hour rollover inside repo)
        val used = repo.incrementUsedSeconds(pkg)

        // Skip blocking checks if master toggle off or paused
        if (!cachedBlockingEnabled) return
        if (System.currentTimeMillis() < cachedPauseUntil) return

        val limitSec = repo.getLimitSeconds(pkg)
        if (used >= limitSec) {
            Log.i(TAG, "$pkg reached limit ($used/$limitSec s) — launching BlockedActivity")
            launchBlockedActivity(pkg)
            // Stop the timer — BlockedActivity takes over until the next clock hour
            stopTimerIfRunning(reason = "limit reached for $pkg")
        }
    }

    /**
     * Cancels the running timer job, if any, and resets [currentForegroundPkg].
     * Safe to call even if no timer is running.
     */
    private fun stopTimerIfRunning(reason: String = "") {
        if (timerJob?.isActive == true) {
            Log.d(TAG, "Stopping timer. Reason: $reason")
            timerJob?.cancel()
        }
        timerJob = null
        currentForegroundPkg = null
    }

    // ── Actions ────────────────────────────────────────────────────────────

    /**
     * Launches [BlockedActivity] with FLAG_ACTIVITY_NEW_TASK so it overlays
     * the monitored app without needing SYSTEM_ALERT_WINDOW permission.
     * This is the approved pattern for accessibility services that need to
     * bring their own UI to the foreground.
     */
    private fun launchBlockedActivity(pkg: String) {
        try {
            val intent = Intent(applicationContext, BlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, pkg)
            }
            applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity for $pkg", e)
        }
    }

    /**
     * Starts HourLockForegroundService using startForegroundService() on API 26+.
     * The foreground service keeps the process alive and shows a persistent
     * notification, which prevents Samsung / Xiaomi / Huawei aggressive battery
     * management from killing our AccessibilityService mid-session.
     */
    private fun startForegroundServiceCompat() {
        try {
            val intent = Intent(applicationContext, HourLockForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }
}
