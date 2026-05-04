# Tasks: Interview Coach Enhancements

**Input**: Design documents from `/specs/010-interview-coach-enhancements/`  
**Branch**: `feature/010-interview-coach-enhancements`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ quickstart.md ✅

**Organization**: Tasks grouped by user story — each story is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with other [P] tasks in the same phase (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to
- No test tasks generated (not requested in spec)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Room migration, new data class files, and DB version bump — must land before any story touches persistence.

- [X] T001 Increment `@Database(version = 10)` in `data/src/main/java/com/meetmind/assistant/data/database/AppDatabase.kt` and add `MIGRATION_9_10` that executes `ALTER TABLE llm_insights ADD COLUMN question_type TEXT`; register migration in the `addMigrations()` call
- [X] T002 Add `val questionType: String? = null` field with `@ColumnInfo(name = "question_type")` to `LlmInsightEntity` in `data/src/main/java/com/meetmind/assistant/data/database/entity/LlmInsightEntity.kt`
- [X] T003 Add `val questionType: String? = null` field to the `LlmInsight` domain model in `domain/src/main/java/com/meetmind/assistant/domain/model/LlmInsight.kt`
- [X] T004 [P] Create `FillerWordStats` data class (`totalCount: Int = 0`, `ratePerMinute: Float = 0f`) in `domain/src/main/java/com/meetmind/assistant/domain/model/FillerWordStats.kt`
- [X] T005 [P] Create `CardTimerEntry` data class (`elapsedMs: Long = 0L`, `nudge: String? = null`) in `presentation/src/main/java/com/meetmind/assistant/presentation/main/CardTimerEntry.kt`
- [ ] T006 Verify the app compiles and installs cleanly after migration (run `./gradlew :app:assembleDebug`; install on device; confirm no crash on launch — Room auto-migrates)

**Checkpoint**: DB schema at v10, new domain models exist, app launches without crash.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core logic changes that multiple stories depend on — `InterviewOutputParser` extension and `InterviewPromptBuilder` speaker-label overload. Must be complete before stories 1, 2, and 5 can progress.

- [X] T007 In `InterviewInsight` (`domain/src/main/java/com/meetmind/assistant/domain/model/InterviewInsight.kt`), add `val questionType: String? = null` field to the `InterviewInsight` data class
- [X] T008 In `InterviewOutputParser.parse()`, add extraction of the `question_type` JSON field after the existing `coaching_tips` extraction; coerce any value not in `{"behavioural", "technical", "situational"}` to `null`; pass `questionType` into the returned `InterviewInsight`
- [X] T009 Update `toLlmInsight()` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/InterviewOutputParser.kt` to map `InterviewInsight.questionType` → `LlmInsight.questionType`
- [X] T010 Update the mapper from `LlmInsight` → `LlmInsightEntity` (and back) in `data/src/main/java/com/meetmind/assistant/data/database/mapper/TranscriptionMappers.kt` to include `questionType` ↔ `question_type` round-trip
- [X] T011 Add `buildWithSpeakerLabels(role: String, segments: List<TranscriptionSegment>, speakerMapping: MutableMap<Int, String>): String` overload to `InterviewPromptBuilder` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/InterviewPromptBuilder.kt`; when all segments have `speakerCluster == null`, delegate to existing `build()`; otherwise prefix each segment text with `[Interviewer]:` or `[You]:` (first-observed cluster = Interviewer) and join, then `takeLast(MAX_TRANSCRIPT_CHARS)`

**Checkpoint**: Parser extracts `question_type`, mapper round-trips it to/from DB, `buildWithSpeakerLabels` compiles.

---

## Phase 3: User Story 1 — Question-Reactive Instant Trigger (Priority: P1) 🎯 MVP

**Goal**: Coaching card appears within 3 seconds of a `?`-terminated or question-opener segment — independent of the 30-second interval.

**Independent Test**: Start Interview recording → say "Tell me about a challenge you've faced" → coaching card must appear within 3 seconds. See `quickstart.md` Story 1 smoke test.

- [X] T012 [US1] Add `private const val MIN_REACTIVE_DEBOUNCE_MS = 10_000L` and `private fun isLikelyQuestion(text: String): Boolean` (checks `endsWith("?")` and a list of opener phrases) to `SyncSttLlmUseCase` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [X] T013 [US1] In `SyncSttLlmUseCase`, inside `transcriptionFlow.collect`, after completed segment tracking, add reactive trigger guard; 30-second interval path unchanged in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [ ] T014 [US1] Smoke-test: install APK → Interview mode → speak a question ending in `?` → verify card appears < 3 s; speak a non-question statement → verify no card fires early

**Checkpoint**: US1 fully functional. Coaching card reactive to questions. Interval fallback still works.

---

## Phase 4: User Story 2 — Speaker-Aware Question Detection (Priority: P2)

**Goal**: Speaker labels `[Interviewer]` / `[You]` in LLM prompt; false positives from candidate's own questions eliminated.

**Independent Test**: Two-voice session (or playback) → only the interviewer's question generates a card. See `quickstart.md` Story 2 smoke test.

- [X] T015 [US2] Add `private val speakerMapping = mutableMapOf<Int, String>()` to `SyncSttLlmUseCase`; clear it in the session-start path (wherever `newSegmentsSinceLastLlm` and `lastInferenceTimestamp` are reset) in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [X] T016 [US2] In `SyncSttLlmUseCase`, when building the prompt for Interview mode, check if any segment in `newSegmentsSinceLastLlm` has a non-null `speakerCluster`; if yes, populate `speakerMapping` (first unseen cluster → `"[Interviewer]"`, subsequent unseen clusters → `"[You]"`) and call `InterviewPromptBuilder.buildWithSpeakerLabels(role, segments)`; otherwise call existing `InterviewPromptBuilder.build(role, text)` in `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- [X] T017 [US2] Update the `prompt_interview` string in `app/src/main/res/values/strings.xml` to add one sentence after the existing instructions: `"Speaker labels [Interviewer] and [You] prefix each turn when available. Only flag questions from [Interviewer] turns."`
- [ ] T018 [US2] Smoke-test: two-voice session → confirm only interviewer questions produce cards; single-voice session → confirm no crash (falls back to unlabelled prompt)

**Checkpoint**: US2 functional. Speaker-labelled prompts work; unlabelled fallback intact.

---

## Phase 5: User Story 3 — Filler Word Counter Badge (Priority: P3)

**Goal**: Real-time filler word badge on Insights tab; colour-coded at 3/min (amber) and 6/min (red).

**Independent Test**: Say filler-heavy sentences → badge appears and updates within 1 second of segment completion. See `quickstart.md` Story 3 smoke test.

- [X] T019 [US3] Create `FillerWordCounter` object in `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/FillerWordCounter.kt` with `fun count(text: String): Int`; tokenise lowercased, de-punctuated text; match single-word fillers (`um`, `uh`, `like`, `basically`, `literally`, `so`) and multi-word fillers (`you know`) via substring scan before tokenisation
- [X] T020 [US3] Add `val fillerWordStats: FillerWordStats = FillerWordStats()` to `MainUiState` in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt`
- [X] T021 [US3] In `MainViewModel`, on each completed segment update: if `isRecording && recordingMode == INTERVIEW`, call `FillerWordCounter.count(segment.text)` and update `_uiState` with accumulated `totalCount` and recomputed `ratePerMinute`; reset `fillerWordStats` when a new session starts
- [X] T022 [US3] In `InsightsSection`, add `fillerWordStats: FillerWordStats` parameter; add `AnimatedVisibility`-wrapped filler badge at `Alignment.TopEnd`; visible only when `isRecording && isInterviewMode`
- [X] T023 [US3] Thread `fillerWordStats` and `cardTimers` from `MainScreen.kt` into the `InsightsSection` call
- [ ] T024 [US3] Smoke-test: record filler-heavy speech → badge appears and colour changes at correct thresholds; non-interview mode → badge not shown

**Checkpoint**: US3 functional. Filler badge visible and colour-coded in Interview mode only.

---

## Phase 6: User Story 4 — Answer Duration Timer (Priority: P4)

**Goal**: Per-card elapsed timer with amber (≥90s) / red (≥120s) border; "Consider elaborating" nudge for answers < 30s.

**Independent Test**: Trigger a question card → speak > 90s → border turns amber → speak > 120s → border turns red. See `quickstart.md` Story 4 smoke test.

- [X] T025 [US4] Add `val cardTimers: Map<String, CardTimerEntry> = emptyMap()` to `MainUiState` in `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt`
- [X] T026 [US4] In `MainViewModel`, when a new `LlmInsight` is added while `isRecording && recordingMode == INTERVIEW`, insert `CardTimerEntry()` into `cardTimers` keyed by `insight.id`; launch 1-second ticker coroutine
- [X] T027 [US4] In `MainViewModel`, when recording stops, cancel ticker and set `nudge = "Consider elaborating"` for entries where `elapsedMs < 30_000L`
- [X] T028 [US4] Add `timerEntry: CardTimerEntry?` to `InterviewInsightItem`; render elapsed `mm:ss` text; apply border: amber at ≥ 90s, error-red at ≥ 120s
- [X] T029 [US4] Render `"Consider elaborating"` chip when `timerEntry?.nudge != null`
- [X] T030 [US4] Thread `cardTimers` from `MainScreen.kt` → `InsightsSection` → `InsightItem` → `InterviewInsightItem`
- [ ] T031 [US4] Smoke-test: question card appears → timer ticks → amber at 90s → red at 120s → "Consider elaborating" chip after short answer

**Checkpoint**: US4 functional. Per-card timers tick live; colour thresholds and nudge chip work correctly.

---

## Phase 7: User Story 5 — STAR-Structured Answer Cards (Priority: P5)

**Goal**: Behavioural questions generate STAR-structured cards with four labelled sections (Situation / Task / Action / Result).

**Independent Test**: Say "Tell me about a time you handled a conflict" → coaching card shows four labelled STAR blocks. See `quickstart.md` Story 5 smoke test.

- [X] T032 [US5] Update `prompt_interview` in `app/src/main/res/values/strings.xml` to add `question_type` schema and STAR formatting instruction
- [X] T033 [US5] Client-side behavioural keyword pre-filter in `InterviewOutputParser` — upgrades `questionType` to `"behavioural"` when detected question matches R-06 openers and LLM missed the classification
- [X] T034 [US5] Create `StarAnswerSection` composable in `InsightsSection.kt`; splits on `|||`, renders 4 labelled blocks with left accent bar; graceful degradation to plain text for < 4 parts
- [X] T035 [US5] In `InterviewInsightItem`, conditionally render `StarAnswerSection` for behavioural + `|||` content, else plain `FormattedInsightText`
- [ ] T036 [US5] Smoke-test: behavioural question → STAR card with 4 labelled blocks; technical question → standard bullet card; LLM returns non-STAR answer for behavioural → plain text fallback, no crash

**Checkpoint**: US5 functional. STAR cards render for behavioural questions; all other question types unaffected.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Edge cases, graceful degradation verification, and quickstart validation.

- [ ] T037 [P] Verify all 5 edge cases from `spec.md` manually: (1) single-speaker diarization fallback; (2) reactive trigger queue when LLM busy; (3) filler word at segment boundary; (4) silence during answer timer; (5) 3-word question with STAR → skip STAR format
- [X] T038 [P] Confirmed `isLikelyQuestion()` only fires on `segment.isComplete == true` — guard already present in `SyncSttLlmUseCase`
- [X] T039 [P] Added 10 behavioural question JSON fixtures (BQ-01 through BQ-10) to `InterviewOutputParserTest.kt`; assert `questionType == "behavioural"` and `content.contains("|||")` (SC-004)
- [X] T039b Ran all 24 `InterviewOutputParserTest` cases — BUILD SUCCESSFUL, no regressions
- [ ] T040 Run `quickstart.md` full smoke-test sequence (all 5 stories) on Lenovo Legion Y700 Gen 3
- [X] T041 [P] Deleted stale `specs/006-mock-apk-testing/plan.md` (blank template)

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup / Migration)
    │
    └──► Phase 2 (Foundational: parser + prompt builder)
              │
              ├──► Phase 3 (US1 — Reactive Trigger)     ← MVP, ship here
              │         │
              │         └──► Phase 4 (US2 — Speaker Labels)  ← depends on US1 trigger path
              │
              ├──► Phase 5 (US3 — Filler Badge)          ← independent of US1/US2
              │
              ├──► Phase 6 (US4 — Answer Timer)          ← independent of US1/US2/US3
              │
              └──► Phase 7 (US5 — STAR Cards)            ← depends on Phase 2 parser work
                        │
                        └──► Phase 8 (Polish)
```

### User Story Dependencies

| Story | Depends on | Can start after |
|-------|-----------|-----------------|
| US1 (Reactive Trigger) | Phase 2 complete | T011 |
| US2 (Speaker Labels) | Phase 2 + US1 trigger path (T013) | T014 |
| US3 (Filler Badge) | Phase 1 (FillerWordStats) | T006 |
| US4 (Answer Timer) | Phase 1 (CardTimerEntry) | T006 |
| US5 (STAR Cards) | Phase 2 (parser T008) + Phase 1 DB (T001–T003) | T011 |

### Parallel Opportunities

**Within Phase 1**: T004 and T005 can run in parallel (different new files).  
**Within Phase 2**: T007–T010 (parser/mapper) and T011 (prompt builder) can run in parallel — different files.  
**After Phase 2**: US3 (Filler, T019–T024) and US4 (Timer, T025–T031) are fully independent of US1/US2 and can be worked simultaneously.

---

## Parallel Example: After Phase 2 Complete

```
Stream A (US1 + US2):  T012 → T013 → T014 → T015 → T016 → T017 → T018
Stream B (US3):        T019 → T020 → T021 → T022 → T023 → T024
Stream C (US4):        T025 → T026 → T027 → T028 → T029 → T030 → T031
Stream D (US5):        T032 → T033 → T034 → T035 → T036
```

All four streams can run in parallel after Phase 2; merge into Phase 8 (Polish).

---

## Implementation Strategy

### MVP First (US1 only — ship after T014)

1. Complete Phase 1 (T001–T006)
2. Complete Phase 2 (T007–T011)
3. Complete Phase 3 (T012–T014)
4. **STOP and VALIDATE**: Coaching card fires reactively on questions < 3s
5. Sideload APK and demo — already materially better than the 30-second baseline

### Incremental Delivery

| After | Value delivered |
|-------|----------------|
| Phase 3 (US1) | Reactive coaching — biggest UX improvement |
| Phase 4 (US2) | Eliminates false positives (requires diarization active) |
| Phase 5 (US3) | Real-time filler coaching with zero inference cost |
| Phase 6 (US4) | Time-management feedback per answer |
| Phase 7 (US5) | Structured STAR coaching for behavioural questions |

---

## Notes

- Tasks T001–T006 (migration + new data classes) MUST land before any story starts — Room schema must match entity definitions at compile time
- Reactive trigger (T013) inserts into the segment collector at ~line 202 of `SyncSttLlmUseCase.kt` — read the file before editing to confirm exact line
- Speaker mapping (T015–T016) resets per-session — verify the reset is co-located with wherever `newSegmentsSinceLastLlm.clear()` is called
- The `prompt_interview` string is updated twice (T017 in US2, T032 in US5) — do these in order to avoid conflicts
- `InsightsSection` receives new parameters in US3, US4 — update the composable signature and all call-sites together (compiler will catch missing args)
- Commit after each phase checkpoint to keep the branch bisectable
