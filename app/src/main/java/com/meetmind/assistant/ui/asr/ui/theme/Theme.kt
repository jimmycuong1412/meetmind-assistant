package com.meetmind.assistant.ui.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.meetmind.assistant.domain.model.ThemeMode

/**
 * MeetMind Assistant Material 3 Theme
 *
 * Warm parchment system - see DESIGN.md for the full specification.
 *
 * Design philosophy:
 * - Color restraint: terracotta only for primary actions
 * - Warm neutrals throughout; no cool blue-grays
 * - Borders over shadows (DESIGN.md section 5)
 * - Explicit surfaceContainer tiers for layering
 * - Both schemes fully specified and contrast-verified
 */

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,

    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,

    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,

    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,

    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,

    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,

    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,

    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,

    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,

    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,

    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,

    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,

    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,

    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,

    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

/**
 * Semantic colors that Material 3 has no role for.
 *
 * M3 ships `error` but nothing for success or warning, so these travel alongside
 * the color scheme instead of being hardcoded at call sites. Read them through
 * [semanticColors] so both themes resolve correctly:
 *
 * ```
 * tint = MaterialTheme.semanticColors.success
 * ```
 *
 * Values and verified contrast ratios: DESIGN.md §7.1.
 */
@Immutable
data class SemanticColors(
    val success: Color,
    val warning: Color,
    /** Primary text/icons on a brand gradient. */
    val onGradient: Color,
    /** Secondary text on a brand gradient (80% alpha - AA-safe for bodySmall). */
    val onGradientVariant: Color,
    /** Hairline divider on a brand gradient. */
    val onGradientDivider: Color,
    /** Translucent glass fill for pills sitting on a brand gradient. */
    val onGradientScrim: Color,
)

// The onGradient* values are identical in both schemes: a brand gradient is a dark
// ground whichever theme is active, so its content colors do not flip.
private val LightSemanticColors = SemanticColors(
    success = SuccessLight,
    warning = WarningLight,
    onGradient = OnGradient,
    onGradientVariant = OnGradientVariant,
    onGradientDivider = OnGradientDivider,
    onGradientScrim = OnGradientScrim,
)

private val DarkSemanticColors = SemanticColors(
    success = SuccessDark,
    warning = WarningDark,
    onGradient = OnGradient,
    onGradientVariant = OnGradientVariant,
    onGradientDivider = OnGradientDivider,
    onGradientScrim = OnGradientScrim,
)

private val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** Semantic colors for the active theme. Companion to `MaterialTheme.colorScheme`. */
val MaterialTheme.semanticColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

@Composable
fun LibellulaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Dynamic color is available on Android 12+ (Material You).
    // Off by default: Material You would discard the warm palette this app is built on.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Determine if dark theme should be active based on theme mode
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

// Backward compatibility alias
@Composable
fun SimulateStreamingAsrTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = LibellulaTheme(
    themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
    dynamicColor = dynamicColor,
    content = content
)
