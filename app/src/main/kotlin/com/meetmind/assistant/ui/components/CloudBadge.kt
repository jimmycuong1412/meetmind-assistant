// T023: Reusable CloudBadge Compose component — mandatory per constitution v2.0
package com.meetmind.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meetmind.assistant.data.model.CloudBadgeState

/**
 * Displays the cloud inference origin badge.
 *
 * Per constitution v2.0 Principle VI: this badge is NON-DISMISSIBLE and must be
 * visible whenever a suggestion is produced via cloud inference.
 *
 * - [CloudBadgeState.HIDDEN]       → renders nothing (on-device suggestion)
 * - [CloudBadgeState.CLOUD_ACTIVE] → "☁ Cloud" badge in primary colour
 * - [CloudBadgeState.FALLBACK]     → "⚠ Local" badge in warning colour
 */
@Composable
fun CloudBadge(
    state: CloudBadgeState,
    modifier: Modifier = Modifier
) {
    when (state) {
        CloudBadgeState.HIDDEN -> Unit

        CloudBadgeState.CLOUD_ACTIVE -> BadgeChip(
            icon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(12.dp)) },
            label = "Cloud",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = modifier
        )

        CloudBadgeState.FALLBACK -> BadgeChip(
            icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp)) },
            label = "Local",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = modifier
        )
    }
}

@Composable
private fun BadgeChip(
    icon: @Composable () -> Unit,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
