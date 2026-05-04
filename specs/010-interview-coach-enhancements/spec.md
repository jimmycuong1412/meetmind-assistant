# Feature Specification: Interview Coach Enhancements

**Feature Branch**: `feature/010-interview-coach-enhancements`  
**Created**: 2026-05-03  
**Status**: Draft  
**Input**: User description: "make interview coach mode more realistic and effective in a real interview"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Question-Reactive Instant Trigger (Priority: P1)

The coach fires the moment a question is detected in the transcript — not on a fixed 30-second clock. When the interviewer finishes a question (ending in `?` or matching known question openers), inference starts within 1 second so the suggestion is ready before the candidate needs to speak.

**Why this priority**: The 30-second interval is the single biggest gap between the current app and a real-interview coach. A suggestion delivered 25 seconds late has zero value.

**Independent Test**: Start a recording in Interview mode. Speak a clear question ending in `?`. Verify a coaching card appears within 3 seconds. The 30-second timer must NOT be the gating factor.

**Acceptance Scenarios**:

1. **Given** Interview mode is active and recording, **When** a completed transcription segment ends with `?`, **Then** LLM inference is triggered immediately (not at the next 30s tick).
2. **Given** a trigger fired early, **When** the 30-second interval fires again, **Then** it skips if a trigger already ran within the last N seconds (no duplicate cards).
3. **Given** a segment ends without a `?` (statement, filler), **When** the interval timer fires, **Then** inference proceeds normally at the interval boundary.

---

### User Story 2 - Speaker-Aware Question Detection (Priority: P2)

The LLM prompt is labelled with `[Interviewer]` / `[You]` speaker turns derived from diarization. The coach only flags questions from the `[Interviewer]` speaker, eliminating false positives where the candidate's own questions are mis-detected.

**Why this priority**: Without speaker labels, the model has no reliable signal about who is asking vs. answering. Diarization is already in the pipeline (sherpa-onnx); it just needs wiring into `InterviewPromptBuilder`.

**Independent Test**: Speak a question yourself ("Can I ask about the tech stack?"), then have a second voice ask a question. Verify only the second question generates a coaching card.

**Acceptance Scenarios**:

1. **Given** diarization assigns Speaker A (interviewer) and Speaker B (candidate), **When** Speaker A asks a question, **Then** a coaching card is generated.
2. **Given** Speaker B (candidate) asks a rhetorical or clarifying question, **When** the segment is processed, **Then** no false-positive coaching card is generated.
3. **Given** diarization is unavailable or returns no labels, **When** inference runs, **Then** fall back to unlabelled prompt (current behaviour) with no crash.

---

### User Story 3 - Filler Word Counter (Priority: P3)

A real-time counter tracks filler words ("um", "uh", "like", "you know", "basically", "literally", "so") per minute in the candidate's speech and displays a small persistent badge on the Insights tab. It turns amber above 3/min and red above 6/min.

**Why this priority**: Filler word density is a top-3 interview feedback item and requires zero LLM inference — it is pure text processing on completed segments.

**Independent Test**: Say "Um, I think, like, basically, you know..." repeatedly. Verify the badge increments correctly and changes colour at the thresholds.

**Acceptance Scenarios**:

1. **Given** recording is active in Interview mode, **When** a completed segment contains filler words, **Then** the running count and rate (per minute) update on screen within 1 second. Rate is computed as `totalCount / max(1f, durationMin)` from the first segment onward — no minimum duration threshold; early inflated values are accepted.
2. **Given** count < 3/min, **Then** badge is neutral colour. ≥3/min → amber. ≥6/min → red.
3. **Given** recording stops, **Then** badge freezes at the final rate and is included in the session summary.

---

### User Story 4 - Answer Duration Timer (Priority: P4)

After a question card is generated, the UI starts a visible countdown/count-up timer showing the candidate's answer duration. It turns amber at 90 seconds ("consider wrapping up") and red at 2 minutes ("too long"). A sub-30-second answer triggers a "too brief" nudge.

**Why this priority**: Time-management is a concrete, actionable coaching dimension that needs no inference — only segment timestamps.

**Independent Test**: After a question card appears, speak for 95 seconds. Verify the timer turns amber. Speak for 130 seconds total and verify it turns red.

**Acceptance Scenarios**:

1. **Given** a coaching card is generated, **When** any completed segment arrives after `insight.timestamp` (regardless of speaker), **Then** a timer starts on that card. When diarization is active and the next segment is labelled `[Interviewer]`, the timer still starts — speaker filtering is not applied to timer start.
2. **Given** the candidate speaks > 90s, **Then** card border or timer turns amber.
3. **Given** the candidate speaks > 120s, **Then** card border or timer turns red.
4. **Given** the candidate's answer was < 30s total, **When** recording stops or a new question card fires (end of turn), **Then** a "consider elaborating" nudge appears retrospectively on that card (not shown live during the answer).

---

### User Story 5 - STAR Format Coaching for Behavioural Questions (Priority: P5)

The system prompt detects behavioural questions ("Tell me about a time…", "Give an example of…", "Describe a situation…") and structures the answer suggestion as explicit STAR sections (Situation, Task, Action, Result) rendered as labelled blocks in the card.

**Why this priority**: STAR is the industry-standard framework for behavioural answers. Generating it requires only a system prompt change, with a new card render variant.

**Independent Test**: Ask "Tell me about a time you handled a conflict on your team." Verify the coaching card shows four labelled STAR sections instead of generic bullet points.

**Acceptance Scenarios**:

1. **Given** the detected question matches a behavioural pattern, **When** the LLM generates the answer, **Then** the JSON includes a `question_type: "behavioural"` field and the answer is structured as STAR sections.
2. **Given** `question_type` is `"technical"` or `"situational"`, **Then** the card renders with the existing bullet-point layout.
3. **Given** the LLM returns a non-STAR answer for a behavioural question (graceful degradation), **Then** the card renders as plain text without crashing.

---

### Edge Cases

- What happens when diarization assigns the same speaker to both parties (single-speaker scenario)? Fall back to unlabelled prompt.
- What if the question-reactive trigger fires and LLM is still busy from a previous request? Queue the trigger; discard if more than 2 are pending.
- What if filler words span two segments (word split at boundary)? Count only within completed segments; no cross-boundary scanning.
- What if the answer timer fires but no segment arrives (silence)? Timer continues; silence counts toward duration.
- What if STAR detection fires on a question that's only 3 words long? Skip STAR format, use generic answer.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST trigger LLM inference within 1 second of detecting a `?`-terminated segment in Interview mode, independent of the 30-second interval.
- **FR-002**: System MUST NOT generate duplicate coaching cards when both the reactive trigger and the interval fire for the same segment window.
- **FR-003**: `InterviewPromptBuilder` MUST prepend diarization speaker labels (`[Interviewer]` / `[You]`) to each segment when diarization data is available.
- **FR-004**: System MUST count filler words (`um`, `uh`, `like`, `you know`, `basically`, `literally`, `so`) in candidate segments and display a running rate badge.
- **FR-005**: Filler word badge MUST update within 1 second of a completed segment being processed.
- **FR-006**: System MUST start an answer duration timer on the active coaching card when the first completed transcription segment arrives after `insight.timestamp`, independent of speaker label (diarization-agnostic).
- **FR-007**: Answer duration timer MUST change colour at 90s (amber) and 120s (red). A "consider elaborating" nudge MUST appear retrospectively on the card when recording stops or a new question card fires and total elapsed time was < 30s — the nudge MUST NOT appear live during an ongoing answer.
- **FR-008**: System MUST classify detected questions as `"behavioural"`, `"technical"`, or `"situational"` (String values) and include `question_type` in the LLM JSON output; `null` when no question is detected. No enum class — `String?` is the canonical type at all layers.
- **FR-009**: Behavioural questions MUST render a STAR-structured answer card with four labelled sections.
- **FR-010**: All enhancements MUST degrade gracefully when diarization is unavailable, LLM is busy, or parse errors occur — no crashes.

### Key Entities

- **FillerWordStats**: `{ count: Int, ratePerMinute: Float, recordingDurationMs: Long }` — computed from completed candidate segments. `ratePerMinute = totalCount / max(1f, recordingDurationMs / 60_000f)` applied from the first segment onward with no minimum duration gate; early inflated values are accepted behaviour.
- **AnswerTimer**: `{ questionTimestamp: Long, firstAnswerSegmentTimestamp: Long?, durationMs: Long }` — per coaching card state.
- **QuestionType**: `String?` throughout — values `"behavioural"`, `"technical"`, `"situational"`, or `null` (null means no question detected or unclassifiable; no enum class). Stored as TEXT in Room, parsed directly from LLM JSON string output.
- **ReactiveTrigerState**: tracks last trigger timestamp to suppress duplicates.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Coaching card appears within 3 seconds of a `?`-terminated segment ending (reactive trigger), measured on Lenovo Legion Y700 Gen 3.
- **SC-002**: Zero false-positive coaching cards from candidate's own questions in a dual-speaker session (requires diarization active).
- **SC-003**: Filler word badge updates within 1 second of each completed segment in a real recording session.
- **SC-004**: STAR-structured cards render correctly for ≥ 90% of behavioural questions in a 10-question test set. Validated via automated JUnit test in `domain/src/test/` using hardcoded JSON fixtures (mocked LLM responses) covering the 10 canonical behavioural question patterns — not dependent on live LLM output.
- **SC-005**: No crash or ANR in any of the 5 edge cases listed above.

## Clarifications

### Session 2026-05-04

- Q: How should the answer timer start when diarization is unavailable — wait for a `[You]`-labelled segment, use any segment after `insight.timestamp`, or wait for a 2-second silence? → A: Option B — start on any completed segment after `insight.timestamp`, diarization-agnostic.
- Q: When does the "too brief" nudge appear — live during recording after 30s, retrospectively on turn end/stop, or only in post-session summary? → A: Option B — retrospectively when recording stops or a new question card fires; never shown live during an ongoing answer.
- Q: Should QuestionType be a Kotlin enum, a String? everywhere, or enum in domain / String at DB boundary? → A: Option A — `String?` throughout all layers; `null` replaces UNKNOWN; no enum class.
- Q: How to validate SC-004 (≥ 90% STAR rendering on 10 behavioural questions) — automated JUnit fixtures, manual spot-check, or remove target? → A: Option A — automated JUnit test with 10 hardcoded JSON fixtures in `InterviewOutputParserTest.kt`; ≥ 9/10 must pass.
- Q: How should the filler badge handle sub-10s sessions where rate is artificially inflated — always show rate, suppress until 30s, or hide badge? → A: Option A — always show `totalCount / max(1f, durationMin)` from the first segment; no minimum duration gate; early inflated values accepted.

## Assumptions

- Diarization (sherpa-onnx) is functional on the primary test device but may be unavailable on others — all diarization-dependent features MUST have unlabelled fallback.
- The existing `SyncSttLlmUseCase` interval logic is not removed — the reactive trigger is additive, not a replacement.
- `LlmInsight` schema can be extended with a nullable `questionType` field without a Room migration (nullable column, default null).
- Filler word list is hard-coded in English; no i18n required for this spec.
- Cloud inference mode (spec 005) is out of scope — all enhancements run on-device.
