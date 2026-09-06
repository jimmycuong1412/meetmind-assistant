package com.meetmind.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.meetmind.assistant.ui.ui.theme.GradientTop
import com.meetmind.assistant.ui.ui.theme.semanticColors

/**
 * Primary button with a gradient background.
 *
 * [textColor] defaults to the on-gradient content color for the active theme.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = SolidColor(GradientTop),
    textColor: Color? = null,
    enabled: Boolean = true
) {
    val resolvedTextColor = textColor ?: MaterialTheme.semanticColors.onGradient
    Box(
        modifier = modifier
            .background(
                brush = if (enabled) gradient else Brush.linearGradient(
                    colors = listOf(Color.Gray, Color.Gray)
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = resolvedTextColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = resolvedTextColor.copy(alpha = 0.6f)
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
