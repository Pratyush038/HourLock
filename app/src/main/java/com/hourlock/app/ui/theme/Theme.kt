package com.hourlock.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Brand colors ───────────────────────────────────────────────────────────────

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// Custom dark scheme tuned for HourLock's deep purple night aesthetic
private val HourLockDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFE9D5FF),

    secondary = Color(0xFFBB86FC),
    onSecondary = Color(0xFF1A0040),
    secondaryContainer = Color(0xFF2D1B69),
    onSecondaryContainer = Color(0xFFDDD6FE),

    tertiary = Color(0xFF9B7FCA),
    onTertiary = Color(0xFF120D28),

    background = Color(0xFF0A0A1A),
    onBackground = Color.White,

    surface = Color(0xFF120D28),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1040),
    onSurfaceVariant = Color(0xFFBBB3D0),

    error = Color(0xFFEF5350),
    onError = Color.White,

    outline = Color(0xFF4A2D82),
    outlineVariant = Color(0xFF2D1B69),
)

// Light scheme (unlikely to be used since the app is dark-first, but required
// for completeness and for users who force light mode at the system level)
private val HourLockLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun HourLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — we use our curated purple palette instead.
    // Dynamic color on Android 12+ would override our brand colors with
    // the user's wallpaper-derived colors, which may not look good.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> HourLockDarkColorScheme
        else -> HourLockLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set the status bar color to match our background
            window.statusBarColor = colorScheme.background.toArgb()
            // Light icons = false → white icons on our dark background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
