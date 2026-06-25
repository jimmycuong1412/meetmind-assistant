package com.meetmind.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width tiers for [ResponsiveContent].
 *
 * - [Reading]: caps content at a comfortable reading width on large screens
 *   (tablets/foldables) so text columns and controls do not over-stretch.
 * - [Wide]: no cap — content uses the full available width. Used for immersive
 *   surfaces like the live transcription and insights views.
 */
enum class ContentWidth(val max: Dp) {
    Reading(600.dp),
    Wide(Dp.Infinity),
}

/**
 * Constrains [content] to a maximum width and centers it horizontally.
 *
 * On phones (narrower than [width].max) this is effectively a no-op: the inner
 * column fills the available width. On wide screens it caps the column at
 * [ContentWidth.max] and centers it, leaving symmetric margins.
 *
 * This is the single entry point for the app's large-screen width behavior so
 * the policy is consistent and reversible.
 */
@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    width: ContentWidth = ContentWidth.Reading,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = width.max),
            verticalArrangement = verticalArrangement,
        ) {
            content()
        }
    }
}

@Preview(name = "Phone", widthDp = 360, heightDp = 640)
@Preview(name = "Large phone", widthDp = 412, heightDp = 915)
@Preview(name = "Tablet", widthDp = 800, heightDp = 1280)
@Composable
private fun ResponsiveContentPreview() {
    ResponsiveContent {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Placeholder content for preview rendering.
        }
    }
}
