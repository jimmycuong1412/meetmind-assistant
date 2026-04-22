// T025: Home screen — session start, cloud toggle, settings navigation
package com.meetmind.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartSession: () -> Unit,
    onOpenCloudSettings: () -> Unit,
    onOpenModelSetup: () -> Unit = {}  // T035 (spec 007): navigate to On-Device Model screen
) {
    val cloudConfig by viewModel.activeCloudConfig.collectAsStateWithLifecycle()
    val isCloudEnabled by viewModel.isCloudEnabled.collectAsStateWithLifecycle()

    val isKeyConfigured = cloudConfig.encryptedApiKey != null &&
        cloudConfig.connectionStatus == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MeetMind") },
                actions = {
                    // T035 (spec 007): On-Device Model setup entry
                    IconButton(onClick = onOpenModelSetup) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "On-Device Model"
                        )
                    }
                    IconButton(onClick = onOpenCloudSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Cloud AI Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Cloud mode toggle — disabled with tooltip when no key configured
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Cloud AI Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (isKeyConfigured) cloudConfig.provider.name else "No key configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isKeyConfigured) {
                    // Show tooltip explaining why toggle is disabled
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("Add API key in Settings") }
                        },
                        state = tooltipState
                    ) {
                        Switch(
                            checked = false,
                            onCheckedChange = null, // disabled
                            enabled = false,
                            modifier = Modifier.testTag("cloud_mode_toggle")
                        )
                    }
                } else {
                    Switch(
                        checked = isCloudEnabled,
                        onCheckedChange = { viewModel.onToggleCloud(it) },
                        modifier = Modifier.testTag("cloud_mode_toggle")
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onStartSession,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Session")
            }
        }
    }
}
