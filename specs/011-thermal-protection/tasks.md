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

- [X] T001 Create `ThermalGateState` data class (`isActive: Boolean = false`, `coolReadings: Int = 0`) in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/ThermalGateState.kt`
- [X] T002 Add `val thermalMode: Boolean = false` field to `MainUiState` in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt`
- [X] T003 Add string resource `thermal_mode_badge` = `"Thermal mode — summary at session end"` to `app/src/main/res/values/strings.xml`

**Checkpoint**: New data class compiles; `MainUiState` has `thermalMode`; string resource exists.

---

## Phase 2: Foundational — Thermal Gate in SyncSttLlmUseCase (Blocking)

**Purpose**: Core gate and buffer logic that US1 and US2 both depend on.

- [X] T004 Add `val isHot = AtomicBoolean(false)` and `private val thermalBuffer` (synchronized list) and `private var coolReadingCount = 0` to `SyncSttLlmUseCase`; expose `setThermalGate(isOverheating: Boolean)` with hysteresis; expose `clearThermalBuffer()` for session-start reset in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [X] T005 Add `suspend fun flushThermalBuffer(role, sessionId, timestamp, systemPrompt, maxTokens): LlmInsight?` to `SyncSttLlmUseCase` — concatenates buffer, runs single LLM call, returns insight with `title = "Session Summary"`, clears buffer in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [X] T006 In `SyncSttLlmUseCase.transcriptionFlow.collect`, at each completed segment: if `isHot.get()`, append segment text to `thermalBuffer` and `return@collect` (skips both reactive trigger and interval); interval trigger also guarded with `if (isHot.get()) return@collect` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`

**Checkpoint**: Gate compiles; `setThermalStatus` toggles `isHot`; hot segments go to buffer instead of inference.

---

## Phase 3: User Story 1 — Thermal Gate Suppresses Live Inference (Priority: P1)

**Goal**: Zero LLM inference calls during a hot session; all segments buffered.

**Independent Test**: Call `setThermalStatus(2)` → feed segments → assert no LLM call fired and `thermalBuffer` is non-empty.

- [X] T007 [US1] In 1-second `timerJob`, poll `thermalMonitor.isOverheating()` each tick; call `syncSttLlmUseCase.setThermalGate(overheating)` and update `_uiState.thermalMode` when gate state changes in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [X] T008 [US1] On `startStreaming()`, call `syncSttLlmUseCase.clearThermalBuffer()` and reset `thermalMode = false` in initial `_uiState.update` in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [X] T009 [US1] `ThermalMonitor` already injected via Hilt; no new listener registration needed — polling via `isOverheating()` in the timer tick is sufficient and simpler in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T010 [US1] Smoke-test: use `adb shell cmd power set-thermal-status 2` → start Interview recording → speak a question → verify no card appears; confirm thermal badge visible on Insights tab

**Checkpoint**: US1 functional. Live inference suppressed when hot; badge shows `thermalMode`.

---

## Phase 4: User Story 2 — End-of-Session Flush (Priority: P1)

**Goal**: Single consolidated LLM call on stop when buffer is non-empty; insight stored with "Session Summary" title.

**Independent Test**: Set hot → buffer 3 segments → call stopStreaming() → assert exactly 1 LLM call fired with concatenated text → assert emitted insight has title "Session Summary".

- [X] T011 [US2] `flushThermalBuffer(role, sessionId, timestamp, systemPrompt, maxTokens): LlmInsight?` implemented in `SyncSttLlmUseCase` — concatenates buffer with `"\n"`, calls `InterviewPromptBuilder.build`, runs inference, overrides title to `"Session Summary"`, clears buffer, returns null on empty/error in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [X] T012 [US2] In `MainViewModel.stopStreaming()`, after `insightsJob?.cancel()`, calls `flushThermalBuffer` guarded by `REAL_TIME + INTERVIEW` check; saves resulting insight via `saveInsightUseCase`; shows error snackbar on null + hot in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [X] T013 [US2] `thermalMode` reset to `false` in flush `launch` after completion in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- [ ] T014 [US2] Smoke-test: `adb shell cmd power set-thermal-status 2` → record session → stop → verify one "Session Summary" card appears within 10 seconds; verify it persists after restart

**Checkpoint**: US2 functional. Flush fires on stop; "Session Summary" card appears and persists.

---

## Phase 5: User Story 3 — Thermal Indicator Badge (Priority: P2)

**Goal**: Amber badge visible in InsightsSection when `thermalMode && isRecording`; fades when deactivated.

**Independent Test**: Set `thermalMode = true` in UI state preview → assert badge is visible with correct copy; set `false` → badge not visible.

- [X] T015 [US3] Added `thermalMode: Boolean = false` param to `InsightsSection`; amber `Surface` badge with `AnimatedVisibility(thermalMode && isRecording)` at `Alignment.TopStart`, opposite the filler badge in `app/src/main/java/com/meetmind/assistant/ui/screens/InsightsSection.kt`
- [X] T016 [US3] Threaded `thermalMode = uiState.thermalMode` from `MainScreen.kt` into `InsightsSection` call in `app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt`
- [ ] T017 [US3] Smoke-test: `adb shell cmd power set-thermal-status 2` during recording → verify amber badge appears on Insights tab; `set-thermal-status 0` → verify badge fades

**Checkpoint**: US3 functional. Thermal badge visible and animated.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T018 [P] API guard: `ThermalMonitor.isOverheating()` implementation handles API < 29 by returning `false`; no change needed in ViewModel — guard is in the ThermalMonitor impl
- [ ] T019 [P] Verify edge case: LLM busy when thermal activates — in-flight inference NOT cancelled (gate check is at trigger time only); manual verification on device
- [ ] T020 [P] Verify edge case: very short session (< 5s) with 1 buffered segment → flush still runs without crash
- [X] T021 [P] `./gradlew :domain:test` — BUILD SUCCESSFUL, all 24 `InterviewOutputParserTest` tests pass, no regressions
- [X] T022 [P] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL in 44s

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
