# Feature Specification: English Coach Mode

**Feature Branch**: `feature/012-english-coach`  
**Created**: 2026-05-04  
**Status**: Draft  
**Input**: User description: "implement english coach mode that help user to correct grammar, up to the context like daily conversation or working environment, suggest their phrase more polish and natural like native speaker"

## Overview

A new recording mode, **English Coach**, gives real-time feedback on the user's spoken English after each completed utterance. For every segment the LLM returns:
1. A corrected version of the phrase with grammar fixes applied
2. A more polished / native-speaker rephrasing tuned to the selected context
3. Targeted coaching tips (what was wrong and why the rewrite is better)

The mode is context-aware: the user selects **Daily Conversation** or **Professional / Work** before starting, and the LLM tailors vocabulary, register, and formality accordingly.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Grammar Correction Per Utterance (Priority: P1)

After each completed transcription segment, the app shows a card with the original phrase, a corrected version, and a brief note on what was fixed.

**Why this priority**: Grammar correction is the core value. Everything else is secondary.

**Independent Test**: Say "I goes to the office yesterday" → card shows corrected form "I went to the office yesterday" with a note "went (past tense of go)". Card appears within the configured interval.

**Acceptance Scenarios**:

1. **Given** English Coach mode is active and recording, **When** a completed segment arrives with ≥ 5 words, **Then** LLM inference is triggered and a correction card appears.
2. **Given** the LLM finds no grammar errors, **Then** the card shows the original phrase with a "Sounds great!" note (no fabricated corrections).
3. **Given** the segment has < 5 words (filler noise), **Then** no card is generated for that segment.

---

### User Story 2 — Polished Native-Speaker Rephrasing (Priority: P1)

Alongside the correction, the card shows an alternative phrasing that a native speaker would use — more natural, idiomatic, and appropriate for the chosen context.

**Why this priority**: Grammar correctness alone is insufficient. Users want to sound native, not just technically correct.

**Independent Test**: Say "Can you give me more detail about this?" (correct grammar) → card shows polish suggestion like "Could you elaborate on that?" (Professional context) or "Tell me more!" (Daily context).

**Acceptance Scenarios**:

1. **Given** Daily Conversation context, **When** a phrase is processed, **Then** the polish suggestion uses informal, friendly language (contractions, everyday idioms).
2. **Given** Professional / Work context, **When** a phrase is processed, **Then** the polish suggestion uses formal vocabulary, hedging, and business register.
3. **Given** the original phrase is already fully idiomatic, **Then** polish suggestion is omitted or marked "Already sounds great!".

---

### User Story 3 — Context Selector (Priority: P2)

The new session dialog includes a context picker for English Coach mode: **Daily Conversation** and **Professional / Work**. The selection is persisted in the session `topic` field (same pattern as Interview Coach role).

**Why this priority**: Without context, the LLM cannot calibrate register. The two contexts cover the primary use cases.

**Independent Test**: Create two sessions — one with Daily, one with Professional — say the same phrase in both → confirm card tone differs.

**Acceptance Scenarios**:

1. **Given** user selects Daily Conversation, **When** session starts, **Then** `topic = "daily"` is stored on the session.
2. **Given** user selects Professional / Work, **When** session starts, **Then** `topic = "professional"` is stored.
3. **Given** no context selected (edge case), **Then** defaults to "daily".

---

### User Story 4 — Coaching Card UI (Priority: P2)

English Coach insights are rendered as a distinct card layout: original phrase → corrected phrase → polish suggestion → coaching tip. Uses a green accent to distinguish from Interview (purple) and Meeting (blue).

**Why this priority**: The three-layer structure (original / corrected / polished) is unique to this mode and needs a dedicated composable so users can scan the hierarchy quickly.

**Independent Test**: Trigger a correction card → verify all four sections visible; verify green accent; verify correct section labels.

**Acceptance Scenarios**:

1. **Given** a coaching card is generated, **Then** it shows labeled sections: "Original", "Corrected", "More Natural", "Why".
2. **Given** no grammar error found, **Then** "Corrected" section shows "✓ No changes" and "Why" section is omitted.
3. **Given** polish suggestion equals corrected phrase, **Then** "More Natural" section is omitted to avoid redundancy.

---

### Edge Cases

- What if the user speaks a non-English sentence? The LLM should note "Not English — coach works on English speech only" in the coaching tip and skip correction/polish.
- What if the segment is only one word (e.g. "Okay")? Skip inference (< 5-word minimum).
- What if the LLM returns malformed JSON? Fall back to showing the raw LLM text as the coaching tip with no structured sections.
- What if recording is in a noisy environment and STT produces garbled output? Coach it as-is; the LLM will note it is unclear.
- What if the corrected phrase is identical to the original? Display "✓ No changes needed" without fabricated corrections.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `RecordingMode` enum MUST include `ENGLISH_COACH` as a new value.
- **FR-002**: `SyncSttLlmUseCase` MUST trigger LLM inference after each completed segment ≥ 5 words in English Coach mode (segment-reactive, not interval-based).
- **FR-003**: The LLM prompt MUST include the selected context (`"daily"` / `"professional"`) and instruct the model to return a JSON object with fields: `original`, `corrected`, `is_correct` (bool), `polish`, `coaching_tip`.
- **FR-004**: `EnglishCoachOutputParser` MUST parse the five-field JSON and map to `EnglishCoachInsight` domain model; fall back gracefully on malformed output.
- **FR-005**: `EnglishCoachInsight` MUST be mapped to `LlmInsight` for persistence using: `title = original phrase (truncated to 80 chars)`, `content = corrected phrase`, `tasks = JSON array of [polish, coaching_tip]`.
- **FR-006**: New session dialog MUST show a context picker (Daily / Professional) when English Coach is selected as the recording mode.
- **FR-007**: Context picker selection MUST be persisted in the session `topic` field.
- **FR-008**: `InsightsSection` MUST render English Coach cards with `EnglishCoachInsightItem` composable (green accent, four labeled sections).
- **FR-009**: All enhancements MUST degrade gracefully when LLM parse fails — no crash.
- **FR-010**: English Coach mode MUST appear in the recording mode selector with description "Real-time grammar and phrasing coach".

### Key Entities

- **EnglishCoachInsight**: `{ original: String, corrected: String, isCorrect: Boolean, polish: String?, coachingTip: String?, context: String }` — in-memory typed representation.
- **EnglishCoachContext**: `String` constants `"daily"` / `"professional"` — stored in session `topic` field, no enum.
- **LlmInsight** (persistence): re-uses existing schema — `title = original`, `content = corrected`, `tasks = ["polish tip", "coaching tip"]`, `questionType = context`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Grammar correction card appears within 5 seconds of a completed segment ≥ 5 words on Lenovo Legion Y700 Gen 3.
- **SC-002**: Daily vs. Professional context produces detectably different register in the polish suggestion (validated via manual spot-check with 5 phrases).
- **SC-003**: No crash or ANR in any of the 5 edge cases listed above.
- **SC-004**: `EnglishCoachOutputParser` unit tests: ≥ 8 fixtures covering correct input, grammar errors, non-English input, malformed JSON, empty input, no-change case, daily context, professional context.
- **SC-005**: English Coach mode appears correctly in the new session dialog mode selector.

---

## Clarifications

### Session 2026-05-04

- Q: Should English Coach trigger per-segment (after every utterance) or on the existing 30-second interval? → A: Per-segment — the value is immediate per-utterance feedback, not a periodic summary. The 30s interval path is unused in this mode.
- Q: Should context be "daily" / "professional" only, or also include academic, creative writing, etc.? → A: Two contexts only for MVP — daily and professional. Avoids prompt complexity and covers primary use cases.
- Q: Should the corrected phrase replace the original in the transcript view, or appear only in the insight card? → A: Insight card only — transcript shows the original as spoken; correction is coaching, not editing.
- Q: Should English Coach mode reuse the Interview Coach card UI or get its own composable? → A: Own composable (`EnglishCoachInsightItem`) with a green accent — the three-layer structure (original / corrected / polished) doesn't map to the interview card layout.
- Q: Should `EnglishCoachInsight` be persisted separately or mapped to `LlmInsight`? → A: Mapped to `LlmInsight` — no new Room migration. Same pattern as Interview Coach.

---

## Assumptions

- STT is already producing English transcription; English Coach does not validate the input language at the STT level — the LLM handles non-English gracefully.
- The existing `topic` field on the session entity is used to carry the English Coach context, same pattern as Interview Coach role.
- No new Room migration needed — `LlmInsight.questionType` column stores the context string (`"daily"` / `"professional"`).
- Cloud inference mode is out of scope — English Coach runs on-device only.
- The `is_correct` boolean is the authoritative signal; if true, the UI shows "No changes" regardless of whether `corrected` differs slightly from `original`.
