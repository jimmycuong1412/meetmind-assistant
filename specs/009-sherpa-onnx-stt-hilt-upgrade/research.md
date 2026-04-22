# Research: Sherpa-ONNX STT, Hilt DI, and Multi-Model Switching

**Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade`  
**Date**: 2026-04-21  
**Reference**: F:\Git\HearoPilot-App (production reference implementation)

---

## Decision 1: ASR Engine — Sherpa-ONNX Nemo Parakeet TDT 0.6B v3 (Int8)

**Decision**: Use Sherpa-ONNX with Nemo Parakeet TDT 0.6B v3 Int8 as the ASR engine, replacing the planned Vosk SDK.

**Rationale**:
- HearoPilot uses this model in production; its prebuilt `.so` files and Kotlin wrappers are available to copy directly
- Nemo Parakeet TDT achieves state-of-the-art English ASR accuracy at a competitive model size (~200 MB vs Vosk's ~50 MB but significantly better WER)
- Int8 quantization runs efficiently on Adreno 750 GPU via ONNX Runtime; no additional GPU configuration needed
- Sherpa-ONNX provides a battle-tested thread-safety model (`decodeMutex`) for the non-thread-safe ONNX InferenceSession
- Fixed 2-thread STT config prevents CPU contention with the LLM engine — validated by HearoPilot

**Alternatives considered**:
- **Vosk Android SDK 0.3.47**: smaller model (~50 MB), lower WER on conversational English, harder to integrate with Silero VAD, no production reference
- **Whisper.cpp on-device**: better WER, but ~30x slower inference unsuitable for real-time streaming on a mobile device

**Integration approach**: Copy 2 prebuilt `.so` files + 6 Kotlin wrapper classes + 1 asset (silero_vad.onnx) from HearoPilot. Adapt `SherpaOnnxDataSource.kt` with package name changes.

---

## Decision 2: VAD — Silero VAD (via Sherpa-ONNX)

**Decision**: Use Silero VAD bundled with Sherpa-ONNX (`silero_vad.onnx` asset, type `0`).

**Rationale**:
- Already integrated in HearoPilot; the `Vad` Kotlin class from `lib-sherpa-onnx` handles all the ONNX session lifecycle
- 512-sample window (32 ms) provides fast response with low latency speech onset detection
- 6400-sample lookback (0.4s) ensures speech onset is captured even after VAD detects it late
- Silero VAD is the industry standard for lightweight on-device VAD (< 1 MB model)

**Parameters**:
```
vadMinSilenceDuration = 0.5s
vadMaxSpeechDuration  = 10.0s
vadThreshold          = 0.5
minSpeechDuration     = 0.25s
windowSize            = 512 samples
```

**Alternatives considered**:
- **TFLite Silero VAD** (original spec 004 plan): requires a separate TFLite runtime dependency; Sherpa-ONNX includes VAD natively

---

## Decision 3: Dependency Injection — Hilt 2.51 (full migration, single PR)

**Decision**: Migrate from manual `AppContainer` to Hilt 2.51 in a single PR; delete `AppContainer.kt`.

**Rationale**:
- `AppContainer` has only 11 dependencies — small enough for a single-pass migration
- Hilt provides compile-time graph validation catching missing bindings at `assembleDebug`, not runtime
- `@HiltViewModel` eliminates 5 identical `Factory` inner classes (pure boilerplate removal)
- `@AndroidEntryPoint` on `AudioProcessingForegroundService` removes the fragile `(application as MeetMindApplication).container` cast
- Hilt 2.51 is stable, matches KSP 2.0.21-1.0.28 already in the project, and is the reference version from HearoPilot

**Module split**:
- `InfraModule`: `ApiKeyStore`, `CloudProviderConfigRepository`, `CloudKeyValidationService`, DataStore helpers
- `InferenceModule`: `GeminiInferenceClient`, `OnDeviceLlamaProvider`, `CloudInferenceEngine`
- `AnalysisModule`: `AnalysisSettingsRepository`, `TranscriptWindowBuffer`, `DefaultConversationAnalyzer`, `AnalysisCadenceController`
- `ModelModule`: `ModelConfigRepository`, `ModelDownloadManager`, `DeviceTierDetector`

**ClaudeProviderFactory handling**: Move the `(apiKey) -> ClaudeInferenceClient` lambda inside `CloudInferenceEngine`'s body; remove it from the constructor. The factory is always `ClaudeInferenceClient(apiKey = apiKey)` — no polymorphism needed.

**Test migration**: Replace fake `AppContainer` subclasses with `@TestInstallIn(SingletonComponent::class)` Hilt test modules that provide fakes. Existing Robolectric tests get `@HiltAndroidTest` + `HiltAndroidRule`.

**Alternatives considered**:
- **Incremental migration (keep AppContainer temporarily)**: Increases complexity (two parallel DI paths); no benefit given small container size
- **Koin**: Not aligned with HearoPilot reference; Hilt is Jetpack-standard

---

## Decision 4: Multi-Model LLM Switching

**Decision**: Add `GemmaModelVariant` enum with 3 values (`Q4_K_M`, `Q8_0`, `IQ4_NL`); store selected variant in DataStore; add `ModelDownloadManager` for HTTP download with resume.

**Rationale**:
- Current app hard-codes a single model path set via the manual Model Setup screen — no download capability
- `Q4_K_M` (~5 GB) is the current production model; `Q8_0` (~7 GB) provides higher quality for flagship devices; `IQ4_NL` (~3 GB) is for devices with < 8 GB available RAM
- Y700 Gen 3 and Honor Magic 6 Pro (both 12 GB RAM, Snapdragon 8 Gen 3) comfortably support any variant
- `DeviceTierDetector` (RAM ≥ 8 GB + SDK ≥ 33 → recommend Q4_K_M) aligns with HearoPilot pattern; recommends the right default without user guesswork

**Model variants**:
| Variant | Filename | Size | HuggingFace repo |
|---------|----------|------|-----------------|
| `Q4_K_M` | `gemma-4-e4b-it-Q4_K_M.gguf` | ~5 GB | `meetmind/gemma-4-e4b-it-GGUF` (or configured) |
| `Q8_0` | `gemma-4-e4b-it-Q8_0.gguf` | ~7 GB | same repo |
| `IQ4_NL` | `gemma-4-e4b-it-IQ4_NL.gguf` | ~3 GB | same repo |

**Download approach**: HTTP with `Range` header for resume; `.partial` file renamed on completion; `ModelDownloadService` keeps download alive in background (DATA_SYNC foreground service).

**Hot-swap**: `OnDeviceLlamaProvider.unload()` called before `loadModel()` when variant changes. Read lock during inference prevents use-after-free; write lock acquired for unload/load.

**Alternatives considered**:
- **Android DownloadManager API**: Less control over partial-file resume and progress; HearoPilot uses custom HTTP implementation for reliability
- **Single model only**: Fails the user requirement for model switching; limits device range

---

## Decision 5: STT Model Download — Foreground Service

**Decision**: Add `ModelDownloadService` (foreground service, DATA_SYNC type) that downloads STT model files from HuggingFace.

**Rationale**:
- 4 files totalling ~200 MB; cannot reliably complete in foreground with the app visible
- Background-compatible via DATA_SYNC foreground service (same pattern as HearoPilot)
- Resume capability via `Range` header prevents re-downloading on interruption
- Model files stored in `getExternalFilesDir(null)/models/stt/` (auto-deleted on uninstall, no manual cleanup)

**Files**:
```
encoder.int8.onnx   (~130 MB)
decoder.int8.onnx   (~10 MB)
joiner.int8.onnx    (~1 MB)
tokens.txt          (~50 KB)
```
Base URL: `https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main`

---

## Decision 6: Audio Pipeline Constants

**Decision**: Adopt HearoPilot's validated audio pipeline constants exactly.

**Constants**:
```kotlin
SAMPLE_RATE_HZ                = 16000
BUFFER_SIZE_MULTIPLIER        = 4        // AudioRecord buffer = minBufferSize × 4
VAD_WINDOW_SIZE               = 512      // samples per VAD acceptWaveform() call
SPEECH_START_LOOKBACK_SAMPLES = 6400     // 0.4s lookback on speech onset
MIN_NEW_AUDIO_SAMPLES         = 24000    // 1.5s before triggering partial inference
MAX_INFERENCE_AUDIO_SAMPLES   = 480000   // 30s cap per decode call
CONTEXT_CARRY_OVER_SAMPLES    = 48000    // 3s carry-over across VAD segment boundaries
AUDIO_READ_CHUNK_SAMPLES      = 1600     // 100ms per AudioRecord.read() call
```

**Rationale**: These were tuned empirically in HearoPilot for Snapdragon 8 Gen 3 devices (the same SoC as Y700 Gen 3 and Honor Magic 6 Pro). Using identical constants avoids re-tuning work and the risk of regression.

---

## Decision 7: Hilt Modules Structure

```
app/src/main/kotlin/com/meetmind/assistant/di/
├── InfraModule.kt       — ApiKeyStore, DataStore, CloudProviderConfigRepository, ...
├── InferenceModule.kt   — GeminiInferenceClient, OnDeviceLlamaProvider, CloudInferenceEngine
├── AnalysisModule.kt    — AnalysisSettings, TranscriptWindowBuffer, Analyzer, CadenceController
└── ModelModule.kt       — ModelConfigRepository, ModelDownloadManager, DeviceTierDetector
```

**All modules**: `@Module @InstallIn(SingletonComponent::class) object`

**ViewModel injection**: `hiltViewModel()` from `androidx.hilt:hilt-navigation-compose:1.1.0`

**Test override**: `@TestInstallIn` modules in `src/test/` provide fakes for `CloudStreamingProvider`, `ConversationAnalyzer`, etc.
