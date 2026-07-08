# Tasks: English Coach Mode

**Input**: Design documents from `/specs/012-english-coach/`  
**Branch**: `feature/012-english-coach`  
**Prerequisites**: plan.md ✅ spec.md ✅

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `ENGLISH_COACH` to `RecordingMode` enum in `domain/src/main/java/com/meetmind/assistant/domain/model/RecordingMode.kt`
- [X] T002 Create `EnglishCoachInsight` data class (`original`, `corrected`, `isCorrect: Boolean`, `polish: String?`, `coachingTip: String?`, `context: String`) in `domain/src/main/java/com/meetmind/assistant/domain/model/EnglishCoachInsight.kt`
- [X] T003 Add `val englishCoachIntervalSeconds: Int = 5` and `val englishCoachDefaultStrategy: InsightStrategy = InsightStrategy.REAL_TIME` to `AppSettings` in `domain/src/main/java/com/meetmind/assistant/domain/model/AppSettings.kt`
- [X] T004 Add string resources: `mode_english_coach`, `mode_english_coach_desc`, `english_coach_context_daily`, `english_coach_context_professional`, `english_coach_original`, `english_coach_corrected`, `english_coach_polish`, `english_coach_why`, `english_coach_no_errors`, `prompt_english_coach` (system prompt with `{context}` placeholder) in `app/src/main/res/values/strings.xml`

**Checkpoint**: Compiles; new enum value exists; new data class exists.

---

## Phase 2: Foundational — Parser + Prompt

- [X] T005 Create `EnglishCoachOutputParser` object in `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/EnglishCoachOutputParser.kt`:
  - `fun parse(rawOutput: String, context: String): EnglishCoachInsight` — strip fences/think, extract five JSON fields, fall back gracefully on malformed JSON
  - `fun toLlmInsight(insight: EnglishCoachInsight, sessionId: String, timestamp: Long, sourceSegmentIds: List<String>): LlmInsight` — `title = original.take(80)`, `content = corrected`, `tasks = JSON array of non-null [polish, coachingTip]`, `questionType = context`
- [X] T006 Fix all exhaustive `when(mode)` branches to add `RecordingMode.ENGLISH_COACH` in:
  - `SyncSttLlmUseCase.calculateIntervalMs()` → `settings.englishCoachIntervalSeconds * 1000L`
  - `SyncSttLlmUseCase.maxTokensForMode()` → `512`
  - `SyncSttLlmUseCase.buildSystemPrompt()` → load `prompt_english_coach`, replace `{context}` with topic field
  - `GenerateFinalInsightUseCase`, `GenerateBatchInsightUseCase`, `InsightOutputParser.parse()`
  - `SettingsViewModel`, `SettingsRepositoryImpl`, `AndroidResourceProvider`, `ResourceProvider`
  - `NewSessionDialog`, `SearchScreen`, `SessionsScreen`, `SettingsScreen`
- [X] T007 In `SyncSttLlmUseCase`, add per-segment reactive trigger for `ENGLISH_COACH` mode

**Checkpoint**: Parser compiles; `when` branches exhaustive; segment-reactive trigger wired.

---

## Phase 3: User Story 1 + 2 — Grammar Correction + Polish (Priority: P1)

- [X] T008 [US1][US2] In `MainViewModel.startStreaming()`, ensure filler-word accumulation and card-timer blocks are guarded to `RecordingMode.INTERVIEW` only (already the case — verified no `ENGLISH_COACH` bleed)
- [X] T009 [US1][US2] Write `EnglishCoachOutputParserTest` with 14 JUnit fixtures covering: grammar error, no error, code fences, think block, null/blank optional fields, toLlmInsight mapping, fallback — in `domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/EnglishCoachOutputParserTest.kt`
- [X] T010 [US1][US2] Run `./gradlew :domain:test` — 14/14 pass

**Checkpoint**: Unit tests pass; grammar correction + polish parsing verified.

---

## Phase 4: User Story 3 — Context Selector in New Session Dialog (Priority: P2)

- [X] T011 [US3] Add `EnglishCoachContextPicker` composable to `NewSessionDialog.kt` — two chips: "Daily Conversation" and "Professional / Work"; shown only when `selectedMode == RecordingMode.ENGLISH_COACH`; selection written to `topic` field (via `englishCoachContext` state)
- [ ] T012 [US3] Smoke-test: create English Coach session with Daily → confirm `topic = "daily"` in DB; create with Professional → confirm `topic = "professional"`

**Checkpoint**: Context picker visible and persists selection.

---

## Phase 5: User Story 4 — English Coach Card UI (Priority: P2)

- [X] T013 [US4] Add `EnglishCoachInsightItem` composable to `InsightsSection.kt` — teal accent, four sections (Original, Corrected, More Natural, Why), no-errors badge when isCorrect
- [X] T014 [US4] Route to `EnglishCoachInsightItem` in `InsightItem` dispatch when `recordingMode == RecordingMode.ENGLISH_COACH`
- [ ] T015 [US4] Smoke-test: record English Coach session → verify card layout (all four sections), teal accent, correct labels

**Checkpoint**: English Coach card renders correctly with green accent.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T016 [P] Added `ENGLISH_COACH` to `NewSessionDialog` mode cards (auto, via `RecordingMode.values()`), `SearchScreen`, `SessionsScreen` filter chips, teal color tokens in `Color.kt`, `ModeEnglishCoach` icon in `AppIcons.kt`
- [X] T017 [P] Added English Coach accordion section to `SettingsScreen.kt` (interval + prompt + default strategy)
- [X] T018 [P] `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] T019 [P] Manual smoke test: full session on device — Daily context, Professional context, non-English phrase, < 5-word segment (no card), malformed (fallback no crash)

---

## Dependencies

```
Phase 1 (enum + data class + strings)
    │
    └──► Phase 2 (parser + when-branches + trigger)
              │
              ├──► Phase 3 (unit tests)    ← parallel with Phase 4 + 5
              ├──► Phase 4 (context picker UI)
              └──► Phase 5 (card UI)
                        │
                        └──► Phase 6 (polish)
```
