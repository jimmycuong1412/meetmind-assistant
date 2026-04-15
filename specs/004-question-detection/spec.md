# Feature Specification: Question Detection and Timely Suggestions

**Feature Branch**: `feature/004-question-detection`
**Created**: 2026-04-14
**Status**: Draft
**Input**: User description: "The app should detect the questions from the interviewer or
presenters, then give out the suggested answer on time"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Detect a Direct Question and Surface a Suggestion (Priority: P1)

During an active listening session, another person speaks a clear, direct question
(e.g., "Can you walk me through your experience with distributed systems?"). The app
recognises that a question has been asked — not just any speech — and immediately
surfaces a relevant suggested answer on screen. The user does not need to tap anything;
the suggestion appears automatically as soon as the question is detected.

**Why this priority**: The distinguishing capability of this feature is question
detection. Generic utterance-to-suggestion (spec 001) fires on every sentence. This
feature must be smarter: it triggers specifically when a question is posed, so the
user only sees a suggestion when they actually need to respond. Everything else is
a refinement of this detection signal.

**Independent Test**: Start a session, have another person (or a recording) ask a
question aloud. Verify a suggestion appears automatically within 3 seconds of the
question ending. Then speak a non-question statement ("The weather is nice today")
and verify no new suggestion is triggered.

**Acceptance Scenarios**:

1. **Given** a session is active and audio is being captured, **When** a question is
   spoken by another person, **Then** a suggestion appears on screen automatically
   within 3 seconds of the question ending.

2. **Given** a session is active, **When** a declarative statement (non-question) is
   spoken, **Then** no new suggestion is triggered.

3. **Given** a question is detected, **When** the suggestion is displayed, **Then**
   it is clearly labeled or visually distinct to indicate it is a suggested *answer*
   (not a summary or action item).

4. **Given** multiple questions are asked in quick succession (less than 5 seconds
   apart), **Then** the app displays a suggestion for the most recent question and
   does not produce duplicate or overlapping suggestions that clutter the screen.

---

### User Story 2 - Question Detection Works in Both Session Modes (Priority: P1)

Question detection fires in both Meeting Mode and Interview Mode, but the suggestions
it produces are shaped differently per mode. In Interview Mode, detected questions
trigger answer-style suggestions framed from the user's perspective. In Meeting Mode,
detected questions trigger collaborative suggestions such as follow-up points,
clarifications, or action items relevant to the question topic.

**Why this priority**: Mode-awareness is non-negotiable for correctness. A suggestion
framed as "Here's how I would answer that as a candidate" is actively harmful in a
meeting context. The detection logic is shared; the suggestion framing is mode-specific.

**Independent Test**: In Interview Mode, ask "Tell me about a time you led a project."
Verify the suggestion is answer-oriented. Switch to a new session in Meeting Mode,
ask "Who owns the Q3 delivery timeline?" Verify the suggestion is meeting-oriented
(e.g., clarifying question, action item prompt, not a personal achievement answer).

**Acceptance Scenarios**:

1. **Given** a question is detected in Interview Mode with a saved profile, **Then**
   the suggestion is framed as a personal, first-person response that draws on the
   user's role and context.

2. **Given** a question is detected in Meeting Mode, **Then** the suggestion is
   framed as a collaborative, group-facing contribution (e.g., a follow-up, a
   summary point, an action item).

3. **Given** the same question is spoken in both modes (tested separately),
   **Then** the two suggestions are observably different in tone and framing.

---

### User Story 3 - Suggestion Timing Indicator (Priority: P2)

When a suggestion is triggered by a detected question, a subtle visual indicator
shows the user that the suggestion is "fresh" — generated in response to the most
recent question. If the suggestion becomes stale (e.g., the conversation has moved
on and more than 60 seconds have passed since the question), the staleness is
communicated visually so the user knows not to use it.

**Why this priority**: In a fast-moving interview or meeting, an old suggestion
that's still visible can mislead. The timing indicator helps the user trust the
suggestion's relevance at a glance without reading timestamps. It's a UX refinement,
not core detection logic.

**Independent Test**: Ask a question, verify the suggestion shows a "fresh" indicator.
Wait 60+ seconds without any new question. Verify the suggestion visually changes
to indicate it is stale.

**Acceptance Scenarios**:

1. **Given** a suggestion has just been generated from a detected question, **When**
   the suggestion is displayed, **Then** a visual freshness indicator (e.g., a colour
   or label) shows that it is current.

2. **Given** 60 or more seconds have elapsed since the suggestion was generated with
   no new question detected, **Then** the visual indicator changes to communicate
   that the suggestion may no longer be relevant.

3. **Given** a new question is detected and a new suggestion replaces the old one,
   **Then** the freshness indicator resets to "current" for the new suggestion.

---

### User Story 4 - Non-Question Speech Is Silently Ignored (Priority: P1)

During a session, large amounts of speech that are not questions (e.g., a presenter
giving a monologue, background conversation, or the user speaking their own answer)
do not trigger suggestion generation. The app remains quiet and unobtrusive during
non-question speech, preserving screen real estate and cognitive attention.

**Why this priority**: False positives — suggestions firing on non-questions — are
more disruptive than no suggestions at all. Precision of the question detector is
therefore a first-class requirement, not just a nice-to-have.

**Independent Test**: Speak 5 consecutive declarative sentences (no questions). Verify
zero new suggestions appear. Then ask one question. Verify exactly one suggestion
appears.

**Acceptance Scenarios**:

1. **Given** a presenter delivers 3 or more consecutive non-question sentences,
   **Then** no suggestion is generated for any of them.

2. **Given** background conversation or ambient speech (non-directed, non-question)
   is picked up by the microphone, **Then** no suggestion is triggered.

3. **Given** the user is speaking their own answer aloud, **Then** no suggestion
   is triggered for the user's own speech.

---

### Edge Cases

- What happens when a rhetorical question is asked that does not require a response
  (e.g., "Isn't that interesting?")?
- What if a question is asked mid-sentence within a longer turn (e.g., "We've done a
  lot — so tell me, what's your take on microservices?")?
- What happens when multiple people speak simultaneously (crosstalk)?
- What if the question is spoken in a non-standard form, such as an implied question
  ("I'd love to know your thoughts on this")?
- What happens if the detected question is very short or ambiguous (e.g., "Why?",
  "Really?")?
- How does the app behave when the user speaks immediately after a question is detected
  — does their speech cancel or delay the suggestion?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST distinguish between questions and non-question utterances
  in the captured audio stream and only trigger suggestion generation on detected
  questions.
- **FR-002**: The system MUST produce a suggestion within 3 seconds of a detected
  question ending, matching the baseline latency SLA from spec 001.
- **FR-003**: The system MUST NOT generate a suggestion in response to declarative
  statements, monologue speech, or background non-question audio.
- **FR-004**: When multiple questions are spoken within 5 seconds of each other,
  the system MUST generate a suggestion for the most recent question only; earlier
  pending suggestions MUST be discarded.
- **FR-005**: Suggestions triggered by question detection MUST incorporate the active
  session mode (Meeting / Interview) as defined in spec 003, producing mode-appropriate
  framing.
- **FR-006**: Suggestions triggered by question detection MUST incorporate the user's
  saved context profile (role + context note) as defined in spec 003.
- **FR-007**: The suggestion display MUST include a visual freshness indicator that
  distinguishes a newly generated suggestion from one that is 60 or more seconds old.
- **FR-008**: After 60 seconds with no new question detected, the freshness indicator
  MUST update to communicate that the current suggestion may be stale.
- **FR-009**: The question detection capability MUST operate entirely on-device with
  no audio or transcript data transmitted externally (per constitution Principle I).
- **FR-010**: The system MUST NOT trigger a suggestion when the primary user's own
  voice is the source of a question-like utterance; detection targets the other
  speaker(s) in the conversation.

### Key Entities

- **Detected Question**: A classified audio segment identified as a question directed
  at the user. Attributes: transcript text, detected timestamp, confidence level
  (high/low — for future use), source (other speaker).
- **Suggestion** (extended from spec 001): Now includes a `trigger_type` field
  (`QUESTION_DETECTED` vs `UTTERANCE_END`) and a `generated_at` timestamp used to
  compute freshness.
- **Freshness State**: A derived display attribute of a Suggestion. Values: `FRESH`
  (< 60 seconds since generation), `STALE` (≥ 60 seconds since generation).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Given 10 distinct direct questions spoken aloud in a test session,
  the app generates a suggestion for at least 9 of them (≥ 90% question recall).
- **SC-002**: Given 10 declarative non-question statements spoken aloud in a test
  session, the app generates a suggestion for at most 1 of them (≤ 10% false
  positive rate).
- **SC-003**: Each suggestion triggered by a detected question appears within 3
  seconds of the question ending, measured across 5 test questions on a mid-range
  device.
- **SC-004**: The same question spoken in Interview Mode and Meeting Mode (two
  separate sessions) produces suggestions that are observably different in tone,
  as assessed by the user reading both outputs.
- **SC-005**: After 60 seconds with no new question, the suggestion's visual state
  changes without any user interaction.

## Assumptions

- The app targets a two-party or small-group conversational setting (one interviewer
  / presenter + the user); it is not designed for large lecture halls or multi-
  speaker panels in v1.
- "Question detection" means identifying interrogative intent from transcribed speech
  — e.g., rising intonation markers, question words (who/what/when/where/why/how),
  or sentence-final punctuation patterns in the transcript. The exact detection
  mechanism is a planning/implementation concern.
- The app cannot reliably distinguish the user's voice from other speakers in v1
  (speaker diarisation is out of scope). As a practical mitigation, FR-010 (no
  self-suggestion) is best-effort in v1: if the user's speech is indistinguishable,
  a false positive may occasionally occur.
- Rhetorical questions (e.g., "Isn't it obvious?") may produce suggestions; filtering
  rhetorical intent is out of scope for v1.
- Implied questions ("I'd love to hear your thoughts") are treated as questions if
  their transcript matches question intent patterns; this is acceptable behaviour.
- Very short ambiguous questions ("Why?", "Really?") will generate a suggestion;
  the quality of that suggestion is a model-quality concern, not a detection concern.
- The freshness threshold of 60 seconds is a starting default; it may be adjusted
  based on user experience feedback without requiring a spec amendment.
- This feature builds directly on spec 001 (continuous audio capture + VAD),
  spec 002 (display layer), and spec 003 (mode + profile context).
