# Feature Specification: Continuous Conversation Analysis

**Feature Branch**: `feature/008-continuous-conversation-analysis`
**Created**: 2026-04-21
**Status**: Draft
**Input**: User direction: "The app should analyze the conversation constantly" — continuous sliding-window analysis AND enriched event types (questions, action items, decisions, confusion signals)
**Constitution version at creation**: 2.0.0

## Clarifications

### Session 2026-04-21

- Q: What is the canonical prefix length for duplicate suppression (FR-011)? → A: 64 chars (implementation and contracts are authoritative; spec updated to match)
- Q: Canonical name for the SessionViewModel analysis StateFlow (FR-008, FR-010)? → A: `analysisEvent` (singular) — StateFlow holds one nullable value; spec updated from `analysisEvents`
- Q: Is SC-007 battery drain a merge gate or post-merge monitoring target? → A: Post-merge monitoring only; not required before branch merge
- Q: Does the TranscriptBuffer assumption accurately reflect the implementation? → A: Updated — `TranscriptWindowBuffer` (spec 008) is the new implementation; spec 004 ASR feeds it via `onTranscriptSegment()` once the VAD/Vosk pipeline is complete

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sliding-Window Proactive Analysis (Priority: P1)

As the developer/owner of MeetMind Assistant, during a meeting session the app continuously
re-analyzes the last N seconds of conversation on a repeating cadence — without waiting for a
detected question — and proactively surfaces a suggestion when the analysis determines one is
warranted.

**Why this priority**: The current pipeline is purely reactive (question-detection → suggestion).
Continuous analysis is the core new capability: it catches conversational moments that are not
phrased as explicit questions but still warrant a response (action items stated aloud, confusion
expressed without a "?" etc.).

**Independent Test**: Start a session and speak a sentence that contains an action item ("Let's
make sure we finish the report by Friday") without any interrogative phrasing. Within the analysis
cadence window, an `AnalysisEvent.ActionItem` is emitted and a suggestion card appears in the
session UI without the user tapping anything.

**Acceptance Scenarios**:

1. **Given** a session is running and `analysisEnabled = true`, **When** `ANALYSIS_INTERVAL_MS` elapses, **Then** `ConversationAnalyzer.analyze()` is called with the transcript window and emits ≥ 1 `AnalysisEvent`
2. **Given** the analysis window contains only silence/filler ("um", "uh", blank), **When** analysis runs, **Then** `AnalysisEvent.NoSignal` is emitted; no suggestion card is shown
3. **Given** analysis returns `AnalysisEvent.ActionItem`, **When** the session screen receives it, **Then** a suggestion card labelled "📋 Action Item" is shown with the extracted item text
4. **Given** analysis returns `AnalysisEvent.Decision`, **When** the session screen receives it, **Then** a suggestion card labelled "✅ Decision" is shown
5. **Given** analysis returns `AnalysisEvent.Confusion`, **When** the session screen receives it, **Then** a suggestion card labelled "❓ Unclear" is shown with a clarification suggestion
6. **Given** analysis is running and a question is simultaneously detected, **When** both signals fire, **Then** the question-detection path takes priority; analysis result is suppressed for that window

---

### User Story 2 - Enriched Event Types with Inference (Priority: P1)

As the developer/owner, the analyzer classifies the current conversation window into one of four
event types — `Question`, `ActionItem`, `Decision`, `Confusion` — using the existing
`CloudInferenceEngine` / `OnDeviceLlamaProvider` stack, and generates a contextually appropriate
suggestion for each type.

**Why this priority**: Equal priority to US1 because the event types define the value of
continuous analysis. A sliding window that only detects questions adds nothing over the existing
pipeline; the enriched event types are the differentiator.

**Independent Test**: With cloud disabled and Gemma 4 E4B loaded, feed a transcript window
containing "We decided to go with the React approach" to `ConversationAnalyzer`. It emits
`AnalysisEvent.Decision(summary = "React approach chosen")` and the suggestion card shows a
follow-up prompt ("Document this decision in your notes").

**Acceptance Scenarios**:

1. **Given** window text contains a clear question, **When** `ConversationAnalyzer.classify()` runs, **Then** `EventType.QUESTION` is returned (delegates to existing QuestionDetector path)
2. **Given** window text contains an action item pattern ("will do", "needs to", "by [date]"), **When** classify runs, **Then** `EventType.ACTION_ITEM` is returned
3. **Given** window text contains a decision pattern ("we decided", "let's go with", "agreed"), **When** classify runs, **Then** `EventType.DECISION` is returned
4. **Given** window text contains confusion signals ("I'm confused", "what does that mean", "not sure I follow"), **When** classify runs, **Then** `EventType.CONFUSION` is returned
5. **Given** `EventType.ACTION_ITEM` is classified, **When** inference runs, **Then** the generated suggestion is an action-item follow-up (e.g., "Who owns this? What's the deadline?") ≤ 150 tokens
6. **Given** cloud times out during analysis inference, **When** 5s timeout fires, **Then** on-device fallback activates; `FallbackReason.TIMEOUT` badge shows; suggestion still streams

---

### User Story 3 - Analysis Settings & Controls (Priority: P2)

As the developer/owner, I can configure the analysis cadence (interval in seconds), the transcript
window size (seconds of history), and enable/disable continuous analysis independently of the
existing question-detection toggle.

**Why this priority**: The cadence directly affects battery drain, inference cost, and UX
density (too many cards = noise). A settings control is required before shipping to prevent
the device from running inference every 5 seconds indefinitely.

**Independent Test**: Navigate to Settings → Conversation Analysis. Set interval to 30s and
window to 60s. Start a session — analysis fires every 30s, the transcript window passed to the
analyzer never exceeds 60s of text. Disabling the toggle stops all analysis cadence timers.

**Acceptance Scenarios**:

1. **Given** analysis is enabled with interval = 15s, **When** a session runs for 45s, **Then** `ConversationAnalyzer.analyze()` is called exactly 3 times
2. **Given** analysis is disabled in settings, **When** a session starts, **Then** no analysis timer is created; existing question-detection continues normally
3. **Given** window size = 30s and session has 90s of transcript, **When** analysis runs, **Then** only the latest 30s of transcript is passed to the analyzer
4. **Given** the session screen is active, **When** the user taps the dismiss button on an analysis suggestion card, **Then** that card is removed and the analysis cadence continues uninterrupted

---

### User Story 4 - Contract Test Coverage (Priority: P1)

As the developer/owner, all continuous analysis contracts are covered by automated JVM tests
(no physical device required) using `FakeConversationAnalyzer` as the test double.

**Why this priority**: The classifier and cadence logic run on every analysis tick — correctness
must be verified before hardware testing.

**Independent Test**: `./gradlew testDebugUnitTest` passes all tests in:
- `ConversationAnalyzerTest` (C1.1–C1.6, classification contracts)
- `AnalysisCadenceControllerTest` (C2.1–C2.4, timer contracts)
- `SessionViewModelAnalysisTest` (C3.1–C3.3, ViewModel integration)

**Acceptance Scenarios**:

1. **Given** `FakeConversationAnalyzer` configured with a fixed `AnalysisEvent`, **When** cadence fires, **Then** the event is routed to `SessionViewModel` and `analysisEvent` StateFlow updated
2. **Given** test uses `TestScope(UnconfinedTestDispatcher())`, **When** analysis interval elapses via `advanceTimeBy`, **Then** cadence fires exactly the expected number of times
3. **Given** question detection fires simultaneously with analysis cadence, **When** both signals arrive, **Then** question-detection event takes precedence in `SessionViewModel`

---

### Edge Cases

- What if analysis fires while the device is under heavy CPU load (e.g., inference still running from the previous tick)? → New tick is skipped if a prior inference is still in-flight (debounce guard in `AnalysisCadenceController`).
- What if the transcript window is empty (silence at start of session)? → `ConversationAnalyzer` returns `AnalysisEvent.NoSignal` immediately; no inference call is made.
- What if a `Decision` and `ActionItem` are both present in the same window? → Classifier returns the higher-priority type (Decision > ActionItem > Confusion > Question); only one `AnalysisEvent` is emitted per tick.
- What if `analysisInterval` is set to a value lower than the average inference latency? → `AnalysisCadenceController` enforces a minimum of 10s regardless of user setting to prevent cascade.
- What if on-device model is not loaded (no GGUF pushed yet)? → Analyzer falls back to a lightweight keyword-matching heuristic for classification; no LLM suggestion is generated; card shows event type label only.
- What if the session ends while an analysis inference is in-flight? → `SessionViewModel.onCleared()` cancels the analysis coroutine scope; no further events are emitted.
- What if the same decision/action item is detected in 3 consecutive windows? → Deduplication: if the leading 64 chars of the extracted text matches a card shown in the last 60s, the new card is suppressed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST run `ConversationAnalyzer.analyze()` on a repeating cadence (`ANALYSIS_INTERVAL_MS`, default 20s) while a session is active and `analysisEnabled = true`
- **FR-002**: `ConversationAnalyzer` MUST classify the transcript window into exactly one of: `EventType.QUESTION`, `EventType.ACTION_ITEM`, `EventType.DECISION`, `EventType.CONFUSION`, or return `AnalysisEvent.NoSignal`
- **FR-003**: The transcript window passed to the analyzer MUST be capped at `windowSizeSeconds` (default 60s) of the most recent transcript text; older text MUST be excluded (data minimisation, Principle I)
- **FR-004**: Text passed to any inference call from the analyzer MUST NOT exceed 600 characters (same truncation rule as existing `OnDeviceLlamaProvider` and `CloudInferenceEngine`)
- **FR-005**: When `EventType.QUESTION` is classified, `ConversationAnalyzer` MUST delegate to the existing `CloudInferenceEngine.streamSuggestion()` path rather than duplicating inference logic
- **FR-006**: `AnalysisCadenceController` MUST skip a tick if a prior analysis inference is still in-flight (debounce guard)
- **FR-007**: `AnalysisCadenceController` MUST enforce a minimum cadence of 10 seconds regardless of the configured `analysisInterval`
- **FR-008**: `SessionViewModel` MUST expose `analysisEvent: StateFlow<AnalysisEvent?>` in addition to the existing `suggestionEvents` flow
- **FR-009**: `SessionScreen` MUST render analysis suggestion cards with a type-specific label: "📋 Action Item", "✅ Decision", "❓ Unclear", alongside the existing question suggestion card
- **FR-010**: If an analysis card and a question-detection card arrive simultaneously, the question card MUST take display priority; `analysisEvent` is cleared to `null` for that tick
- **FR-011**: Duplicate suppression MUST be applied: if the leading 64 chars of a new analysis result matches a card displayed in the last 60s, the new card MUST be suppressed
- **FR-012**: `AnalysisSettings` MUST be persisted to DataStore Preferences (`analysis_enabled`, `analysis_interval_s`, `analysis_window_s`)
- **FR-013**: When `analysisEnabled = false`, all cadence timers MUST be stopped and no inference calls MUST be made; existing question-detection is unaffected
- **FR-014**: If no on-device model is loaded and cloud is disabled, `ConversationAnalyzer` MUST use a keyword-matching heuristic classifier and return an `AnalysisEvent` with `suggestionText = null` (label-only card)
- **FR-015**: `SessionViewModel.onCleared()` MUST cancel the analysis coroutine scope to prevent in-flight inference after session end

### Key Entities

- **AnalysisEvent**: sealed — `NoSignal` | `Question(text, suggestionText)` | `ActionItem(text, suggestionText?)` | `Decision(text, suggestionText?)` | `Confusion(text, suggestionText?)`
- **EventType**: enum — `QUESTION`, `ACTION_ITEM`, `DECISION`, `CONFUSION` (priority order for tie-breaking)
- **AnalysisSettings**: `analysisEnabled: Boolean`, `analysisIntervalS: Int` (10–120, default 20), `analysisWindowS: Int` (15–300, default 60)
- **ConversationAnalyzer**: classifies window text → dispatches inference → emits `AnalysisEvent`; injectable for tests via `FakeConversationAnalyzer`
- **AnalysisCadenceController**: owns the `ticker` coroutine; enforces debounce and minimum cadence; injectable dispatcher for tests
- **TranscriptWindowBuffer**: rolling buffer of ASR segments keyed by timestamp; trims to `analysisWindowS`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Analysis cadence fires within ±500ms of the configured interval on Y700 Gen 3 under normal load
- **SC-002**: First visible analysis card appears ≤ 3s after the cadence tick fires (Principle II — same latency budget as question suggestions)
- **SC-003**: Text passed to inference per analysis tick is always ≤ 600 chars (verified by contract test)
- **SC-004**: `./gradlew testDebugUnitTest` passes all new contract tests with zero native dependencies
- **SC-005**: When `analysisEnabled = false`, zero inference calls are made during a 60s session (verified by contract test with call counter)
- **SC-006**: Duplicate suppression eliminates ≥ 95% of repeated cards for the same decision/action item in smoke testing
- **SC-007**: Battery impact of continuous analysis at default cadence (20s) does not increase session battery drain by more than 10% vs baseline (measured via `adb shell dumpsys batterystats` over 10-minute session) — **post-merge monitoring target only; not a merge gate**

## Assumptions

- `TranscriptWindowBuffer` (introduced in this spec) is the authoritative ASR segment accumulator — not a pre-existing component from spec 004. The spec 004 VAD/Vosk pipeline feeds it via `AudioProcessingForegroundService.onTranscriptSegment()` once the full ASR pipeline lands; until then, the manual question input in `SessionScreen` serves as the demo feed.
- `ConversationAnalyzer` uses the existing `CloudInferenceEngine` / `OnDeviceLlamaProvider` stack for suggestion generation — no new inference engine is introduced
- The system prompt for analysis inference is distinct from the question-detection system prompt and is tailored per event type (e.g., action-item prompt asks "who owns this and by when?")
- Keyword-matching heuristic (fallback when no model loaded) uses simple regex patterns — not a separate ML model
- Analysis runs on the same `Dispatchers.Default` coroutine context as existing inference; no dedicated thread pool is added
- The session UI already has a card-based suggestion display (from specs 001/002); this spec adds additional card types to the existing list, not a separate UI surface
- `analysisEnabled` defaults to `true` on first install (continuous analysis is the headline feature of this spec)
- Cloud badge logic from spec 005 is reused as-is; `FallbackReason` enum gains no new values in this spec
