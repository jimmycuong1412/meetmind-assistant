package com.meetmind.assistant.ui.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Material 3 color palette for MeetMind Assistant - warm parchment system.
 *
 * Full specification, including every verified contrast ratio: DESIGN.md section 2.
 *
 * Design philosophy:
 * - Warm neutrals only. Every gray carries a yellow-brown undertone; no cool blue-grays.
 * - One accent: terracotta. Type and spacing carry hierarchy, not color.
 * - Containment over elevation - borders and hairline rings, not drop shadows.
 *
 * Do not reference these values from UI code. Always read through
 * `MaterialTheme.colorScheme`, `MaterialTheme.semanticColors` or `recordingModeAccent()`,
 * so both themes resolve correctly.
 */

// -- Brand --------------------------------------------------------------------
/**
 * The brand terracotta. Accent use only - icons, strokes, chart marks, large display
 * text (>=24sp). White on this is 3.90:1 and fails AA for body text, which is why
 * [TerracottaDeep] exists and takes the `primary` role. See DESIGN.md section 2.4.
 */
val Terracotta = Color(0xFFC96442)

/** AA-safe terracotta for filled surfaces that carry text. White on this is 5.00:1. */
val TerracottaDeep = Color(0xFFB35334)

/** Dark-scheme accent. Terracotta muddies against warm near-black; coral holds up. */
val Coral = Color(0xFFD97757)

// -- Light scheme -------------------------------------------------------------
val md_theme_light_primary = Color(0xFFB35334)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFF2DDD3)
val md_theme_light_onPrimaryContainer = Color(0xFF4A1D0C)

val md_theme_light_secondary = Color(0xFF5E5D59)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8E6DC)
val md_theme_light_onSecondaryContainer = Color(0xFF33322E)

val md_theme_light_tertiary = Color(0xFF6B705C)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFE3E5DA)
val md_theme_light_onTertiaryContainer = Color(0xFF2A2D23)

val md_theme_light_error = Color(0xFFB53333)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFF7DDD9)
val md_theme_light_onErrorContainer = Color(0xFF4A1210)

val md_theme_light_background = Color(0xFFF5F4ED)   // parchment
val md_theme_light_onBackground = Color(0xFF141413)
val md_theme_light_surface = Color(0xFFFAF9F5)      // ivory
val md_theme_light_onSurface = Color(0xFF141413)
val md_theme_light_surfaceVariant = Color(0xFFE8E6DC)
val md_theme_light_onSurfaceVariant = Color(0xFF5E5D59)

val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFFAF9F5)
val md_theme_light_surfaceContainer = Color(0xFFF2F1E9)
val md_theme_light_surfaceContainerHigh = Color(0xFFECEBE1)
val md_theme_light_surfaceContainerHighest = Color(0xFFE8E6DC)

val md_theme_light_outline = Color(0xFF87867F)
val md_theme_light_outlineVariant = Color(0xFFDEDCD1)
val md_theme_light_scrim = Color(0xFF141413)

// -- Dark scheme --------------------------------------------------------------
// Not an inversion. Terracotta darkens badly on warm near-black, so the accent shifts
// to the lighter coral family.
val md_theme_dark_primary = Color(0xFFE08A68)
val md_theme_dark_onPrimary = Color(0xFF3D1A0B)
val md_theme_dark_primaryContainer = Color(0xFF7A3A22)
val md_theme_dark_onPrimaryContainer = Color(0xFFF7DDD1)

val md_theme_dark_secondary = Color(0xFFC9C6BC)
val md_theme_dark_onSecondary = Color(0xFF33322E)
val md_theme_dark_secondaryContainer = Color(0xFF45443F)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8E6DC)

val md_theme_dark_tertiary = Color(0xFFBFC4B0)
val md_theme_dark_onTertiary = Color(0xFF2A2D23)
val md_theme_dark_tertiaryContainer = Color(0xFF414539)
val md_theme_dark_onTertiaryContainer = Color(0xFFE3E5DA)

val md_theme_dark_error = Color(0xFFF0A094)
val md_theme_dark_onError = Color(0xFF3D0F0C)
val md_theme_dark_errorContainer = Color(0xFF7A2320)
val md_theme_dark_onErrorContainer = Color(0xFFF7DDD9)

val md_theme_dark_background = Color(0xFF141413)
val md_theme_dark_onBackground = Color(0xFFECEAE4)
val md_theme_dark_surface = Color(0xFF1E1D1B)
val md_theme_dark_onSurface = Color(0xFFECEAE4)
val md_theme_dark_surfaceVariant = Color(0xFF45443F)
val md_theme_dark_onSurfaceVariant = Color(0xFFB0AEA5)

val md_theme_dark_surfaceContainerLowest = Color(0xFF0F0F0E)
val md_theme_dark_surfaceContainerLow = Color(0xFF1A1917)
val md_theme_dark_surfaceContainer = Color(0xFF1E1D1B)
val md_theme_dark_surfaceContainerHigh = Color(0xFF30302E)
val md_theme_dark_surfaceContainerHighest = Color(0xFF3A3A37)

val md_theme_dark_outline = Color(0xFF9A978E)
val md_theme_dark_outlineVariant = Color(0xFF45443F)
val md_theme_dark_scrim = Color(0xFF000000)

// -- Semantic colors (no M3 role exists for these) ----------------------------
// Material 3 defines `error` but has no success/warning role, so these are supplied
// per-scheme and surfaced through `MaterialTheme.semanticColors` (see Theme.kt).
//
// Values and verified contrast ratios: DESIGN.md section 7.1. Note WarningLight is
// #8F6415 rather than a brighter ochre - #B3811F measured only 3.13:1 on parchment,
// which fails AA.
val SuccessLight = Color(0xFF4F7A4F)
val SuccessDark = Color(0xFF8FB88F)
val WarningLight = Color(0xFF8F6415)
val WarningDark = Color(0xFFD9AE5C)

// -- Gradient surfaces --------------------------------------------------------
// Full-bleed download / welcome screens, brand headers and the record button.
//
// The lightest stop is #8D3A1E rather than the brand terracotta because secondary text
// on these surfaces is drawn at 80% alpha: over #B35334 that composites to 3.78:1 and
// fails AA for the bodySmall/bodyMedium it is used on. Over #8D3A1E it is 5.47:1. The
// previous violet build had the same defect at 70-75% alpha; it is fixed here rather
// than carried across. See DESIGN.md section 2.5.
val GradientTop = Color(0xFF8D3A1E)
val GradientMid = Color(0xFF6B3320)
val GradientBottom = Color(0xFF241A16)

/** Brand gradient for headers and accent bars. */
val PrimaryGradient = Brush.verticalGradient(
    colors = listOf(GradientTop, GradientMid)
)

/** Vertical brand gradient - header bands that fade into the page. */
val PrimaryGradientVertical = Brush.verticalGradient(
    colors = listOf(GradientTop, GradientMid)
)

/** Higher-contrast gradient for FABs and primary CTAs. */
val AccentGradient = Brush.verticalGradient(
    colors = listOf(GradientTop, GradientBottom)
)

/** Full-screen vertical gradient for the immersive download / welcome states. */
val DownloadImmersiveGradient = Brush.verticalGradient(
    colors = listOf(GradientTop, GradientBottom)
)

// -- Content colors for gradient grounds --------------------------------------
// M3 has no role meaning "on top of a brand gradient", so these live here and are read
// through `MaterialTheme.semanticColors`. They are identical in both themes: a gradient
// ground is dark regardless of the active scheme.

/** Primary text and icons on a gradient. */
val OnGradient = Color(0xFFFFFFFF)

/** Secondary text on a gradient. 80% alpha - the level at which bodySmall still clears AA. */
val OnGradientVariant = Color(0xCCFFFFFF)

/** Hairline divider on a gradient. Non-text, so a lower alpha is fine. */
val OnGradientDivider = Color(0x2EFFFFFF)

/** Translucent glass fill for pills and cards sitting on a gradient. */
val OnGradientScrim = Color(0x24FFFFFF)

// -- DaytimeSkyBanner ---------------------------------------------------------
// Time-of-day sky, rewarmed to the parchment system. Each state is a two-stop
// vertical gradient; hue carries the time, and legibility is handled separately by
// SkyScrimAlpha rather than by keeping every stop dark.
//
// The banner carries white header text. Under the previous cool palette four of the
// five states failed AA - white on the morning sky was 1.33:1 - because the scrim
// started at 45% height while the text sits above it. With a full-height scrim the
// worst case across all states is 5.14:1. See DaytimeSkyBannerContrastTest.
val SkyNight = Color(0xFF1C1A2E) to Color(0xFF0E0D14)
val SkyDawn = Color(0xFF8A4526) to Color(0xFFC4763F)
val SkyMorning = Color(0xFFA8794C) to Color(0xFFD4A574)
val SkyAfternoon = Color(0xFFB07A43) to Color(0xFFE0A868)
val SkyDusk = Color(0xFF8D3A1E) to Color(0xFF4A2418)

/** Uniform veil over the whole banner. The single knob that guarantees legibility. */
const val SkyScrimAlpha = 0.50f

val SkyStar = Color(0xA6FFFFFF)
val SkySunGlow = Color(0xFFE8B04B)
val SkySunDisk = Color(0xFFF0C25E)
val SkyMoonDisk = Color(0xFFE8E2D5)
val SkyMoonCrater = Color(0xFFC9C1B0)
val SkyHillBack = Color(0x4D3A2418)
val SkyHillFront = Color(0xFF3A2418)
