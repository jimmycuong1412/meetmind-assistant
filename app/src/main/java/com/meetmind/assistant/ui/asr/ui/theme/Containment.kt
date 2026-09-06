package com.meetmind.assistant.ui.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Containment helpers - DESIGN.md section 5.
 *
 * The system separates surfaces by drawing them, not by lighting them: a hairline
 * outline plus a surface-tone step, rather than a drop shadow. Material 3 elevation is
 * off everywhere except things that genuinely float (FAB, modal sheets, menus, dialogs).
 *
 * Use these instead of hand-rolling `CardDefaults.cardElevation(...)` + border pairs, so
 * the ring weight and tone stay consistent across screens.
 *
 * ```
 * Card(
 *     colors = containedCardColors(),
 *     elevation = flatCardElevation(),
 *     border = containmentRing(),
 * )
 * ```
 */

/** Zero elevation in every interaction state. */
@Composable
fun flatCardElevation(): CardElevation = CardDefaults.cardElevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp,
    draggedElevation = 0.dp,
    disabledElevation = 0.dp,
)

/**
 * The standard hairline ring.
 *
 * [emphasised] uses `outline` instead of `outlineVariant` for containers that need to
 * read as a distinct object rather than a quiet subdivision.
 */
@Composable
@ReadOnlyComposable
fun containmentRing(emphasised: Boolean = false): BorderStroke = BorderStroke(
    width = 1.dp,
    color = if (emphasised) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    },
)

/**
 * Card colors for a contained card.
 *
 * Defaults to `surfaceContainer` - one tone step above the page - which is what gives
 * the card presence once its shadow is gone.
 */
@Composable
fun containedCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)
