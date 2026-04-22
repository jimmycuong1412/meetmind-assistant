// T032 (spec 007): On-Device Model Setup screen
// spec 009 — T035: Multi-variant selection UI (GemmaModelVariant rows with download/select)
package com.meetmind.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetmind.assistant.data.model.DownloadProgress
import com.meetmind.assistant.data.model.GemmaModelVariant
import com.meetmind.assistant.data.model.ModelLoadState
import com.meetmind.assistant.data.model.SttModelConfig
import com.meetmind.assistant.viewmodel.ModelSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupScreen(
    viewModel: ModelSetupViewModel,
    onNavigateBack: () -> Unit
) {
    val config by viewModel.modelConfig.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val selectedVariant by viewModel.selectedVariant.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val sttDownloadProgress by viewModel.sttDownloadProgress.collectAsStateWithLifecycle()

    var showAdvanced by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("On-Device Model") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Status chip ───────────────────────────────────────────────────

            item {
                Spacer(Modifier.height(0.dp))
                ModelStatusChip(loadState, modifier = Modifier.testTag("model_status_chip"))
            }

            // ── Variant selection header ───────────────────────────────────────

            item {
                Text(
                    "Select Model Variant",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Choose the quantisation level that fits your device's RAM. " +
                        "The recommended variant is auto-detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── One card per variant ───────────────────────────────────────────

            items(viewModel.variants) { variant ->
                val isSelected = variant == selectedVariant
                val isRecommended = variant == viewModel.recommendedVariant
                val isDownloaded = viewModel.isDownloaded(variant)
                val isDownloading = downloadProgress != null &&
                    downloadProgress!!.filename == variant.llmFilename &&
                    !downloadProgress!!.isComplete

                VariantCard(
                    variant = variant,
                    isSelected = isSelected,
                    isRecommended = isRecommended,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = if (isDownloading) downloadProgress else null,
                    onDownload = { viewModel.startDownload(variant) },
                    onSelect = { viewModel.setVariant(variant) },
                    modifier = Modifier.testTag("variant_card_${variant.name}")
                )
            }

            // ── STT model download section ────────────────────────────────────

            item {
                SttDownloadSection(
                    isSttReady = viewModel.isSttReady(),
                    sttDownloadProgress = sttDownloadProgress,
                    onStartDownload = { viewModel.startSttDownload() },
                    modifier = Modifier.testTag("stt_download_section")
                )
            }

            // ── Load / Unload buttons ─────────────────────────────────────────

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.loadModel() },
                        enabled = loadState !is ModelLoadState.Loading &&
                            loadState !is ModelLoadState.Ready &&
                            viewModel.isDownloaded(selectedVariant),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_model_button")
                    ) {
                        if (loadState is ModelLoadState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                        Text(if (loadState is ModelLoadState.Loading) "Loading…" else "Load Model")
                    }

                    if (loadState is ModelLoadState.Ready) {
                        OutlinedButton(
                            onClick = { viewModel.unloadModel() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unload")
                        }
                    }
                }
            }

            // ── Advanced settings (collapsed by default) ──────────────────────

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Settings", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAdvanced) "Collapse" else "Expand advanced settings"
                        )
                    }
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // CPU Threads
                        Text(
                            "CPU Threads: ${config.nThreads}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = config.nThreads.toFloat(),
                            onValueChange = { viewModel.setNThreads(it.toInt()) },
                            valueRange = 1f..16f,
                            steps = 14,
                            modifier = Modifier.testTag("threads_slider")
                        )
                        Text(
                            "Recommended: 6 for Snapdragon 8 Gen 3 (1×X4 + 5×A720)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        // GPU Layers
                        val gpuDisplay =
                            if (config.nGpuLayers == -1) "All (Adreno 750/GPU)" else "${config.nGpuLayers}"
                        Text("GPU Layers: $gpuDisplay", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "-1 = offload all layers to Adreno GPU via OpenCL (recommended)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Footer spacer
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

/**
 * Card for a single [GemmaModelVariant] with badges and action button.
 *
 * States:
 *  - Not downloaded + not downloading → "Download" button
 *  - Downloading → LinearProgressIndicator + "Downloading…" label
 *  - Downloaded + not selected → "Select" button
 *  - Downloaded + selected → "✓ Selected" chip (no button)
 *
 * spec 009 — T035
 */
@Composable
private fun VariantCard(
    variant: GemmaModelVariant,
    isSelected: Boolean,
    isRecommended: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected    -> MaterialTheme.colorScheme.primary
        isRecommended -> MaterialTheme.colorScheme.tertiary
        else          -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header row: display name + badges ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = variant.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isRecommended) {
                        BadgeChip(
                            label = "Recommended",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (isDownloaded) {
                        BadgeChip(
                            label = "Downloaded",
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // ── Size hint ─────────────────────────────────────────────────────
            Text(
                text = "Size: ~${variant.sizeHintMb / 1024} GB  •  ${variant.llmFilename}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Download progress bar ─────────────────────────────────────────
            if (isDownloading && downloadProgress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val downloadedMb = downloadProgress.bytesDownloaded / 1_000_000
                    val totalMb = if (downloadProgress.totalBytes > 0)
                        "${downloadProgress.totalBytes / 1_000_000} MB"
                    else
                        "? MB"
                    Text(
                        text = "${downloadedMb} MB / $totalMb  " +
                            "(${(downloadProgress.progressFraction * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Action button ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when {
                    isDownloading -> {
                        OutlinedButton(
                            onClick = { /* cancel not yet supported */ },
                            enabled = false
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 4.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Downloading…")
                        }
                    }

                    !isDownloaded -> {
                        Button(onClick = onDownload) {
                            Text("Download")
                        }
                    }

                    isSelected -> {
                        // Selected chip — no action button needed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Selected",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    else -> {
                        OutlinedButton(onClick = onSelect) {
                            Text("Select")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * STT model download section.
 *
 * States:
 *  - Not ready + not downloading → "Download STT Model" button (~200 MB)
 *  - Downloading → file-by-file LinearProgressIndicator + percentage
 *  - Ready → green "STT Model Ready" badge, "Start Session" enabled
 *
 * spec 009 — T048
 */
@Composable
private fun SttDownloadSection(
    isSttReady: Boolean,
    sttDownloadProgress: DownloadProgress?,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSttReady)
                Color(0xFF2E7D32).copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSttReady) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Speech Recognition (STT)",
                    style = MaterialTheme.typography.titleSmall
                )
                if (isSttReady) {
                    BadgeChip(label = "Ready", color = Color(0xFF2E7D32))
                }
            }

            Text(
                text = "Nemo Parakeet TDT 0.6B v3 Int8  •  ~200 MB  •  ${SttModelConfig.STT_FILES.size} files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                isSttReady -> {
                    // Nothing more to show — badge above communicates status
                }

                sttDownloadProgress != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { sttDownloadProgress.progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val downloadedMb = sttDownloadProgress.bytesDownloaded / 1_000_000
                        val totalMb = if (sttDownloadProgress.totalBytes > 0)
                            "${sttDownloadProgress.totalBytes / 1_000_000} MB"
                        else "? MB"
                        Text(
                            text = "${sttDownloadProgress.filename}: ${downloadedMb} MB / $totalMb" +
                                " (${(sttDownloadProgress.progressFraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier.testTag("download_stt_button")
                        ) {
                            Text("Download STT Model")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusChip(state: ModelLoadState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        is ModelLoadState.NotLoaded -> "🔴  Not loaded" to MaterialTheme.colorScheme.error
        is ModelLoadState.Loading   -> "🟡  Loading model…" to MaterialTheme.colorScheme.tertiary
        is ModelLoadState.Ready     -> "🟢  Model ready" to Color(0xFF2E7D32)
        is ModelLoadState.Error     -> "🔴  Error: ${state.message}" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}
