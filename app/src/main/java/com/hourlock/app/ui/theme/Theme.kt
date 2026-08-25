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

// ── Convenient Direct Aliases to DesignTokens ─────────────────────────────────
val PureBlack = DesignTokens.Palette.PureBlack
val DarkBackground = DesignTokens.Palette.DarkBackground
val DarkSurface = DesignTokens.Palette.DarkSurface
val DarkSurfaceCard = DesignTokens.Palette.DarkCard
val DarkSurfaceElevated = DesignTokens.Palette.DarkElevated
val DarkBorder = DesignTokens.Palette.DarkBorder
val DarkBorderSubtle = DesignTokens.Palette.DarkBorderSubtle

val PureWhite = DesignTokens.Palette.PureWhite
val TextPrimaryDark = DesignTokens.Palette.PureWhite
val TextSecondaryDark = DesignTokens.Palette.GraySecondary
val TextMutedDark = DesignTokens.Palette.GrayMuted

val WarningAccent = DesignTokens.Palette.WarningAccent
val WarningAccentMuted = DesignTokens.Palette.WarningAccentMuted
val WarningAccentBorder = DesignTokens.Palette.WarningAccentBorder

val AccentOrange = DesignTokens.Palette.WarningAccent
val AccentRed = DesignTokens.Palette.StatusError
val AccentGreen = DesignTokens.Palette.StatusSuccess

// ── Dark Scheme (Strict Monochrome + Muted Warning) ───────────────────────────
private val HourLockDarkColorScheme = darkColorScheme(
    primary = DesignTokens.Palette.PureWhite,
    onPrimary = DesignTokens.Palette.PureBlack,
    primaryContainer = DesignTokens.Palette.DarkCard,
    onPrimaryContainer = DesignTokens.Palette.PureWhite,

    secondary = DesignTokens.Palette.GraySecondary,
    onSecondary = DesignTokens.Palette.PureBlack,
    secondaryContainer = DesignTokens.Palette.DarkElevated,
    onSecondaryContainer = DesignTokens.Palette.PureWhite,

    tertiary = DesignTokens.Palette.WarningAccent,
    onTertiary = DesignTokens.Palette.PureWhite,

    background = DesignTokens.Palette.DarkBackground,
    onBackground = DesignTokens.Palette.PureWhite,

    surface = DesignTokens.Palette.DarkSurface,
    onSurface = DesignTokens.Palette.PureWhite,
    surfaceVariant = DesignTokens.Palette.DarkCard,
    onSurfaceVariant = DesignTokens.Palette.GraySecondary,

    error = DesignTokens.Palette.StatusError,
    onError = DesignTokens.Palette.PureWhite,

    outline = DesignTokens.Palette.DarkBorder,
    outlineVariant = DesignTokens.Palette.DarkBorderSubtle,
)

// ── Light Scheme ──────────────────────────────────────────────────────────────
private val HourLockLightColorScheme = lightColorScheme(
    primary = DesignTokens.Palette.PureBlack,
    onPrimary = DesignTokens.Palette.PureWhite,
    primaryContainer = DesignTokens.Palette.LightCard,
    onPrimaryContainer = DesignTokens.Palette.PureBlack,

    secondary = DesignTokens.Palette.GrayMuted,
    onSecondary = DesignTokens.Palette.PureWhite,
    secondaryContainer = DesignTokens.Palette.LightElevated,
    onSecondaryContainer = DesignTokens.Palette.PureBlack,

    tertiary = DesignTokens.Palette.WarningAccent,
    onTertiary = DesignTokens.Palette.PureWhite,

    background = DesignTokens.Palette.LightBackground,
    onBackground = DesignTokens.Palette.PureBlack,

    surface = DesignTokens.Palette.LightSurface,
    onSurface = DesignTokens.Palette.PureBlack,
    surfaceVariant = DesignTokens.Palette.LightCard,
    onSurfaceVariant = DesignTokens.Palette.GrayMuted,

    error = DesignTokens.Palette.StatusError,
    onError = DesignTokens.Palette.PureWhite,

    outline = DesignTokens.Palette.LightBorder,
    outlineVariant = DesignTokens.Palette.LightBorderSubtle,
)

@Composable
fun HourLockTheme(
    darkTheme: Boolean = true, // Default to sleek dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HourLockDarkColorScheme else HourLockLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
