# Tasks: On-Device Gemma 4 E4B Inference

**Input**: Design documents from `/specs/007-on-device-gemma4/`
**Branch**: `feature/007-on-device-gemma4`
**Prerequisites**: plan.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths included in all descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: NDK build wiring and project scaffolding. Must complete before any JNI work.

- [X] T001 Add `jniLibs/arm64-v8a/` directory to `.gitignore` and create placeholder `README.md` explaining that `liballama.so` and `libggml.so` must be built from llama.cpp source per `quickstart.md` — file: `app/src/main/jniLibs/arm64-v8a/BUILD_INSTRUCTIONS.md`
- [X] T002 [P] Add `externalNativeBuild` block to `app/build.gradle.kts` pointing to a stub `CMakeLists.txt` (empty at first) so Gradle resolves NDK toolchain — file: `app/build.gradle.kts`
- [X] T003 [P] Create stub `app/CMakeLists.txt` with `cmake_minimum_required(VERSION 3.22)` and `project(meetmind)` — to be filled in Story 1 — file: `app/CMakeLists.txt`
- [X] T004 [P] Add DataStore Preferences key constants for `on_device_` prefix to `libs.versions.toml` if any new version bumps needed; confirm existing `datastore-preferences` dep covers spec 007 needs — file: `gradle/libs.versions.toml`

**Checkpoint**: Gradle syncs cleanly with NDK configured; `jniLibs/` directory structure exists.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data types and interfaces that all stories depend on. Must complete before US1–US4.

- [X] T005 Create `TokenCallback.kt` interface with `onToken(token: String)`, `onComplete(fullText: String)`, `onError(message: String)` — file: `app/src/main/kotlin/com/meetmind/assistant/inference/TokenCallback.kt`
- [X] T006 [P] Create `ModelLoadState.kt` sealed class: `NotLoaded`, `Loading`, `Ready(handle: Long)`, `Error(message: String)` — file: `app/src/main/kotlin/com/meetmind/assistant/data/model/ModelLoadState.kt`
- [X] T007 [P] Create `ModelConfig.kt` data class: `modelPath: String`, `nThreads: Int`, `nGpuLayers: Int`, `contextSize: Int` with companion defaults (`DEFAULT_PATH`, `DEFAULT_THREADS = 6`, `DEFAULT_GPU_LAYERS = -1`, `DEFAULT_CONTEXT = 2048`) — file: `app/src/main/kotlin/com/meetmind/assistant/data/model/ModelConfig.kt`
- [X] T008 Create `LlamaJni.kt` with all `external fun` declarations: `loadModel`, `unloadModel`, `inferenceAsync`, `canLoad`; add `System.loadLibrary("llama")` in `init` block wrapped in try/catch so unit tests don't crash on missing `.so` — file: `app/src/main/kotlin/com/meetmind/assistant/inference/LlamaJni.kt`
- [X] T009 Create `FakeLlamaJni.kt` test double implementing the same `loadModel`/`unloadModel`/`inferenceAsync`/`canLoad` surface via a companion object swap pattern or interface extraction — file: `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeLlamaJni.kt`

**Checkpoint**: All types compile. `FakeLlamaJni` is importable in test classes. `LlamaJni.init` does not crash in Robolectric context.

---

## Phase 3: User Story 1 — JNI Bridge + OnDeviceLlamaProvider (Priority: P1) 🎯 MVP

**Goal**: Replace the `AppContainer` placeholder lambda with a real `OnDeviceLlamaProvider` that
streams `InferenceEvent` tokens via `callbackFlow` + `LlamaJni`. App remains launchable if model
not loaded (graceful degradation via `Error` event).

**Independent Test**: With `FakeLlamaJni` injected, `onDeviceLlamaProvider.generate("what is agile?")`
emits ≥ 1 `InferenceEvent.Token` followed by exactly 1 `InferenceEvent.Complete`. With no model
loaded, emits `InferenceEvent.Error`. Verify in `OnDeviceLlamaProviderTest`.

### Contract Tests for User Story 1

- [X] T010 [P] [US1] Write contract test C1.1 (emits ≥ 1 Token before Complete) using `FakeLlamaJni` — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T011 [P] [US1] Write contract test C1.2 (Complete.fullText == joined tokens) — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T012 [P] [US1] Write contract test C1.3 (model not loaded → emits Error, no exception) — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T013 [P] [US1] Write contract test C1.4 (blank questionText → emits Error "empty question") — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T014 [P] [US1] Write contract test C5.1 (questionText > 600 chars is silently truncated before JNI call) — verify via FakeLlamaJni that captured `userText` arg length ≤ 600 — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T015 [P] [US1] Write contract test C5.3 (system prompt built by provider is ≤ 320 chars) — expose `internal fun buildSystemPrompt()` for test access — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T016 [P] [US1] Write contract test C3.1 (FakeLlamaJni returns handle > 0 for valid path) and C3.2 (returns -1 for `/nonexistent/`) — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`
- [X] T017 [P] [US1] Write contract test C3.3 (unloadModel called twice does not throw) — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`

### Implementation for User Story 1

- [X] T018 [US1] Implement `ModelConfigRepository.kt` with DataStore Preferences: `observe(): Flow<ModelConfig>`, `setModelPath()`, `setNThreads()` (clamp 1..16), `setNGpuLayers()`, `setContextSize()` — file: `app/src/main/kotlin/com/meetmind/assistant/data/ModelConfigRepository.kt`
- [X] T019 [US1] Implement `OnDeviceLlamaProvider.kt`: lazy model load via `ReentrantReadWriteLock`, `generate()` with `callbackFlow`, `questionText.take(600)` guard, `buildSystemPrompt()` (internal, ≤ 320 chars), `loadModel(): ModelLoadState`, `unload()` — file: `app/src/main/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProvider.kt`
- [X] T020 [US1] Extract `LlamaJni` calls behind an interface `LlamaBackend` so `OnDeviceLlamaProvider` takes `backend: LlamaBackend = LlamaJni` — enables `FakeLlamaJni` injection without companion-swap — update `LlamaJni.kt` to implement `LlamaBackend` — files: `app/src/main/kotlin/com/meetmind/assistant/inference/LlamaBackend.kt`, `app/src/main/kotlin/com/meetmind/assistant/inference/LlamaJni.kt`
- [X] T021 [US1] Wire `OnDeviceLlamaProvider` into `AppContainer.kt`: add `modelConfigRepository` lazy property, add `onDeviceLlamaProvider` lazy property, replace placeholder `onDeviceFallback` lambda with `onDeviceLlamaProvider` — file: `app/src/main/kotlin/com/meetmind/assistant/di/AppContainer.kt`
- [X] T022 [US1] Add `ModelConfigRepository` instantiation to `AppContainer.kt` using existing shared DataStore instance (create new DataStore file `"on_device_config"` distinct from cloud config) — file: `app/src/main/kotlin/com/meetmind/assistant/di/AppContainer.kt`
- [X] T023 [US1] Update `OnDeviceLlamaProviderTest.kt` to inject `FakeLlamaJni` via `LlamaBackend` interface — run all T010–T017 tests, confirm they FAIL before T019, PASS after — file: `app/src/test/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProviderTest.kt`

**Checkpoint**: `./gradlew test` passes all `OnDeviceLlamaProviderTest` cases. `./gradlew installDebug` on Y700 Gen 3 launches without crash even with no model file present. `onDeviceFallback` placeholder is fully removed from `AppContainer`.

---

## Phase 4: User Story 2 — ModelConfigRepository Tests (Priority: P1)

**Goal**: Full contract test coverage for `ModelConfigRepository` so config persistence is
verified without a real device.

**Independent Test**: `ModelConfigRepositoryTest` passes on JVM with in-memory DataStore.

### Contract Tests for User Story 2

- [X] T024 [P] [US2] Write contract test C2.1 (setModelPath → observe returns updated path) using in-memory DataStore — file: `app/src/test/kotlin/com/meetmind/assistant/data/ModelConfigRepositoryTest.kt`
- [X] T025 [P] [US2] Write contract test C2.2 (`isModelReady` returns false when file does not exist; true when file exists + `canLoad()` returns true) — mock filesystem via `FakeLlamaJni.canLoad` — file: `app/src/test/kotlin/com/meetmind/assistant/data/ModelConfigRepositoryTest.kt`
- [X] T026 [P] [US2] Write contract test C2.3 (`setNThreads(0)` stores 1; `setNThreads(99)` stores 16) — file: `app/src/test/kotlin/com/meetmind/assistant/data/ModelConfigRepositoryTest.kt`

### Implementation for User Story 2

- [X] T027 [US2] Add `isModelReady(backend: LlamaBackend): Boolean` to `ModelConfigRepository` — returns `File(modelPath).exists() && backend.canLoad(modelPath)` — file: `app/src/main/kotlin/com/meetmind/assistant/data/ModelConfigRepository.kt`
- [X] T028 [US2] Run `ModelConfigRepositoryTest`, confirm C2.1–C2.3 all pass — file: `app/src/test/kotlin/com/meetmind/assistant/data/ModelConfigRepositoryTest.kt`

**Checkpoint**: `./gradlew test` passes all `ModelConfigRepositoryTest` cases.

---

## Phase 5: User Story 3 — ModelSetupScreen + ViewModel (Priority: P2)

**Goal**: User can open Settings → On-Device Model, see model load status, configure path,
and tap "Load Model". App never crashes if model is absent.

**Independent Test**: Launch app → navigate to Settings → On-Device Model. With no model file:
status chip shows 🔴 "Model not found" and adb push command is visible. With model present:
"Load Model" button is enabled. After tap: chip transitions 🟡 → 🟢 or 🔴 with error message.

### Contract Tests for User Story 3

- [X] T029 [P] [US3] Write ViewModel unit test C4.3 (tapping load → `loadState` transitions `NotLoaded → Loading → Ready` or `Error`) using `FakeLlamaJni` and `UnconfinedTestDispatcher` — file: `app/src/test/kotlin/com/meetmind/assistant/viewmodel/ModelSetupViewModelTest.kt`
- [X] T030 [P] [US3] Write ViewModel unit test C4.1 (when `isModelReady == true`, `loadState == Ready`) — file: `app/src/test/kotlin/com/meetmind/assistant/viewmodel/ModelSetupViewModelTest.kt`

### Implementation for User Story 3

- [X] T031 [US3] Implement `ModelSetupViewModel.kt`: `modelConfig: StateFlow<ModelConfig>`, `loadState: StateFlow<ModelLoadState>`, `fun loadModel()` (calls `onDeviceLlamaProvider.loadModel()`, updates `loadState`), `fun setModelPath(path: String)` — file: `app/src/main/kotlin/com/meetmind/assistant/viewmodel/ModelSetupViewModel.kt`
- [X] T032 [US3] Implement `ModelSetupScreen.kt` Compose screen: editable path `TextField`, status chip (`NotLoaded`=🔴, `Loading`=🟡 + spinner, `Ready`=🟢, `Error`=🔴 + message), "Load Model" `Button` (disabled while `Loading`), adb push command `Text` (shown only when file not found), advanced settings collapse (nThreads, nGpuLayers sliders) — file: `app/src/main/kotlin/com/meetmind/assistant/ui/screens/ModelSetupScreen.kt`
- [X] T033 [US3] Add `ModelSetupViewModel` factory to `AppContainer.kt` following existing ViewModel factory pattern — file: `app/src/main/kotlin/com/meetmind/assistant/di/AppContainer.kt`
- [X] T034 [US3] Add `modelSetup` destination to `AppNavGraph.kt`: route `"settings/model"`, composable `ModelSetupScreen` — file: `app/src/main/kotlin/com/meetmind/assistant/ui/navigation/AppNavGraph.kt`
- [X] T035 [US3] Add "On-Device Model" `ListItem` entry in Settings screen (or Home screen settings if no dedicated settings screen exists) that navigates to `"settings/model"` — file: `app/src/main/kotlin/com/meetmind/assistant/ui/screens/HomeScreen.kt` or `SettingsScreen.kt`

**Checkpoint**: Navigate to Settings → On-Device Model in the running app. Screen loads. Status reflects actual model state. "Load Model" tap shows loading indicator.

---

## Phase 6: User Story 4 — APK Smoke Test + onTrimMemory Hook (Priority: P2)

**Goal**: End-to-end verification on real Y700 Gen 3 hardware. Plus memory pressure safety
via `onTrimMemory` hook so Android can reclaim the 5–8 GB model RAM when needed.

**Independent Test**: Follow `quickstart.md` steps 1–8. First visible token appears ≤ 1s
from question detection. Cloud fallback (when cloud times out) correctly routes through
Gemma 4 E4B on-device.

### Implementation for User Story 4

- [X] T036 [US4] Add `onTrimMemory(level: Int)` override in `MeetMindApplication.kt` (or create it if it doesn't exist): call `appContainer.onDeviceLlamaProvider.unload()` when `level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL` — file: `app/src/main/kotlin/com/meetmind/assistant/MeetMindApplication.kt`
- [X] T037 [US4] Add logcat tags to `OnDeviceLlamaProvider.kt`: log model load duration (ms), first-token latency, decode rate (tok/s) at `Log.i("OnDeviceLlamaProvider", ...)` — file: `app/src/main/kotlin/com/meetmind/assistant/inference/OnDeviceLlamaProvider.kt`
- [ ] T038 [US4] Manual APK smoke test per `quickstart.md` Step 5 (path config + load), Step 6 (live question → tokens stream), Step 7 (first-token ≤ 1s via logcat), Step 8 (cloud timeout → Gemma 4 fallback confirmed by `CloudBadgeState.FALLBACK` badge) — document results in `specs/007-on-device-gemma4/smoke-test-results.md`
- [ ] T039 [US4] If first-token latency > 1s on hardware: profile with `adb shell simpleperf` and tune `nThreads`/`nGpuLayers` defaults in `ModelConfig.kt` — file: `app/src/main/kotlin/com/meetmind/assistant/data/model/ModelConfig.kt`

**Checkpoint**: `smoke-test-results.md` documents passing results. First token ≤ 1s. `TRIM_MEMORY_RUNNING_CRITICAL` triggers unload (verify via `adb shell dumpsys meminfo`).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T040 [P] Update `CLAUDE.md` Active Technologies section: add llama.cpp JNI, Gemma 4 E4B IT Q4_K_M, OpenCL/Adreno 750 backend, `ModelConfigRepository` (DataStore) — file: `CLAUDE.md`
- [X] T041 [P] Run `/graphify . --update` to add the 6 new source files to the knowledge graph — `OnDeviceLlamaProvider`, `LlamaJni`, `LlamaBackend`, `ModelConfigRepository`, `ModelConfig`, `ModelLoadState`
- [X] T042 [P] Add `liballama.so` and `libggml.so` patterns to `.gitignore` so built artifacts are never committed — file: `.gitignore`
- [ ] T043 Update `quickstart.md` with any corrections discovered during smoke test (actual SHA256 of GGUF, pinned llama.cpp commit) — file: `specs/007-on-device-gemma4/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — blocks all user stories
- **Phase 3 (US1 — JNI Bridge)**: Depends on Phase 2 — this is the MVP core
- **Phase 4 (US2 — Config Tests)**: Depends on Phase 2; can run in parallel with Phase 3
- **Phase 5 (US3 — Setup UI)**: Depends on Phase 3 (needs `OnDeviceLlamaProvider`)
- **Phase 6 (US4 — Smoke Test)**: Depends on Phase 3 + Phase 5 — requires real device
- **Phase 7 (Polish)**: Depends on Phase 6

### User Story Dependencies

- **US1**: Start after Phase 2 — no other story dependencies
- **US2**: Start after Phase 2 — no dependency on US1 (shares `ModelConfigRepository` but tests are independent)
- **US3**: Start after US1 complete (needs `OnDeviceLlamaProvider` instance)
- **US4**: Start after US1 + US3 complete (needs working provider + setup UI)

### Within Each User Story

- Contract tests (T010–T017) MUST be written first and confirmed FAILING before T018–T023
- `LlamaBackend` interface (T020) must exist before `OnDeviceLlamaProvider` (T019)
- `ModelConfigRepository` (T018) before `OnDeviceLlamaProvider` (T019)
- `ModelSetupViewModel` (T031) before `ModelSetupScreen` (T032)

### Parallel Opportunities

- T002, T003, T004 — all Phase 1 setup tasks, different files
- T005, T006, T007, T008, T009 — all foundational, different files
- T010–T017 — all contract tests for US1, different test methods (same file, can be written sequentially fast)
- T024, T025, T026 — all US2 contract tests, same file
- T029, T030 — US3 ViewModel tests
- T036, T037 — US4 polish tasks, different files
- T040, T041, T042 — all polish, different files/tools

---

## Parallel Example: User Story 1

```
# Write all contract tests in parallel (same file, different @Test methods):
T010 C1.1: emits ≥ 1 Token
T011 C1.2: Complete.fullText == joined tokens
T012 C1.3: not loaded → Error
T013 C1.4: blank input → Error "empty question"
T014 C5.1: truncation at 600 chars
T015 C5.3: system prompt ≤ 320 chars
T016 C3.1 + C3.2: FakeLlamaJni handle behavior
T017 C3.3: idempotent unload

# Then implement in dependency order:
T018 ModelConfigRepository → T019 OnDeviceLlamaProvider → T020 LlamaBackend interface
T021 + T022 AppContainer wiring (can overlap with T020)
T023 Run all tests, confirm green
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T004)
2. Complete Phase 2: Foundational (T005–T009)
3. Write contract tests T010–T017 (confirm FAIL)
4. Complete Phase 3 US1 implementation (T018–T022)
5. Run T023 — confirm all pass
6. **STOP and VALIDATE**: `./gradlew installDebug` on Y700 Gen 3; on-device fallback emits real Gemma 4 tokens

### Incremental Delivery

1. Phase 1 + 2 → foundation ready
2. US1 (T010–T023) → real on-device inference working, placeholder removed ← **MVP**
3. US2 (T024–T028) → config repo fully tested
4. US3 (T029–T035) → users can configure model path in UI
5. US4 (T036–T039) → smoke tested on hardware, memory safety in place
6. Polish (T040–T043) → graph updated, docs finalized

### Task Count Summary

| Phase | Tasks | Story |
|-------|-------|-------|
| Setup | 4 | — |
| Foundational | 5 | — |
| US1 (JNI Bridge) | 14 | P1 MVP |
| US2 (Config Tests) | 5 | P1 |
| US3 (Setup UI) | 7 | P2 |
| US4 (Smoke Test) | 4 | P2 |
| Polish | 4 | — |
| **Total** | **43** | |

---

## Notes

- `[P]` tasks target different files — safe to parallelise
- `LlamaBackend` interface (T020) is the key testability unlock — do it before T019
- `FakeLlamaJni` (T009) must be complete before any contract tests (T010–T017)
- The `.so` files are NEVER committed — only `BUILD_INSTRUCTIONS.md` and `.gitignore` entry
- Stop at Phase 3 checkpoint to validate MVP on real hardware before proceeding to UI work
