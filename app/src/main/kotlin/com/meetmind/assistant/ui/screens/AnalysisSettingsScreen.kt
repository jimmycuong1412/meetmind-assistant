// T026 (spec 008): Settings screen for continuous conversation analysis configuration
package com.meetmind.assistant.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.data.model.AnalysisSettings
import com.meetmind.assistant.viewmodel.AnalysisSettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSettingsScreen(
    viewModel: AnalysisSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation Analysis") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Enable / disable toggle
            EnabledToggleRow(
                enabled = settings.analysisEnabled,
                onToggle = { viewModel.setEnabled(it) }
            )

            Spacer(Modifier.height(24.dp))

            // Cadence interval slider
            SettingsSlider(
                label = "Analysis interval",
                value = settings.analysisIntervalS.toFloat(),
                valueRange = AnalysisSettings.MIN_INTERVAL_S.toFloat()..AnalysisSettings.MAX_INTERVAL_S.toFloat(),
                unitLabel = "${settings.analysisIntervalS}s",
                onValueChangeFinished = { viewModel.setIntervalS(it) },
                enabled = settings.analysisEnabled,
                testTag = "slider_interval"
            )

            Spacer(Modifier.height(16.dp))

            // Window size slider
            SettingsSlider(
                label = "Transcript window",
                value = settings.analysisWindowS.toFloat(),
                valueRange = AnalysisSettings.MIN_WINDOW_S.toFloat()..AnalysisSettings.MAX_WINDOW_S.toFloat(),
                unitLabel = "${settings.analysisWindowS}s",
                onValueChangeFinished = { viewModel.setWindowS(it) },
                enabled = settings.analysisEnabled,
                testTag = "slider_window"
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "When enabled, MeetMind analyzes the last ${settings.analysisWindowS}s of " +
                    "conversation every ${settings.analysisIntervalS}s and surfaces action items, " +
                    "decisions, and confusion signals proactively.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnabledToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Continuous analysis",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Proactively detect action items, decisions and confusion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag("toggle_analysis_enabled")
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unitLabel: String,
    onValueChangeFinished: (Int) -> Unit,
    enabled: Boolean,
    testTag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = unitLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = { /* live update handled by onValueChangeFinished */ },
            onValueChangeFinished = { onValueChangeFinished(value.roundToInt()) },
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
