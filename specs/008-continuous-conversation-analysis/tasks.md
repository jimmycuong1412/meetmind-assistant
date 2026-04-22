# Tasks: Continuous Conversation Analysis

**Feature Branch**: `feature/008-continuous-conversation-analysis`
**Input**: Design documents from `/specs/008-continuous-conversation-analysis/`
**Prerequisites**: spec.md ✅ plan.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅
**Last Updated**: 2026-04-22 (clarification pass — FR-002 priority order, FR-005 QUESTION routing, FR-014 heuristic NoSignal)

**Tests**: Contract tests ARE included — explicitly required by spec User Story 4 (P1).

**Organization**: Tasks grouped by user story for independent implementation and testing.

### Clarification Notes (2026-04-22)

The following were formally clarified and are already implemented correctly in the codebase:

- **FR-002 tie-breaking**: `DECISION > ACTION_ITEM > CONFUSION > QUESTION` (lower ordinal = higher priority in `EventType` enum) — `AnalysisEvent.kt` uses this ordering; `data-model.md` updated to match
- **FR-005 QUESTION routing**: `EventType.QUESTION` result routes to `suggestionEvents` StateFlow ONLY; `analysisEvent` is NOT updated — `DefaultConversationAnalyzer.kt` must verify this is implemented
- **FR-014 heuristic NoSignal**: When keyword heuristic finds no match (no-model + cloud-disabled conditions), return `AnalysisEvent.NoSignal` — no card shown; `analysisEvent` stays `null`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create all new packages and shared model types used by every user story.

- [X] T001 Create package `analysis/` under `app/src/main/kotlin/com/meetmind/assistant/analysis/`
- [X] T002 [P] Create `AnalysisEvent` sealed class and `EventType` enum in `app/src/main/kotlin/com/meetmind/assistant/analysis/AnalysisEvent.kt`
- [X] T003 [P] Create `AnalysisSettings` data class with defaults in `app/src/main/kotlin/com/meetmind/assistant/data/model/AnalysisSettings.kt`
- [X] T004 [P] Create test helpers package `app/src/test/kotlin/com/meetmind/assistant/analysis/` and `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeConversationAnalyzer.kt`

**Checkpoint**: Shared types compile; `FakeConversationAnalyzer` is injectable

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure — `TranscriptWindowBuffer`, `KeywordHeuristicClassifier`, `ConversationAnalyzer` interface and `FakeInferenceEngine` — that all user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T005 Implement `TimestampedSegment` data class and `TranscriptWindowBuffer` (ArrayDeque, eager trim, `windowText(windowSizeMs)`, 600-char tail truncation) in `app/src/main/kotlin/com/meetmind/assistant/analysis/TranscriptWindowBuffer.kt`
- [X] T006 [P] Write contract tests C5.1–C5.2 for `TranscriptWindowBuffer` in `app/src/test/kotlin/com/meetmind/assistant/analysis/TranscriptWindowBufferTest.kt`
- [X] T007 Implement `KeywordHeuristicClassifier` with three compiled `Regex` objects (ActionItem, Decision, Confusion) and `classify(text): EventType?` in `app/src/main/kotlin/com/meetmind/assistant/analysis/KeywordHeuristicClassifier.kt`
- [X] T008 [P] Define `ConversationAnalyzer` interface (`analyze(windowText: String): Flow<AnalysisEvent>`) in `app/src/main/kotlin/com/meetmind/assistant/analysis/ConversationAnalyzer.kt`
- [X] T009 [P] Create `FakeInferenceEngine` test helper (stub token stream, `callCount: AtomicInteger`) in `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeInferenceEngine.kt`
- [X] T010 Implement `DuplicateSuppressor` (64-char normalised prefix, `LinkedHashMap` with 60s TTL eviction, `isSuppressed(text): Boolean` + `record(text)`) in `app/src/main/kotlin/com/meetmind/assistant/analysis/DuplicateSuppressor.kt`
- [X] T011 [P] Write contract tests C4.1–C4.2 for `DuplicateSuppressor` in `app/src/test/kotlin/com/meetmind/assistant/analysis/DuplicateSuppressorTest.kt`

**Checkpoint**: Foundation compiles; C5 and C4 contract tests pass; `./gradlew testDebugUnitTest --tests "*.TranscriptWindowBufferTest" --tests "*.DuplicateSuppressorTest"` green

---

## Phase 3: User Story 1 — Sliding-Window Proactive Analysis (Priority: P1) 🎯 MVP

**Goal**: `AnalysisCadenceController` fires every N seconds, reads the transcript window, calls `ConversationAnalyzer`, routes the result to `SessionViewModel.analysisEvents` StateFlow, and renders a basic card in `SessionScreen`.

**Independent Test**: Start a session, speak any phrase; within the configured interval an analysis card appears (even if it shows only "⬜ Analyzing…" on NoSignal). `./gradlew testDebugUnitTest --tests "*.AnalysisCadenceControllerTest"` passes.

### Contract Tests — US1

- [X] T012 [P] [US1] Write contract tests C2.1–C2.4 for `AnalysisCadenceController` (tick count, mutex skip, stop(), min-cadence clamp) using `StandardTestDispatcher` + `advanceTimeBy` in `app/src/test/kotlin/com/meetmind/assistant/analysis/AnalysisCadenceControllerTest.kt`

### Implementation — US1

- [X] T013 [US1] Implement `AnalysisCadenceController` (`delay()` loop, `Mutex.tryLock()` debounce, 10s min-cadence clamp, injectable `CoroutineDispatcher`, `start(scope)` / `stop()`) in `app/src/main/kotlin/com/meetmind/assistant/analysis/AnalysisCadenceController.kt`
- [X] T014 [US1] Add `analysisEvents: StateFlow<AnalysisEvent?>` to `SessionViewModel` and wire cadence start/stop to session lifecycle (`onSessionStart` / `onCleared`) in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/SessionViewModel.kt`
- [X] T015 [US1] Write contract tests C3.1–C3.3 for `SessionViewModel` analysis integration (`FakeConversationAnalyzer`, question-priority suppression, `analysisEnabled=false` zero-call) in `app/src/test/kotlin/com/meetmind/assistant/viewmodel/SessionViewModelAnalysisTest.kt`
- [X] T016 [US1] Add analysis suggestion card composable (type label + dismiss button, `AnalysisEvent` → label mapping: "📋 Action Item" / "✅ Decision" / "❓ Unclear" / "💬 Question") to `app/src/main/kotlin/com/meetmind/assistant/ui/screens/SessionScreen.kt`
- [X] T017 [US1] Implement question-detection priority: if `suggestionEvents` and `analysisEvents` fire simultaneously, question card takes display priority and analysis card is discarded for that tick in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/SessionViewModel.kt`

**Checkpoint**: US1 independently testable. `./gradlew testDebugUnitTest --tests "*.AnalysisCadenceControllerTest" --tests "*.SessionViewModelAnalysisTest"` green.

---

## Phase 4: User Story 2 — Enriched Event Types with Inference (Priority: P1)

**Goal**: `DefaultConversationAnalyzer` classifies window text via keyword heuristic then calls `CloudInferenceEngine` with a type-specific system prompt, generating a real suggestion for ActionItem / Decision / Confusion / Question events.

**Independent Test**: With cloud disabled and Gemma 4 E4B loaded, speak "We decided to go with the React approach". Within the cadence window, a "✅ Decision" card appears with a follow-up suggestion. `./gradlew testDebugUnitTest --tests "*.ConversationAnalyzerTest"` passes all C1.1–C1.6.

### Contract Tests — US2

- [X] T018 [P] [US2] Write contract tests C1.1–C1.6 for `DefaultConversationAnalyzer` (blank → NoSignal, question delegation, action-item suggestion, decision suggestion, confusion suggestion, 600-char truncation) in `app/src/test/kotlin/com/meetmind/assistant/analysis/ConversationAnalyzerTest.kt`

### Implementation — US2

- [X] T019 [US2] Implement `DefaultConversationAnalyzer` (heuristic classify → `CloudInferenceEngine.streamSuggestion()` with event-type system prompt, `DuplicateSuppressor` check, `AnalysisEvent.NoSignal` fast path) in `app/src/main/kotlin/com/meetmind/assistant/analysis/ConversationAnalyzer.kt`
- [X] T020 [US2] Define per-event-type system prompts (≤ 320 chars each) inside `DefaultConversationAnalyzer.buildSystemPrompt(eventType: EventType): String`
- [X] T021 [US2] Wire `DefaultConversationAnalyzer` to `TranscriptWindowBuffer` in `SessionViewModel`: on each cadence tick, call `buffer.windowText(windowSizeMs)` → pass to `analyzer.analyze()` → emit result to `analysisEvents` in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/SessionViewModel.kt`
- [X] T022 [US2] Handle no-model-loaded fallback in `DefaultConversationAnalyzer`: if `OnDeviceLlamaProvider.isLoaded() == false` and cloud disabled, return `AnalysisEvent` with `suggestionText = null` (label-only card) in `app/src/main/kotlin/com/meetmind/assistant/analysis/ConversationAnalyzer.kt`

**Checkpoint**: US2 independently testable; all C1 contract tests pass; on-device generates real Decision/ActionItem/Confusion suggestions.

---

## Phase 5: User Story 3 — Analysis Settings & Controls (Priority: P2)

**Goal**: `AnalysisSettingsRepository` (DataStore, `analysis_` prefix) + `AnalysisSettingsScreen` (enable toggle, interval slider 10–120s, window slider 15–300s). Changing interval restarts the cadence controller. Disabling stops all ticks.

**Independent Test**: Navigate to Settings → Conversation Analysis. Toggle off → session produces zero analysis cards. Change interval to 30s → cadence fires at 30s intervals. `./gradlew testDebugUnitTest --tests "*.AnalysisSettingsRepositoryTest"` passes.

### Contract Tests — US3

- [X] T023 [P] [US3] Write `AnalysisSettingsRepositoryTest` (enabled/disabled read-write, interval clamping 10–120, window clamping 15–300, DataStore key prefix `analysis_`) in `app/src/test/kotlin/com/meetmind/assistant/data/AnalysisSettingsRepositoryTest.kt`

### Implementation — US3

- [X] T024 [US3] Implement `AnalysisSettingsRepository` (DataStore Preferences, keys `analysis_enabled`, `analysis_interval_s`, `analysis_window_s`, clamp on write) in `app/src/main/kotlin/com/meetmind/assistant/data/AnalysisSettingsRepository.kt`
- [X] T025 [US3] Create `AnalysisSettingsViewModel` (observes `AnalysisSettingsRepository`, exposes `settings: StateFlow<AnalysisSettings>`, `setEnabled()`, `setInterval()`, `setWindow()`) in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/AnalysisSettingsViewModel.kt`
- [X] T026 [US3] Implement `AnalysisSettingsScreen` (Compose: enable toggle, two sliders with labels and current value, navigation back) in `app/src/main/kotlin/com/meetmind/assistant/ui/screens/AnalysisSettingsScreen.kt`
- [X] T027 [US3] Add "Conversation Analysis" entry to Settings navigation in `app/src/main/kotlin/com/meetmind/assistant/ui/navigation/AppNavGraph.kt`
- [X] T028 [US3] Wire `AnalysisSettingsRepository` into `SessionViewModel`: observe `analysisEnabled` and restart/stop `AnalysisCadenceController` on change; observe `analysisIntervalS` and `analysisWindowS` for live reconfiguration in `app/src/main/kotlin/com/meetmind/assistant/viewmodel/SessionViewModel.kt`

**Checkpoint**: Settings screen functional; toggling off/on stops/restarts analysis; interval change reflected within one tick.

---

## Phase 6: User Story 4 — Contract Test Coverage (Priority: P1)

**Goal**: All contract tests pass (`./gradlew testDebugUnitTest`) with zero native library dependencies. Tests for US4 are already written inline in Phases 2–5 — this phase wires them all up and adds any missing coverage.

**Independent Test**: `./gradlew testDebugUnitTest` reports all tests in `ConversationAnalyzerTest`, `AnalysisCadenceControllerTest`, `SessionViewModelAnalysisTest`, `TranscriptWindowBufferTest`, `DuplicateSuppressorTest`, `AnalysisSettingsRepositoryTest` as passed.

- [X] T029 [US4] Verify all C1–C5 contract tests pass without native `.so` file; fix any `UnsatisfiedLinkError` issues by ensuring `FakeConversationAnalyzer` is injected in all test paths
- [X] T030 [US4] Add `FakeConversationAnalyzer` to `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeConversationAnalyzer.kt` with configurable event sequence and `analyzeCallCount` counter
- [X] T031 [US4] Confirm 600-char truncation contract (C1.6): add assertion that `FakeInferenceEngine.lastReceivedText.length <= 600` when window text is 800 chars in `ConversationAnalyzerTest`

**Checkpoint**: `./gradlew testDebugUnitTest` BUILD SUCCESSFUL, all new tests green.

---

## Phase 7: Polish & AppContainer Wiring

**Purpose**: Wire all new components into `AppContainer`, add logcat tags, and run the quickstart smoke test.

- [X] T032 Wire `TranscriptWindowBuffer`, `DefaultConversationAnalyzer`, `AnalysisCadenceController`, `AnalysisSettingsRepository` into `app/src/main/kotlin/com/meetmind/assistant/di/AppContainer.kt`
- [X] T033 [P] Add logcat tags (`CadenceController`, `ConversationAnalyzer`) per `quickstart.md` filter spec to `AnalysisCadenceController.kt` and `ConversationAnalyzer.kt`
- [X] T034 [P] Add `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` hook in `MeetMindApplication` to stop `AnalysisCadenceController` (mirrors spec 007 model unload pattern) in `app/src/main/kotlin/com/meetmind/assistant/MeetMindApplication.kt`
- [X] T035 Wire `TranscriptWindowBuffer.append()` into the existing ASR segment callback in `AudioProcessingForegroundService` so live transcript feeds the analysis window in `app/src/main/kotlin/com/meetmind/assistant/service/AudioProcessingForegroundService.kt`
- [ ] T036 Run APK smoke test on Y700 Gen 3 per `quickstart.md`: speak each example phrase (ActionItem, Decision, Confusion), verify card appears within 3s, verify logcat timestamps
- [X] T037 Verify FR-005 QUESTION routing in `DefaultConversationAnalyzer`: confirm `EventType.QUESTION` result is forwarded only to `CloudInferenceEngine.streamSuggestion()` path and `analysisEvent` StateFlow is NOT updated (add assertion to C1.2 test if missing) in `app/src/test/kotlin/com/meetmind/assistant/analysis/ConversationAnalyzerTest.kt`
- [X] T038 Verify FR-014 heuristic NoSignal path in `DefaultConversationAnalyzer`: confirm that when keyword heuristic returns no match AND no model loaded AND cloud disabled, `AnalysisEvent.NoSignal` is emitted (add test case to `ConversationAnalyzerTest.kt` if not already covered)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user story phases**
- **Phase 3 (US1)**: Depends on Phase 2 completion
- **Phase 4 (US2)**: Depends on Phase 3 (needs `AnalysisCadenceController` + `SessionViewModel` wiring)
- **Phase 5 (US3)**: Depends on Phase 2; can run in parallel with Phase 4
- **Phase 6 (US4)**: Depends on Phases 3–5 (verifies all contracts complete)
- **Phase 7 (Polish)**: Depends on Phase 6

### User Story Dependencies

- **US1 (P1)**: After Phase 2 — no other story dependencies
- **US2 (P1)**: After US1 (needs cadence controller + SessionViewModel analysisEvents)
- **US3 (P2)**: After Phase 2 — independent of US1/US2 for settings + repository layer; UI wiring needs US1
- **US4 (P1)**: After US1 + US2 + US3 — validates all contracts

### Parallel Opportunities

- T002, T003, T004 (Phase 1) — run together
- T006, T008, T009, T011 (Phase 2) — run together after T005, T007, T010
- T012 (contract tests) before T013 (implementation) within US1
- T018 (contract tests) before T019 (implementation) within US2
- T023 (contract tests) before T024 (implementation) within US3
- T033, T034 (Phase 7) — run together

---

## Parallel Example: User Story 2

```
# Run simultaneously:
Task T018: Write C1.1–C1.6 contract tests for ConversationAnalyzerTest
Task T019: Implement DefaultConversationAnalyzer (after tests written)
Task T020: Define per-event-type system prompts (parallel with T021)
Task T021: Wire analyzer to TranscriptWindowBuffer in SessionViewModel
```

---

## Implementation Strategy

### MVP (Phases 1–3: cadence fires, NoSignal path, basic card)

1. Complete Phase 1: Setup (T001–T004)
2. Complete Phase 2: Foundational (T005–T011)
3. Complete Phase 3: US1 (T012–T017)
4. **STOP and VALIDATE**: cadence fires, basic card visible, C2 + C3 tests pass
5. Demo: session running, every 20s a card tick visible in logcat

### Full Feature (Phases 4–7)

6. Phase 4 (US2): Real classifier + LLM suggestions
7. Phase 5 (US3): Settings screen
8. Phase 6 (US4): Full contract test sweep
9. Phase 7 (Polish): AppContainer wiring + smoke test

---

## Notes

- `[P]` = different files, no blocking dependency on an incomplete parallel task
- `[US#]` label maps task to the user story for traceability
- Contract tests (C1–C5) MUST be written **before** their corresponding implementation tasks
- `StandardTestDispatcher` is required for cadence timing tests (C2.1); `UnconfinedTestDispatcher` for all others
- Minimum cadence (10s) is enforced in `AnalysisCadenceController`, not in `AnalysisSettingsRepository`
- System prompts per event type must each be ≤ 320 chars (same constitution constraint as spec 007)
- `TranscriptWindowBuffer` feeds from the existing ASR segment callback — do not duplicate the audio pipeline
