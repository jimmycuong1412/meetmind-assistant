# Data Model: Question Detection and Timely Suggestions

**Feature**: 004-question-detection
**Date**: 2026-04-14

---

## Entities

### AudioChunk

A raw PCM audio buffer segment captured from the microphone, passed through the VAD
pipeline before any transcription.

| Field | Type | Notes |
|-------|------|-------|
| `data` | `ByteArray` | 16kHz, 16-bit mono PCM; typical size 480–960 bytes (30–60ms) |
| `capturedAt` | `Long` | Unix timestamp (ms) of capture start |
| `vadState` | `VadState` | `SILENCE`, `SPEECH`, `BOUNDARY` |

**State transitions** (`vadState`):
```
SILENCE ──(speech detected by WebRTC VAD)──► SPEECH
SPEECH  ──(sentence boundary by Silero VAD)──► BOUNDARY
BOUNDARY ──(next chunk)──► SILENCE
```
Only chunks in `SPEECH` state are forwarded to the Vosk recogniser. A `BOUNDARY`
event triggers finalisation of the current Vosk hypothesis.

---

### TranscriptSegment

A confirmed (sentence-final) transcription result produced by Vosk when a VAD
boundary is detected.

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `UUID` | Generated on creation |
| `text` | `String` | Normalised: lowercase, leading/trailing whitespace stripped |
| `confidence` | `Float` | 0.0–1.0; from Vosk result |
| `startedAt` | `Long` | Unix timestamp (ms) of first audio in this segment |
| `finalisedAt` | `Long` | Unix timestamp (ms) when Vosk returned final result |
| `wordCount` | `Int` | Derived: `text.split(" ").size` |

**Validation rules**:
- `wordCount` ≥ 4 required before question classification is attempted
- `confidence` < 0.4 → segment is discarded without classification

---

### DetectedQuestion

A `TranscriptSegment` that has been classified as a question by the heuristic
classifier.

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `UUID` | Generated on creation |
| `segmentId` | `UUID` | FK → `TranscriptSegment.id` |
| `text` | `String` | Copy of `TranscriptSegment.text` |
| `detectedAt` | `Long` | Unix timestamp (ms) of classification |
| `classifierSignal` | `ClassifierSignal` | Which rule fired (see below) |

**ClassifierSignal** (enum):
- `INTERROGATIVE_WORD` — question starts with who/what/when/where/why/how/which
- `AUXILIARY_INVERSION` — starts with is/are/was/were/do/does/did/can/could/will/
  would/should/have/has/had followed by a noun or pronoun
- `TERMINAL_QUESTION_MARK` — ASR output contains trailing `?`
- `COMPOUND` — multiple signals fired

**Deduplication rule**: If a new `DetectedQuestion` is created within 5 seconds of
the previous one, the earlier one is superseded and its pending suggestion (if not
yet displayed) is cancelled.

---

### Suggestion

A generated response produced by the LLM in reaction to a `DetectedQuestion`,
incorporating the active `SessionMode` and `ContextProfile`.

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `UUID` | Generated on creation |
| `questionId` | `UUID` | FK → `DetectedQuestion.id` |
| `text` | `String` | 2–4 sentences; max 400 characters |
| `mode` | `SessionMode` | `MEETING` or `INTERVIEW` (snapshot at generation time) |
| `generatedAt` | `Long` | Unix timestamp (ms) when LLM returned result |
| `triggerType` | `TriggerType` | `QUESTION_DETECTED` (this feature) or `UTTERANCE_END` (spec 001 fallback) |
| `freshnessState` | `FreshnessState` | Derived; see below |

**FreshnessState** (derived, not stored):
```
FRESH   — (now - generatedAt) < 60,000ms
STALE   — (now - generatedAt) >= 60,000ms
```
Computed on each UI render tick; no database persistence needed.

---

### SessionMode (enum)

Selected by the user before each session. Not persisted across sessions.

| Value | Description |
|-------|-------------|
| `MEETING` | Suggestions framed as collaborative contributions: follow-up questions, action items, summaries |
| `INTERVIEW` | Suggestions framed as first-person candidate answers drawing on the user's profile |

---

### ContextProfile

The user's persistent identity used to ground LLM prompts. One instance per device.
(Defined in spec 003; referenced here for completeness.)

| Field | Type | Constraints |
|-------|------|-------------|
| `role` | `String` | Required; max 100 chars |
| `contextNote` | `String?` | Optional; max 1000 chars |
| `updatedAt` | `Long` | Unix timestamp (ms) of last save |

---

### Session

A single continuous listening period. Extended from spec 001 to carry `mode` and a
snapshot of the `ContextProfile` active at start time.

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `UUID` | Generated on creation |
| `mode` | `SessionMode` | Snapshot at session start |
| `profileRole` | `String` | Snapshot of `ContextProfile.role` at start |
| `profileContext` | `String?` | Snapshot of `ContextProfile.contextNote` at start |
| `startedAt` | `Long` | Unix timestamp (ms) |
| `endedAt` | `Long?` | Null while session is active |
| `suggestions` | `List<Suggestion>` | Ordered by `generatedAt` asc |

---

## Entity Relationships

```
Session 1 ──────────────────────── * Suggestion
                                          │
                                          │ questionId
                                          ▼
                                   DetectedQuestion
                                          │
                                          │ segmentId
                                          ▼
                                   TranscriptSegment
                                          │
                                          │ (produced from)
                                          ▼
                                      AudioChunk *
```

`ContextProfile` is a singleton — no foreign key; injected into the LLM prompt at
session start via the `Session.profileRole` / `Session.profileContext` snapshot.

---

## LLM Prompt Schema

The LLM receives a structured prompt assembled from the above entities. This is the
internal "contract" for the suggestion engine.

```
[SYSTEM]
You are a real-time suggestion assistant. The user is in a {mode} session.
User role: {session.profileRole}
Context: {session.profileContext ?? "not provided"}
Generate a concise, helpful suggestion (2–4 sentences) for the user to respond to
the question below. In INTERVIEW mode, write in first person. In MEETING mode,
write as a collaborative contribution.

[USER]
Question detected: "{detectedQuestion.text}"
```

**Token budget**: System prompt ≤200 tokens; question ≤50 tokens. Total input ≤250
tokens to keep prefill latency under 1s on Snapdragon 700.

---

## Storage

| Entity | Persistence | Store |
|--------|-------------|-------|
| `ContextProfile` | Permanent (survives reboot) | Room DB |
| `Session` | Duration of app process | In-memory only (v1) |
| `Suggestion` | Duration of session | In-memory only (v1) |
| `DetectedQuestion` | Duration of session | In-memory only (v1) |
| `TranscriptSegment` | Transient (pipeline only) | Not stored |
| `AudioChunk` | Transient (pipeline only) | Not stored |

Persisting session history to Room DB is deferred to a future spec.
