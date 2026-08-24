# HourLock — Setup Guide

> **HourLock** limits usage of specific apps (default: Instagram) to a configurable number of minutes per rolling clock hour. It works entirely offline — no data ever leaves your device.

---

## Prerequisites

- **Android Studio Hedgehog** (2023.1.1) or newer
- An Android device or emulator running **Android 8.0 (API 26)** or higher
- A USB cable / ADB connection for sideloading (Play Store not required)

---

## Building

1. Open the `HourLock/` folder in Android Studio as an existing project.
2. Let Gradle sync complete (it will download ~150 MB of dependencies on first run).
3. Click **Run ▶** or use `./gradlew installDebug` from the project root.

> **Emulator note:** AccessibilityServices and battery optimization behavior differ significantly on emulators. Test on a real device for accurate results.

---

## Required Manual Setup Steps

HourLock requires **three** special permissions that Android does not grant automatically — each must be enabled manually in system settings. The **Settings screen** inside the app shows live status and links directly to each permission page.

### 1. Accessibility Service (REQUIRED — core feature won't work without this)

This is the most critical permission. Without it, HourLock cannot detect when a monitored app is in the foreground.

**Steps:**
1. Open the HourLock app → tap **Settings & Permissions**
2. Tap **Accessibility Service → Enable**
3. In the system Accessibility Settings, find **"HourLock Screen Time"** under "Downloaded apps"
4. Tap it → toggle it **ON** → confirm the dialog that appears

> **What HourLock can see:** Only which app is currently in the foreground (the package name). It cannot see your screen content, read text, or interact with any other app.

### 2. Usage Access (REQUIRED for "Today's total" stat on Home screen)

Without this, the daily usage stat shows "0s" but blocking still works.

**Steps:**
1. Tap **Usage Access → Enable** in HourLock Settings
2. Find **HourLock** in the list → toggle **ON**

### 3. Battery Optimization Exemption (STRONGLY RECOMMENDED)

Without this, Samsung, Xiaomi, Huawei, and Oppo devices may kill HourLock's background service within 5–30 minutes of the screen being off, silently disabling blocking.

**Steps:**
1. Tap **Battery Optimization Exempt → Enable** in HourLock Settings
2. A system dialog will appear asking to exempt HourLock → tap **Allow**

**Alternative manual path (if the dialog doesn't appear):**
> Settings → Apps → HourLock → Battery → Unrestricted

### 4. Notifications (Android 13+ only)

On first launch on Android 13+, you'll see a system notification permission prompt. Tap **Allow** to enable the persistent foreground service notification. Without it, the foreground service cannot display its notification on Android 13+ and may be killed.

---

## OEM-Specific Battery Management

Some Android skins add extra layers of battery management beyond the standard Android setting:

| OEM | Setting Location |
|-----|-----------------|
| **Samsung** | Settings → Device Care → Battery → Background usage limits → Never sleeping apps → Add HourLock |
| **Xiaomi / MIUI** | Settings → Apps → HourLock → Battery saver → No restrictions |
| **Huawei** | Settings → Battery → App launch → HourLock → Manage manually → all three toggles ON |
| **Oppo / OnePlus** | Settings → Battery → Battery optimization → HourLock → Don't optimize |

---

## First-Run Checklist

- [ ] Build and install the APK
- [ ] Open HourLock → Settings
- [ ] Enable **Accessibility Service** ✓
- [ ] Enable **Usage Access** ✓
- [ ] Request **Battery Optimization Exemption** ✓
- [ ] (Android 13+) Allow **Notification permission** ✓
- [ ] OEM battery setting exemption (if applicable)
- [ ] Return to Home screen → verify the progress ring shows and the toggle is green

---

## Architecture Overview

```
HourLock/
├── app/src/main/java/com/hourlock/app/
│   ├── HourLockApplication.kt       # Application class (lightweight init)
│   ├── MainActivity.kt              # Single-Activity Compose nav host
│   ├── HomeScreen.kt                # Dashboard: ring, toggle, today stat
│   ├── SettingsScreen.kt            # Permissions, apps, safety controls
│   ├── BlockedActivity.kt           # Full-screen lock overlay + challenge UI
│   ├── UsageTrackerService.kt       # AccessibilityService — event listener
│   ├── HourLockForegroundService.kt # Keep-alive foreground service
│   ├── PrefsRepository.kt           # DataStore layer — all persisted state
│   └── ui/theme/                    # Material 3 theme, colors, typography
├── app/src/main/res/
│   ├── xml/accessibility_service_config.xml
│   ├── xml/data_extraction_rules.xml
│   ├── xml/backup_rules.xml
│   └── values/ {strings, themes, colors}
└── app/build.gradle.kts             # Kotlin DSL build config
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **AccessibilityService** over UsageStatsManager polling | Real-time (no polling delay), no background-thread overhead |
| **DataStore** over SharedPreferences | Coroutine-safe, atomic writes, Flow-based reactive reads |
| **Per-tick persistence** (every 1s) | Process can be killed at any moment; at most 1s of data lost |
| **FLAG_ACTIVITY_NEW_TASK** for BlockedActivity | Allows overlay without SYSTEM_ALERT_WINDOW permission |
| **NEVER_BLOCK_PACKAGES** hardcoded denylist | Prevents misconfiguration from locking users out of calls/settings |
| **SupervisorJob** in AccessibilityService | Timer crash for one app doesn't stop tracking for others |
| **canRetrieveWindowContent=false** in XML | Framework-level guarantee we can't read other apps' UI trees |

---

## Privacy Guarantees

- **Zero network permissions** — no `INTERNET`, no `ACCESS_NETWORK_STATE`
- **No analytics, no crash reporting** — all errors are logged locally via `android.util.Log`
- **No cloud backup** — DataStore data excluded from Google Drive backup
- **No screen content access** — `canRetrieveWindowContent="false"` in the a11y config
- **No touch injection** — no `dispatchGesture()` or touch interception in any code path

---

## Troubleshooting

**HourLock stops blocking after the phone screen is off for a while**
→ Complete the OEM battery management steps above. Samsung in particular kills background services aggressively.

**The Blocked screen flashes briefly and then disappears**
→ This can happen if the monitored app handles `onPause()` by returning to the foreground. Try setting the unlock mode to "Wait 30 seconds" in Settings.

**"Today's total" always shows 0**
→ Usage Access permission is not granted. Go to Settings → enable it.

**The accessibility service disappears from the list after a reboot**
→ This is a known issue on some Xiaomi MIUI/HyperOS versions. Grant "Autostart" permission in MIUI Security app for HourLock.
