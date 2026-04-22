# Data Model: Continuous Conversation Analysis (Feature 008)

**Feature Branch**: `feature/008-continuous-conversation-analysis`
**Created**: 2026-04-21
**Status**: Draft

---

## 1. Entity Definitions

### 1.1 `AnalysisEvent` (sealed class)

Represents the outcome of one analysis tick. Exactly one subtype is emitted per tick.

```
sealed class AnalysisEvent {
    object NoSignal : AnalysisEvent()

    data class Question(
        val text:           String,   // extracted question text, ≤ 600 chars
        val suggestionText: String    // LLM-generated suggestion, non-null
    ) : AnalysisEvent()

    data class ActionItem(
        val text:           String,        // extracted action text, ≤ 600 chars
        val suggestionText: String? = null // null when no model is loaded
    ) : AnalysisEvent()

    data class Decision(
        val text:           String,
        val suggestionText: String? = null
    ) : AnalysisEvent()

    data class Confusion(
        val text:           String,
        val suggestionText: String? = null
    ) : AnalysisEvent()
}
```

**Field validation rules**

| Field            | Type    | Rules                                                                                  |
|------------------|---------|----------------------------------------------------------------------------------------|
| `text`           | String  | 1–600 chars (truncated before inference); must not be blank                            |
| `suggestionText` | String? | null only when no on-device model is loaded and cloud is disabled; ≤ 150 tokens output |

**Persistence**: in-memory only — `AnalysisEvent` is never written to Room or DataStore.

---

### 1.2 `EventType` (enum)

Used internally by the classifier to rank concurrent signals before constructing an `AnalysisEvent`.

```
enum class EventType {
    QUESTION,     // ordinal 0 — lowest classifier priority (delegates to existing path)
    ACTION_ITEM,  // ordinal 1
    DECISION,     // ordinal 2
    CONFUSION;    // ordinal 3 — highest classifier priority

    companion object {
        /** Higher ordinal wins when multiple types are detected in the same window. */
        fun highestPriority(types: Set<EventType>): EventType =
            types.maxBy { it.ordinal }
    }
}
```

**Priority order** (highest → lowest for tie-breaking):
`CONFUSION` > `DECISION` > `ACTION_ITEM` > `QUESTION`

Only one `EventType` is promoted per tick; exactly one `AnalysisEvent` subtype is emitted.

**Persistence**: in-memory only.

---

### 1.3 `AnalysisSettings` (data class)

User-configurable parameters for the analysis cadence and transcript window. All three fields are
persisted to DataStore Preferences.

```
data class AnalysisSettings(
    val analysisEnabled:  Boolean = true,
    val analysisIntervalS: Int    = 20,
    val analysisWindowS:   Int    = 60
)
```

**Field validation rules**

| Field              | Type    | Default | Min | Max | DataStore key            |
|--------------------|---------|---------|-----|-----|--------------------------|
| `analysisEnabled`  | Boolean | `true`  | —   | —   | `analysis_enabled`       |
| `analysisIntervalS`| Int     | `20`    | 10  | 120 | `analysis_interval_s`    |
| `analysisWindowS`  | Int     | `60`    | 15  | 300 | `analysis_window_s`      |

- Values written outside the declared range MUST be clamped before persistence.
- `AnalysisCadenceController` enforces a hard floor of 10 s regardless of the stored value
  (FR-007), so even if `analysis_interval_s` is somehow stored below 10, the controller will not
  fire faster than 10 s.

**Persistence**: DataStore Preferences, key prefix `analysis_`. Written by
`AnalysisSettingsRepository` (analogous to `ModelConfigRepository` from spec 007). Read as a
`Flow<AnalysisSettings>` by `SessionViewModel`.

---

### 1.4 `TimestampedSegment` (data class)

One ASR segment produced by the VAD/Vosk pipeline.

```
data class TimestampedSegment(
    val timestampMs: Long,   // epoch ms when the segment was finalized
    val text:        String  // raw ASR text for this segment; may be empty (silence)
)
```

**Field validation rules**

| Field         | Type   | Rules                                         |
|---------------|--------|-----------------------------------------------|
| `timestampMs` | Long   | > 0; monotonically increasing within a session|
| `text`        | String | may be empty string (silence/filler segment)  |

**Persistence**: in-memory only. Segments are held in `TranscriptWindowBuffer` and discarded when
they fall outside the active window.

---

### 1.5 `TranscriptWindowBuffer` (class)

A rolling deque of `TimestampedSegment` objects trimmed to the configured window size.

```
class TranscriptWindowBuffer(private val windowSizeMs: Long) {

    // Internal: ArrayDeque<TimestampedSegment> — append-only from ASR, trim from head
    fun append(segment: TimestampedSegment)

    /**
     * Returns concatenated text of all segments whose timestampMs is
     * >= (now - windowSizeMs), truncated to 600 chars (FR-004).
     */
    fun windowText(windowSizeMs: Long = this.windowSizeMs): String

    fun clear()
}
```

**Invariants**

- Segments older than `windowSizeMs` are removed on every `append()` call (eager trim).
- `windowText()` truncates the concatenated result to 600 chars before returning (data minimisation,
  FR-004). Characters are taken from the *end* of the string (most-recent speech) so the latest
  context is preserved.
- `windowSizeMs` is derived from `AnalysisSettings.analysisWindowS * 1_000L`.

**Persistence**: in-memory only.

---

### 1.6 `ConversationAnalyzer` (interface)

Injectable contract for the analysis pipeline. The production implementation delegates to the
existing inference stack; `FakeConversationAnalyzer` is the test double.

```
interface ConversationAnalyzer {
    /**
     * Classifies [windowText] and streams exactly one AnalysisEvent.
     * Implementations MUST complete the flow (not leave it open-ended).
     * windowText is guaranteed ≤ 600 chars by the caller.
     */
    fun analyze(windowText: String): Flow<AnalysisEvent>
}
```

**Contracts**

- Returns `AnalysisEvent.NoSignal` immediately when `windowText` is blank or contains only filler
  tokens ("um", "uh", etc.) — no inference call is made.
- When `EventType.QUESTION` is classified, delegates to the existing
  `CloudInferenceEngine.streamSuggestion()` path (FR-005).
- When no on-device model is loaded and cloud is disabled, uses keyword-matching heuristic and
  returns an `AnalysisEvent` with `suggestionText = null` (FR-014).
- The returned `Flow` emits exactly one terminal `AnalysisEvent` and then completes.

---

### 1.7 `AnalysisCadenceController` (class)

Owns the repeating-ticker coroutine that drives analysis. Injectable `CoroutineDispatcher` for
tests.

```
class AnalysisCadenceController(
    private val analyzer:   ConversationAnalyzer,
    private val buffer:     TranscriptWindowBuffer,
    private val settings:   AnalysisSettings,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    fun start(scope: CoroutineScope)
    fun stop()
}
```

**Invariants**

- Interval used = `max(settings.analysisIntervalS, 10)` seconds (FR-007).
- A debounce guard (boolean flag) prevents a new tick from launching while the prior
  `analyzer.analyze()` call is still in-flight (FR-006). The skipped tick is logged but not
  retried.
- `stop()` cancels the ticker job; any in-flight `analyze()` call is also cancelled via scope
  cancellation (FR-015).
- Events emitted by `analyze()` are forwarded to `SessionViewModel` via a `MutableSharedFlow`
  injected at construction.

---

## 2. Relationships

```
AnalysisSettings ──────────────────────────────► AnalysisCadenceController
    (interval, window, enabled flag)                  (reads settings at start)

AnalysisCadenceController ────── owns ──────────► ticker coroutine
        │
        │ on each tick
        ▼
TranscriptWindowBuffer ── windowText() ──────────► ConversationAnalyzer.analyze()
                                                        │
                                                        │ classifies via
                                                        ▼
                                                    EventType (enum)
                                                        │
                                                        │ maps to
                                                        ▼
                                                    AnalysisEvent (sealed)
                                                        │
                                                        │ emitted to
                                                        ▼
                                                   SessionViewModel
                                               analysisEvents: StateFlow<AnalysisEvent?>
                                                        │
                                                        │ observed by
                                                        ▼
                                                   SessionScreen
                                              (type-labelled suggestion card)
```

**ASR pipeline feed**

```
VAD / Vosk ASR ──► TimestampedSegment ──► TranscriptWindowBuffer.append()
```

The existing question-detection path reads from the same `TranscriptWindowBuffer` but via a
separate consumer; the two paths do not share a coroutine job.

---

## 3. State Transition Diagram

```
                    ┌─────────────────────────────────────────────────────┐
                    │                  SESSION LIFECYCLE                  │
                    └─────────────────────────────────────────────────────┘

  ┌──────────┐  session start +        ┌──────────────────┐
  │  IDLE    │  analysisEnabled=true   │  CADENCE_RUNNING  │
  │          │ ───────────────────────►│                   │
  └──────────┘                         └──────────────────┘
       ▲                                       │  │
       │  session end /                        │  │  tick fires (every analysisIntervalS)
       │  analysisEnabled=false                │  │
       │  (CadenceController.stop())           │  ▼
       │                                ┌──────────────────┐
       │                                │  ANALYSIS_TICK    │◄──── prior tick in-flight?
       │                                │                   │      skip + stay in CADENCE
       │                                └──────────────────┘
       │                                       │
       │                      windowText blank?│
       │                  ┌────────────────────┴────────────────────────┐
       │                  │ YES                                          │ NO
       │                  ▼                                             ▼
       │        ┌──────────────────┐                      ┌─────────────────────────┐
       │        │  EMIT_NO_SIGNAL  │                      │  INFERENCE_IN_FLIGHT     │
       │        │  (no LLM call)   │                      │  (analyzer.analyze())    │
       │        └──────────────────┘                      └─────────────────────────┘
       │                  │                                             │
       │                  │                         ┌───────────────────┤
       │                  │                         │                   │
       │                  │                 success ▼          timeout / error ▼
       │                  │        ┌──────────────────────┐   ┌──────────────────────┐
       │                  │        │  EMIT_ANALYSIS_EVENT  │   │  EMIT_NO_SIGNAL /    │
       │                  │        │  (Question|ActionItem │   │  fallback heuristic  │
       │                  │        │   Decision|Confusion) │   └──────────────────────┘
       │                  │        └──────────────────────┘             │
       │                  │                   │                         │
       │                  └───────────────────┴─────────────────────────┤
       │                                                                 │
       │                              deduplicate?                       │
       │                  ┌──────────────────────────────────────────────┤
       │         YES      │                                    NO        │
       │   ◄──────────────┘                                             │
       │   (card suppressed)                                            │
       │                                                                ▼
       │                                                   ┌──────────────────────┐
       │                                                   │  UPDATE_SESSION_UI    │
       │                                                   │  (StateFlow update)   │
       │                                                   └──────────────────────┘
       │                                                                │
       │                             return to CADENCE_RUNNING ─────────┘
       │
       └──── on session end: CadenceController.stop() cancels ticker + in-flight job
```

---

## 4. Persistence vs. In-Memory Summary

| Entity / Field                          | Storage          | Key / Table                  | Notes                                          |
|-----------------------------------------|------------------|------------------------------|------------------------------------------------|
| `AnalysisSettings.analysisEnabled`      | DataStore Prefs  | `analysis_enabled`           | Boolean; default `true`                        |
| `AnalysisSettings.analysisIntervalS`    | DataStore Prefs  | `analysis_interval_s`        | Int; clamped 10–120 before write               |
| `AnalysisSettings.analysisWindowS`      | DataStore Prefs  | `analysis_window_s`          | Int; clamped 15–300 before write               |
| `TimestampedSegment`                    | In-memory        | —                            | Held in `TranscriptWindowBuffer` deque         |
| `TranscriptWindowBuffer`                | In-memory        | —                            | Cleared on `SessionViewModel.onCleared()`      |
| `AnalysisEvent` (any subtype)           | In-memory        | —                            | Exposed via `StateFlow`; never persisted       |
| `EventType`                             | In-memory        | —                            | Intermediate classifier output; discarded      |
| Deduplication fingerprints (80-char)    | In-memory        | —                            | Bounded ring buffer, last 60 s of fingerprints |

Room DB is **not** extended by this feature. The existing `ContextProfile` table (spec 004) and
`CloudProviderConfig` ciphertext (spec 005) are unchanged.

---

## 5. Deduplication Rule

A new `AnalysisEvent` is suppressed if all of the following hold:

1. The event subtype is the same as a card displayed in the last 60 seconds.
2. The leading 80 characters of the new `event.text` match (case-insensitive, trimmed) the
   leading 80 characters of the previously-shown card's `text`.

The fingerprint ring buffer holds at most `ceil(60 / minInterval)` = 6 entries (at the 10 s
minimum cadence). Entries expire after 60 seconds wall-clock time.

---

## 6. Interface Boundaries and Injection Points

| Component                  | Interface / Type               | Production impl                          | Test double                  |
|----------------------------|--------------------------------|------------------------------------------|------------------------------|
| `ConversationAnalyzer`     | `ConversationAnalyzer`         | `CloudConversationAnalyzer`              | `FakeConversationAnalyzer`   |
| `AnalysisCadenceController`| `AnalysisCadenceController`    | same class, `Dispatchers.Default`        | same class, `TestDispatcher` |
| DataStore access           | `Flow<AnalysisSettings>`       | `AnalysisSettingsRepository`             | in-memory `StateFlow` stub   |
| Inference (suggestion gen) | `CloudInferenceEngine` (reuse) | existing production impl from spec 005   | existing `FakeInferenceEngine`|

---

## 7. Cross-Feature Notes

- **Spec 004 (Question Detection)**: `TranscriptWindowBuffer` extends the concept of
  `TranscriptBuffer` from spec 004 by adding timestamp-keyed trimming. Both paths read the same
  buffer; question-detection retains its own signal path and takes display priority (FR-010).
- **Spec 005 (Cloud AI Inference)**: `ConversationAnalyzer` reuses `CloudInferenceEngine` for
  suggestion generation when the cloud provider is configured. `FallbackReason` from spec 005 is
  reused as-is for cloud-timeout handling; no new enum values are added.
- **Spec 007 (On-Device Gemma 4)**: `ConversationAnalyzer` calls `OnDeviceLlamaProvider` when
  cloud is unavailable. The 600-char input truncation rule from spec 007 applies unchanged.
  `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` unloads the model and causes the next analysis tick
  to use keyword-matching heuristic (FR-014).
