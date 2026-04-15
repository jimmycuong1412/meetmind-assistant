// T020: Cloud AI Settings screen — provider selection, API key entry, validation status
package com.meetmind.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.CloudProviderConfig
import com.meetmind.assistant.viewmodel.CloudSettingsViewModel
import com.meetmind.assistant.viewmodel.ValidationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    viewModel: CloudSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val geminiConfig by viewModel.geminiConfig.collectAsStateWithLifecycle()
    val claudeConfig by viewModel.claudeConfig.collectAsStateWithLifecycle()
    val validationState by viewModel.validationState.collectAsStateWithLifecycle()

    val activeConfig = if (selectedProvider == CloudProvider.GEMINI) geminiConfig else claudeConfig

    var keyText by remember(selectedProvider) { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud AI Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text("Select Provider", style = MaterialTheme.typography.titleMedium)

            CloudProvider.entries.forEach { provider ->
                val config = if (provider == CloudProvider.GEMINI) geminiConfig else claudeConfig
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedProvider == provider,
                        onClick = { viewModel.onSelectProvider(provider) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = config.statusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = config.statusColor()
                        )
                    }
                    if (config.encryptedApiKey != null) {
                        IconButton(onClick = { viewModel.onDeleteKey(provider) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove ${provider.displayName} key",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("API Key", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${selectedProvider.displayName} API Key") },
                placeholder = { Text("Paste your API key here") },
                visualTransformation = if (keyVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "Hide key" else "Show key"
                        )
                    }
                },
                singleLine = true
            )

            Spacer(Modifier.height(4.dp))

            // Validation status row
            when (val state = validationState) {
                is ValidationState.Idle -> Unit
                is ValidationState.Validating -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Validating…", style = MaterialTheme.typography.bodySmall)
                }
                is ValidationState.Success -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Connected ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is ValidationState.Failure -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Error: ${state.reason.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { viewModel.onSaveKey(selectedProvider, keyText) },
                modifier = Modifier.fillMaxWidth(),
                enabled = keyText.isNotBlank() && validationState !is ValidationState.Validating
            ) {
                Text("Save & Validate")
            }

            if (activeConfig.encryptedApiKey != null) {
                TextButton(
                    onClick = { viewModel.onDeleteKey(selectedProvider) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Remove ${selectedProvider.displayName} Key",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// --- Display helpers ---

private val CloudProvider.displayName: String
    get() = when (this) {
        CloudProvider.GEMINI -> "Gemini (Google)"
        CloudProvider.CLAUDE -> "Claude (Anthropic)"
    }

private val CloudProviderConfig.statusLabel: String
    get() = when {
        encryptedApiKey == null -> "Not configured"
        connectionStatus == true -> "Connected ✓"
        connectionStatus == false -> "Error: invalid key"
        else -> "Not validated"
    }

@Composable
private fun CloudProviderConfig.statusColor() = when {
    encryptedApiKey == null -> MaterialTheme.colorScheme.onSurfaceVariant
    connectionStatus == true -> MaterialTheme.colorScheme.primary
    connectionStatus == false -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val com.meetmind.assistant.inference.ValidationResult.label: String
    get() = when (this) {
        com.meetmind.assistant.inference.ValidationResult.SUCCESS -> "Success"
        com.meetmind.assistant.inference.ValidationResult.INVALID_KEY -> "Invalid key"
        com.meetmind.assistant.inference.ValidationResult.NETWORK_ERROR -> "Network error"
        com.meetmind.assistant.inference.ValidationResult.PROVIDER_ERROR -> "Provider error"
    }
