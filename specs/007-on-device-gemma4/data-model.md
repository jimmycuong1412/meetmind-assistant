# Data Model: On-Device Gemma 4 E4B Inference (spec 007)

## New Entities

### ModelConfig (in-memory, DataStore-persisted)

Stores the user-configured path to the GGUF file and runtime parameters.

| Field | Type | Validation | Default |
|-------|------|------------|---------|
| `modelPath` | `String` | Non-empty, file must exist | `/sdcard/Download/gemma-4-E4B-it-Q4_K_M.gguf` |
| `nThreads` | `Int` | 1..16 | `6` |
| `nGpuLayers` | `Int` | -1..999 (-1 = all) | `-1` |
| `contextSize` | `Int` | 512..8192 | `2048` |
| `isModelReady` | `Boolean` | derived — file exists + loadable | `false` |

**Storage**: DataStore Preferences (same file as cloud config, different key prefix `on_device_`).
No encryption needed (paths are not secrets).

---

### ModelLoadState (in-memory, ViewModel StateFlow)

Tracks the lifecycle of the native model handle.

```kotlin
sealed class ModelLoadState {
    object NotLoaded : ModelLoadState()
    object Loading : ModelLoadState()
    data class Ready(val handle: Long) : ModelLoadState()
    data class Error(val message: String) : ModelLoadState()
}
```

---

### OnDeviceInferenceRequest (in-memory)

Passed to `OnDeviceLlamaProvider.generate()`. Reuses the existing `questionText: String`
parameter from the `OnDeviceFallback` functional interface — no new type needed.

---

## Modified Entities

### CloudBadgeState (existing enum — EXTEND)

Add a new state to distinguish on-device-by-choice from on-device-by-fallback:

```kotlin
enum class CloudBadgeState {
    HIDDEN,          // existing: on-device mode, no badge
    CLOUD_ACTIVE,    // existing: cloud inference running
    FALLBACK,        // existing: cloud failed, fell back to on-device
    // No new state needed — HIDDEN covers "on-device by design (Gemma 4)"
}
```

No change required — `HIDDEN` correctly represents "on-device Gemma 4 active".

---

## State Transitions

```
App launch
  └─ ModelLoadState.NotLoaded
       │
       ├─ user opens ModelSetupScreen, model file found
       │    └─ ModelLoadState.Loading → (JNI loadModel) → ModelLoadState.Ready(handle)
       │
       └─ model file not found
            └─ ModelSetupScreen shows setup instructions (adb push or download)

Inference request (cloud disabled or fallback):
  Ready(handle) → callbackFlow → Stream InferenceEvent.Token* → InferenceEvent.Complete

onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL):
  Ready(handle) → (JNI unloadModel) → NotLoaded
```

---

## No New Database Tables

Room DB is unchanged. `ModelConfig` uses DataStore Preferences (consistent with `CloudProviderConfig`).
All inference state is in-memory (same pattern as cloud inference).

---

## New Files

| File | Purpose |
|------|---------|
| `inference/OnDeviceLlamaProvider.kt` | Wraps LlamaJni, implements OnDeviceFallback |
| `inference/LlamaJni.kt` | JNI declarations + System.loadLibrary |
| `inference/TokenCallback.kt` | Callback interface from native → Kotlin |
| `data/model/ModelConfig.kt` | DataStore-backed config data class |
| `data/model/ModelLoadState.kt` | Sealed class for model lifecycle |
| `data/ModelConfigRepository.kt` | Read/write ModelConfig to DataStore |
| `ui/screens/ModelSetupScreen.kt` | Setup UI: path picker + status + load button |
| `viewmodel/ModelSetupViewModel.kt` | Drives ModelSetupScreen |
| `jniLibs/arm64-v8a/liballama.so` | Built from llama.cpp source (not checked in) |
| `CMakeLists.txt` (app-level) | Points to llama.cpp source for NDK build |
