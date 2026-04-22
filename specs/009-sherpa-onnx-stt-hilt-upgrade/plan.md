# Implementation Plan: Sherpa-ONNX STT, Hilt DI, and Multi-Model Switching

**Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade` | **Date**: 2026-04-21  
**Spec**: `specs/009-sherpa-onnx-stt-hilt-upgrade/spec.md`  
**Reference**: `F:\Git\HearoPilot-App` (production implementation reference)

---

## Summary

Three parallel upgrades bring MeetMind Assistant to production-grade technical parity with HearoPilot-App:

1. **Sherpa-ONNX STT**: Replace the ASR stub with a real-time Nemo Parakeet TDT 0.6B v3 pipeline (Silero VAD + OfflineRecognizer, mutex-serialized decode, 16 kHz audio)
2. **Hilt DI**: Replace manual `AppContainer` with Hilt 2.51 — `@HiltAndroidApp`, `@HiltViewModel`, `@AndroidEntryPoint`, 4 `@Module` objects
3. **Multi-Model LLM**: Add `GemmaModelVariant` enum, `ModelDownloadManager`, `DeviceTierDetector`, and a model-selection UI in `ModelSetupScreen`

All three upgrades target **Lenovo Y700 Gen 3** and **Honor Magic 6 Pro** (Snapdragon 8 Gen 3, 12 GB RAM, Android 14).

---

## Technical Context

**Language/Version**: Kotlin 2.0.21 (JVM target 17) — unchanged  
**AGP**: 8.13.2 — unchanged (HearoPilot uses 8.4.0; no downgrade needed)

**New Dependencies**:
```toml
# gradle/libs.versions.toml additions
hilt = "2.51"
hiltNavigationCompose = "1.1.0"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

**Native Libraries Added** (copied from HearoPilot):
- `app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so`
- `app/src/main/jniLibs/arm64-v8a/libonnxruntime.so`

**Asset Added**:
- `app/src/main/assets/silero_vad.onnx`

**Storage**:
- STT model files: `getExternalFilesDir(null)/models/stt/<model-dir>/`
- LLM model files: `getExternalFilesDir(null)/models/<variant-filename>.gguf`
- `on_device_model_variant` DataStore key added to existing `on_device_config` DataStore

**Testing**: All existing 44 spec-008 Robolectric tests must continue to pass. New contract tests (C1–C6) target:
- `SherpaOnnxDataSource` (mutex, emission)
- `ModelDownloadManager` (progress, resume)
- `ModelConfigRepository` (variant persistence)
- `DeviceTierDetector` (recommendation logic)
- `OnDeviceLlamaProvider` (path-change unload)
- Hilt `@Singleton` scope identity

---

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| **I — Privacy-First Inference** | ✅ PASS | Raw audio never leaves device. ASR text (≤600 chars) only. STT runs fully on-device. No new network categories. |
| **II — Real-Time Responsiveness** | ✅ PASS | Parakeet TDT decode target ≤ 2s on Snapdragon 8 Gen 3. Fixed 2-thread STT avoids contention with LLM (validated by HearoPilot). |
| **III — APK Distribution** | ✅ PASS | No new Play Services APIs. Prebuilt `.so` files bundled in APK. |
| **IV — Minimal Permissions Footprint** | ✅ PASS | No new permissions. `RECORD_AUDIO` + `INTERNET` already declared. `FOREGROUND_SERVICE_DATA_SYNC` added for `ModelDownloadService` — existing pattern from spec 005. |
| **V — Incremental Demo-Able Slices** | ✅ PASS | US2 (Hilt) is independently buildable. US1 (STT) adds on top. US3 (model switching) is independently testable via Settings. Each leaves the app launchable. |
| **VI — Cloud Inference Mode** | ✅ PASS | Cloud inference path unchanged. Hilt migration does not alter `CloudInferenceEngine` behavior. No new API calls. |

**Constitution violations**: None.

---

## Project Structure

### New Source Files

```text
app/src/main/kotlin/com/meetmind/assistant/
├── di/
│   ├── InfraModule.kt            # ApiKeyStore, CloudProviderConfigRepository, DataStore
│   ├── InferenceModule.kt        # GeminiClient, OnDeviceLlamaProvider, CloudInferenceEngine
│   ├── AnalysisModule.kt         # AnalysisSettings, Buffer, Analyzer, CadenceController
│   └── ModelModule.kt            # ModelConfigRepository, ModelDownloadManager, DeviceTierDetector
├── stt/
│   ├── SttRepository.kt          # interface
│   ├── SttRepositoryImpl.kt      # pass-through to SherpaOnnxDataSource
│   └── SherpaOnnxDataSource.kt   # adapted from HearoPilot; audio capture + VAD + ASR
├── stt/sherpa/                   # copied from HearoPilot lib-sherpa-onnx (package renamed)
│   ├── OfflineRecognizer.kt
│   ├── Vad.kt
│   ├── FeatureConfig.kt
│   ├── OfflineStream.kt
│   ├── QnnConfig.kt
│   └── HomophoneReplacerConfig.kt
├── data/
│   ├── ModelDownloadManager.kt   # HTTP download with resume (.partial file + Range header)
│   └── DeviceTierDetector.kt     # RAM + SDK → GemmaModelVariant recommendation
└── service/
    └── ModelDownloadService.kt   # Foreground service (DATA_SYNC) for model downloads

app/src/main/kotlin/com/meetmind/assistant/data/model/
└── GemmaModelVariant.kt          # enum with 3 values + metadata
```

### Modified Files

```text
app/build.gradle.kts              # Add Hilt plugin, hilt-android, hilt-compiler, hilt-navigation-compose
gradle/libs.versions.toml         # hilt = "2.51", hiltNavigationCompose = "1.1.0"
settings.gradle.kts               # Add hilt gradle plugin to classpath
AndroidManifest.xml               # ModelDownloadService declaration + FOREGROUND_SERVICE_DATA_SYNC

app/src/main/kotlin/com/meetmind/assistant/
├── MeetMindApplication.kt        # @HiltAndroidApp; @Inject fields; remove container
├── di/AppContainer.kt            # DELETED
├── inference/OnDeviceLlamaProvider.kt    # Detect path change; force unload on variant switch
├── data/ModelConfigRepository.kt # Add variant key + observeVariant()/setModelVariant()
├── viewmodel/HomeViewModel.kt    # @HiltViewModel @Inject constructor; delete Factory
├── viewmodel/SessionViewModel.kt # @HiltViewModel @Inject constructor; delete Factory
├── viewmodel/ModelSetupViewModel.kt      # @HiltViewModel; multi-variant support
├── viewmodel/AnalysisSettingsViewModel.kt # @HiltViewModel; delete Factory
├── viewmodel/CloudSettingsViewModel.kt   # @HiltViewModel; delete Factory
├── service/AudioProcessingForegroundService.kt  # @AndroidEntryPoint; wire SherpaOnnxDataSource
├── ui/navigation/AppNavGraph.kt  # hiltViewModel() everywhere; drop application param
└── ui/screens/ModelSetupScreen.kt # Multi-variant UI; download progress; Recommended badge

app/src/main/jniLibs/arm64-v8a/
├── libsherpa-onnx-jni.so         # Copied from HearoPilot
└── libonnxruntime.so             # Copied from HearoPilot

app/src/main/assets/
└── silero_vad.onnx               # Copied from HearoPilot assets
```

### Test Files

```text
app/src/test/kotlin/com/meetmind/assistant/
├── stt/
│   └── SherpaOnnxDataSourceTest.kt        # C1.1–C1.3
├── data/
│   ├── ModelDownloadManagerTest.kt        # C2.1–C2.2
│   ├── ModelConfigRepositoryVariantTest.kt # C3.1–C3.2
│   └── DeviceTierDetectorTest.kt          # C4.1–C4.2
├── inference/
│   └── OnDeviceLlamaProviderSwitchTest.kt # C5.1–C5.2
├── di/
│   └── HiltSingletonScopeTest.kt          # C6.1
└── helpers/
    ├── FakeSherpaOnnxDataSource.kt
    ├── FakeModelDownloadManager.kt
    └── FakeDeviceTierDetector.kt
```

---

## Implementation Strategy

### MVP (User Stories 2 + 3 first — Hilt + Model Switching)

1. **Hilt migration** (US2) — no new features; existing app compiles with Hilt; all spec-008 tests pass
2. **GemmaModelVariant + ModelConfigRepository** (US3 data layer)
3. **DeviceTierDetector + ModelDownloadManager** (US3 infrastructure)
4. **ModelSetupScreen update** (US3 UI)
5. **OnDeviceLlamaProvider path-change handling** (US3 core)
6. **STOP and validate**: app builds, model switching works, all tests pass

### Full (User Stories 1 + 4)

7. **Sherpa-ONNX JNI files + wrapper classes** (US1 foundation)
8. **SherpaOnnxDataSource** (US1 core pipeline)
9. **AudioProcessingForegroundService ASR loop** (US1 wiring)
10. **SttRepository + DI wiring** (US1 completion)
11. **ModelDownloadService** (US4 download service)
12. **Setup/onboarding download flow** (US4 UI)
13. **Full contract test suite** (all C1–C6)
14. **Hardware smoke test** per quickstart.md

---

## Complexity Tracking

No constitution violations. No complexity justification required.
