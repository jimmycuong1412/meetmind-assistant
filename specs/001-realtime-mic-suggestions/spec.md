# Feature Specification: Real-Time Mic Suggestions

**Feature Branch**: `feature/001-realtime-mic-suggestions`
**Created**: 2026-04-14
**Status**: Draft
**Input**: User description: "The app can listen from microphone then generate a suggestion answer in real time, no need record then send the audio"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start Listening and Receive Suggestions (Priority: P1)

A user opens the app and taps a single "Start Listening" button. The app immediately
begins capturing audio from the microphone. As the user or another speaker finishes
a sentence or question, the app displays a contextually relevant suggested response
or talking point on screen — all without the user having to stop, record, export, or
send anything.

**Why this priority**: This is the entire product. Without streaming audio-to-suggestion
working end-to-end, nothing else is useful. Every other story is a refinement of this
core loop.

**Independent Test**: Open the app, tap Start, speak a question aloud (e.g., "Tell me
about your experience with project management"), and verify a suggestion appears within
3 seconds without any manual interaction.

**Acceptance Scenarios**:

1. **Given** the app is open and idle, **When** the user taps "Start Listening",
   **Then** the microphone activates and a visual indicator shows that audio capture
   is active.

2. **Given** audio capture is active, **When** a complete sentence is detected,
   **Then** a relevant suggestion appears on screen within 3 seconds of the sentence
   ending.

3. **Given** audio capture is active, **When** there is silence or background noise
   (no speech detected), **Then** no spurious suggestions are generated and the
   previous suggestion remains visible.

4. **Given** the app is in the listening state, **When** the user taps "Stop
   Listening", **Then** audio capture halts and no further suggestions are generated.

---

### User Story 2 - Suggestions Visible While Conversation Continues (Priority: P2)

While the conversation continues, the user can glance at the screen and read the
latest suggestion without any interaction. New suggestions replace or append below
the previous ones, maintaining a short history so the user can refer back.

**Why this priority**: The suggestions must be glanceable mid-conversation. If the
display clears too aggressively or requires taps to reveal content, the feature is
unusable in a live meeting.

**Independent Test**: Speak three successive questions. Verify each produces a new
suggestion and at least the last two suggestions are visible on screen simultaneously.

**Acceptance Scenarios**:

1. **Given** the app is listening and a suggestion is displayed, **When** a new
   suggestion is generated, **Then** it appears at the top of the list and the
   previous suggestion scrolls down (retaining at least 2 visible entries).

2. **Given** a suggestion list is on screen, **When** the user does nothing,
   **Then** suggestions remain readable without auto-dismissal for at least 30 seconds.

3. **Given** the suggestion list exceeds display capacity, **When** the user
   scrolls, **Then** older suggestions are accessible by scrolling.

---

### User Story 3 - Dismiss and Clear Session (Priority: P3)

At the end of a meeting or interview, the user can clear all suggestions and session
data from the screen with a single action, returning the app to its idle state.
No data persists after the session is cleared.

**Why this priority**: Privacy cleanup matters but is a finishing touch. The core
value is delivered by P1 and P2.

**Independent Test**: After generating several suggestions, tap "Clear Session" and
verify the suggestion list is empty and the app returns to idle state.

**Acceptance Scenarios**:

1. **Given** a session with suggestions is displayed, **When** the user taps
   "Clear Session", **Then** all suggestions are removed from screen and the app
   returns to idle state.

2. **Given** the user taps "Clear Session" while listening is active, **Then**
   listening stops first, then suggestions are cleared.

3. **Given** the session has been cleared, **When** the user starts a new session,
   **Then** no suggestions from the previous session are visible.

---

### Edge Cases

- What happens when the microphone permission is denied or not yet granted?
- How does the system handle very long uninterrupted speech (> 60 seconds) without
  a natural pause?
- What happens if the on-device model takes longer than expected (e.g., device
  under CPU load) — does the suggestion arrive late or get dropped?
- What if no meaningful speech is detected (e.g., background TV, music)?
- What happens when the app is sent to the background while listening is active?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST capture audio from the device microphone in real time
  without requiring the user to record and then upload or send a file.
- **FR-002**: The system MUST transcribe captured speech on-device, producing a
  running transcript without transmitting audio to any external service.
- **FR-003**: The system MUST analyze the transcript context and generate a suggestion
  response relevant to the most recent utterance or question.
- **FR-004**: The system MUST display suggestions within 3 seconds of a detected
  sentence or question ending.
- **FR-005**: The system MUST use Voice Activity Detection (VAD) to distinguish
  speech from silence or background noise before invoking the suggestion engine.
- **FR-006**: The system MUST retain at least the last 5 suggestions on screen in
  a scrollable list during an active session.
- **FR-007**: The system MUST provide a "Start Listening" and "Stop Listening"
  control that immediately activates or deactivates audio capture.
- **FR-008**: The system MUST provide a "Clear Session" action that removes all
  suggestions from the current session.
- **FR-009**: All audio processing and suggestion generation MUST occur entirely
  on-device; no audio, transcript, or suggestion data may leave the device.
- **FR-010**: The system MUST request microphone permission before attempting to
  capture audio and gracefully handle denial (show user-friendly explanation).

### Key Entities

- **Session**: A single continuous listening period from "Start" to "Stop" or
  "Clear". Contains an ordered list of Suggestions.
- **Utterance**: A segment of detected speech that ends at a natural pause or
  sentence boundary. Input unit for the suggestion engine.
- **Suggestion**: A generated response or talking point derived from an Utterance.
  Attributes: text content, source utterance summary, timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A suggestion appears on screen within 3 seconds of a question or
  sentence ending, measured on a mid-range Android device (any device released
  within the last 4 years).
- **SC-002**: The app produces zero suggestions during sustained silence (5+ seconds
  of no speech), confirming VAD is functioning correctly.
- **SC-003**: The user can complete the full flow (open app → start listening →
  receive suggestion → stop → clear) without consulting any instructions.
- **SC-004**: No audio data, transcript, or suggestion content is transmitted over
  the network at any point during a session (verifiable by monitoring network
  activity during use).
- **SC-005**: The app remains responsive (no ANR, no dropped frames on suggestion
  display) while audio capture and inference are running simultaneously.

## Assumptions

- The user is the sole operator of the device; no multi-user or shared-device
  scenarios are in scope.
- The device microphone is the only audio input; Bluetooth headset or external
  microphone routing is out of scope for this story.
- Suggestions are in English; multi-language support is out of scope for v1.
- The suggestion engine provides generalized contextual responses (e.g., interview
  talking points, meeting follow-up prompts) rather than domain-specific scripted
  answers — the exact suggestion style will be refined during planning.
- The app runs in the foreground during a session; background audio capture is
  out of scope for v1.
- There is no user account, login, or cloud sync; the app is entirely local.
