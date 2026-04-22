# Feature Specification: Sherpa-ONNX STT, Hilt DI, and Multi-Model Switching

**Feature Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade`
**Created**: 2026-04-21
**Status**: Draft
**Input**: "Implement the current app up to the technical level in the project under F:\Git\HearoPilot-App, make sure the app is still suitable for Lenovo Y700 and Honor Magic 6 pro devices, allow setting to switch the local llm model"
**Constitution version at creation**: 2.0.0
**Reference project**: `F:\Git\HearoPilot-App` (production-grade meeting assistant)

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Real-Time Speech-to-Text with Sherpa-ONNX (Priority: P1)

As the developer/owner, the app captures microphone audio during a session, runs it through
the Sherpa-ONNX Nemo Parakeet TDT 0.6B v3 ASR engine with Silero VAD gating, and streams
recognised text segments into `TranscriptWindowBuffer` in real time — replacing the current
manual text-input demo path.

**Why this priority**: This is the most critical functional gap. The entire continuous-analysis
pipeline (spec 008) produces zero real-world value without a live transcript feed. Everything
from cloud inference to analysis cadence depends on real ASR.

**Independent Test**: Start a recording session. Speak "We need to finish the report by Friday".
Within 2s of the utterance ending, a `TranscriptionSegment` appears in the session UI and
`TranscriptWindowBuffer` contains the text.

**Acceptance Scenarios**:

1. **Given** STT models are downloaded, **When** a session starts, **Then** `SherpaOnnxDataSource.startRecording()` begins capturing audio at 16 kHz mono PCM
2. **Given** audio is being captured, **When** Silero VAD detects speech onset, **Then** audio is accumulated into a segment buffer with 0.4s lookback
3. **Given** a VAD segment ends (≥0.5s silence), **When** `OfflineRecognizer.decode()` runs, **Then** the result text is emitted via `Flow<RecognitionResult>` and appended to `TranscriptWindowBuffer`
4. **Given** the recognized text is non-empty, **When** it is appended to the buffer, **Then** `AnalysisCadenceController` will include it in the next analysis tick
5. **Given** a session ends, **When** `stopRecording()` is called, **Then** any in-flight decode completes and the audio resources are released cleanly
6. **Given** `decode()` is called concurrently, **When** a `Mutex` is held, **Then** the second call waits; no `SIGSEGV` from libonnxruntime

---

### User Story 2 — Hilt Dependency Injection (Priority: P1)

As the developer/owner, all app components use Hilt for dependency injection, replacing the
manual `AppContainer` pattern. ViewModels use `@HiltViewModel`, services use `@AndroidEntryPoint`,
and the Application class uses `@HiltAndroidApp`.

**Why this priority**: Hilt is the production-standard DI for Android. The manual AppContainer
approach is brittle (no compile-time graph validation), requires manual Factory boilerplate in
every ViewModel, and makes testing harder. HearoPilot uses Hilt 2.51 throughout.

**Independent Test**: `./gradlew assembleDebug` completes with Hilt-generated code. All existing
Robolectric unit tests pass. `HomeScreen` navigates to `SessionScreen` via `hiltViewModel()`
without a crash.

**Acceptance Scenarios**:

1. **Given** `@HiltAndroidApp` is on `MeetMindApplication`, **When** the app launches, **Then** the Hilt singleton component is initialised
2. **Given** `@HiltViewModel @Inject constructor` is on all 5 ViewModels, **When** Compose navigation creates them, **Then** `hiltViewModel()` resolves all dependencies without a crash
3. **Given** `@AndroidEntryPoint` is on `AudioProcessingForegroundService`, **When** `onCreate()` fires, **Then** `cloudInferenceEngine` and `transcriptWindowBuffer` are injected via `@Inject lateinit var`
4. **Given** `@Singleton` scoped providers are in Hilt modules, **When** two consumers request the same type, **Then** the same instance is returned (verified by identity check in a unit test)
5. **Given** `AppContainer.kt` is deleted, **When** `./gradlew assembleDebug` runs, **Then** no compilation errors from manual DI references remain

---

### User Story 3 — Multi-Model LLM Selection (Priority: P1)

As the developer/owner, I can select which local LLM model is used for on-device inference from
a Settings screen. The app supports at minimum two GGUF model variants for Gemma 4 E4B, detects
the recommended variant for the current device, and reloads the model when the selection changes.

**Why this priority**: The Y700 (12 GB RAM, Snapdragon 8 Gen 3) can run Q4_K_M comfortably.
Honor Magic 6 Pro (12 GB, same SoC) benefits from the same model. A Q8_0 path (higher quality)
and an IQ4_NL path (lower RAM) must be selectable so the app works across a wider device range.

**Independent Test**: Navigate to Settings → On-Device Model. Two variants are shown with size
hints. Tap Q4_K_M (current default). Tap Q8_0 (if downloaded). Session screen uses the newly
selected model on the next inference.

**Acceptance Scenarios**:

1. **Given** `GemmaModelVariant` enum has ≥2 values, **When** user opens Model Settings, **Then** each variant is shown with filename, size hint, and download status
2. **Given** a variant is already downloaded, **When** user taps it, **Then** `ModelConfigRepository.setModelVariant()` persists the choice and the new path is written to DataStore
3. **Given** a variant is not yet downloaded, **When** user taps Download, **Then** `ModelDownloadManager` begins an HTTP download with progress feedback
4. **Given** model variant changes while the current model is loaded, **When** the selection is saved, **Then** `OnDeviceLlamaProvider.unload()` is called first, then the new model is loaded
5. **Given** `DeviceTierDetector` reads RAM ≥ 8 GB and SDK ≥ 33, **When** user opens Model Settings, **Then** Q4_K_M (or equivalent flagship variant) shows a "Recommended" badge
6. **Given** a model switch is in progress (loading), **When** an inference is requested, **Then** the request queues until the new model is ready (no crash or stale handle use)

---

### User Story 4 — STT Model Download & Setup Flow (Priority: P2)

As the developer/owner, the onboarding/setup flow downloads the Sherpa-ONNX Nemo Parakeet
TDT 0.6B v3 ASR model files before the first recording session begins.

**Why this priority**: Without the 4 model files (~200 MB), the STT engine cannot initialize.
The download must happen once, be resumable, and block the session start until complete.

**Acceptance Scenarios**:

1. **Given** STT model files are missing, **When** user tries to start a session, **Then** a setup/download screen is shown (session does not start)
2. **Given** download is in progress, **When** app is backgrounded, **Then** download continues via `ModelDownloadService`
3. **Given** download is interrupted, **When** app restarts, **Then** the partial file is detected and download resumes from the byte offset
4. **Given** all 4 STT model files are present, **When** user taps Start Session, **Then** `SherpaOnnxDataSource.initialize()` succeeds and recording begins within 2s

---

### Edge Cases

- What if `OfflineRecognizer.decode()` is called while the previous decode is still running? → `decodeMutex.lock()` serializes all decode calls — the second waits, never runs concurrently
- What if the STT model files are corrupt (failed download)? → `OfflineRecognizer` constructor throws; caught in datasource init; session start fails with a user-visible error message
- What if the user switches LLM models during an active inference? → `OnDeviceLlamaProvider` holds a read lock during inference; model switch waits for write lock — inference completes first
- What if device RAM is < 8 GB and Q4_K_M is selected? → Allow but show a warning; `DeviceTierDetector` recommends a lighter variant but does not block the choice
- What if Hilt graph has a missing binding? → Hilt compile-time validation catches it at `./gradlew assembleDebug` — no runtime `NullPointerException`
- What if the VAD model (silero_vad.onnx) is missing from assets? → `Vad` constructor throws `IllegalStateException`; caught in service init; session fails with clear error

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST integrate Sherpa-ONNX Nemo Parakeet TDT 0.6B v3 (Int8) as the ASR engine via JNI (`libsherpa-onnx-jni.so`, `libonnxruntime.so`)
- **FR-002**: Silero VAD MUST gate audio capture; audio is only sent to `OfflineRecognizer.decode()` when a speech segment is detected
- **FR-003**: `OfflineRecognizer.decode()` MUST be serialized via a `Mutex` to prevent concurrent ONNX InferenceSession calls (avoids SIGSEGV)
- **FR-004**: Recognised text MUST be appended to `TranscriptWindowBuffer` via `AudioProcessingForegroundService.onTranscriptSegment()` — the single injection point for ASR output
- **FR-005**: The app MUST use Hilt 2.51 for all dependency injection; `AppContainer.kt` MUST be deleted
- **FR-006**: All 5 ViewModels MUST use `@HiltViewModel @Inject constructor`; all `Factory` inner classes MUST be removed
- **FR-007**: `AudioProcessingForegroundService` MUST be annotated `@AndroidEntryPoint` and use `@Inject lateinit var` for all dependencies
- **FR-008**: `MeetMindApplication` MUST be annotated `@HiltAndroidApp`
- **FR-009**: `GemmaModelVariant` enum MUST exist with at least 2 values (e.g. `Q4_K_M`, `Q8_0`) mapping to distinct GGUF filenames and HuggingFace download URLs
- **FR-010**: `ModelConfigRepository` MUST store the selected `GemmaModelVariant` in DataStore and expose `observeVariant(): Flow<GemmaModelVariant>` and `setModelVariant(variant)`
- **FR-011**: `ModelSetupScreen` MUST display all available model variants with: name, size hint, download status badge, "Recommended" badge (from `DeviceTierDetector`), and a Download/Select button
- **FR-012**: When a model variant change is saved, `OnDeviceLlamaProvider` MUST unload the current model before loading the new one
- **FR-013**: A `ModelDownloadManager` MUST handle HTTP downloads of GGUF model files with resume capability (`.partial` file + `Range` header)
- **FR-014**: STT model files (4 Parakeet TDT files, ~200 MB) MUST be downloadable via `ModelDownloadService` (foreground service, DATA_SYNC type)
- **FR-015**: `DeviceTierDetector` MUST recommend `Q4_K_M` for devices with ≥8 GB RAM + SDK ≥ 33, and `IQ4_NL` (lighter variant) otherwise

### Key Entities

- **GemmaModelVariant**: enum — `Q4_K_M` | `Q8_0` | `IQ4_NL` (maps to distinct GGUF file + URL)
- **ModelVariantConfig**: `data class(variant, llmUrl, llmFilename, sizeHintMb)`
- **SttModelConfig**: `data class(sttBaseUrl, sttFiles: List<String>)` — fixed Parakeet TDT
- **RecognitionResult**: `data class(text: String, isComplete: Boolean)` — emitted from STT datasource
- **DownloadProgress**: `data class(bytesDownloaded: Long, totalBytes: Long, isComplete: Boolean)`
- **SherpaOnnxDataSource**: wraps `OfflineRecognizer` + `Vad` + `AudioRecord`; emits `Flow<RecognitionResult>`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `./gradlew assembleDebug` succeeds with Hilt annotation processing; zero `AppContainer` references remain in production code
- **SC-002**: `./gradlew testDebugUnitTest` passes all existing 44 spec-008 contract tests (Hilt migration must not break them)
- **SC-003**: Speech-to-text latency: recognised text appears in `TranscriptWindowBuffer` within 2s of end of speech on Y700 Gen 3 and Honor Magic 6 Pro
- **SC-004**: Model variant switching completes without an `OutOfMemoryError`; old model is fully unloaded before new model loads (verified by `OnDeviceLlamaProvider.isLoaded() == false` after unload)
- **SC-005**: `OfflineRecognizer.decode()` mutex prevents concurrent calls — verified by a JVM test asserting `maxConcurrentDecodes == 1` under parallel invocation
- **SC-006**: STT model download is resumable — if interrupted at 50% and restarted, download resumes from byte offset (no re-download of completed bytes)
- **SC-007**: App launches and navigates to SessionScreen on Y700 Gen 3 and Honor Magic 6 Pro with no crash after Hilt migration (verified by T036-equivalent smoke test)

## Assumptions

- Sherpa-ONNX prebuilt JNI `.so` files (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) will be copied from `F:\Git\HearoPilot-App\lib-sherpa-onnx\src\main\jniLibs\arm64-v8a\` into `app/src/main/jniLibs/arm64-v8a\`
- `silero_vad.onnx` will be copied from HearoPilot-App assets into `app/src/main/assets/`
- The 6 Sherpa-ONNX Kotlin wrapper classes (`OfflineRecognizer.kt`, `Vad.kt`, `FeatureConfig.kt`, `OfflineStream.kt`, `QnnConfig.kt`, `HomophoneReplacerConfig.kt`) will be copied verbatim into `com.meetmind.assistant.stt.sherpa` package
- The `SherpaOnnxDataSource` implementation is adapted from HearoPilot's version with minor package-name changes
- STT thread count is fixed at 2 to avoid CPU contention with the LLM engine (same rationale as HearoPilot)
- Hilt version 2.51 is used to match HearoPilot reference; KSP plugin already present
- Existing Robolectric tests will be migrated from fake `AppContainer` subclasses to `@TestInstallIn` Hilt replacement modules
- No Room database is added in this spec; session data remains in-memory
- Y700 Gen 3 (12 GB RAM, Snapdragon 8 Gen 3, Android 14) and Honor Magic 6 Pro (12 GB, same SoC) both qualify for `Q4_K_M` recommendation from `DeviceTierDetector`
- Min SDK remains 26 (Hilt is compatible with API 26+)
