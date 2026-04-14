# Contract: Audio Processing Pipeline

**Feature**: 004-question-detection
**Date**: 2026-04-14

This document describes the internal component contracts for the on-device audio
processing pipeline. These are module boundaries, not external APIs.

---

## AudioCaptureSource

**Responsibility**: Continuously capture raw PCM audio from the microphone.

**Output contract**:
- Emits `AudioChunk` objects on a dedicated audio thread
- Format: 16kHz sample rate, 16-bit PCM, mono channel
- Chunk size: 30ms of audio (480 samples = 960 bytes) for VAD alignment
- Buffer size: 2× minimum buffer size as reported by the platform
- Emits `CaptureEvent.STARTED` / `CaptureEvent.STOPPED` lifecycle events

**Error contract**:
- If microphone permission is not granted: emits `CaptureError.PERMISSION_DENIED`
  and stops without crashing
- If another app holds the microphone exclusively: emits `CaptureError.RESOURCE_BUSY`
  and retries up to 3 times with 500ms backoff

---

## VadPipeline

**Responsibility**: Classify each `AudioChunk` with a `VadState` and detect sentence
boundaries.

**Input**: `AudioChunk` stream from `AudioCaptureSource`

**Output contract**:
- Returns each `AudioChunk` with its `vadState` field populated
- Emits `SentenceBoundaryEvent` when `Silero VAD` detects a sentence-final pause
  following a `SPEECH` segment

**Stage 1 — WebRTC VAD gate**:
- Input: raw `AudioChunk`
- Output: `SILENCE` (stop forwarding) or `SPEECH` (forward to Stage 2)
- Aggressiveness level: 2 (balanced false-positive / false-negative)

**Stage 2 — Silero VAD boundary detector**:
- Input: `AudioChunk` in `SPEECH` state
- Output: `SPEECH` (continue) or `BOUNDARY` (sentence ended)
- State: carries hidden-state tensor across consecutive calls (reset on each
  `SentenceBoundaryEvent`)

---

## StreamingRecogniser

**Responsibility**: Convert a stream of `SPEECH` audio chunks to a confirmed
`TranscriptSegment` when a `SentenceBoundaryEvent` is received.

**Input**: `AudioChunk` (state = `SPEECH`) + `SentenceBoundaryEvent` trigger

**Output contract**:
- On `SentenceBoundaryEvent`: returns a `TranscriptSegment` with `text`,
  `confidence`, `startedAt`, `finalisedAt`
- Partial results are available as `PartialTranscript(text: String)` events for
  optional display (not used by question detector)
- Resets internal state immediately after emitting a final `TranscriptSegment`

**Rejection contract**:
- Segments with `wordCount < 4` are emitted but flagged as `TOO_SHORT`
- Segments with `confidence < 0.4` are emitted but flagged as `LOW_CONFIDENCE`
- The question classifier MUST NOT process flagged segments

---

## QuestionClassifier

**Responsibility**: Classify a `TranscriptSegment` as a question or non-question
using rule-based heuristics.

**Input**: `TranscriptSegment` (not flagged as `TOO_SHORT` or `LOW_CONFIDENCE`)

**Output contract**:
- Returns `ClassificationResult.QUESTION(signal: ClassifierSignal)` or
  `ClassificationResult.NOT_QUESTION`
- Latency: <1ms per segment (synchronous, no I/O)

**Rules applied in order** (first match wins):
1. Terminal `?` in `text` → `TERMINAL_QUESTION_MARK`
2. First word (lowercase) in {what, where, when, why, who, how, which, whose, whom}
   → `INTERROGATIVE_WORD`
3. First word (lowercase) in {is, are, was, were, do, does, did, can, could, will,
   would, should, have, has, had} AND second token is a pronoun or noun →
   `AUXILIARY_INVERSION`
4. No match → `NOT_QUESTION`

**Deduplication**: If a `QUESTION` result is produced within 5 seconds of the
previous `QUESTION` result, the classifier emits `ClassificationResult.SUPERSEDED`
and the pipeline discards the earlier pending suggestion.

---

## SuggestionEngine

**Responsibility**: Generate a contextual suggestion for a `DetectedQuestion` using
the on-device LLM (llama.cpp + Phi-3-mini Q4_K_M).

**Input**:
- `DetectedQuestion`
- `Session` (contains `mode`, `profileRole`, `profileContext` snapshot)

**Output contract**:
- Returns `Suggestion` within the latency SLA (target ≤2.5s on mid-range device)
- If generation exceeds 3.5s: returns a `Suggestion` with `text = TIMEOUT_PLACEHOLDER`
  and `triggerType = TIMEOUT`; the UI renders a "suggestion unavailable" state
- Text: 2–4 sentences, max 400 characters

**Prompt constraints** (enforced by the engine, not the caller):
- System prompt: ≤200 tokens
- Question input: ≤50 tokens (truncated if longer)
- Max new tokens: 120 (sufficient for 4 sentences; limits generation time)
- Temperature: 0.7 (consistent, slightly creative but not random)

**Model lifecycle**:
- Model is loaded once at `AudioProcessingForegroundService.onCreate()`
- Model is NOT reloaded between suggestions
- Model is unloaded at `AudioProcessingForegroundService.onDestroy()`
- If model file is absent (first launch, incomplete download): engine emits
  `EngineError.MODEL_NOT_READY` and the suggestion layer shows a "downloading model"
  state

---

## OverlayRenderer

**Responsibility**: Display `Suggestion` objects in the overlay or full-screen view.

**Input**: `Suggestion` events from `SuggestionEngine`

**Display contract**:
- New `Suggestion` is inserted at the top of the suggestion list
- Maximum visible suggestions: 5 (older ones scroll off but remain accessible)
- `FreshnessState.FRESH` → suggestion card rendered with active/highlighted style
- `FreshnessState.STALE` → suggestion card rendered with muted/dimmed style
- Freshness state is recomputed every 10 seconds by a UI ticker

**Floating mode constraints**:
- Floating window shows the most recent suggestion only (single card)
- Tapping the floating window returns to full-screen (spec 002)
- Floating window never shows more than 3 lines of text; overflow is truncated with
  ellipsis and a "tap to expand" affordance
