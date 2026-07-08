# Concurrent Photo Capture During Vision Analysis — Design

**Date:** 2026-07-08
**Status:** Approved (approach A)

## Problem

While the vision model describes a captured photo (10–30+ s on-device), the camera button
in the recording top bar is disabled (`MainScreen.kt`, `enabled = !uiState.isAnalyzingPhoto`)
and `MainViewModel.onPhotoCaptured` **deletes** any photo that arrives while
`isAnalyzingPhoto` is true. In a fast-moving meeting the user cannot photograph a second
whiteboard/slide until the first analysis finishes.

## Constraint

llama.cpp is not thread-safe. All inference is serialized on a single-thread dispatcher,
and `AnalyzePhotoUseCase` wraps each call in `LlmRepository.beginInference()` /
`endInference()` so the periodic insight loop skips its tick during photo analysis.
Photos therefore cannot be *analyzed* concurrently — they must be *captured* freely and
analyzed strictly one at a time.

## Chosen approach: FIFO analysis queue (approach A)

Capture is never blocked. Every captured photo is persisted to `session_photos`
immediately, then its analysis job is appended to a FIFO queue processed by a single
worker. Rejected alternatives: relying on the repository dispatcher to serialize
concurrent coroutines (corrupts `isGenerating` bookkeeping, no order guarantee), and a
hard cap of one queued capture (arbitrary; same wall on the third photo).

## Components

### 1. `PhotoAnalysisQueue` (new, `:domain`, `usecase/llm/PhotoAnalysisQueue.kt`)

A small serial job queue (sibling in spirit to `PhotoContextQueue`):

- `fun submit(job: suspend () -> Unit)` — appends a job; never blocks.
- `val pending: StateFlow<Int>` — jobs submitted but not yet finished (includes the one
  running). `0` = idle.
- `suspend fun process()` — worker loop; runs jobs strictly in submission order, one at a
  time. A job that throws is logged-and-dropped (does not kill the worker); cancellation
  propagates normally. The owner launches this once in its scope.

Unbounded (`Channel.UNLIMITED`): each entry is a closure; the camera-app round trip
(seconds per photo) naturally bounds the arrival rate.

### 2. `MainViewModel` (changed)

- Owns one `PhotoAnalysisQueue`; launches `process()` in `viewModelScope` from `init`,
  and mirrors `pending` into `MainUiState.pendingPhotoAnalysisCount`.
- `onPhotoCaptured`: the `isAnalyzingPhoto` early-return guard (and its file delete) is
  removed. The photo row is inserted right away in its own coroutine (photos appear in
  Session Details immediately, description pending); the analysis job — which joins the
  insert first, then runs `AnalyzePhotoUseCase`, updates the row description, and queues
  the description for the next insight tick — is submitted to the queue. A failed
  analysis sets the existing error banner and leaves the queue running.

### 3. `MainUiState` (changed)

- New `pendingPhotoAnalysisCount: Int = 0`.
- `isAnalyzingPhoto` becomes a derived property (`pendingPhotoAnalysisCount > 0`) so the
  banner and any other consumers keep working unchanged.

### 4. `MainScreen` (changed)

- Camera button: always enabled while recording + vision-capable (remove the
  `!isAnalyzingPhoto` gate and the dimmed tint).
- Analysis banner: with 1 photo in flight, unchanged ("Analyzing photo…"); with more
  queued, shows `photo_analyzing_queued` — "Analyzing photo… %1$d more in queue".
- `pendingPhotoFile` stays a single slot: the system camera contract means at most one
  capture can be in flight in the camera app itself.

### 5. Strings

`photo_analyzing_queued` added to `values/strings.xml` and `values-vi/strings.xml`
(the camera string group is Vietnamese-localized, unlike the settings LLM group).

## Data flow

1. User taps camera → `TakePicture` launches (button never disabled by analysis).
2. Camera returns → `onPhotoCaptured(path, …)` → insert `SessionPhoto` row (own
   coroutine) → `queue.submit(analysisJob)`.
3. Worker runs jobs FIFO: `AnalyzePhotoUseCase` (serialized against the insight loop via
   `beginInference`) → row description updated → `queuePhotoDescription()` for the next
   insight tick.
4. `pending` drives the banner: `1` → "Analyzing photo…", `n > 1` → "… n−1 more in queue".

## Error handling

- Analysis failure: error banner (existing string), row keeps `description = null`,
  photo remains in Session Details, queue continues with the next photo.
- Recording stops with photos still queued: jobs keep running in `viewModelScope` (same
  lifetime semantics as today's single in-flight analysis); descriptions still persist
  per-photo. Descriptions queued after the last insight tick simply aren't folded into an
  insight — the per-photo persistence means no data loss.

## Testing (TDD, `:domain` + JUnit4 + kotlinx-coroutines-test)

`PhotoAnalysisQueueTest`:
1. Jobs run in submission order, strictly one at a time (second job must not start while
   the first is suspended).
2. `pending` increments on submit and decrements after each job finishes.
3. A throwing job does not stop later jobs, and still decrements `pending`.

ViewModel/UI wiring is covered by the compile gate (`:app:compileDebugKotlin`) + human
on-device check (agent environment cannot run an emulator), per the handoff doc §4/§5.
