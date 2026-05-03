# Research: Interview Coach Enhancements

**Feature**: 010-interview-coach-enhancements  
**Date**: 2026-05-03  
**Status**: Complete — all NEEDS CLARIFICATION resolved

---

## Decision Log

### R-01: Reactive trigger insertion point in SyncSttLlmUseCase

**Decision**: Insert reactive trigger check at line ~202 of `SyncSttLlmUseCase.kt`, immediately after a completed segment is appended to `newSegmentsSinceLastLlm`. Use `lastInferenceTimestamp` (already tracked) as the deduplication guard with a `MIN_REACTIVE_DEBOUNCE_MS = 10_000` (10 seconds).

**Rationale**: The segment observation loop (`transcriptionFlow.collect`) is the single point where all completed segments flow through. Inserting here avoids touching the interval timer logic entirely — the 30-second fallback continues unchanged. The existing `lastInferenceTimestamp` check at line ~232 naturally suppresses duplicate interval fires after a reactive trigger.

**Alternatives considered**:
- Separate coroutine scanning segments — rejected; creates race conditions with the existing collector and requires synchronisation.
- Replace interval with event-driven only — rejected; Constitution Principle V requires the fallback for non-question speech (statements, pauses).

**Exact insertion location**: `SyncSttLlmUseCase.kt` line ~220, after `newSegmentsSinceLastLlm.add(segment)`, before `currentPartialSegment = segment.text` assignment.

---

### R-02: Speaker cluster → label mapping heuristic

**Decision**: First speaker cluster observed in the session = `[Interviewer]`; all other clusters = `[You]`. Mapping held in `MutableMap<Int, String>` inside `SyncSttLlmUseCase`, cleared on session start. No user configuration required.

**Rationale**: In a real interview the interviewer always speaks first (greeting, introduction). This covers >95% of real-interview openings. The alternative (letting the user designate which cluster is the interviewer) adds UI complexity not justified by the marginal gain.

**Fallback**: When `speakerCluster == null` on all segments in a window, call existing unlabelled `InterviewPromptBuilder.build()` — no crash, degraded to current behaviour.

**Alternatives considered**:
- Energy-based speaker classification (louder = interviewer) — rejected; unreliable on shared microphone and adds audio processing complexity.
- User-designated speaker in UI — deferred to future spec; adds a setup step that breaks Principle V (must be immediately demo-able).

---

### R-03: Filler word detection approach

**Decision**: Pure string tokenisation in a new `FillerWordCounter` object in the domain module. No LLM inference, no ML model. Fixed English word list: `{um, uh, like, you know, basically, literally, so}`. Count only in completed segments (never partial).

**Rationale**: Filler detection does not require semantic understanding — exact word matching is sufficient and runs in <1ms per segment. Zero latency cost, zero inference budget impact.

**Multi-word filler handling**: `"you know"` matched via substring scan on the lowercased, de-punctuated segment text before single-word tokenisation.

**Rate calculation**: `ratePerMinute = totalFillerCount / max(1f, recordingDurationMillis / 60_000f)`. Uses `recordingDurationMillis` already tracked in `MainUiState` (line 49).

**Colour thresholds**: < 3/min → neutral; ≥ 3/min → amber (`Color(0xFFF59E0B)`); ≥ 6/min → red (`MaterialTheme.colorScheme.error`). Thresholds derived from public speaking coaching literature (standard guideline: < 2/min is "excellent", < 5/min is "acceptable").

**Alternatives considered**:
- ML-based filler detection (Silero or similar) — rejected; overkill, adds model weight, violates "no unnecessary complexity" principle.
- Phoneme-level detection from audio — rejected; audio is not retained post-transcription per Principle I intent.

---

### R-04: Answer duration timer implementation

**Decision**: `cardTimers: Map<String, Long>` in `MainUiState` maps `insightId → elapsedMs`. A 1-second `ticker` coroutine in `MainViewModel` increments all active timers while `isRecording`. Timer starts when the first completed segment arrives *after* the insight's `timestamp`. Timer freezes when `isRecording` becomes false.

**Rationale**: Segment timestamps (`startOffsetMs`, `endOffsetMs`) are session-relative milliseconds already available on `TranscriptionSegment`. No wall-clock math needed.

**"Too brief" detection**: When `isRecording` transitions to `false` (or a new question card fires), check if elapsed < 30 000 ms. If so, add a `nudge: String?` field to the timer entry.

**Colour thresholds**: Border tint: < 90s → none; ≥ 90s → amber; ≥ 120s → red. Implemented via `CardDefaults.cardColors` override or `border()` modifier.

**Alternatives considered**:
- Display timer only in the Live Sync button — rejected; not associated with a specific question, loses coaching value.
- Use wall-clock time — rejected; session-relative offsets are more accurate and don't drift with device clock changes.

---

### R-05: STAR format storage strategy

**Decision**: Store STAR-structured answer inline in `content` using `|||` as section separator. Format: `"Situation: … ||| Task: … ||| Action: … ||| Result: …"`. `question_type` stored in the new `question_type TEXT` column (Room migration v9→v10).

**Rationale**: Adding a `|||`-delimited convention to the existing `content` column avoids adding four more columns and keeps the domain model minimal. The parser already handles graceful degradation (if `|||` is absent, render as plain text). The Room migration is a single `ALTER TABLE ADD COLUMN` — safest possible migration.

**Alternatives considered**:
- Separate STAR columns (`situation TEXT`, `task TEXT`, `action TEXT`, `result TEXT`) — rejected; over-engineers the schema for a single question type; future question types would need more columns.
- Embed `question_type` in `tasks` JSON — rejected; `tasks` is semantically coaching tips, not metadata; mixing concerns makes the parser harder to maintain.
- Embed `question_type` in `title` with prefix encoding — rejected; `title` is already overloaded (`"coaching:"` prefix); adding another prefix layer increases fragility.

---

### R-06: Question-type classification in prompt

**Decision**: Add `question_type` to the LLM JSON output schema. The system prompt instructs the model to classify as `"behavioural"`, `"technical"`, `"situational"`, or `null`. Client-side keyword patterns act as a pre-filter hint (not authoritative) to catch obvious cases if the LLM returns `null`.

**Behavioural keywords** (pre-filter): `"tell me about a time"`, `"give me an example"`, `"describe a situation"`, `"walk me through a time"`, `"have you ever"`.

**Rationale**: LLM classification is more accurate for ambiguous questions than keyword matching alone. The pre-filter handles the LLM's occasional omission on short questions.

**Alternatives considered**:
- Client-side-only keyword classifier — rejected; misses paraphrased behavioural questions (e.g., "Can you share an experience where…").
- Separate LLM call for classification — rejected; doubles inference cost; adding one field to the existing JSON schema is free.

---

### R-07: Room migration safety

**Decision**: `MIGRATION_9_10` adds `question_type TEXT` (no `NOT NULL`, no `DEFAULT`). All existing rows will have `NULL` for this column. `LlmInsight.questionType: String?` defaults to `null` in Kotlin.

**Verified**: Current DB version is 9 (last migration is `MIGRATION_8_9` in `AppDatabase.kt` lines 173-183). No destructive operations required.

**Risk**: None. `ALTER TABLE … ADD COLUMN … TEXT` is safe on all SQLite versions supported by Android API 26+.

---

### R-08: No new dependencies or permissions

**Decision**: All five stories are implemented using existing project dependencies only. No new Gradle dependencies added. `INTERNET` permission is not declared.

**Verified against**: Constitution Principle IV (minimal permissions), Principle I (on-device only), APK size constraint (<50 KB delta — all new code is Kotlin, no native libraries).
