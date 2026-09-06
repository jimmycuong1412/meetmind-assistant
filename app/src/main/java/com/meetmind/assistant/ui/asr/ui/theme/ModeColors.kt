package com.meetmind.assistant.ui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.meetmind.assistant.domain.model.RecordingMode

/**
 * Recording-mode accent colors — the single source of truth.
 *
 * DESIGN.md §6 (Option C, two-tier):
 *
 *  - **Capture** modes (Simple Listening, Short Meeting, Long Meeting) all share the brand
 *    terracotta and are told apart by icon and label. They are three variations of one
 *    activity — recording a meeting — so three near-identical terracotta shades bought no
 *    real separation (measured 1.13–1.34 contrast between them; see §6.3) while tripling
 *    the palette.
 *  - **Live-assist** modes (Translation, Interview, English Coach) are genuinely different
 *    activities and each keep a distinct hue.
 *
 * Every value is verified ≥4.5:1 against both the surface and the background of its own
 * theme, because these are used as small label text (e.g. `SessionsScreen`), not only as
 * icon tints. Ratios are tabulated in DESIGN.md §6.2.
 *
 * This replaces three near-duplicate `when (mode)` mappings that previously lived in
 * `SessionsScreen`, `SearchScreen` and `NewSessionDialog`.
 */

// ── Light ────────────────────────────────────────────────────────────────────
private val ModeCaptureLight     = Color(0xFFB35334) // terracotta deep — shared by all 3 capture modes
private val ModeTranslationLight = Color(0xFF4F6F82) // slate blue
private val ModeInterviewLightC  = Color(0xFF734765) // plum
private val ModeCoachLight       = Color(0xFF547449) // moss

// ── Dark ─────────────────────────────────────────────────────────────────────
private val ModeCaptureDark      = Color(0xFFE08A68) // coral
private val ModeTranslationDark  = Color(0xFF8FB0C4)
private val ModeInterviewDarkC   = Color(0xFFC495B4)
private val ModeCoachDark        = Color(0xFF9BBE90)

/**
 * The accent color for [mode] in the active theme.
 *
 * Safe for label text, icon tints and strokes alike — see the contrast note above.
 */
@Composable
@ReadOnlyComposable
fun recordingModeAccent(mode: RecordingMode, dark: Boolean = isAppInDarkTheme()): Color =
    when (mode) {
        RecordingMode.SIMPLE_LISTENING,
        RecordingMode.SHORT_MEETING,
        RecordingMode.LONG_MEETING          -> if (dark) ModeCaptureDark else ModeCaptureLight
        RecordingMode.REAL_TIME_TRANSLATION -> if (dark) ModeTranslationDark else ModeTranslationLight
        RecordingMode.INTERVIEW             -> if (dark) ModeInterviewDarkC else ModeInterviewLightC
        RecordingMode.ENGLISH_COACH         -> if (dark) ModeCoachDark else ModeCoachLight
    }

/**
 * Gradient for [mode], used for the accent bars on session cards.
 *
 * Derived from [recordingModeAccent] rather than hand-tuned per mode, so the gradient can
 * never drift out of sync with the flat tint the way the previous six hardcoded brushes did.
 */
@Composable
@ReadOnlyComposable
fun recordingModeBrush(mode: RecordingMode): Brush {
    val accent = recordingModeAccent(mode)
    return Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.72f)))
}

/**
 * Whether the active color scheme is the dark one.
 *
 * Derived from the scheme itself rather than `isSystemInDarkTheme()`, so it honours an
 * explicit in-app [com.meetmind.assistant.domain.model.ThemeMode] override.
 */
@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

// ── Speaker colors ───────────────────────────────────────────────────────────
// DESIGN.md §7.2. Replaces five raw Material 2 primaries (#2196F3, #4CAF50,
// #FF9800, #9C27B0, #F44336) that were unthemed, cool, and marginal for small text
// on a light ground.
//
// Every value is verified >=4.5:1 against both the surface and the background of its
// own theme. Four are shared with the mode palette above on purpose: one set of warm
// hues serves both, so the app carries fewer colors rather than more.

private val SpeakersLight = listOf(
    Color(0xFFB35334), // terracotta deep
    Color(0xFF4F6F82), // slate blue
    Color(0xFF547449), // moss
    Color(0xFF8A6A2F), // ochre
    Color(0xFF734765), // plum
    Color(0xFF5E5D59), // olive gray
)

private val SpeakersDark = listOf(
    Color(0xFFE08A68),
    Color(0xFF8FB0C4),
    Color(0xFF9BBE90),
    Color(0xFFD4B36A),
    Color(0xFFC495B4),
    Color(0xFFB0AEA5),
)

/**
 * A stable color for [speaker], picked deterministically from the name so the same
 * person keeps the same color for the life of a session.
 */
@Composable
@ReadOnlyComposable
fun speakerAccent(speaker: String): Color {
    val palette = if (isAppInDarkTheme()) SpeakersDark else SpeakersLight
    return palette[(speaker.hashCode() and 0x7FFFFFFF) % palette.size]
}

/** The speaker palette for the active theme — for previews and tests. */
@Composable
@ReadOnlyComposable
fun speakerPalette(): List<Color> = if (isAppInDarkTheme()) SpeakersDark else SpeakersLight
