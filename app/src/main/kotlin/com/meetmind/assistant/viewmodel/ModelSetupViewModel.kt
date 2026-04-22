// T031 (spec 007): ViewModel for the On-Device Model setup screen
// spec 009 — T034: @HiltViewModel + multi-variant support (GemmaModelVariant, download, recommended)
package com.meetmind.assistant.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.data.DeviceTierDetector
import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.data.ModelDownloadManager
import com.meetmind.assistant.data.model.DownloadProgress
import com.meetmind.assistant.data.model.GemmaModelVariant
import com.meetmind.assistant.data.model.ModelConfig
import com.meetmind.assistant.data.model.ModelLoadState
import com.meetmind.assistant.data.model.SttModelConfig
import com.meetmind.assistant.service.AudioProcessingForegroundService
import com.meetmind.assistant.inference.OnDeviceLlamaProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives [ModelSetupScreen].
 *
 * Exposes:
 *  - [modelConfig]        — current persisted model config (path, threads, GPU layers)
 *  - [loadState]          — model lifecycle state (NotLoaded → Loading → Ready | Error)
 *  - [variants]           — all [GemmaModelVariant] values
 *  - [selectedVariant]    — currently persisted variant selection
 *  - [recommendedVariant] — recommended variant for this device (DeviceTierDetector)
 *  - [downloadProgress]   — live download progress for the active download (null if idle)
 *  - [setVariant]         — persist a new variant + update model path
 *  - [startDownload]      — start downloading the GGUF for a given variant
 *  - [loadModel]          — trigger native model load
 *  - [unloadModel]        — free native RAM
 *
 * spec 009 — T034
 */
@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ModelConfigRepository,
    private val llamaProvider: OnDeviceLlamaProvider,
    private val deviceTierDetector: DeviceTierDetector,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    // ── Model config (path, threads, etc.) ────────────────────────────────────

    /** Live config from DataStore. */
    val modelConfig: StateFlow<ModelConfig> = configRepository
        .observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelConfig())

    /** Current model load state. Starts as [ModelLoadState.NotLoaded]. */
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotLoaded)
    val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    // ── Variant selection ─────────────────────────────────────────────────────

    /** All available [GemmaModelVariant] values. */
    val variants: List<GemmaModelVariant> = GemmaModelVariant.entries

    /** The variant recommended for this device (12 GB Snapdragon 8 Gen 3 → Q4_K_M). */
    val recommendedVariant: GemmaModelVariant = deviceTierDetector.recommendedVariant(context)

    /** The variant currently persisted in DataStore. */
    val selectedVariant: StateFlow<GemmaModelVariant> = configRepository
        .observeVariant()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), recommendedVariant)

    // ── Download progress ─────────────────────────────────────────────────────

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _sttDownloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val sttDownloadProgress: StateFlow<DownloadProgress?> = _sttDownloadProgress.asStateFlow()

    // ── Model loading ─────────────────────────────────────────────────────────

    /**
     * Trigger model load for the currently selected variant.
     * Transitions: NotLoaded → Loading → Ready(handle) | Error(message)
     * If model is already [ModelLoadState.Ready] at the same path, this is a no-op.
     */
    fun loadModel() {
        if (_loadState.value is ModelLoadState.Loading) return
        viewModelScope.launch {
            _loadState.value = ModelLoadState.Loading
            val path = resolvedPath(selectedVariant.value)
            _loadState.value = llamaProvider.loadModel(overridePath = path)
        }
    }

    /** Unload the model (free native RAM). Resets state to [ModelLoadState.NotLoaded]. */
    fun unloadModel() {
        viewModelScope.launch {
            llamaProvider.unload()
            _loadState.value = ModelLoadState.NotLoaded
        }
    }

    // ── Variant switching ─────────────────────────────────────────────────────

    /**
     * Select [variant] as the active LLM model.
     * Persists the variant + updates the model path. Does not trigger a load — the user
     * must tap Load or start a session to actually load the new model.
     */
    fun setVariant(variant: GemmaModelVariant) {
        viewModelScope.launch {
            configRepository.setModelVariant(variant)
            configRepository.setModelPath(resolvedPath(variant))
        }
    }

    /**
     * Start downloading the GGUF for [variant]. Progress is emitted via [downloadProgress].
     * Does nothing if the file already exists.
     */
    fun startDownload(variant: GemmaModelVariant) {
        viewModelScope.launch {
            val destFile = File(resolvedPath(variant))
            if (destFile.exists()) {
                return@launch
            }
            downloadManager.downloadFile(variant.llmUrl, destFile)
                .collect { progress -> _downloadProgress.value = progress }
            _downloadProgress.value = null
        }
    }

    /**
     * Returns true if the GGUF file for [variant] exists on disk.
     */
    fun isDownloaded(variant: GemmaModelVariant): Boolean =
        File(resolvedPath(variant)).exists()

    /**
     * Returns true if all 4 Parakeet TDT STT model files are present on disk.
     * spec 009 T048
     */
    fun isSttReady(): Boolean =
        downloadManager.sttModelsReady(AudioProcessingForegroundService.sttModelDir(context))

    /**
     * Download all 4 STT model files. Progress emitted via [sttDownloadProgress].
     * Does nothing if all files already exist.
     * spec 009 T048
     */
    fun startSttDownload() {
        if (isSttReady()) return
        viewModelScope.launch {
            val sttModelDir = AudioProcessingForegroundService.sttModelDir(context)
            downloadManager.downloadSttModel(sttModelDir)
                .collect { progress -> _sttDownloadProgress.value = progress }
            _sttDownloadProgress.value = null
        }
    }

    // ── Legacy single-model config setters ────────────────────────────────────

    fun setModelPath(path: String) {
        viewModelScope.launch { configRepository.setModelPath(path) }
    }

    fun setNThreads(n: Int) {
        viewModelScope.launch { configRepository.setNThreads(n) }
    }

    fun setNGpuLayers(n: Int) {
        viewModelScope.launch { configRepository.setNGpuLayers(n) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun resolvedPath(variant: GemmaModelVariant): String {
        val externalFilesDir = context.getExternalFilesDir(null)
            ?: context.filesDir  // fallback to internal storage
        return configRepository.resolvedModelPath(variant, externalFilesDir)
    }
}
