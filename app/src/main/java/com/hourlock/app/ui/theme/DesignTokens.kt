package com.hourlock.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DesignTokens
 * ────────────
 * The single source of truth for HourLock's visual language.
 *
 * Strict monochrome hierarchy with 5+ grayscale steps, single muted warning accent,
 * rounded geometry (28dp / 20dp / 14dp), 4dp spacing grid, and flat border-based elevation.
 */
object DesignTokens {

    // ── 1. Color Palette (Strict Monochrome + Warning Accent) ─────────────────
    object Palette {
        // True & Near Blacks
        val PureBlack = Color(0xFF000000)
        val DarkBackground = Color(0xFF0A0A0A)
        val DarkSurface = Color(0xFF141414)
        val DarkCard = Color(0xFF1A1A1A)
        val DarkElevated = Color(0xFF242424)

        // Borders & Dividers
        val DarkBorder = Color(0xFF2E2E2E)
        val DarkBorderSubtle = Color(0xFF222222)
        val LightBorder = Color(0xFFE5E5E5)
        val LightBorderSubtle = Color(0xFFECECEC)

        // Grayscale Text & Icon Levels
        val GrayMuted = Color(0xFF6B6B6B)
        val GraySecondary = Color(0xFFA1A1AA)
        val GraySubtle = Color(0xFFD4D4D8)
        val GrayLight = Color(0xFFE5E5E5)
        val LightBackground = Color(0xFFF7F7F8)
        val LightSurface = Color(0xFFFFFFFF)
        val LightCard = Color(0xFFF3F3F5)
        val LightElevated = Color(0xFFEBEBEF)
        val PureWhite = Color(0xFFFFFFFF)

        // Single Functional Accent (Muted Amber for Blocked/Warning states ONLY)
        val WarningAccent = Color(0xFFD97706)      // Muted Amber (#D97706)
        val WarningAccentMuted = Color(0x26D97706) // 15% opacity tint for badges
        val WarningAccentBorder = Color(0x66D97706)// 40% opacity border

        // System Permission Indicator Dots ONLY (green/red dot badges)
        val StatusSuccess = Color(0xFF22C55E)
        val StatusSuccessMuted = Color(0x2622C55E)
        val StatusError = Color(0xFFEF4444)
        val StatusErrorMuted = Color(0x26EF4444)
    }

    // ── 2. Spacing Grid (4dp Base Grid) ──────────────────────────────────────
    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 20.dp
        val xxl: Dp = 24.dp
        val xxxl: Dp = 32.dp
        val huge: Dp = 48.dp
    }

    // ── 3. Shapes & Radii (28dp / 20dp / 14dp — No sharp corners) ─────────────
    object Shapes {
        /** Main progress & hero cards */
        val MainCard = RoundedCornerShape(28.dp)

        /** Standard list items, metric cards & modal sheets */
        val Card = RoundedCornerShape(20.dp)

        /** Interactive buttons, chips, search inputs & pill toggles */
        val Button = RoundedCornerShape(14.dp)
        val Chip = RoundedCornerShape(14.dp)

        /** Small badges, tags & mini progress indicators */
        val Badge = RoundedCornerShape(10.dp)

        /** Fully rounded pills / circles */
        val Pill = RoundedCornerShape(50)
    }

    // ── 4. Typography Scale ───────────────────────────────────────────────────
    object Typography {
        /** Display (32sp, SemiBold) — for the big "X min left" center number */
        fun display(fontFamily: FontFamily = FontFamily.Default) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp
        )

        /** Title (20sp, Medium/SemiBold) — for card headers & screen titles */
        fun title(fontFamily: FontFamily = FontFamily.Default) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp
        )

        /** Subtitle (17sp, Medium) */
        fun subtitle(fontFamily: FontFamily = FontFamily.Default) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        )

        /** Body (15sp, Regular) — for descriptions & readable content */
        fun body(fontFamily: FontFamily = FontFamily.Default) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        )

        /** Body Bold (15sp, SemiBold) */
        fun bodyMedium(fontFamily: FontFamily = FontFamily.Default) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        )

        /** Caption (12sp, Regular, uppercase, letter-spaced) — for section labels like "TODAY" / "THIS WEEK" */
        fun caption(fontFamily: FontFamily = FontFamily.Default) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.5.sp
        )

        /** Monospaced Numbers (Tabular Figures) for countdown timers to prevent jitter */
        fun monospacedNumber(
            fontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
            fontWeight: FontWeight = FontWeight.Bold,
            fontFamily: FontFamily = FontFamily.Monospace
        ) = TextStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            letterSpacing = 1.sp
        )
    }

    // ── 5. Border & Elevation (Flat, subtle line borders) ─────────────────────
    object Elevation {
        val borderWidth: Dp = 1.dp
        val hairline: Dp = 0.5.dp
        val thickBorder: Dp = 1.5.dp
    }
}
