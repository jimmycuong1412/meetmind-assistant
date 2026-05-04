# Tasks: Thermal Protection — End-of-Session Insight Fallback

**Input**: Design documents from `/specs/011-thermal-protection/`  
**Branch**: `feature/011-thermal-protection`  
**Prerequisites**: plan.md ✅ spec.md ✅

**Organization**: Tasks grouped by user story — each story is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with other [P] tasks in the same phase (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to
- No test tasks generated (not requested in spec)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New data class and `SyncSttLlmUseCase` constructor extension — must land before any story logic is added.

- [ ] T001 Create `ThermalGateState` data class (`isActive: Boolean = false`, `coolReadings: Int = 0`) in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/ThermalGateState.kt`
- [ ] T002 Add `val thermalMode: Boolean = false` field to `MainUiState` in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt`
- [ ] T003 Add string resource `thermal_mode_badge` = `"Thermal mode — summary at session end"` to `app/src/main/res/values/strings.xml`

**Checkpoint**: New data class compiles; `MainUiState` has `thermalMode`; string resource exists.

---

## Phase 2: Foundational — Thermal Gate in SyncSttLlmUseCase (Blocking)

**Purpose**: Core gate and buffer logic that US1 and US2 both depend on.

- [ ] T004 Add `val isHot = AtomicBoolean(false)` and `private val thermalBuffer = mutableListOf<TranscriptionSegmentWithSession>()` and `private var coolReadingCount = 0` to `SyncSttLlmUseCase` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`; clear `thermalBuffer` and reset `coolReadingCount` in the session-start path (alongside `newSegmentsSinceLastLlm.clear()`)
- [ ] T005 Add `fun setThermalStatus(status: Int)` method to `SyncSttLlmUseCase`: if `status >= 2` (MODERATE), set `isHot.set(true)` and reset `coolReadingCount = 0`; if `status < 2`, increment `coolReadingCount` and if `coolReadingCount >= 2` set `isHot.set(false)` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [ ] T006 In `SyncSttLlmUseCase.transcriptionFlow.collect`, at each completed segment: if `isHot.get()`, append segment to `thermalBuffer` and skip inference (both reactive trigger and 30-second interval); if not hot, proceed normally in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`

**Checkpoint**: Gate compiles; `setThermalStatus` toggles `isHot`; hot segments go to buffer instead of inference.

---

## Phase 3: User Story 1 — Thermal Gate Suppresses Live Inference (Priority: P1)

**Goal**: Zero LLM inference calls during a hot session; all segments buffered.

**Independent Test**: Call `setThermalStatus(2)` → feed segments → assert no LLM call fired and `thermalBuffer` is non-empty.

- [ ] T007 [US1] Register `OnThermalStatusChangedListener` in `MainViewModel.init` (API 29+ guard): get `PowerManager` via `getApplication<Application>().getSystemService(PowerManager::class.java)`; call `syncSttLlmUseCase.setThermalStatus(pm.currentThermalStatus)` for initial state; in listener callback, call `setThermalStatus(status)` and update `_uiState.update { it.copy(thermalMode = syncSttLlmUseCase.isHot.get()) }` in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T008 [US1] Unregister the listener in `MainViewModel.onCleared()` (API 29+ guard) in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T009 [US1] On `startStreaming()`, reset `thermalMode` to current `isHot` value and clear any stale thermal buffer state in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T010 [US1] Smoke-test: enable thermal simulation on device (or use `adb shell cmd power set-thermal-status 2`) → start Interview recording → speak a question → verify no card appears; confirm `thermalMode = true` in debug UI

**Checkpoint**: US1 functional. Live inference suppressed when hot; badge shows `thermalMode`.

---

## Phase 4: User Story 2 — End-of-Session Flush (Priority: P1)

**Goal**: Single consolidated LLM call on stop when buffer is non-empty; insight stored with "Session Summary" title.

**Independent Test**: Set hot → buffer 3 segments → call stopStreaming() → assert exactly 1 LLM call fired with concatenated text → assert emitted insight has title "Session Summary".

- [ ] T011 [US2] Add `suspend fun flushThermalBuffer(role: String): LlmInsight?` to `SyncSttLlmUseCase`: concatenate `thermalBuffer` texts with `"\n"`, call `InterviewPromptBuilder.build(role, text)`, run inference, call `InterviewOutputParser.parse()` then `toLlmInsight()`; override `title` to `"Session Summary"`; clear `thermalBuffer`; return null if buffer empty; catch all exceptions and return null (log error) in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [ ] T012 [US2] In `MainViewModel.stopStreaming()`, after cancelling the card ticker, call `syncSttLlmUseCase.flushThermalBuffer(currentRole)` in a `viewModelScope.launch`; if result is non-null, emit insight via the existing insights `StateFlow` update path; if result is null and buffer was non-empty (i.e., flush was attempted), show snackbar "Could not generate session summary" in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T013 [US2] Reset `_uiState.thermalMode` to `false` after flush completes (or on normal stop with no buffer) in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T014 [US2] Smoke-test: thermal mode active → record session → stop → verify exactly one "Session Summary" card appears within 10 seconds; verify card is stored in Room (visible after app restart)

**Checkpoint**: US2 functional. Flush fires on stop; "Session Summary" card appears and persists.

---

## Phase 5: User Story 3 — Thermal Indicator Badge (Priority: P2)

**Goal**: Amber badge visible in InsightsSection when `thermalMode && isRecording`; fades when deactivated.

**Independent Test**: Set `thermalMode = true` in UI state preview → assert badge is visible with correct copy; set `false` → badge not visible.

- [ ] T015 [US3] In `InsightsSection`, add `thermalMode: Boolean = false` parameter; add `AnimatedVisibility(visible = thermalMode && isRecording)` wrapping an amber `Surface` badge with text `stringResource(R.string.thermal_mode_badge)` and a warning icon; position at `Alignment.TopStart` (opposite side from filler badge at `TopEnd`) in `app/src/main/java/com/meetmind/assistant/ui/screens/InsightsSection.kt`
- [ ] T016 [US3] Thread `thermalMode = uiState.thermalMode` from `MainScreen.kt` into the `InsightsSection` call in `app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt`
- [ ] T017 [US3] Smoke-test: simulate thermal mode → verify amber badge appears on Insights tab; drop thermal → verify badge fades out

**Checkpoint**: US3 functional. Thermal badge visible and animated.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Verify API < 29 guard: confirm `setThermalStatus` is only called inside `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` blocks; gate never activates on emulators without thermal HAL
- [ ] T019 [P] Verify edge case: LLM busy when thermal activates — confirm in-flight inference is not cancelled; only subsequent triggers are suppressed (gate check is at trigger time, not mid-inference)
- [ ] T020 [P] Verify edge case: very short session (< 5s) with 1 buffered segment → flush still runs without crash
- [ ] T021 [P] Run `./gradlew :domain:testDebugUnitTest` to confirm no regressions in existing `InterviewOutputParserTest` (24 tests)
- [ ] T022 [P] Run `./gradlew :app:assembleDebug` to confirm clean compile

---

## Dependencies & Execution Order

```
Phase 1 (Setup: ThermalGateState, MainUiState field, strings)
    │
    └──► Phase 2 (Foundational: gate + buffer in SyncSttLlmUseCase)
              │
              ├──► Phase 3 (US1 — ViewModel listener + gate wiring)
              │
              ├──► Phase 4 (US2 — Flush on stop)         ← depends on Phase 2 buffer
              │
              └──► Phase 5 (US3 — Badge)                 ← depends on Phase 1 thermalMode field
                        │
                        └──► Phase 6 (Polish)
```

### Parallel Opportunities

- T001, T002, T003 (Phase 1) can all run in parallel — different files.
- T004, T005, T006 (Phase 2) are sequential — same file, each builds on the previous.
- Phase 3 (US1) and Phase 5 (US3 badge) can start as soon as Phase 1 + Phase 2 complete — different files.
- Phase 4 (US2 flush) depends on Phase 2 buffer being in place.
- Phase 6 tasks are all parallel.

---

## Implementation Strategy

### MVP First (US1 + US2 — ship after T014)

1. Phase 1 setup (T001–T003)
2. Phase 2 gate + buffer (T004–T006)
3. Phase 3 ViewModel wiring (T007–T009)
4. Phase 4 flush (T011–T013)
5. **STOP AND VALIDATE**: thermal gate suppresses inference; flush produces "Session Summary" card
6. Then add badge (Phase 5) as visual polish

| After | Value delivered |
|-------|----------------|
| Phase 3 (US1) | Thermal protection active — no inference when hot |
| Phase 4 (US2) | End-of-session summary preserves coaching value |
| Phase 5 (US3) | User knows why cards aren't appearing live |
