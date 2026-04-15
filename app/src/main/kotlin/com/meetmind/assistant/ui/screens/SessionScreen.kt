// T026: Session screen — displays live suggestion with non-dismissible CloudBadge
package com.meetmind.assistant.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (suggestionTokens.isNotEmpty()) {
                SuggestionCard(
                    text = suggestionTokens,
                    badgeState = badgeState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
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
