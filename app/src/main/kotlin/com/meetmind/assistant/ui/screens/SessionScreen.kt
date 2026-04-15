// T026: Session screen — displays live suggestion with non-dismissible CloudBadge
package com.meetmind.assistant.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.ui.components.CloudBadge
import com.meetmind.assistant.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onEndSession: () -> Unit
) {
    val badgeState by viewModel.cloudBadgeState.collectAsStateWithLifecycle()
    val suggestionTokens by viewModel.currentSuggestionTokens.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session") },
                actions = {
                    // T026: CloudBadge fixed in top-right of suggestion area, non-dismissible
                    CloudBadge(
                        state = badgeState,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = onEndSession) {
                        Icon(Icons.Default.Close, contentDescription = "End session")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Suggestion display area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (suggestionTokens.isNotEmpty()) {
                    SuggestionCard(
                        text = suggestionTokens,
                        badgeState = badgeState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    Text(
                        text = "Listening for questions…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Manual question input — lets users trigger end-to-end inference without real audio.
            // This acts as a demo mode until the VAD/ASR pipeline (spec 004) is wired in.
            QuestionInputRow(onSubmit = { viewModel.onQuestionDetected(it) })
        }
    }
}

@Composable
private fun QuestionInputRow(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a question…") },
            singleLine = true,
            label = { Text("Question") }
        )
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSubmit(text)
                    text = ""
                }
            },
            enabled = text.isNotBlank()
        ) {
            Icon(Icons.Default.Send, contentDescription = "Submit question")
        }
    }
}

@Composable
fun SuggestionCard(
    text: String,
    badgeState: com.meetmind.assistant.data.model.CloudBadgeState,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            CloudBadge(state = badgeState)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
