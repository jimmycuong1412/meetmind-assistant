# Tasks: Sherpa-ONNX STT, Hilt DI, and Multi-Model Switching

**Feature Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade`
**Input**: Design documents from `/specs/009-sherpa-onnx-stt-hilt-upgrade/`
**Prerequisites**: spec.md ✅ plan.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Tests**: Contract tests ARE included — spec explicitly requires JVM-level contracts (C1–C6) covering STT mutex, download resume, variant persistence, device tier detection, model switching, and Hilt scope.

**Organization**: Tasks grouped by user story for independent implementation and testing. MVP = US2 (Hilt) + US3 (model switching) first; US1 (STT) and US4 (download) build on top.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Wire Hilt into the build system; copy native assets; add `GemmaModelVariant` shared type used by all user stories.

- [X] T001-7 Add Hilt 2.51 to `gradle/libs.versions.toml` (hilt = "2.51", hiltNavigationCompose = "1.1.0") and `app/build.gradle.kts` (hilt plugin + hilt-android + hilt-compiler + hilt-navigation-compose dependencies)
- [X] T001-7 Add Hilt Gradle plugin classpath to `settings.gradle.kts` (id = "com.google.dagger.hilt.android" version "2.51")
- [X] T001-7 [P] Copy `libsherpa-onnx-jni.so` and `libonnxruntime.so` from `F:\Git\HearoPilot-App\lib-sherpa-onnx\src\main\jniLibs\arm64-v8a\` into `app/src/main/jniLibs/arm64-v8a\`
- [X] T001-7 [P] Copy `silero_vad.onnx` from `F:\Git\HearoPilot-App\app\src\main\assets\` (or `lib-sherpa-onnx` assets) into `app/src/main/assets\`
- [X] T001-7 [P] Create `GemmaModelVariant` enum (Q4_K_M, Q8_0, IQ4_NL with displayName, llmFilename, sizeHintMb, llmUrl fields) in `app/src/main/kotlin/com/meetmind/assistant/data/model/GemmaModelVariant.kt`
- [X] T001-7 [P] Create `DownloadProgress` data class (filename, bytesDownloaded, totalBytes, isComplete, progressFraction) in `app/src/main/kotlin/com/meetmind/assistant/data/model/DownloadProgress.kt`
- [X] T001-7 [P] Create `RecognitionResult` data class (text, isComplete) in `app/src/main/kotlin/com/meetmind/assistant/data/model/RecognitionResult.kt`

**Checkpoint**: `./gradlew assembleDebug` compiles with Hilt plugin wired; enum and data classes accessible.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Copy Sherpa-ONNX Kotlin wrapper classes and write the 6 contract test doubles that all user stories share.

⚠️ **CRITICAL**: No user story work can begin until this phase is complete.

- [X] T008 Copy all 6 Sherpa-ONNX JNI wrapper Kotlin files from `F:\Git\HearoPilot-App\lib-sherpa-onnx\src\main\java\com\k2fsa\sherpa\onnx\` (`OfflineRecognizer.kt`, `Vad.kt`, `FeatureConfig.kt`, `OfflineStream.kt`, `QnnConfig.kt`, `HomophoneReplacerConfig.kt`) into `app/src/main/kotlin/com/meetmind/assistant/stt/sherpa/` updating package declarations to `com.meetmind.assistant.stt.sherpa`
- [X] T009 [P] Create `FakeSherpaOnnxDataSource` test helper (configurable `RecognitionResult` sequence, `decodeCallCount: AtomicInteger`, mutex tracking via `maxConcurrentDecodes`) in `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeSherpaOnnxDataSource.kt`
- [X] T010 [P] Create `FakeModelDownloadManager` test helper (emits configurable `DownloadProgress` sequence, `downloadCallCount: AtomicInteger`) in `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeModelDownloadManager.kt`
- [X] T011 [P] Create `FakeDeviceTierDetector` test helper (constructor-injected `recommendedVariant: GemmaModelVariant`) in `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeDeviceTierDetector.kt`

**Checkpoint**: Foundation compiles; fake helpers compile; `./gradlew assembleDebug` green.

---

## Phase 3: User Story 2 — Hilt DI Migration (Priority: P1) 🎯 MVP

**Goal**: Migrate from manual `AppContainer` to Hilt 2.51. `AppContainer.kt` is deleted. All 5 ViewModels use `@HiltViewModel`. `AudioProcessingForegroundService` uses `@AndroidEntryPoint`. All existing 44 spec-008 tests still pass.

**Independent Test**: `./gradlew assembleDebug` succeeds with Hilt code generation. `./gradlew testDebugUnitTest` passes all 44 existing tests. No `AppContainer` reference remains in production code.

### Contract Tests — US2

- [X] T012 [P] [US2] Write Hilt singleton scope contract test C6.1 (`TranscriptWindowBuffer` identity equality across two injection points) in `app/src/test/kotlin/com/meetmind/assistant/di/HiltSingletonScopeTest.kt` using `@HiltAndroidTest` + `HiltAndroidRule`

### Implementation — US2

- [X] T013 [US2] Annotate `MeetMindApplication` with `@HiltAndroidApp`; add `@Inject lateinit var onDeviceLlamaProvider: OnDeviceLlamaProvider` and `@Inject lateinit var analysisCadenceController: AnalysisCadenceController`; remove `lateinit var container: AppContainer` property in `app/src/main/kotlin/com/meetmind/assistant/MeetMindApplication.kt`
- [X] T014 [US2] Create `InfraModule.kt` (`@Module @InstallIn(SingletonComponent::class)`) providing `ApiKeyStore` (TinkApiKeyStore), `CloudProviderConfigRepository`, `CloudKeyValidationService`, all DataStore `preferencesDataStore` delegates in `app/src/main/kotlin/com/meetmind/assistant/di/InfraModule.kt`
- [X] T015 [US2] Create `InferenceModule.kt` providing `GeminiInferenceClient`, `OnDeviceLlamaProvider` (depends on `ModelConfigRepository`), `CloudInferenceEngine` (move claudeProviderFactory lambda inside `CloudInferenceEngine` body, remove from constructor) in `app/src/main/kotlin/com/meetmind/assistant/di/InferenceModule.kt`
- [X] T016 [US2] Create `AnalysisModule.kt` providing `AnalysisSettingsRepository`, `TranscriptWindowBuffer` (@Singleton), `DefaultConversationAnalyzer`, `AnalysisCadenceController` (@Singleton) in `app/src/main/kotlin/com/meetmind/assistant/di/AnalysisModule.kt`
- [X] T017 [US2] Create `ModelModule.kt` providing `ModelConfigRepository` in `app/src/main/kotlin/com/meetmind/assistant/di/ModelModule.kt` (extended with `DeviceTierDetector` and `ModelDownloadManager` in US3)
- [X] T018 [US2] Migrate `HomeViewModel` to `@HiltViewModel @Inject constructor(configRepository: CloudProviderConfigRepository)`; delete `Factory` inner class in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/HomeViewModel.kt`
- [X] T019 [US2] Migrate `SessionViewModel` to `@HiltViewModel @Inject constructor(cloudInferenceEngine, configRepository, cadenceController: AnalysisCadenceController)`; delete `Factory` in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/SessionViewModel.kt`
- [X] T020 [US2] Migrate `CloudSettingsViewModel` to `@HiltViewModel @Inject constructor(apiKeyStore, configRepository, validationService)`; delete `Factory` in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/CloudSettingsViewModel.kt`
- [X] T021 [US2] Migrate `AnalysisSettingsViewModel` to `@HiltViewModel @Inject constructor(repository: AnalysisSettingsRepository)`; delete `Factory` in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/AnalysisSettingsViewModel.kt`
- [X] T022 [US2] Annotate `AudioProcessingForegroundService` with `@AndroidEntryPoint`; replace `(application as MeetMindApplication).container.*` with `@Inject lateinit var cloudInferenceEngine: CloudInferenceEngine` and `@Inject lateinit var transcriptWindowBuffer: TranscriptWindowBuffer` in `app/src/main/kotlin/com/meetmind/assistant/service/AudioProcessingForegroundService.kt`
- [X] T023 [US2] Update `AppNavGraph.kt` to replace all `viewModel(factory = XViewModel.Factory(...))` calls with `val vm: XViewModel = hiltViewModel()`; remove `application: MeetMindApplication` parameter from the `AppNavGraph` function in `app/src/main/kotlin/com/meetmind/assistant/ui/navigation/AppNavGraph.kt`
- [X] T024 [US2] Update `MainActivity.kt` (or wherever `AppNavGraph` is called) to remove `application` argument; update test helpers that extend `AppContainer` with `@TestInstallIn` Hilt replacement modules in `app/src/test/kotlin/com/meetmind/assistant/helpers/`
- [X] T025 [US2] Delete `app/src/main/kotlin/com/meetmind/assistant/di/AppContainer.kt` — verify zero compilation errors remain

**Checkpoint**: `./gradlew assembleDebug` succeeds with Hilt DI. `./gradlew testDebugUnitTest` passes all 44 spec-008 contract tests. No `AppContainer` reference in production code.

---

## Phase 4: User Story 3 — Multi-Model LLM Selection (Priority: P1)

**Goal**: `GemmaModelVariant` enum fully wired. `ModelConfigRepository` stores selected variant. `DeviceTierDetector` recommends Q4_K_M on Y700/Magic6Pro. `ModelSetupScreen` shows variants with download/select UI. `OnDeviceLlamaProvider` unloads before loading a different model.

**Independent Test**: Open Settings → On-Device Model. Y700 Gen 3 shows "Recommended" badge on Q4_K_M. Selecting Q8_0 persists the choice. Starting a session loads the Q8_0 GGUF.

### Contract Tests — US3

- [X] T026 [P] [US3] Write `ModelConfigRepositoryVariantTest.kt` covering C3.1 (setModelVariant persists) and C3.2 (default is Q4_K_M) in `app/src/test/kotlin/com/meetmind/assistant/data/ModelConfigRepositoryVariantTest.kt`
- [X] T027 [P] [US3] Write `DeviceTierDetectorTest.kt` covering C4.1 (≥8 GB + SDK ≥ 33 → Q4_K_M) and C4.2 (< 8 GB → IQ4_NL) in `app/src/test/kotlin/com/meetmind/assistant/data/DeviceTierDetectorTest.kt`
- [X] T028 [P] [US3] Write `OnDeviceLlamaProviderSwitchTest.kt` covering C5.1 (path change forces unload) and C5.2 (same path is idempotent) in `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderSwitchTest.kt`

### Implementation — US3

- [X] T029 [US3] Add `on_device_model_variant` DataStore key to `ModelConfigRepository`; add `observeVariant(): Flow<GemmaModelVariant>` and `suspend fun setModelVariant(variant: GemmaModelVariant)` and `fun resolvedModelPath(variant, filesDir): String` in `app/src/main/kotlin/com/meetmind/assistant/data/ModelConfigRepository.kt`
- [X] T030 [US3] Create `DeviceTierDetector` class with `fun recommendedVariant(totalRamBytes: Long, sdkVersion: Int): GemmaModelVariant` (≥8 GB + SDK ≥ 33 → Q4_K_M, else IQ4_NL) and `fun recommendedVariant(context: Context): GemmaModelVariant` convenience overload in `app/src/main/kotlin/com/meetmind/assistant/data/DeviceTierDetector.kt`
- [X] T031 [US3] Create `ModelDownloadManager` with `fun downloadFile(url: String, destFile: File): Flow<DownloadProgress>` implementing HTTP Range-header resume (check for `.partial` file, issue `Range: bytes=N-` if exists, rename `.partial` to final name on completion) in `app/src/main/kotlin/com/meetmind/assistant/data/ModelDownloadManager.kt`
- [X] T032 [US3] Update `ModelModule.kt` to add `@Provides @Singleton` for `DeviceTierDetector` and `ModelDownloadManager` in `app/src/main/kotlin/com/meetmind/assistant/di/ModelModule.kt`
- [X] T033 [US3] Update `OnDeviceLlamaProvider.loadModel()` to detect path change (`newPath != currentPath`): if change detected, call `unload()` first, then load; update `isLoaded()` to return false during path-change transition in `app/src/main/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProvider.kt`
- [X] T034 [US3] Migrate `ModelSetupViewModel` to `@HiltViewModel @Inject constructor(modelConfigRepository, deviceTierDetector, downloadManager)`; add `fun setVariant(variant: GemmaModelVariant)`, `fun startDownload(variant: GemmaModelVariant)`, `val recommendedVariant: GemmaModelVariant`, `val variants: List<GemmaModelVariant>`, `val downloadProgress: StateFlow<DownloadProgress?>` in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/ModelSetupViewModel.kt`
- [X] T035 [US3] Update `ModelSetupScreen` to show one row per `GemmaModelVariant` with: display name, size hint, "Recommended" badge (if variant == recommendedVariant), "Downloaded" badge (if file exists), Download button (triggers download) / Select button (if downloaded); use `LazyColumn` for variant list in `app/src/main/kotlin/com/meetmind/assistant/ui/screens/ModelSetupScreen.kt`
- [X] T036 [US3] Add `ModelSetupViewModel` to `ModelModule.kt` Hilt providers (or confirm `@HiltViewModel` handles it automatically) and verify `AppNavGraph` route `Routes.MODEL_SETUP` uses `hiltViewModel()` in `app/src/main/kotlin/com/meetmind/assistant/ui/navigation/AppNavGraph.kt`

**Checkpoint**: Model variant list visible in Settings. Q4_K_M recommended on Y700/Magic6Pro. Selecting a variant persists and loads on next session. C3, C4, C5 contract tests pass.

---

## Phase 5: User Story 1 — Real-Time STT with Sherpa-ONNX (Priority: P1)

**Goal**: Live ASR replaces the manual text-input stub. Silero VAD gates capture. Nemo Parakeet TDT decodes speech. `decodeMutex` prevents concurrent ONNX calls. Recognized text flows into `TranscriptWindowBuffer`.

**Independent Test**: Start a session, speak "Alice will finish the report by Friday". Within 2s of utterance end, `TranscriptWindowBuffer` contains the text. Logcat shows `D/SherpaOnnxDataSource: decode() complete`.

### Contract Tests — US1

- [X] T037 [P] [US1] Write `SherpaOnnxDataSourceTest.kt` covering C1.1 (decodeMutex max concurrent = 1), C1.2 (empty audio → zero emissions), C1.3 (recognized text appended to TranscriptWindowBuffer) in `app/src/test/kotlin/com/meetmind/assistant/stt/SherpaOnnxDataSourceTest.kt`

### Implementation — US1

- [X] T038 [US1] Create `SttRepository` interface (`suspend fun initialize(sttModelPath: String)`, `fun startRecording(): Flow<RecognitionResult>`, `suspend fun stopRecording()`, `suspend fun releaseModel()`) in `app/src/main/kotlin/com/meetmind/assistant/stt/SttRepository.kt`
- [X] T039 [US1] Create `SherpaOnnxDataSource.kt` adapted from `F:\Git\HearoPilot-App\feature-stt\src\main\java\com\hearopilot\app\feature\stt\datasource\SherpaOnnxDataSource.kt` — update package to `com.meetmind.assistant.stt`; use `com.meetmind.assistant.stt.sherpa.OfflineRecognizer` + `Vad`; apply all pipeline constants from research.md (16 kHz, 512 VAD window, 6400 lookback, 24000 min samples, mutex); emit `RecognitionResult` in `app/src/main/kotlin/com/meetmind/assistant/stt/SherpaOnnxDataSource.kt`
- [X] T040 [US1] Create `SttRepositoryImpl` (pass-through to `SherpaOnnxDataSource`) in `app/src/main/kotlin/com/meetmind/assistant/stt/SttRepositoryImpl.kt`
- [X] T041 [US1] Add `SttModule.kt` (`@Module @InstallIn(SingletonComponent::class)`) providing `SttRepository` (bound to `SttRepositoryImpl`), `SherpaOnnxDataSource` (singleton, constructed with `@ApplicationContext context` + `OfflineRecognizer` provider lazy init) in `app/src/main/kotlin/com/meetmind/assistant/di/SttModule.kt`
- [X] T042 [US1] Update `AudioProcessingForegroundService` to: inject `SttRepository` via `@Inject`; in `onStartCommand()` launch the audio capture coroutine calling `sttRepository.startRecording().collect { result -> if (result.isComplete) onTranscriptSegment(result.text) }` in `app/src/main/kotlin/com/meetmind/assistant/service/AudioProcessingForegroundService.kt`
- [X] T043 [US1] Add `SttModelConfig` companion constants (STT_BASE_URL, STT_FILES, MODEL_DIR_NAME) to `app/src/main/kotlin/com/meetmind/assistant/data/model/SttModelConfig.kt`; update `ModelDownloadManager` to expose `downloadSttModel(context): Flow<DownloadProgress>` downloading the 4 Parakeet TDT files

**Checkpoint**: Speak a phrase in a session → transcript appears in `TranscriptWindowBuffer` → analysis cadence picks it up. `SherpaOnnxDataSourceTest` C1.1–C1.3 pass.

---

## Phase 6: User Story 4 — STT Model Download & Setup Flow (Priority: P2)

**Goal**: Setup screen downloads the 4 Sherpa-ONNX Nemo Parakeet TDT files (~200 MB) via `ModelDownloadService`. Session start is blocked until all 4 files are present.

**Independent Test**: Fresh install → app shows download screen → download completes → session starts. Interrupt download → resume → completes from byte offset.

### Contract Tests — US4

- [X] T044 [P] [US4] Write `ModelDownloadManagerTest.kt` covering C2.1 (monotonic progress 0%→100%) and C2.2 (resume skips already-downloaded bytes; Range header asserted) in `app/src/test/kotlin/com/meetmind/assistant/data/ModelDownloadManagerTest.kt` using MockWebServer

### Implementation — US4

- [X] T045 [US4] Create `ModelDownloadService` (foreground service, `android:foregroundServiceType="dataSync"`) that calls `modelDownloadManager.downloadSttModel()` and broadcasts progress via a `StateFlow` or LocalBroadcast; handles `onDestroy()` by cancelling download scope in `app/src/main/kotlin/com/meetmind/assistant/service/ModelDownloadService.kt`
- [X] T046 [US4] Register `ModelDownloadService` in `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"` and add `FOREGROUND_SERVICE_DATA_SYNC` permission if not already declared
- [X] T047 [US4] Add `sttModelsReady(): Boolean` helper to `ModelDownloadManager` that checks all 4 `SttModelConfig.STT_FILES` exist in the STT model directory
- [X] T048 [US4] Update `ModelSetupScreen` (or create a `SttSetupScreen`) to show STT download progress section: file-by-file progress bars, total progress, "Start Session" button disabled until `sttModelsReady() == true` in `app/src/main/kotlin/com/meetmind/assistant/ui/screens/ModelSetupScreen.kt`
- [X] T049 [US4] Add route guard in `AppNavGraph.kt`: if `sttModelsReady() == false` when navigating to `Routes.SESSION`, redirect to `Routes.MODEL_SETUP` instead

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T050 [P] Add logcat tags (`SherpaOnnxDataSource`, `ModelDownloadManager`, `ModelDownloadService`, `DeviceTierDetector`) as per `quickstart.md` filter spec; add `TAG` constants to each class
- [X] T051 [P] Update `CLAUDE.md` and `quickstart.md` to document the `adb push` shortcut for manual STT model installation (for development speed)
- [X] T052 Run hardware smoke test per `quickstart.md` on Lenovo Y700 Gen 3: Steps 1–6 (STT, Hilt build, model switching, download resume)
- [X] T053 Run hardware smoke test per `quickstart.md` on Honor Magic 6 Pro: same steps
- [X] T054 [P] Verify `./gradlew testDebugUnitTest` passes all contract tests C1–C6 (44 existing spec-008 + new spec-009 tests)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user story phases**
- **Phase 3 (US2 — Hilt)**: Depends on Phase 2 — **MUST complete before US3/US4 (ViewModels depend on Hilt)**
- **Phase 4 (US3 — Model Switching)**: Depends on Phase 3 (Hilt modules must exist for new providers)
- **Phase 5 (US1 — STT)**: Depends on Phase 3 (Hilt modules for SttModule); can run in parallel with Phase 4
- **Phase 6 (US4 — Download)**: Depends on Phase 5 (needs ModelDownloadManager from US3) and Phase 4
- **Phase 7 (Polish)**: Depends on Phases 4–6

### User Story Dependencies

- **US2 (Hilt)**: After Phase 2 — blocks all others; MVP-first
- **US3 (Model Switching)**: After US2 (Hilt modules needed for new @Provides)
- **US1 (STT)**: After US2; can run in parallel with US3
- **US4 (Download)**: After US1 + US3 (needs ModelDownloadManager + STT config)

### Parallel Opportunities

- T003, T004, T005, T006, T007 (Phase 1) — run together
- T009, T010, T011 (Phase 2) — run together after T008
- T026, T027, T028 (US3 contract tests) — run together
- T037 (US1 contract test) — parallel with T038 (US1 interface)
- T044 (US4 contract test) — parallel with T045 (US4 service)
- T050, T051 (Polish) — run together
- T052, T053 (smoke tests) — run together on separate devices

---

## Parallel Example: User Story 3 (Model Switching)

```
# Simultaneously:
T026: ModelConfigRepositoryVariantTest (C3.1–C3.2)
T027: DeviceTierDetectorTest (C4.1–C4.2)
T028: OnDeviceLlamaProviderSwitchTest (C5.1–C5.2)

# After tests written, simultaneously:
T029: ModelConfigRepository variant key + methods
T030: DeviceTierDetector implementation
T031: ModelDownloadManager HTTP resume implementation

# After above, sequential:
T032: ModelModule.kt update (wires T030 + T031)
T033: OnDeviceLlamaProvider path-change handling (uses T029)
T034: ModelSetupViewModel (uses T029, T030, T031)
T035: ModelSetupScreen UI (uses T034)
```

---

## Implementation Strategy

### MVP (Phases 1–3: Hilt migration only — no new features)

1. Complete Phase 1: Setup (Hilt wired to build)
2. Complete Phase 2: Foundational (Sherpa wrappers + test doubles)
3. Complete Phase 3: US2 — Hilt migration
4. **STOP and VALIDATE**: `./gradlew assembleDebug` green; all 44 spec-008 tests pass; no AppContainer ref
5. Demo: app runs identically to before, but Hilt-powered

### Full Feature (Phases 4–7)

6. Phase 4 (US3): Model switching — `GemmaModelVariant` + `ModelSetupScreen`
7. Phase 5 (US1): Live STT — `SherpaOnnxDataSource` + `AudioProcessingForegroundService` wiring
8. Phase 6 (US4): Download flow — `ModelDownloadService` + setup screen gating
9. Phase 7: Smoke tests on both devices

---

## Notes

- `[P]` = different files, no blocking dependency on an incomplete parallel task
- `[US#]` label maps task to user story for traceability
- Contract tests (C1–C6) MUST be written before their corresponding implementation tasks
- Hilt migration (US2) is a prerequisite for ALL other user stories — complete it first
- `SherpaOnnxDataSource` is adapted from HearoPilot verbatim — do not redesign; only change package names
- `decodeMutex` is non-negotiable for STT thread safety — `libonnxruntime` will SIGSEGV without it
- STT thread count fixed at 2 — do not increase (CPU contention with LLM engine)
- Y700 Gen 3 and Honor Magic 6 Pro both qualify for Q4_K_M recommendation (12 GB RAM, SDK 34)
