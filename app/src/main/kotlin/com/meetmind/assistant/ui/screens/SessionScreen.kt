// T026: Session screen — displays live suggestion with non-dismissible CloudBadge
// T016 (spec 008): Added analysis event cards (ActionItem / Decision / Confusion / Question)
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.analysis.AnalysisEvent
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
    val analysisEvent by viewModel.analysisEvent.collectAsStateWithLifecycle()

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
            // Suggestion display area — question suggestion takes priority over analysis card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    // T017 (spec 008): Question-detection suggestion takes priority
                    suggestionTokens.isNotEmpty() -> {
                        SuggestionCard(
                            text = suggestionTokens,
                            badgeState = badgeState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    // T016 (spec 008): Analysis event card (ActionItem / Decision / Confusion)
                    analysisEvent != null && analysisEvent !is AnalysisEvent.NoSignal -> {
                        AnalysisEventCard(
                            event = analysisEvent!!,
                            onDismiss = { viewModel.dismissAnalysisEvent() },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    else -> {
                        Text(
                            text = "Listening for questions…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Manual question input — lets users trigger end-to-end inference without real audio.
            // This acts as a demo mode until the VAD/ASR pipeline (spec 004) is wired in.
            QuestionInputRow(onSubmit = { viewModel.onQuestionDetected(it) })
        }
    }
}

/**
 * T016 (spec 008): Card showing the result of a continuous analysis tick.
 * Displays a type-specific emoji label and the suggestion text (if available).
 */
@Composable
fun AnalysisEventCard(
    event: AnalysisEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, text) = when (event) {
        is AnalysisEvent.ActionItem -> "📋 Action Item" to (event.suggestionText ?: event.text)
        is AnalysisEvent.Decision   -> "✅ Decision"    to (event.suggestionText ?: event.text)
        is AnalysisEvent.Confusion  -> "❓ Unclear"     to (event.suggestionText ?: event.text)
        is AnalysisEvent.Question   -> "💬 Question"    to (event.suggestionText)
        is AnalysisEvent.NoSignal   -> return  // NoSignal cards are never shown
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analysis_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("analysis_card_dismiss")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
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
            modifier = Modifier
                .weight(1f)
                .testTag("question_input"),
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
