package com.hourlock.app

import android.app.Application
import android.util.Log

/**
 * HourLockApplication
 * ────────────────────
 * Custom Application class used for:
 *  - Initializing the DataStore singleton (via the [dataStore] extension delegate
 *    declared in PrefsRepository.kt). The extension delegate is lazy and
 *    process-scoped, so it's safe to access from any component.
 *  - Providing a single logging tag for app-wide crash reporting (if added later).
 *
 * NOTE: We intentionally keep this class lean. Heavy initialization
 * (network, DB migrations, etc.) does NOT belong here for HourLock since
 * the app has no network and uses DataStore which initializes on first access.
 */
class HourLockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("HourLock", "Application created (process: ${android.os.Process.myPid()})")
        // DataStore is initialized lazily on first access — nothing to do here.
    }
}
