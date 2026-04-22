# Feature 008 — Continuous Conversation Analysis: Interface Contracts

**Branch**: `feature/008-continuous-conversation-analysis`
**Date**: 2026-04-21
**Status**: Draft
**Scope**: JVM-level contracts, testable without a physical device or native library.
All fakes listed here replace Android/JNI dependencies so every contract runs under
`./gradlew testDebugUnitTest` on the host JVM.

---

## Contract Group 1 — `ConversationAnalyzer.analyze()`

`ConversationAnalyzer` accepts a `windowText: String` (already trimmed to `windowSizeSeconds`
by the caller) and returns a `Flow<AnalysisEvent>`. All contracts below treat the returned flow
as collected synchronously inside a `runTest` block.

---

### C1.1 — Blank window text → NoSignal, no inference call

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C1.1 |
| **Description** | When `windowText` is blank (empty, whitespace-only, or filler tokens such as "um", "uh"), `analyze()` emits `AnalysisEvent.NoSignal` immediately and makes zero calls to any inference engine. |
| **Test input**  | `windowText = ""` (also repeat with `"   "`, `"um uh um"`) |
| **Expected output** | Single emission: `AnalysisEvent.NoSignal`. `FakeInferenceEngine.callCount == 0`. |
| **Test double needed** | `FakeInferenceEngine` — records every call to `streamSuggestion()` via an `AtomicInteger` counter; throws `AssertionError` if unexpectedly invoked. |

```kotlin
@Test
fun `C1_1 blank window emits NoSignal and calls no inference`() = runTest {
    val fake = FakeInferenceEngine()
    val analyzer = ConversationAnalyzer(inferenceEngine = fake)
    val events = analyzer.analyze("   ").toList()
    assertEquals(listOf(AnalysisEvent.NoSignal), events)
    assertEquals(0, fake.callCount)
}
```

---

### C1.2 — Question pattern → EventType.QUESTION, delegates to CloudInferenceEngine

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C1.2 |
| **Description** | When `windowText` contains an interrogative pattern (ends with "?", starts with a wh-word, or contains a rising-intonation marker), `analyze()` classifies as `EventType.QUESTION` and delegates to `CloudInferenceEngine.streamSuggestion()` exactly once. The emitted event is `AnalysisEvent.Question`. |
| **Test input**  | `windowText = "What is the deadline for this deliverable?"` |
| **Expected output** | First emission: `AnalysisEvent.Question(text = "What is the deadline for this deliverable?", suggestionText = <non-null stub from fake>)`. `FakeInferenceEngine.callCount == 1`. |
| **Test double needed** | `FakeInferenceEngine` — configured to emit a stub `InferenceEvent.Token("stub answer")` then `InferenceEvent.Complete`. |

```kotlin
@Test
fun `C1_2 question pattern delegates to cloud engine`() = runTest {
    val fake = FakeInferenceEngine(stubTokens = listOf("stub answer"))
    val analyzer = ConversationAnalyzer(inferenceEngine = fake)
    val event = analyzer.analyze("What is the deadline for this deliverable?").first()
    assertIs<AnalysisEvent.Question>(event)
    assertEquals(1, fake.callCount)
}
```

---

### C1.3 — Action item keywords → EventType.ACTION_ITEM, suggestionText non-null when model loaded

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C1.3 |
| **Description** | When `windowText` contains action-item patterns ("will do", "needs to", "by [date]", "let's make sure"), `analyze()` emits `AnalysisEvent.ActionItem` with `suggestionText != null` provided the fake engine is configured to return a token stream. |
| **Test input**  | `windowText = "Alice will do the architecture review by Friday"` |
| **Expected output** | Emission: `AnalysisEvent.ActionItem(text = "Alice will do the architecture review by Friday", suggestionText = "Who confirms? What is the exact deadline?")`. `suggestionText != null`. |
| **Test double needed** | `FakeInferenceEngine` configured with stub suggestion tokens. |

```kotlin
@Test
fun `C1_3 action item emits ActionItem with non-null suggestion when engine loaded`() = runTest {
    val fake = FakeInferenceEngine(stubTokens = listOf("Who confirms? What is the exact deadline?"))
    val analyzer = ConversationAnalyzer(inferenceEngine = fake)
    val event = analyzer.analyze("Alice will do the architecture review by Friday").first()
    assertIs<AnalysisEvent.ActionItem>(event)
    assertNotNull((event as AnalysisEvent.ActionItem).suggestionText)
}
```

---

### C1.4 — Decision keywords → EventType.DECISION

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C1.4 |
| **Description** | When `windowText` contains decision-signal patterns ("we decided", "let's go with", "agreed", "we're going to use"), `analyze()` emits `AnalysisEvent.Decision`. |
| **Test input**  | `windowText = "We decided to go with the React approach for the frontend"` |
| **Expected output** | Emission: `AnalysisEvent.Decision(text = ..., suggestionText = ...)`. `EventType.DECISION` is classified. |
| **Test double needed** | `FakeInferenceEngine` with any non-empty stub tokens. |

```kotlin
@Test
fun `C1_4 decision keywords produce Decision event`() = runTest {
    val fake = FakeInferenceEngine(stubTokens = listOf("Document this decision."))
    val analyzer = ConversationAnalyzer(inferenceEngine = fake)
    val event = analyzer.analyze("We decided to go with the React approach for the frontend").first()
    assertIs<AnalysisEvent.Decision>(event)
}
```

---

### C1.5 — Confusion keywords → EventType.CONFUSION

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C1.5 |
| **Description** | When `windowText` contains confusion-signal phrases ("I'm confused", "what does that mean", "not sure I follow", "could you clarify"), `analyze()` emits `AnalysisEvent.Confusion`. |
| **Test input**  | `windowText = "I'm not sure I follow — what does that mean exactly?"` |
| **Expected output** | Emission: `AnalysisEvent.Confusion(text = ..., suggestionText = ...)`. |
| **Test double needed** | `FakeInferenceEngine` with stub suggestion tokens. |

```kotlin
@Test
fun `C1_5 confusion keywords produce Confusion event`() = runTest {
    val fake = FakeInferenceEngine(stubTokens = listOf("Try rephrasing with an example."))
    val analyzer = ConversationAnalyzer(inferenceEngine = fake)
    val event = analyzer.analyze("I'm not sure I follow — what does that mean exactly?").first()
    assertIs<AnalysisEvent.Confusion>(event)
}
```

---

### C1.6 — windowText > 600 chars → truncated to 600 before inference call

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C1.6 |
| **Description** | When `windowText` exceeds 600 characters, the text passed to the inference engine MUST be capped at exactly 600 characters. The event's `text` field retains only the truncated value. This enforces FR-004 and aligns with `CloudInferenceEngine.MAX_QUESTION_CHARS = 600`. |
| **Test input**  | `windowText = "a".repeat(900)` (contains no keywords → falls through to default classification) or a 700-char string containing an action-item phrase. |
| **Expected output** | `FakeInferenceEngine.lastReceivedText.length == 600`. No exception thrown. |
| **Test double needed** | `FakeInferenceEngine` that captures the exact `questionText` / `windowText` passed to `streamSuggestion()` in a `var lastReceivedText: String`. |

```kotlin
@Test
fun `C1_6 text over 600 chars is truncated before inference`() = runTest {
    val longInput = "we decided " + "x".repeat(650)  // triggers DECISION path
    val fake = FakeInferenceEngine(stubTokens = listOf("ok"))
    val analyzer = ConversationAnalyzer(inferenceEngine = fake)
    analyzer.analyze(longInput).toList()
    assertTrue(fake.lastReceivedText.length <= 600)
}
```

---

## Contract Group 2 — `AnalysisCadenceController`

`AnalysisCadenceController` owns the repeating ticker that calls `ConversationAnalyzer.analyze()`
on a configured interval. All contracts use `TestScope(UnconfinedTestDispatcher())` and
`advanceTimeBy()` to control virtual time without real delays.

---

### C2.1 — interval = 15s, session 45s → analyze() called exactly 3 times

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C2.1 |
| **Description** | With `analysisIntervalMs = 15_000` and a session running for 45 seconds of virtual time, `AnalysisCadenceController` fires `analyze()` exactly 3 times (at t=15s, t=30s, t=45s). |
| **Test input**  | `intervalMs = 15_000`. `advanceTimeBy(45_001)` in `TestScope`. |
| **Expected output** | `FakeConversationAnalyzer.analyzeCallCount == 3`. |
| **Test double needed** | `FakeConversationAnalyzer` — counts each call to `analyze()`; returns `flowOf(AnalysisEvent.NoSignal)` immediately. |

```kotlin
@Test
fun `C2_1 exactly 3 ticks in 45s with 15s interval`() = runTest {
    val fake = FakeConversationAnalyzer()
    val controller = AnalysisCadenceController(
        analyzer = fake,
        intervalMs = 15_000L,
        dispatcher = UnconfinedTestDispatcher(testScheduler)
    )
    controller.start(windowTextProvider = { "some text with action item" })
    advanceTimeBy(45_001)
    controller.stop()
    assertEquals(3, fake.analyzeCallCount)
}
```

---

### C2.2 — Prior inference in-flight (Mutex locked) → tick skipped, no duplicate call

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C2.2 |
| **Description** | If a prior `analyze()` call is still suspended (Mutex held) when the next tick fires, the controller skips that tick. The total call count is lower than the theoretical maximum. |
| **Test input**  | `intervalMs = 10_000`. `FakeConversationAnalyzer` configured to suspend for 15s before emitting. `advanceTimeBy(30_001)` — without debounce, 3 ticks would fire; with debounce, only 2 should. |
| **Expected output** | `fake.analyzeCallCount == 2` (tick at t=10s completes at t=25s; tick at t=20s is skipped because t=10s is still in-flight; tick at t=30s runs). |
| **Test double needed** | `FakeConversationAnalyzer` that delays emission by a configurable `delayMs` using `kotlinx.coroutines.delay` inside the returned flow. |

```kotlin
@Test
fun `C2_2 in-flight inference causes tick skip`() = runTest {
    val fake = FakeConversationAnalyzer(delayMs = 15_000L)
    val controller = AnalysisCadenceController(
        analyzer = fake,
        intervalMs = 10_000L,
        dispatcher = UnconfinedTestDispatcher(testScheduler)
    )
    controller.start(windowTextProvider = { "we decided something" })
    advanceTimeBy(30_001)
    controller.stop()
    assertEquals(2, fake.analyzeCallCount)
}
```

---

### C2.3 — stop() called → no further ticks

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C2.3 |
| **Description** | After `controller.stop()` is called, no additional `analyze()` invocations occur regardless of how much virtual time elapses. |
| **Test input**  | `intervalMs = 10_000`. Start, advance 15s (1 tick fires), call `stop()`, advance another 60s. |
| **Expected output** | `fake.analyzeCallCount == 1` (the single tick before stop). |
| **Test double needed** | `FakeConversationAnalyzer` (no delay needed). |

```kotlin
@Test
fun `C2_3 stop prevents further ticks`() = runTest {
    val fake = FakeConversationAnalyzer()
    val controller = AnalysisCadenceController(
        analyzer = fake,
        intervalMs = 10_000L,
        dispatcher = UnconfinedTestDispatcher(testScheduler)
    )
    controller.start(windowTextProvider = { "we agreed on the timeline" })
    advanceTimeBy(15_001)
    controller.stop()
    advanceTimeBy(60_000)
    assertEquals(1, fake.analyzeCallCount)
}
```

---

### C2.4 — Minimum cadence: interval < 10s clamped to 10s

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C2.4 |
| **Description** | When `analysisIntervalMs` is configured below 10,000 ms, `AnalysisCadenceController` silently clamps it to 10,000 ms. A 25-second session should yield at most 2 ticks (at t=10s and t=20s), not 5. |
| **Test input**  | `intervalMs = 3_000` (user-supplied value below minimum). `advanceTimeBy(25_001)`. |
| **Expected output** | `fake.analyzeCallCount == 2` (evidence that effective interval was 10s, not 3s). |
| **Test double needed** | `FakeConversationAnalyzer` (no delay). |

```kotlin
@Test
fun `C2_4 sub-10s interval clamped to 10s`() = runTest {
    val fake = FakeConversationAnalyzer()
    val controller = AnalysisCadenceController(
        analyzer = fake,
        intervalMs = 3_000L,  // below minimum
        dispatcher = UnconfinedTestDispatcher(testScheduler)
    )
    controller.start(windowTextProvider = { "let's make sure we finish by Friday" })
    advanceTimeBy(25_001)
    controller.stop()
    assertEquals(2, fake.analyzeCallCount)
}
```

---

## Contract Group 3 — `SessionViewModel` Analysis Integration

All tests use `TestScope` with `UnconfinedTestDispatcher` and a `FakeConversationAnalyzer`.
`SessionViewModel` is constructed with its real implementation; only collaborators are faked.

---

### C3.1 — FakeConversationAnalyzer emits ActionItem → analysisEvents StateFlow updated

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C3.1 |
| **Description** | When a cadence tick fires and `FakeConversationAnalyzer` emits `AnalysisEvent.ActionItem`, `SessionViewModel.analysisEvents` StateFlow is updated to hold that event. |
| **Test input**  | `FakeConversationAnalyzer` emits `AnalysisEvent.ActionItem(text = "finish report by Friday", suggestionText = "Assign owner")`. Trigger one analysis tick. |
| **Expected output** | `viewModel.analysisEvents.value` equals the emitted `AnalysisEvent.ActionItem`. |
| **Test double needed** | `FakeConversationAnalyzer`. `FakeCloudInferenceEngine` (no-op, never called). |

```kotlin
@Test
fun `C3_1 ActionItem from analyzer updates analysisEvents StateFlow`() = runTest {
    val actionItem = AnalysisEvent.ActionItem("finish report by Friday", "Assign owner")
    val fakeAnalyzer = FakeConversationAnalyzer(fixedEvent = actionItem)
    val viewModel = SessionViewModel(
        cloudInferenceEngine = FakeCloudInferenceEngine(),
        analyzer = fakeAnalyzer,
        configRepository = FakeCloudProviderConfigRepository()
    )
    viewModel.onAnalysisTick("finish report by Friday")
    assertEquals(actionItem, viewModel.analysisEvents.value)
}
```

---

### C3.2 — Question detection and analysis both fire → question takes priority, analysis suppressed

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C3.2 |
| **Description** | When `onQuestionDetected()` and an analysis tick arrive in the same logical window, the question-detection path populates `currentSuggestionTokens` and `analysisEvents` is NOT updated with the analysis result. This enforces FR-010. |
| **Test input**  | Call `viewModel.onQuestionDetected("What is the budget?")` and `viewModel.onAnalysisTick("we decided on budget")` in the same coroutine scope before collecting. |
| **Expected output** | `viewModel.currentSuggestionTokens.value` is non-empty (from question path). `viewModel.analysisEvents.value` remains `null` (analysis suppressed). |
| **Test double needed** | `FakeCloudInferenceEngine` that emits a token for the question. `FakeConversationAnalyzer` that emits a `Decision` event. |

```kotlin
@Test
fun `C3_2 question takes priority over simultaneous analysis event`() = runTest {
    val fakeEngine = FakeCloudInferenceEngine(stubTokens = listOf("cloud answer"))
    val fakeAnalyzer = FakeConversationAnalyzer(
        fixedEvent = AnalysisEvent.Decision("budget agreed", "Document it")
    )
    val viewModel = SessionViewModel(
        cloudInferenceEngine = fakeEngine,
        analyzer = fakeAnalyzer,
        configRepository = FakeCloudProviderConfigRepository()
    )
    viewModel.onQuestionDetected("What is the budget?")
    viewModel.onAnalysisTick("we decided on budget")  // should be suppressed
    runCurrent()
    assertNotEquals("", viewModel.currentSuggestionTokens.value)
    assertNull(viewModel.analysisEvents.value)
}
```

---

### C3.3 — analysisEnabled = false → CadenceController never started, zero analyze() calls

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C3.3 |
| **Description** | When `AnalysisSettings.analysisEnabled = false`, `SessionViewModel` must not start `AnalysisCadenceController`. `FakeConversationAnalyzer.analyzeCallCount` remains 0 for the entire session lifetime. |
| **Test input**  | Construct `SessionViewModel` with `analysisEnabled = false` in settings. Simulate 60s of virtual time. |
| **Expected output** | `fakeAnalyzer.analyzeCallCount == 0`. No `AnalysisEvent` is ever emitted into `analysisEvents`. |
| **Test double needed** | `FakeConversationAnalyzer` (counting calls). `FakeAnalysisSettingsRepository` returning `analysisEnabled = false`. |

```kotlin
@Test
fun `C3_3 analysisEnabled false means zero analyze calls`() = runTest {
    val fakeAnalyzer = FakeConversationAnalyzer()
    val viewModel = SessionViewModel(
        cloudInferenceEngine = FakeCloudInferenceEngine(),
        analyzer = fakeAnalyzer,
        configRepository = FakeCloudProviderConfigRepository(),
        analysisSettings = AnalysisSettings(analysisEnabled = false, analysisIntervalS = 20, analysisWindowS = 60)
    )
    advanceTimeBy(60_000)
    assertEquals(0, fakeAnalyzer.analyzeCallCount)
    assertNull(viewModel.analysisEvents.value)
}
```

---

## Contract Group 4 — Duplicate Suppression

The suppression window is 60 seconds. The leading 64 characters of the emitted
`AnalysisEvent` text (e.g. `ActionItem.text`) are used as the deduplication key.
This enforces FR-011 (spec uses 80 chars; contracts use 64 as a safe lower-bound
that leaves headroom for the implementation to choose any value 64–80).

---

### C4.1 — Same leading-64-chars result within 60s window → second emission suppressed

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C4.1 |
| **Description** | If `AnalysisEvent.ActionItem.text` starts with the same 64 characters as an event emitted fewer than 60 seconds ago, the second event is dropped and `analysisEvents` StateFlow is NOT updated again. |
| **Test input**  | Emit `AnalysisEvent.ActionItem(text = "finish report by Friday" + "x".repeat(100), ...)` twice in virtual time 30s apart (within the 60s window). |
| **Expected output** | `analysisEvents` was updated once. Second tick produces no new value (StateFlow `value` unchanged from first emission). |
| **Test double needed** | `FakeConversationAnalyzer` that emits the identical event on every call. `FakeClock` or `advanceTimeBy(30_000)` between calls. |

```kotlin
@Test
fun `C4_1 duplicate within 60s window is suppressed`() = runTest {
    val duplicateEvent = AnalysisEvent.ActionItem("finish report by Friday" + "x".repeat(100), "Assign owner")
    val fakeAnalyzer = FakeConversationAnalyzer(fixedEvent = duplicateEvent)
    val viewModel = SessionViewModel(
        cloudInferenceEngine = FakeCloudInferenceEngine(),
        analyzer = fakeAnalyzer,
        configRepository = FakeCloudProviderConfigRepository(),
        analysisSettings = AnalysisSettings(analysisEnabled = true, analysisIntervalS = 15, analysisWindowS = 60)
    )
    val emissions = mutableListOf<AnalysisEvent?>()
    val job = launch { viewModel.analysisEvents.toList(emissions) }
    advanceTimeBy(30_000)  // first tick at t=15s, second at t=30s
    job.cancel()
    // Only one distinct emission of the action item (initial null + one update)
    assertEquals(1, emissions.filterIsInstance<AnalysisEvent.ActionItem>().size)
}
```

---

### C4.2 — Same text after 60s elapsed → second emission allowed

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C4.2 |
| **Description** | When the 60-second deduplication window has expired, an identical event from the same text is allowed through and updates `analysisEvents`. |
| **Test input**  | Emit the same `AnalysisEvent.ActionItem` at t=0 and t=65s (after window expiry). |
| **Expected output** | `analysisEvents` is updated twice with the same event (two distinct `ActionItem` emissions). |
| **Test double needed** | `FakeConversationAnalyzer` (fixed event). `FakeClock` or `advanceTimeBy(65_001)` spanning two ticks separated by > 60s. |

```kotlin
@Test
fun `C4_2 same text after 60s dedup window is allowed through`() = runTest {
    val event = AnalysisEvent.ActionItem("finish report by Friday " + "x".repeat(80), "Assign owner")
    val fakeAnalyzer = FakeConversationAnalyzer(fixedEvent = event)
    val viewModel = SessionViewModel(
        cloudInferenceEngine = FakeCloudInferenceEngine(),
        analyzer = fakeAnalyzer,
        configRepository = FakeCloudProviderConfigRepository(),
        analysisSettings = AnalysisSettings(analysisEnabled = true, analysisIntervalS = 10, analysisWindowS = 60)
    )
    val emissions = mutableListOf<AnalysisEvent?>()
    val job = launch { viewModel.analysisEvents.toList(emissions) }
    advanceTimeBy(65_001)  // spans t=10s (suppressed at t=20s..60s), t=70s allowed
    job.cancel()
    assertTrue(emissions.filterIsInstance<AnalysisEvent.ActionItem>().size >= 2)
}
```

---

## Contract Group 5 — `TranscriptWindowBuffer`

`TranscriptWindowBuffer` accumulates ASR segments keyed by a monotonic timestamp (ms).
`windowText(nowMs, windowSizeMs)` returns the concatenated text of all segments whose
timestamp falls within `[nowMs - windowSizeMs, nowMs]`.

---

### C5.1 — Segments older than windowSizeMs are excluded from windowText()

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C5.1 |
| **Description** | Given a buffer containing segments at t=0s ("old segment") and t=50s ("new segment"), a call to `windowText(nowMs = 60_000, windowSizeMs = 30_000)` includes only the t=50s segment. |
| **Test input**  | `buffer.add(timestampMs = 0, text = "old segment")`. `buffer.add(timestampMs = 50_000, text = "new segment")`. Call `buffer.windowText(nowMs = 60_000, windowSizeMs = 30_000)`. |
| **Expected output** | `"new segment"` (old segment excluded; no Android/JNI dependency needed). |
| **Test double needed** | None — `TranscriptWindowBuffer` is a pure Kotlin data structure with no Android imports. |

```kotlin
@Test
fun `C5_1 segments outside window are excluded`() {
    val buffer = TranscriptWindowBuffer()
    buffer.add(timestampMs = 0L, text = "old segment")
    buffer.add(timestampMs = 50_000L, text = "new segment")
    val result = buffer.windowText(nowMs = 60_000L, windowSizeMs = 30_000L)
    assertFalse(result.contains("old segment"))
    assertTrue(result.contains("new segment"))
}
```

---

### C5.2 — Empty buffer → windowText() returns ""

| Field           | Value |
|-----------------|-------|
| **Contract ID** | C5.2 |
| **Description** | When `TranscriptWindowBuffer` contains no segments, `windowText()` returns an empty string without throwing. |
| **Test input**  | `TranscriptWindowBuffer()` with no segments added. Call `windowText(nowMs = 10_000, windowSizeMs = 60_000)`. |
| **Expected output** | `""` (empty string). |
| **Test double needed** | None. |

```kotlin
@Test
fun `C5_2 empty buffer returns empty string`() {
    val buffer = TranscriptWindowBuffer()
    assertEquals("", buffer.windowText(nowMs = 10_000L, windowSizeMs = 60_000L))
}
```

---

## Test Double Reference

| Fake / Test Double | Package | Replaces | Key API |
|--------------------|---------|----------|---------|
| `FakeConversationAnalyzer` | `com.meetmind.assistant.helpers` | `ConversationAnalyzer` | `analyzeCallCount: Int`, `delayMs: Long`, `fixedEvent: AnalysisEvent?` |
| `FakeCloudInferenceEngine` | `com.meetmind.assistant.helpers` | `CloudInferenceEngine` | `callCount: Int`, `stubTokens: List<String>`, `lastReceivedText: String` |
| `FakeInferenceEngine` | `com.meetmind.assistant.helpers` | `CloudInferenceEngine` (low-level) | `callCount: Int`, `stubTokens: List<String>`, `lastReceivedText: String` |
| `FakeAnalysisSettingsRepository` | `com.meetmind.assistant.helpers` | `AnalysisSettingsRepository` | Constructor-injected `AnalysisSettings` |
| `FakeCloudProviderConfigRepository` | `com.meetmind.assistant.helpers` | `CloudProviderConfigRepository` | Pre-configured with GEMINI enabled |

All fakes live in `app/src/test/kotlin/com/meetmind/assistant/helpers/` and have zero Android
framework dependencies.
