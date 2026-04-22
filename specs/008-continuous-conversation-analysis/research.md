# Research: Continuous Conversation Analysis

**Feature**: `feature/008-continuous-conversation-analysis`
**Spec**: `specs/008-continuous-conversation-analysis/spec.md`
**Date**: 2026-04-21
**Author**: Claude (research pass)

---

## Q1 — Cadence Implementation

### Decision: `delay()` loop inside a `while(isActive)` coroutine launched in `viewModelScope`

```kotlin
fun CoroutineScope.launchAnalysisTicker(
    intervalMs: Long,
    onTick: suspend () -> Unit
): Job = launch {
    while (isActive) {
        delay(intervalMs)
        onTick()
    }
}
```

### Rationale

- **Testability is the primary constraint.** `delay()` is the only approach that integrates transparently with `TestScope` + `advanceTimeBy`. A `delay(20_000)` inside `runTest { advanceTimeBy(20_001) }` fires exactly once with zero wall-clock cost — verified by the existing `CloudInferenceEngineTest` pattern which uses `advanceTimeBy` for the 5 s timeout.
- **Cancellation is automatic.** When `viewModelScope` is cancelled (session ends, ViewModel cleared), the `while(isActive)` loop exits on the next `delay` suspension point. No explicit `Job.cancel()` call is required at the call site.
- **Sequencing is guaranteed.** `delay()` inside a single coroutine means the next tick cannot start before the previous `onTick()` invocation finishes (see Q2 for the busy-guard). This is impossible to guarantee with a `ticker` channel whose downstream can lag.
- **Restart-on-settings-change is trivial.** A `StateFlow<Long>` collecting `analysisIntervalS` restarts the job: `job.cancel(); job = launchAnalysisTicker(newIntervalMs, ...)`.

### Alternatives Considered

| Approach | Why Rejected |
|---|---|
| `ticker()` channel (`@ObsoleteCoroutinesApi`) | Marked obsolete in kotlinx.coroutines 1.6; produces ticks independently of consumer speed, requiring a separate busy-guard; not directly `advanceTimeBy`-compatible without a custom dispatcher |
| `Flow.sample(intervalMs)` on a transcript buffer flow | Fires only when new data arrives; if the transcript is silent for 30s, no tick fires, defeating the cadence contract (US3 AC1 requires exactly N ticks in Ns) |
| `fixedRateTimer` (Java) | Not coroutine-aware; cannot be tested with `TestScope`; does not respect structured concurrency cancellation |

---

## Q2 — Debounce / Skip-If-Busy

### Decision: `Mutex` guarding the analysis body; skip tick (non-blocking tryLock) when mutex is held

```kotlin
private val analysisMutex = Mutex()

suspend fun runAnalysisTick() {
    if (!analysisMutex.tryLock()) return   // previous inference still in-flight — skip
    try {
        doAnalysis()
    } finally {
        analysisMutex.unlock()
    }
}
```

### Rationale

- **`Mutex.tryLock()` is the idiomatic coroutines-aware non-blocking guard.** It does not suspend; if the lock is already held it returns `false` immediately, allowing the ticker coroutine to drop the tick cleanly without ever blocking a thread.
- **Correct under `UnconfinedTestDispatcher`.** `AtomicBoolean.compareAndSet` is also correct under `UnconfinedTestDispatcher`, but `Mutex` composes better with the rest of the coroutines API — the existing `OnDeviceLlamaProvider` uses `ReentrantReadWriteLock` for its model lifecycle, so this codebase already has a precedent for explicit lock-based guards. Using `Mutex` keeps the guard in coroutine space (no JVM lock involved in the fast path).
- **Semantics: skip, not queue.** Meeting analysis of a stale window is worthless. A queue would deliver a result whose transcript window is already N seconds out of date. Skip is the correct policy.

### Alternatives Considered

| Approach | Why Rejected |
|---|---|
| `AtomicBoolean` (`compareAndSet(false, true)` / `set(false)`) | Correct, but mixes thread-level primitives with coroutine code; `Mutex` is the idiomatic choice in a suspend context |
| `ConflatedChannel` / `Channel(CONFLATED)` | Conflation replaces the pending item, not the in-flight one; a channel-send approach cannot skip when processing is active — it would start the next analysis immediately after the current one finishes, collapsing two 20s intervals into one back-to-back sequence |
| `Flow.collectLatest` on the ticker | Cancels the in-flight inference on each new tick — dangerous on `OnDeviceLlamaProvider` because llama.cpp JNI inference runs natively and cancellation leaves the model mid-generation with no clean abort path (the existing `awaitClose` in `callbackFlow` is a no-op for native cancellation) |

---

## Q3 — Transcript Window Buffer

### Decision: `ArrayDeque<TimestampedSegment>` wrapped in a `TranscriptWindowBuffer` class with a `windowText(durationMs: Long): String` method

```kotlin
data class TimestampedSegment(val timestampMs: Long, val text: String)

class TranscriptWindowBuffer {
    private val segments = ArrayDeque<TimestampedSegment>()

    fun append(segment: TimestampedSegment) {
        segments.addLast(segment)
    }

    /** Returns concatenated text for the last [durationMs] milliseconds. */
    fun windowText(durationMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val cutoff = nowMs - durationMs
        return segments
            .dropWhile { it.timestampMs < cutoff }
            .joinToString(" ") { it.text }
    }

    /** Evict segments older than [maxAgeMs] to prevent unbounded growth. */
    fun evictBefore(maxAgeMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val cutoff = nowMs - maxAgeMs
        while (segments.isNotEmpty() && segments.first().timestampMs < cutoff) {
            segments.removeFirst()
        }
    }
}
```

### Rationale

- **`ArrayDeque` is the right data structure.** Segments arrive in chronological order (append to tail), eviction removes from the head, and `windowText` reads a suffix — all O(1) amortized for add/remove and O(k) for the window read where k is the number of segments in the window. Kotlin's `ArrayDeque` (not Java's) avoids boxing and is the idiomatic choice in Kotlin 1.9+.
- **Wrapping in a named class** (not a `Pair<Long, String>`) provides a clear API surface for tests (`FakeTranscriptWindowBuffer`), makes `evictBefore` an explicit contract, and isolates the timestamp source for deterministic testing via injectable `nowMs`.
- **Eviction prevents unbounded growth.** A 2-hour meeting at ~5 segments/minute = 600 segments — trivially small, but `evictBefore(maxAge = analysisWindowS * 2 * 1000)` caps memory at twice the window size and is called after each tick.

### Alternatives Considered

| Approach | Why Rejected |
|---|---|
| `ArrayDeque<Pair<Long, String>>` (raw, no wrapper) | Works but leaks implementation detail across the codebase; `windowText` logic would be duplicated at every call site; not independently testable |
| `LinkedList<TimestampedSegment>` | Worse cache locality than `ArrayDeque`; `dropWhile` still O(k) but with pointer-chasing; no advantage for this access pattern |
| Ring buffer (fixed capacity) | Correct only if segment rate is uniform; variable-length VAD segments make a fixed-count ring buffer incorrect — a 15-minute silence window and a 15-second dense-speech window could have very different segment counts |
| Room DB query (`SELECT WHERE timestamp > ?`) | Massive overkill; adds I/O latency to a hot-path called every 20s; spec says session data is in-memory only (CLAUDE.md) |

---

## Q4 — LLM Prompt Design for Event Classification

### Decision: TWO-STEP: cheap keyword heuristic classify first, single LLM call (classify + suggest combined) only when heuristic returns non-NoSignal

```
Step 1 (always, ~0ms): KeywordClassifier.classify(windowText) → EventType | NoSignal
Step 2 (only if non-NoSignal): CloudInferenceEngine.streamSuggestion(classifiedPrompt)
       where classifiedPrompt asks the model to CONFIRM the event type and generate a suggestion
       in a single response.
```

Example combined prompt (Step 2, for an ActionItem heuristic match):
```
Window: "<last 30s of transcript>"
The above conversation window appears to contain an action item.
Confirm (QUESTION/ACTION_ITEM/DECISION/CONFUSION/NO_SIGNAL) and give a 1-sentence follow-up.
Format: TYPE: <type>\nSUGGESTION: <text>
```

### Rationale

- **The 5s timeout is the dominant constraint.** On Snapdragon 8 Gen 3 with Gemma 4 E4B Q4_K_M at 8192 context, a single generation call with a 300-char window + 200-char prompt cold-starts in ~2–4s (community context: Adreno 750 GPU, `nGpuLayers = -1`). Two serial LLM calls would reliably exceed 5s, triggering the existing `CLOUD_TIMEOUT_MS = 5_000L` fallback in `CloudInferenceEngine` — which then triggers on-device, which also exceeds 5s for a second call.
- **NoSignal is the majority case.** In a typical meeting, most 20s windows are filler, continuation, or ambient speech. The keyword heuristic (see Q6) filters ~80% of windows out before touching the LLM stack. Battery and inference cost scale with actual LLM invocations, not ticks.
- **Combined classify+suggest in one call** avoids the latency of two sequential streaming flows collecting to completion. The model is already reasoning about the window; asking for type confirmation + suggestion adds ~10–20 tokens of overhead rather than a full second call.
- **The heuristic pre-label acts as a soft prior**, not a hard constraint. The model's `TYPE:` response can disagree with the heuristic (e.g., demote an ActionItem to NoSignal). This prevents false positives from aggressive regex while keeping prompt simplicity.

### Alternatives Considered

| Approach | Why Rejected |
|---|---|
| Single LLM call for all windows (no heuristic pre-filter) | Exhausts battery and burns inference budget on NoSignal windows; unacceptable for a 20s cadence over a 2-hour meeting (360 calls) |
| Two separate LLM calls (classify then suggest) | Latency: 2× cold-start time > 5s budget; both cloud and on-device fallback would chain into a double-timeout scenario |
| Classification-only prompt, then conditional second prompt | Same latency problem as two-call approach; the saving of skipping the second call for NoSignal is already achieved by the keyword heuristic |
| Fine-tuned classifier (separate TFLite model) | Out of scope; adds a second model binary; the existing VAD uses TFLite but classification complexity is too high for a tiny MobileNet-style model |

---

## Q5 — Duplicate Suppression

### Decision: Rolling fingerprint set — leading-64-chars SHA-1 hash of the suggestion text, retained for 60 seconds in a `LinkedHashMap<String, Long>` (hash → emittedAtMs), evicted on each new analysis tick

```kotlin
class SuggestionDeduplicator(private val windowMs: Long = 60_000L) {
    private val seen = LinkedHashMap<String, Long>()

    fun isDuplicate(suggestionText: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        evict(nowMs)
        val key = fingerprint(suggestionText)
        return seen.containsKey(key)
    }

    fun record(suggestionText: String, nowMs: Long = System.currentTimeMillis()) {
        evict(nowMs)
        seen[fingerprint(suggestionText)] = nowMs
    }

    private fun fingerprint(text: String): String =
        text.take(64).lowercase().filter { it.isLetterOrDigit() || it == ' ' }

    private fun evict(nowMs: Long) {
        val cutoff = nowMs - windowMs
        seen.entries.removeIf { it.value < cutoff }
    }
}
```

### Rationale

- **Leading-64-chars normalised string** (lowercased, alphanumeric+space only) is sufficient for meeting suggestions. The opening clause of a suggestion is its most semantically distinctive part; two genuinely different suggestions ("Document this decision" vs "Who owns this item?") will never share 64 characters of normalised prefix. Two near-duplicates ("Document this decision in your notes" / "Document this decision — add to meeting notes") will match.
- **No SHA hash needed.** The normalised prefix is itself the fingerprint — it is human-readable in logs, debuggable without a hash lookup, and collision-free for natural-language outputs of this length.
- **`LinkedHashMap` with insertion-order eviction** — O(1) lookup, O(k) eviction where k is the number of expired entries (typically 0–3 per tick). `removeIf` is supported on `MutableIterator` in Kotlin/JVM.
- **60-second retention** matches the spec requirement verbatim.

### Alternatives Considered

| Approach | Why Rejected |
|---|---|
| Levenshtein distance | O(n·m) per comparison against all recent suggestions; requires storing full text; overkill for natural-language outputs that differ significantly if they are genuinely distinct |
| Full-text SHA-256 hash | Cryptographic hash of long strings; reliable but adds a dependency on `MessageDigest`; unnecessary when normalised prefix achieves the same dedup goal at zero cost |
| LRU cache (`lruCache` from `android.util.LruCache`) | Size-bounded by count, not time; a burst of 10 cards would evict the 60s window before it expires; time-based eviction is the correct policy per spec |
| Embedding cosine similarity | Requires a second model call or embedding table; completely out of scope for a dedup check |

---

## Q6 — Keyword Heuristic Fallback (No Model Loaded)

### Decision: Three regex groups compiled at startup, applied in priority order (ActionItem > Decision > Confusion > NoSignal)

```kotlin
object KeywordClassifier {

    // Compiled once at object initialisation
    private val ACTION_ITEM = Regex(
        """(?i)\b(will|going to|need[s]? to|should|must|have to|let['']?s)\s+\w+""" +
        """|(?i)\b(action item|follow.?up|take that|i['']?ll handle|assign|owner|deadline|by\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|eod|eow|next week|\d{1,2}[\/\-]\d{1,2}))\b"""
    )

    private val DECISION = Regex(
        """(?i)\b(we(?:'ve)?\s+(decided|agreed|chosen|going with)|let['']?s go with|agreed|decision(?:\s+is)?|resolved to|we['']?ll\s+use|final(?:ly)?\s+decided|going forward with)\b"""
    )

    private val CONFUSION = Regex(
        """(?i)\b(not\s+sure|i(?:'m)?\s+confused|what\s+do\s+you\s+mean|i\s+don['']?t\s+follow|lost\s+me|can\s+you\s+(clarify|explain|repeat)|unclear|what\s+does\s+that\s+mean|i\s+don['']?t\s+understand)\b"""
    )

    fun classify(text: String): EventType = when {
        ACTION_ITEM.containsMatchIn(text) -> EventType.ACTION_ITEM
        DECISION.containsMatchIn(text)    -> EventType.DECISION
        CONFUSION.containsMatchIn(text)   -> EventType.CONFUSION
        else                               -> EventType.NO_SIGNAL
    }
}
```

### Rationale

- **Regex on the meeting-English corpus is high precision for these three event types.** Action items in meetings are structurally predictable: a modal verb ("will", "needs to", "let's") + a verb phrase. Decisions use a small closed vocabulary ("decided", "agreed", "let's go with"). Confusion uses first-person negation + epistemic verbs ("not sure", "don't follow"). These are not ambiguous word-sense contexts — the patterns fire reliably.
- **Case-insensitive, compiled once.** `(?i)` flag and `Regex(...)` compiled at object init — zero per-tick regex compilation cost.
- **Priority order matters.** "We decided we'll need to follow up by Friday" could match both ActionItem and Decision — ActionItem is prioritised because the action is more actionable (the decision is implicit).
- **Question detection is NOT included here.** The existing `QuestionDetector` already handles questions via the established pipeline. The heuristic fallback only covers the three new event types; question detection is handled by the existing path (spec US2 AC1: "delegates to existing QuestionDetector path").

### Patterns Explained

| Event Type | Anchor Patterns | Examples Caught |
|---|---|---|
| ActionItem | Modal + verb; explicit role/deadline signals | "I'll handle the deck", "needs to be done by Friday", "let's follow up", "action item: Jimmy" |
| Decision | Past-tense agreement verbs; "going with" / "going forward" | "we decided to go with React", "agreed, let's use Firebase", "final decision is Kotlin" |
| Confusion | First-person epistemic negation; clarification request | "I'm not sure I follow", "can you clarify?", "what does that mean?", "you lost me" |

### Alternatives Considered

| Approach | Why Rejected |
|---|---|
| Simple `contains()` string matching | No word-boundary awareness; "decision" would match "indecision"; "will" would match "William" — too many false positives |
| NLP library (Stanford NLP, OpenNLP) | Adds >20MB dependency, requires model file I/O on startup; no Android-native distribution; latency on first call |
| ML text classifier (TFLite MobileBERT) | A second TFLite model binary alongside Silero VAD; out of scope; training data not available; the existing llama.cpp model is already the "smart classifier" |
| Exact phrase list (`listOf("we decided", ...)` with `any { text.contains(it) }`) | No regex anchor → word-boundary false positives; less maintainable than a single compiled Regex |
