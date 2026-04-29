package com.meetmind.assistant.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.ui.theme.PrimaryGradient
import kotlinx.coroutines.delay

/** How long each message is visible before crossfading to the next. */
private const val CAROUSEL_DISPLAY_MS = 4_000L
private const val CAROUSEL_FADE_IN_MS = 600
private const val CAROUSEL_FADE_OUT_MS = 400

/**
 * Carousel cycling through the core onboarding messages while a download runs.
 *
 * Curated to the 4 highest-value messages — earlier versions had 11 messages, but
 * funnel data shows attention drops sharply after the third, so we lead with the
 * single biggest selling points (privacy, simplicity, AI summaries) and end with
 * a thank-you. The remaining tip-style messages live on a "Tips" screen the user
 * can revisit anytime.
 *
 * Two modes:
 * - Default: gradient rounded card with [PrimaryGradient] background.
 * - Immersive ([immersive] = true): no background card, large text rendered
 *   directly on whatever surface the caller provides (full-screen gradient).
 */
@Composable
fun DownloadOnboardingCarousel(
    modifier: Modifier = Modifier,
    immersive: Boolean = false
) {
    val messages = listOf(
        stringResource(R.string.onboarding_message_5),  // Privacy: works offline, your voice stays private
        stringResource(R.string.onboarding_message_9),  // One tap to start, one tap to stop
        stringResource(R.string.onboarding_message_4),  // Automatic summaries help you focus
        stringResource(R.string.onboarding_message_10), // Thank you for choosing MeetMind Assistant
    )

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(CAROUSEL_DISPLAY_MS)
            currentIndex = (currentIndex + 1) % messages.size
        }
    }

    val animatedContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(CAROUSEL_FADE_IN_MS)) togetherWith
                        fadeOut(animationSpec = tween(CAROUSEL_FADE_OUT_MS))
            },
            label = "download_carousel_msg"
        ) { index ->
            Text(
                text = messages[index],
                style = if (immersive) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (immersive) {
        // No card background — text floats directly on the caller's surface
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            animatedContent()
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryGradient)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            animatedContent()
        }
    }
}
