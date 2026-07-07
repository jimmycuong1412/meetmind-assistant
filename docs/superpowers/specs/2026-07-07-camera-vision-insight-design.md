# Camera Vision Insight — Design

**Date:** 2026-07-07
**Status:** Approved (pending spec review)

## 1. Summary

Add a camera button next to the existing screen-lock button in the recording top bar. Tapping
it launches the system camera app to take a photo. The photo is analyzed on-device by a
vision-capable LLM immediately after capture; the resulting text description is merged into
the context for the *next* scheduled AI insight, so summaries/action items naturally reference
what was in the photo (e.g. a whiteboard or slide) — the same way they already reference the
live transcript.

This feature is **on-device only**, consistent with the project's zero-network-during-recording
privacy guarantee. No cloud vision API is used.

## 2. Background / constraint that shapes this design

The app's current LLM (Gemma 3 1B GGUF via llama.cpp) is **text-only**. There is no
vision/image-understanding capability anywhere in the codebase today. True on-device image
analysis requires a **multimodal model** (vision encoder + adapter, LLaVA/mtmd-style), which is
a materially bigger model than the existing 1B text model.

Investigation found the vendored llama.cpp source (`F:\Git\llama.cpp-master`, sibling checkout,
commit `bcb5eeb6...`, not a submodule) **already contains `tools/mtmd`** (clip + multimodal
projector support) — it is simply not built. The app's CMake currently passes
`-DLLAMA_BUILD_COMMON=ON` but not `LLAMA_BUILD_TOOLS`, so `tools/mtmd` (`lib-llama-android/src/main/cpp/CMakeLists.txt`)
is never compiled or linked. Enabling multimodal support is therefore **build-wiring +
new JNI functions**, not an upstream source merge.

## 3. Decisions locked in during brainstorming

1. **Vision approach:** swap in a genuinely vision-capable on-device model (not OCR-only, not a
   photo-attachment-with-no-analysis approach).
2. **Model replacement scope:** replace only `DefaultModelConfig` (the auto-recommended
   flagship variant) with a vision-capable Gemma 3 4B multimodal build + its `mmproj` GGUF. All
   five other variants (`LowEndModelConfig`, `Qwen35ModelConfig`, `Gemma3_4B_Q4Config`,
   `Qwen3_4B_Q4Config`, `Phi4MiniQ4Config`) stay text-only and unchanged. The camera button is
   visible only when the active variant supports vision.
3. **Insight integration:** the photo's analysis is **merged into the next AI insight** as extra
   context — not a separate "photo insight" card type. No new `LlmInsight` subtype, no new
   parsing format.
4. **Analysis timing:** vision analysis runs **immediately after capture**, independent of the
   recording mode's periodic insight-generation interval. The resulting description is queued
   and spliced into the *next* interval tick's prompt, then the queue clears.
5. **Multiple photos per session:** supported. Each capture is analyzed and queued
   independently; multiple pending descriptions can accumulate if captured faster than insight
   ticks consume them (concatenated in capture order when spliced into the prompt).
6. **Concurrency:** the camera button is **disabled while busy** — i.e. while a previous photo's
   vision analysis is still running, or while the active model isn't loaded/ready yet. This
   avoids overlapping llama.cpp inferences (the native context is single-threaded/serialized via
   `Dispatchers.Default.limitedParallelism(1)`).

## 4. Architecture

### 4.1 Model & download layer

- **`ModelConfig`** (`data/.../config/ModelConfig.kt`) gains two new nullable fields:
  `mmprojUrl: String? = null`, `mmprojFilename: String? = null`. Only `DefaultModelConfig` sets
  them; every other variant object leaves them `null` (no source changes needed there beyond
  the new fields defaulting to `null`).
- **`DefaultModelConfig`** LLM URL/filename change to a vision-capable Gemma 3 4B GGUF build
  (e.g. `ggml-org/gemma-3-4b-it-GGUF`, a quantization suitable for on-device use), plus the
  matching `mmproj` GGUF from the same release.
- **`ModelDownloadManager`** (`data/.../datasource/ModelDownloadManager.kt`) downloads the
  mmproj file as an additional item in the same download batch/progress tracking whenever
  `mmprojUrl != null` — reuses existing resume/progress/error logic, no new download screen or
  UI state.
- **`LlmModelVariant`** (`domain/.../model/`) gains a capability flag, e.g.
  `supportsVision: Boolean`, `true` only for the variant(s) backed by a multimodal config
  (initially just `Q8_0`/default). This flag is the single source of truth the UI reads to
  decide whether to show the camera button — no separate "is vision model downloaded and
  loaded" check duplicated in the UI layer beyond what's needed for the busy/disabled state.

### 4.2 Native layer (`lib-llama-android`)

- **`CMakeLists.txt`**: enable building `tools/mtmd` (add the necessary flag/subdirectory so
  the vendored mtmd/clip sources compile and link into the native lib alongside the existing
  `ai_chat.cpp`).
- **`ai_chat.cpp`**: add new JNI-exposed functions:
  - `loadMmproj(path: String)` — loads the clip/vision-adapter GGUF via mtmd, called once after
    the base model loads (only for vision-capable variants).
  - `processImagePrompt(imagePath: String, promptText: String)` — encodes the image via
    mtmd/clip, feeds the resulting embeddings + accompanying text prompt through the existing
    generation pipeline, reusing `generateNextToken` for streaming output. Mirrors the
    structure of the existing `processUserPrompt` but takes an image path.
  - Existing text-only functions (`load`, `processUserPrompt`, etc.) are unchanged — vision is
    additive, not a replacement of the text path.

### 4.3 Kotlin JNI binding (`InferenceEngine` / `InferenceEngineImpl`)

- `InferenceEngine` interface gains `loadMmproj(path: String)` and an image-capable overload,
  e.g. `sendUserPrompt(message: String, predictLength: Int, imagePath: String? = null): Flow<String>`.
  When `imagePath` is null, behavior is identical to today (no change to existing callers).

### 4.4 Domain layer

- **`LlmRepository.generateInsight`** gains `imagePath: String? = null`, threaded down to
  `InferenceEngine.sendUserPrompt`'s new parameter. Existing callers omit it and are unaffected.
- **New use case: `AnalyzePhotoUseCase`** — takes a captured photo file path, calls
  `llmRepository.generateInsight(prompt = <fixed "describe this image for meeting notes" prompt>, imagePath = path)`,
  collects the resulting description text, and returns it. Runs through the same
  `beginInference`/`endInference`/`isLlmBusy` locking the periodic insight loop already uses, so
  it cannot overlap with a scheduled text insight generation.
- **`SyncSttLlmUseCase`**: add a pending-photo-description queue (in-memory, per session,
  cleared on splice) alongside the existing transcript `contextBuffer`. `buildUserPrompt` is
  extended to prepend/append any queued photo descriptions (in capture order) to the prompt,
  then the queue is cleared after that prompt is built — so each queued description contributes
  to exactly one insight.

### 4.5 Data layer (persistence)

- New Room entity `SessionPhotoEntity` (one-to-many with `TranscriptionSessionEntity`):
  `id`, `sessionId`, `filePath`, `description` (nullable until analysis completes), `timestamp`.
  Supports multiple photos per session (locked-in decision #5) without touching the existing
  session/segment/insight entities.
- Photo files are written to an app-private subdirectory (e.g.
  `context.filesDir/photos/<sessionId>/`), referenced via the existing `FileProvider`
  (`app/src/main/res/xml/file_paths.xml`) for the camera intent's output URI — this file already
  exists for session export, so it only needs a new `<files-path>` entry for the photos
  subdirectory, not a new provider.

### 4.6 UI layer

- **`AppIcons`**: add `Camera` (Material `PhotoCamera` icon).
- **`MainScreen.kt`** top bar `actions`: new `IconButton` placed immediately before/after the
  existing lock/unlock `IconButton` (`MainScreen.kt:287-298`). Visible when
  `uiState.isRecording && uiState.isVisionCapable`. Disabled (not hidden) while
  `uiState.isAnalyzingPhoto` is true, matching the existing disabled-while-busy visual treatment
  used elsewhere (e.g. the finalizing-session FAB state).
- **Capture flow**: `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())`
  writes to a `FileProvider` URI in the new photos subdirectory. On success,
  `viewModel.onPhotoCaptured(uri)`; on cancel/failure, no-op (button re-enables, no error
  needed for a user-cancelled capture).
- **Camera permission**: requested lazily on first camera-button tap (same pattern as the
  existing mic/notification permission flow in `MainScreen.kt:122-158`), not upfront at launch.
- **Transient feedback**: a small inline banner ("Analyzing photo…") shown while
  `isAnalyzingPhoto` is true, styled like the existing thermal-downgrade banner
  (`MainScreen.kt:496-528`) — dismissible only implicitly (disappears when analysis completes).
- **`SessionDetailsScreen`**: displays captured photo thumbnails + their descriptions for a
  completed session (read from `SessionPhotoEntity`).

## 5. Error handling

| Scenario | Behavior |
|---|---|
| Active variant is not vision-capable | Camera button not rendered at all (state-driven via `isVisionCapable`). |
| Camera permission denied | Standard Android permission-denial UX; button remains visible/enabled for retry, no crash. |
| No camera app on device / user cancels capture | `TakePicture()` result `false`; no-op, button re-enables. |
| Vision analysis throws (OOM, decode failure, native error) | Caught in `AnalyzePhotoUseCase`/`LlmRepository` same as existing `generateInsight` try/catch; description stays null for that photo, transient error banner shown, session continues normally, subsequent text insights unaffected. |
| Camera tapped while previous photo still analyzing, or model not yet loaded | Button disabled — enforced both in UI (disabled state) and at the use-case level via the existing `isLlmBusy`/`beginInference` lock, so no overlapping native inference regardless of UI state. |
| Recording stopped while photo analysis in-flight | Session finalization waits for in-flight inference to complete, reusing the existing `isGenerating`/`isFinalizingSession` mechanism — no new wait logic needed. |

## 6. Testing

- **`:domain` unit tests**: extend the existing `SyncSttLlmUseCase`/prompt-building test
  coverage to verify: a queued photo description is spliced into the next built prompt; the
  queue clears after use; multiple queued descriptions are concatenated in capture order.
- **No new Compose UI test coverage** — consistent with existing project reality (no broad UI
  test suite; `app/src/test`/`androidTest` remain placeholders).
- **Manual on-device verification required for:** camera button visibility gating across model
  variants, camera intent round-trip (capture → FileProvider URI → analysis), vision model
  output quality/latency on a real device, `SessionDetailsScreen` photo display, and confirming
  the `mtmd` native build actually links (`:app:assembleDebug`, not just `compileDebugKotlin`,
  since this changes CMake flags).

## 7. Out of scope

- OCR-only fallback path (rejected during brainstorming in favor of true vision).
- Vision support for the other 5 model variants (only the default variant gets it; documented
  as a possible future follow-up, not part of this design).
- In-app custom camera UI (CameraX) — this design reuses the system camera app via
  `ACTION_IMAGE_CAPTURE`/`TakePicture()`.
- Cloud-based vision analysis of any kind.
