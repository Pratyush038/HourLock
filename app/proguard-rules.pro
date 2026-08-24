-keep class com.hourlock.app.UsageTrackerService { *; }
-keep class com.hourlock.app.HourLockForegroundService { *; }
-keep class com.hourlock.app.BlockedActivity { *; }
-keep class com.hourlock.app.MainActivity { *; }
-keep class com.hourlock.app.HourLockApplication { *; }

# DataStore - keep preferences keys from being obfuscated
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences$Key { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
