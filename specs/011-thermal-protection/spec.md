# Feature Specification: Thermal Protection — End-of-Session Insight Fallback

**Feature Branch**: `feature/011-thermal-protection`  
**Created**: 2026-05-04  
**Status**: Draft  
**Input**: User description: "phone is hot-switching to end-of-session insight to protect performance"

## Overview

When the device reaches a thermal stress threshold during a recording session, the app automatically suspends live per-segment LLM inference and switches to a single consolidated end-of-session insight generated when recording stops. This prevents thermal throttling from degrading audio quality, transcription accuracy, and system responsiveness during the interview itself.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Thermal Gate: Suppress Live Inference When Hot (Priority: P1)

While recording, if the device thermal status reaches `THERMAL_STATUS_MODERATE` (Android API level ≥ 29) or higher, the reactive trigger and the 30-second interval in `SyncSttLlmUseCase` are silenced. All completed transcription segments continue to be buffered in memory. A small persistent UI indicator ("Thermal mode — insight at session end") appears on the Insights tab so the user understands why no live cards are appearing.

**Why this priority**: Suppressing inference is the entire point of the feature. Everything else is secondary to this gate working correctly.

**Independent Test**: Simulate `THERMAL_STATUS_MODERATE` via the `ThermalStateSimulator` helper (injected in tests / debug builds). Start a recording in Interview mode. Speak a clear `?`-terminated question. Verify no coaching card fires during the recording. Verify the thermal indicator badge is visible.

**Acceptance Scenarios**:

1. **Given** `PowerManager.currentThermalStatus >= THERMAL_STATUS_MODERATE` at the time a question trigger would fire, **When** the trigger evaluates, **Then** inference is skipped and the segment is buffered.
2. **Given** the thermal gate is active, **When** the 30-second interval fires, **Then** it is also suppressed (no inference).
3. **Given** thermal status drops back below `THERMAL_STATUS_MODERATE` mid-session, **When** the next trigger evaluates, **Then** live inference resumes automatically (hysteresis: require two consecutive `LIGHT` or `NONE` readings to re-enable, to prevent flapping).
4. **Given** the device is API level < 29 (no thermal API), **When** the gate is evaluated, **Then** always return `false` (gate never fires; feature is a no-op on old devices).

---

### User Story 2 — End-of-Session Flush: Single Consolidated Insight on Stop (Priority: P1)

When recording stops and at least one segment was buffered (i.e., the thermal gate fired at least once during the session), `SyncSttLlmUseCase` makes a single LLM call with the full buffered transcript and emits the resulting insight. This is presented as a normal coaching card labelled "Session Summary" in the title.

**Why this priority**: Without the flush, all buffered transcription is silently discarded and the user gets zero coaching value from the session. The flush is what makes the thermal gate a graceful degradation rather than a silent failure.

**Independent Test**: Simulate thermal mode → record a 60-second session with several questions → stop recording → verify exactly one "Session Summary" coaching card appears within 10 seconds of stop.

**Acceptance Scenarios**:

1. **Given** recording stops and `bufferedSegments` is non-empty, **When** `stopStreaming()` is called, **Then** a single LLM inference is triggered over the concatenated buffered transcript.
2. **Given** the LLM returns a result, **Then** the insight is emitted with `title = "Session Summary"` and stored normally in Room.
3. **Given** recording stops and `bufferedSegments` is empty (thermal gate never fired, or session had no completed segments), **Then** no flush call is made — normal behaviour.
4. **Given** the LLM call fails (timeout, model error), **Then** the failure is logged and surfaced as a `snackbarMessage` ("Could not generate session summary"); no crash.

---

### User Story 3 — Thermal Indicator Badge (Priority: P2)

A non-blocking badge replaces the live coaching card stream on the Insights tab when thermal mode is active. It reads "Thermal mode — summary at session end" with an amber thermometer icon. It disappears when thermal mode deactivates.

**Why this priority**: Without feedback, the user thinks the app is broken. The badge provides just enough signal to explain the silence without interrupting the interview.

**Independent Test**: Simulate thermal mode → verify badge appears within 1 second. Drop thermal status → verify badge disappears within 2 seconds (after hysteresis).

**Acceptance Scenarios**:

1. **Given** thermal gate activates, **When** `MainUiState.thermalMode == true`, **Then** the badge is visible with the correct copy.
2. **Given** thermal gate deactivates (hysteresis satisfied), **When** `thermalMode == false`, **Then** badge disappears with a fade animation.
3. **Given** no diarization and no coaching cards yet, **When** thermal mode is active, **Then** badge is the only element visible in the insights area.

---

### Edge Cases

- What if thermal status oscillates rapidly (flapping)? Hysteresis: require two consecutive readings below `MODERATE` before re-enabling live inference.
- What if the LLM is already mid-inference when thermal status crosses the threshold? Let the in-flight call complete; suppress all subsequent calls.
- What if the session is stopped within 5 seconds of starting (very short session)? Flush is still attempted if any segments were buffered; LLM may return a thin or empty result — treat gracefully.
- What if the device reaches `THERMAL_STATUS_CRITICAL` or `THERMAL_STATUS_EMERGENCY`? Same behaviour as `MODERATE` — thermal gate is already active; no additional action needed beyond what US1 provides.
- What if `PowerManager` is unavailable (test environment / emulator without thermal HAL)? Default to `false` (gate inactive); log a debug warning.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `SyncSttLlmUseCase` MUST expose a thermal gate that suppresses reactive triggers and interval inference when device thermal status is `THERMAL_STATUS_MODERATE` or higher (API 29+).
- **FR-002**: All transcription segments received while the thermal gate is active MUST be buffered in memory for the duration of the session.
- **FR-003**: On recording stop, if any segments were buffered, `SyncSttLlmUseCase` MUST make exactly one LLM call with the full buffered transcript and emit the resulting insight.
- **FR-004**: The end-of-session insight MUST be stored in Room with `title = "Session Summary"` and a non-null `sessionId`.
- **FR-005**: Thermal gate MUST re-enable live inference automatically when thermal status drops below `MODERATE` for two consecutive polling intervals (hysteresis, prevents flapping).
- **FR-006**: On API level < 29, the thermal gate MUST always return `false` (feature is a no-op).
- **FR-007**: `MainUiState` MUST expose a `thermalMode: Boolean` flag that is `true` iff the thermal gate is currently active.
- **FR-008**: `InsightsSection` MUST display an amber thermal indicator badge when `thermalMode == true` and `isRecording == true`.
- **FR-009**: All thermal-related failures (flush LLM error, PowerManager unavailable) MUST be surfaced as a `snackbarMessage` and MUST NOT crash the app.

### Key Entities

- **ThermalGate**: `{ isActive: Boolean, consecutiveCoolReadings: Int }` — internal state in `SyncSttLlmUseCase`; not persisted.
- **BufferedSegments**: `List<TranscriptionSegmentWithSession>` — in-memory, cleared on session start and after flush.
- **ThermalStatus** (Android enum proxy): `NONE=0, LIGHT=1, MODERATE=2, SEVERE=3, CRITICAL=4, EMERGENCY=5` — sourced from `PowerManager` constants; the gate activates at ≥ 2.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero LLM inference calls fire during a simulated `THERMAL_STATUS_MODERATE` recording session (verified via unit test with mock `PowerManager`).
- **SC-002**: Exactly one LLM call fires on `stopStreaming()` when buffered segments are non-empty (verified via unit test).
- **SC-003**: Thermal indicator badge appears within 1 second of thermal gate activation on Lenovo Legion Y700 Gen 3.
- **SC-004**: No crash in any of the 5 edge cases listed above (manual smoke test).
- **SC-005**: On API < 29 device or emulator, thermal gate never activates — live inference behaviour is unchanged (verified via unit test with API level mock).

---

## Clarifications

### Session 2026-05-04

- Q: At which thermal threshold should the gate activate — LIGHT (level 1), MODERATE (level 2), or SEVERE (level 3)? → A: MODERATE (level 2) — conservative enough to prevent jank without being too aggressive; LIGHT is too hair-trigger.
- Q: Should thermal status be polled on a timer, registered as a listener, or checked only at inference-trigger time? → A: Check at inference-trigger time (lazy evaluation); also register `OnThermalStatusChangedListener` (API 29+) to update `MainUiState.thermalMode` in near-real-time for the badge.
- Q: Should the end-of-session flush use the Interview Coach prompt or a separate "session summary" prompt? → A: Re-use the existing Interview Coach prompt (`InterviewPromptBuilder`) with the full buffered transcript; no new prompt needed. Title is overridden to "Session Summary" in `toLlmInsight()`.
- Q: Should buffered segments be persisted to disk (survive process kill) or kept in memory only? → A: In-memory only; if the process is killed mid-session the session data is already partially lost via the normal transcription pipeline — thermal buffer follows the same contract.

---

## Assumptions

- `PowerManager.getCurrentThermalStatus()` and `addThermalStatusListener()` are available on API 29+. The primary test device (Lenovo Legion Y700 Gen 3) runs Android 13+ so this API is always present there.
- Thermal status polling via listener is low-overhead and does not itself contribute to thermal load.
- The existing `SyncSttLlmUseCase` buffer (`newSegmentsSinceLastLlm`) is distinct from the new `thermalBuffer` — the thermal buffer accumulates *all* segments since gate activation; the existing buffer is cleared after each LLM call.
- Cloud inference mode (spec 005) is out of scope — thermal protection applies to on-device inference only.
- This feature does not change the Room schema — `title = "Session Summary"` is stored in the existing `title` column.
