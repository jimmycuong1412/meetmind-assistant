# Feature Specification: On-Device Gemma 4 E4B Inference

**Feature Branch**: `feature/007-on-device-gemma4`
**Created**: 2026-04-17
**Status**: Draft
**Input**: User direction: "Implement Gemma 4 E4B IT as the target model" for the on-device inference fallback in MeetMind Assistant (Y700 Gen 3, Snapdragon 8 Gen 3, 16 GB RAM)
**Constitution version at creation**: 2.0.0

## User Scenarios & Testing *(mandatory)*

### User Story 1 - On-Device Inference Works (Priority: P1)

As the developer/owner of MeetMind Assistant, when a meeting question is detected and either
cloud inference is disabled or falls back, the app uses the locally-loaded Gemma 4 E4B IT model
to generate a suggestion, streaming tokens to the UI in real-time.

**Why this priority**: This is the core on-device fallback that replaces the current hardcoded
placeholder string `"[On-device fallback — model not loaded]"`. Without it, the app provides
no meaningful suggestion when cloud is unavailable.

**Independent Test**: With cloud inference disabled and Gemma 4 E4B GGUF loaded at the
configured path, trigger a question from the session screen. Tokens stream visibly within 1 second.
The `CloudBadgeState` shows `HIDDEN` (on-device, no cloud badge).

**Acceptance Scenarios**:

1. **Given** model is loaded (handle > 0) and cloud is disabled, **When** a question is detected, **Then** `OnDeviceLlamaProvider.generate()` emits ≥ 1 `InferenceEvent.Token` followed by `InferenceEvent.Complete`
2. **Given** model is NOT loaded (handle = -1), **When** a question is detected, **Then** `InferenceEvent.Error` is emitted with a descriptive message; app does not crash
3. **Given** `questionText` is blank, **When** `generate()` is called, **Then** `InferenceEvent.Error("empty question")` is emitted
4. **Given** `questionText` is 800 chars, **When** `generate()` is called, **Then** only 600 chars are passed to the native layer (data minimisation)
5. **Given** cloud times out (>5s), **When** `CloudInferenceEngine` falls back, **Then** `OnDeviceLlamaProvider.generate()` is called and streams tokens normally

---

### User Story 2 - Model Setup Screen (Priority: P2)

As the developer/owner, I can open a "On-Device Model" settings screen to configure the GGUF
file path, load the model, and see real-time load status before starting a meeting session.

**Why this priority**: Without a setup UI, the model path is hardcoded and there is no way
to see if the model loaded successfully or diagnose failures.

**Independent Test**: Navigate to Settings → On-Device Model. The screen shows the current
model path, a status chip (🔴/🟡/🟢), and a "Load Model" button. Tapping it transitions the
chip through Loading → Ready (or Error with a message).

**Acceptance Scenarios**:

1. **Given** model GGUF is absent from configured path, **When** ModelSetupScreen opens, **Then** status chip shows 🔴 "Not loaded" and adb push command is visible
2. **Given** model GGUF exists at configured path, **When** "Load Model" is tapped, **Then** chip transitions 🟡 Loading → 🟢 Ready within 10s
3. **Given** model fails to load (corrupted/wrong format), **When** load completes, **Then** chip shows 🔴 Error with message from native layer
4. **Given** model is Ready, **When** OS signals `TRIM_MEMORY_RUNNING_CRITICAL`, **Then** model is unloaded and ~6–8 GB RAM freed; chip reverts to 🔴 Not loaded

---

### User Story 3 - Contract Test Coverage (Priority: P1)

As the developer/owner, all on-device inference contracts are covered by automated JVM tests
(no physical device required) using `FakeLlamaJni` as the test double.

**Why this priority**: The JNI boundary is a major risk surface — native crashes produce
undebuggable JVM-side errors. Contracts must be verified before hardware testing.

**Independent Test**: `./gradlew testDebugUnitTest` passes all tests in:
- `OnDeviceLlamaProviderTest` (C1.1–C1.5, C3.1–C3.5, C5.1–C5.3)
- `ModelConfigRepositoryTest` (C2.1–C2.3)
- `ModelSetupViewModelTest` (C4.1, C4.3)

**Acceptance Scenarios**:

1. **Given** `FakeLlamaJni` with valid tokens, **When** tests run on JVM, **Then** all contract tests pass without loading any `.so` file
2. **Given** `FakeLlamaJni` with `loadShouldFail = true`, **When** `loadModel()` is called, **Then** `ModelLoadState.Error` is returned
3. **Given** `questionText` of 800 chars, **When** contract test C5.1 runs, **Then** captured `lastUserText.length == 600` is asserted

---

### User Story 4 - APK Smoke Test on Y700 Gen 3 (Priority: P2)

As the developer/owner, the app builds and installs correctly on the Lenovo Legion Y700 Gen 3,
and on-device Gemma 4 E4B inference produces visible token output within 1 second of question
detection.

**Why this priority**: JNI + native libraries only work on real ARM64 hardware. The JVM tests
verify the Kotlin contract layer but not the actual llama.cpp execution path.

**Independent Test**: Follow `quickstart.md`. First visible token ≤ 1s measured via logcat
timestamps. `adb shell dumpsys meminfo` shows ~6–8 GB allocated to the process.

**Acceptance Scenarios**:

1. **Given** GGUF pre-pushed via adb, **When** "Load Model" is tapped on Y700 Gen 3, **Then** model loads in ≤ 10s; status shows 🟢 Ready
2. **Given** model is loaded, **When** a question is spoken in a session, **Then** first token appears in UI within 1s
3. **Given** cloud API key is set and cloud times out, **When** 5s timeout triggers, **Then** `FALLBACK` badge appears and Gemma 4 on-device tokens stream normally

---

### Edge Cases

- What happens when the GGUF file is deleted while the model is loaded in RAM? (handle remains valid until `unloadModel()` — no crash expected)
- What happens on first launch before `adb push`? → One-time snackbar "Model not found — open Settings to configure" is shown; app remains fully usable without model (graceful degradation via `InferenceEvent.Error`)
- What if the user calls "Load Model" while a load is already in progress? (ViewModel guards: no-op if already Loading)
- What if `nGpuLayers > 0` but OpenCL is not available on the device? → `loadModel()` returns ≤ 0 with an error message from the native layer; `ModelSetupViewModel` detects "opencl"/"gpu" in the message and surfaces "OpenCL/GPU init failed — check Adreno drivers or set GPU layers to 0"
- What if the model generates an infinite loop / never calls `onComplete`? (no current timeout — future improvement)
- What if `inferenceAsync` is called with an already-unloaded handle? (native layer returns error via `onError` callback)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST replace the placeholder `onDeviceFallback` in `AppContainer` with a real `OnDeviceLlamaProvider` backed by `LlamaJni`
- **FR-002**: `OnDeviceLlamaProvider.generate()` MUST emit `InferenceEvent.Token` fragments then `InferenceEvent.Complete` for each inference request
- **FR-003**: `questionText` MUST be silently truncated to 600 characters before passing to the native layer (Principle I data minimisation)
- **FR-004**: The system prompt built by `OnDeviceLlamaProvider` MUST be ≤ 320 characters
- **FR-005**: `ModelConfigRepository` MUST persist model path, thread count (clamped 1..16), GPU layers, and context size to DataStore Preferences
- **FR-006**: `ModelSetupScreen` MUST display real-time model load status (NotLoaded / Loading / Ready / Error)
- **FR-007**: `ModelSetupScreen` MUST display the adb push command when the model file is not found at the configured path
- **FR-008**: `MeetMindApplication.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` MUST call `OnDeviceLlamaProvider.unload()` to free native RAM
- **FR-009**: `LlamaJni.init` MUST catch `UnsatisfiedLinkError` so the app does not crash in JVM test environments where `liballama.so` is absent
- **FR-010**: `LlamaJni` MUST be abstracted behind `LlamaBackend` interface to enable `FakeLlamaJni` injection in tests
- **FR-011**: On app start, `MeetMindApplication` MUST trigger `OnDeviceLlamaProvider.loadModel()` in the background (non-blocking, on `Dispatchers.Default`) if the GGUF file exists at the configured path; the UI remains fully usable during load
- **FR-012**: If the GGUF file is absent at app start (auto-load check), the app MUST surface a one-time snackbar/notification "Model not found — open Settings to configure"; this notification must not repeat on subsequent launches unless the path changes
- **FR-013**: If `loadModel()` returns a handle ≤ 0, `ModelSetupViewModel` MUST inspect the error message from the native layer; if the message contains "opencl" or "gpu" (case-insensitive), the `ModelLoadState.Error` message surfaced to the UI MUST include a human-readable hint: "OpenCL/GPU init failed — check that Adreno drivers are up to date, or set GPU layers to 0 to use CPU only"

### Key Entities

- **ModelConfig**: `modelPath`, `nThreads` (1–16), `nGpuLayers` (-1=all), `contextSize` (tokens; default 8192 = full Gemma 4 E4B context)
- **ModelLoadState**: sealed — NotLoaded | Loading | Ready(handle: Long) | Error(message: String)
- **LlamaBackend**: interface over JNI — `loadModel`, `unloadModel`, `inferenceAsync`, `canLoad`
- **OnDeviceLlamaProvider**: implements `CloudInferenceEngine.OnDeviceFallback`; lazy model load, callbackFlow streaming
- **ModelConfigRepository**: DataStore Preferences (`on_device_config`) reader/writer

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: First visible token ≤ 1 second from question detection on Y700 Gen 3 (Snapdragon 8 Gen 3 + OpenCL Adreno 750)
- **SC-002**: Total suggestion latency ≤ 3 seconds for typical meeting question (≤ 150 output tokens) — Constitution Principle II
- **SC-003**: Decode rate ≥ 8 tok/s with OpenCL GPU offload enabled (`nGpuLayers = -1`)
- **SC-004**: `./gradlew testDebugUnitTest` passes all 24+ new contract tests with zero native library dependencies
- **SC-005**: `questionText` passed to JNI is always ≤ 600 chars (verified by contract test C5.1)
- **SC-006**: System prompt passed to JNI is always ≤ 320 chars (verified by contract test C5.3)
- **SC-007**: Model unloads within 500ms of `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` signal
- **SC-008**: App launches and is usable without a model loaded (graceful degradation — `InferenceEvent.Error` instead of crash)

## Clarifications

### Session 2026-04-17

- Q: When the app launches fresh, should Gemma 4 auto-reload in the background or only load when user taps "Load Model"? → A: Auto-load on app start (background, non-blocking)
- Q: What llama.cpp version/commit should be pinned for reproducible builds? → A: Pin to a specific git tag (e.g. latest `b5xxx` at build time)
- Q: When auto-load triggers on app start and the GGUF file is absent (first run before adb push), what should happen? → A: Show a one-time snackbar/notification "Model not found — open Settings to configure"
- Q: Should the app gracefully fall back to CPU if OpenCL is unavailable, or detect and report it? → A: Detect at runtime — if OpenCL init fails, surface a descriptive message in `ModelLoadState.Error`; no automatic CPU retry
- Q: What should the default context size be for Gemma 4 E4B IT (model supports up to 8192 tokens)? → A: 8192 — maximum context; Y700 Gen 3 has 16 GB RAM so ~1.6 GB KV-cache overhead is acceptable

## Assumptions

- Model GGUF (`gemma-4-E4B-it-Q4_K_M.gguf`, ~5 GB) is delivered out-of-band via `adb push` for this personal sideloaded APK
- Target device is Lenovo Legion Y700 Gen 3 (Snapdragon 8 Gen 3, 16 GB LPDDR5X, Adreno 750)
- llama.cpp is built from source, pinned to a specific git tag (latest `b5xxx` tag at build time; document exact tag + SHA256 in `quickstart.md`) with `-DGGML_OPENCL=ON -DGGML_OPENCL_USE_ADRENO_KERNELS=ON`
- `liballama.so` and `libggml*.so` are placed in `app/src/main/jniLibs/arm64-v8a/` manually and are NOT checked into git
- `TRIM_MEMORY_RUNNING_CRITICAL` is the only threshold that triggers model unload (lower levels are ignored)
- The `InferenceEvent.Complete.provider` field is set to `CloudProvider.GEMINI` as a sentinel for on-device completions (no real provider concept applies)
- OpenCL is available on all target devices (Y700 Gen 3 ships with Adreno 750 which has full OpenCL support)
- Audio bytes never reach the JNI layer — only pre-transcribed question text is passed (Constitution Principle I)
