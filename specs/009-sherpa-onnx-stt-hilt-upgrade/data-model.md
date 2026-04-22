# Data Model: Sherpa-ONNX STT, Hilt DI, and Multi-Model Switching

**Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade`  
**Date**: 2026-04-21

---

## New Entities

### GemmaModelVariant (enum)

```kotlin
enum class GemmaModelVariant(
    val displayName: String,
    val llmFilename: String,
    val sizeHintMb: Int,
    val llmUrl: String
) {
    Q4_K_M(
        displayName   = "Gemma 4 E4B Q4_K_M",
        llmFilename   = "gemma-4-e4b-it-Q4_K_M.gguf",
        sizeHintMb    = 5120,
        llmUrl        = "https://huggingface.co/meetmind/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-Q4_K_M.gguf"
    ),
    Q8_0(
        displayName   = "Gemma 4 E4B Q8_0",
        llmFilename   = "gemma-4-e4b-it-Q8_0.gguf",
        sizeHintMb    = 7168,
        llmUrl        = "https://huggingface.co/meetmind/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-Q8_0.gguf"
    ),
    IQ4_NL(
        displayName   = "Gemma 4 E4B IQ4_NL",
        llmFilename   = "gemma-4-e4b-it-IQ4_NL.gguf",
        sizeHintMb    = 3072,
        llmUrl        = "https://huggingface.co/meetmind/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-IQ4_NL.gguf"
    )
}
```

**Validation**: `llmFilename` must match the file on disk at `getExternalFilesDir(null)/models/<llmFilename>`.  
**Default**: `Q4_K_M` (recommended for Y700 Gen 3 and Honor Magic 6 Pro).

---

### RecognitionResult (data class)

```kotlin
data class RecognitionResult(
    val text: String,
    val isComplete: Boolean   // true = VAD segment ended; false = partial result
)
```

Emitted by `SherpaOnnxDataSource` as `Flow<RecognitionResult>`.  
Only `isComplete = true` results are appended to `TranscriptWindowBuffer`.

---

### DownloadProgress (data class)

```kotlin
data class DownloadProgress(
    val filename: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean
) {
    val progressFraction: Float get() =
        if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}
```

Emitted by `ModelDownloadManager` as `Flow<DownloadProgress>`.

---

### SttModelConfig (data class)

```kotlin
data class SttModelConfig(
    val baseUrl: String = STT_BASE_URL,
    val files: List<String> = STT_FILES
) {
    companion object {
        const val STT_BASE_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"
        val STT_FILES = listOf(
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            "joiner.int8.onnx",
            "tokens.txt"
        )
        const val MODEL_DIR_NAME = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
    }
}
```

Fixed config — STT model does not change based on user selection.

---

## Modified Entities

### ModelConfig (modified DataStore keys)

**File**: `app/src/main/kotlin/com/meetmind/assistant/data/ModelConfigRepository.kt`

**New DataStore keys added** (existing keys preserved):

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `on_device_model_variant` | `String` | `"Q4_K_M"` | Selected `GemmaModelVariant.name()` |

**New methods added**:
```kotlin
fun observeVariant(): Flow<GemmaModelVariant>
suspend fun setModelVariant(variant: GemmaModelVariant)
fun resolvedModelPath(variant: GemmaModelVariant, filesDir: File): String
    // → filesDir.absolutePath + "/models/" + variant.llmFilename
```

**Existing keys unchanged**:
- `on_device_model_path` — still the resolved absolute path (derived from variant + filesDir)
- `on_device_n_threads` — thread count (default 4)
- `on_device_n_gpu_layers` — GPU layers (default -1 for full offload)
- `on_device_context_size` — context size (default 8192)

---

## Component Architecture Changes

### New Components

| Component | Package | Description |
|-----------|---------|-------------|
| `SherpaOnnxDataSource` | `com.meetmind.assistant.stt` | Audio capture + VAD + ASR pipeline |
| `SttRepository` / `SttRepositoryImpl` | `com.meetmind.assistant.stt` | Pass-through to `SherpaOnnxDataSource` |
| `ModelDownloadManager` | `com.meetmind.assistant.data` | HTTP download with resume capability |
| `ModelDownloadService` | `com.meetmind.assistant.service` | Foreground service (DATA_SYNC) for downloads |
| `DeviceTierDetector` | `com.meetmind.assistant.data` | RAM + SDK → recommended `GemmaModelVariant` |
| `InfraModule` | `com.meetmind.assistant.di` | Hilt module — infra singletons |
| `InferenceModule` | `com.meetmind.assistant.di` | Hilt module — inference singletons |
| `AnalysisModule` | `com.meetmind.assistant.di` | Hilt module — analysis singletons |
| `ModelModule` | `com.meetmind.assistant.di` | Hilt module — model config + download |
| `com.k2fsa.sherpa.onnx.*` (6 files) | `com.meetmind.assistant.stt.sherpa` | Copied Sherpa-ONNX JNI wrappers |

### Modified Components

| Component | Change |
|-----------|--------|
| `MeetMindApplication` | `@HiltAndroidApp`; `@Inject` fields replace `container` property |
| `AppContainer.kt` | **Deleted** |
| `HomeViewModel` | `@HiltViewModel @Inject constructor`; `Factory` deleted |
| `SessionViewModel` | `@HiltViewModel @Inject constructor`; `cadenceController` injected as `@Singleton` |
| `ModelSetupViewModel` | `@HiltViewModel @Inject constructor`; multi-variant support added |
| `AnalysisSettingsViewModel` | `@HiltViewModel @Inject constructor` |
| `CloudSettingsViewModel` | `@HiltViewModel @Inject constructor` |
| `AudioProcessingForegroundService` | `@AndroidEntryPoint`; ASR loop wired to `SherpaOnnxDataSource` |
| `AppNavGraph.kt` | `viewModel(factory=...)` → `hiltViewModel()`; drop `application` param |
| `ModelSetupScreen` | Multi-variant selection UI; download progress; "Recommended" badge |
| `ModelConfigRepository` | New variant key + methods |
| `OnDeviceLlamaProvider` | Detect path change on `loadModel()`; force unload if different path |

---

## State Transitions

### OnDeviceLlamaProvider (updated)

```
NotLoaded → [loadModel(path)] →
    Checking(path)  →  if same path && handle > 0 → ModelReady (idempotent)
                    →  if different path → Unloading → NotLoaded → Loading → ModelReady
                    →  if new path       →            Loading → ModelReady | Error

ModelReady → [unload()] → NotLoaded
ModelReady → [inference()] → Generating → ModelReady
Generating → [onTrimMemory(CRITICAL)] → NotLoaded (forced)
```

### ModelDownloadManager

```
Idle → [startDownload(variant)] → Downloading(progress) → Complete
Downloading → [interrupt] → Paused(.partial file preserved)
Paused → [resume] → Downloading (from byte offset via Range header) → Complete
Complete → [file verified] → Idle (file available)
```

### STT Pipeline

```
Idle → [initialize(sttModelPath)] → Ready
Ready → [startRecording()] → Listening
Listening → [VAD: silence] → Listening (no decode)
Listening → [VAD: speechStart] → Accumulating(audio[])
Accumulating → [VAD: speechEnd] → Decoding (mutex lock)
Decoding → [decode() complete] → Emitting(RecognitionResult) → Listening
Listening → [stopRecording()] → Stopping → Idle
```
