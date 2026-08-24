package com.hourlock.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Monochromatic Black & White Design Tokens ─────────────────────────────────

val PureBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF09090B)
val DarkSurface = Color(0xFF121215)
val DarkSurfaceCard = Color(0xFF18181C)
val DarkSurfaceElevated = Color(0xFF222228)
val DarkBorder = Color(0xFF27272E)
val DarkBorderSubtle = Color(0xFF1C1C22)

val PureWhite = Color(0xFFFFFFFF)
val TextPrimaryDark = Color(0xFFF4F4F5)
val TextSecondaryDark = Color(0xFFA1A1AA)
val TextMutedDark = Color(0xFF71717A)

// Subtle functional accents
val AccentOrange = Color(0xFFFF5B22) // Reference warm pill badge
val AccentGreen = Color(0xFF22C55E)  // Active status
val AccentRed = Color(0xFFEF4444)    // Blocked alert

// Dark Scheme
private val HourLockDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = DarkSurfaceCard,
    onPrimaryContainer = PureWhite,

    secondary = TextSecondaryDark,
    onSecondary = PureBlack,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = PureWhite,

    tertiary = AccentOrange,
    onTertiary = PureWhite,

    background = DarkBackground,
    onBackground = TextPrimaryDark,

    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = TextSecondaryDark,

    error = AccentRed,
    onError = PureWhite,

    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
)

// Light Scheme
private val HourLockLightColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = PureBlack,

    secondary = Color(0xFF52525B),
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = PureBlack,

    tertiary = AccentOrange,
    onTertiary = PureWhite,

    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF09090B),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF71717A),

    error = AccentRed,
    onError = PureWhite,

    outline = Color(0xFFE4E4E7),
    outlineVariant = Color(0xFFF4F4F5),
)

@Composable
fun HourLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HourLockDarkColorScheme else HourLockLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
