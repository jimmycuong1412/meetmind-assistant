# Feature 008 — Continuous Conversation Analysis: Developer Quickstart

**Branch**: `feature/008-continuous-conversation-analysis`
**Date**: 2026-04-21
**Target device**: Snapdragon Y700 Gen 3 (arm64-v8a, Adreno 750)

This guide gets you from zero to a working end-to-end test of continuous conversation analysis
in under 15 minutes. Follow steps in order.

---

## 1. Preconditions

Before running any manual test, confirm all of the following are true:

### 1.1 On-device model loaded (optional but recommended for full suggestion text)

1. Open the app and navigate to **Settings → On-Device Model**.
2. The status chip must show green ("Model ready"). If it shows red or yellow, tap **Load Model**
   and wait for the download/verification to complete (Gemma 4 E4B Q4_K_M, ~5 GB).
3. If you skip model loading, the analyzer falls back to keyword-matching only. Analysis cards
   will show the event type label but `suggestionText` will be `null` — no suggestion body.

### 1.2 Session running

1. From the Home screen, tap **Start Session**.
2. Confirm the session screen is active and the microphone is live (the waveform animates).
3. Confirm **Conversation Analysis** is enabled: tap the gear icon → **Conversation Analysis**
   toggle must be ON.
4. Default cadence: analysis fires every **20 seconds**. You can lower it to 10s minimum
   (see Section 5) for faster iteration during manual testing.

### 1.3 ADB connected

```bash
adb devices   # should list your device as "device" (not "unauthorized")
```

---

## 2. Triggering Each Event Type Manually

Speak the following phrases clearly after the session is running. Wait up to one full cadence
interval (default 20s) for the analysis card to appear. Set cadence to 10s for faster feedback.

### 2.1 EventType.ACTION_ITEM

Speak: **"Alice will handle the architecture review and needs to finish it by next Friday."**

Trigger keywords: "will handle", "needs to", "by [date]". Expected card label: `Action Item`.

### 2.2 EventType.DECISION

Speak: **"We decided to go with the React approach for the frontend dashboard."**

Trigger keywords: "we decided", "go with", "agreed". Expected card label: `Decision`.

### 2.3 EventType.CONFUSION

Speak: **"I'm not sure I follow — what does that mean exactly?"**

Trigger keywords: "not sure I follow", "what does that mean", "confused". Expected card label: `Unclear`.

### 2.4 EventType.QUESTION

Speak: **"What is the deadline for the final deliverable?"**

Trigger keywords: interrogative phrasing, ends with "?". This path delegates to `CloudInferenceEngine`
and produces the existing question suggestion card (not an analysis card). Expected card label: none
(question cards use the existing plain card style from spec 005).

### 2.5 AnalysisEvent.NoSignal

Speak only filler: **"Um... uh... hmm..."** or stay silent.

Expected: no card appears. `NoSignal` is logged but not surfaced in the UI.

### 2.6 Duplicate suppression

Repeat the same decision phrase in two consecutive cadence windows (within 60s). The second
window should produce no additional card — the first card remains visible and is not duplicated.

---

## 3. What to Look for in Logcat

Open logcat with the relevant tags:

```bash
adb logcat -s CadenceController:V ConversationAnalyzer:V SessionViewModel:V CloudInferenceEngine:V
```

### Key log lines to observe

| Tag | Log message | What it means |
|-----|-------------|---------------|
| `CadenceController` | `tick fired, windowText.length=NNN` | Analysis cadence fired; NNN chars sent to analyzer |
| `CadenceController` | `tick skipped — inference in-flight` | Debounce guard engaged; prior inference not finished |
| `CadenceController` | `stopped` | `stop()` called; no further ticks |
| `ConversationAnalyzer` | `classified: ACTION_ITEM` | Keyword match or LLM classification result |
| `ConversationAnalyzer` | `NoSignal — blank window, skipping inference` | Window was empty or filler-only |
| `ConversationAnalyzer` | `truncated windowText 750 -> 600 chars` | Data minimisation truncation applied (C1.6) |
| `SessionViewModel` | `analysisEvent suppressed — duplicate within 60s` | Duplicate suppression fired (C4.1) |
| `SessionViewModel` | `question priority — analysis event discarded` | Question detection took priority (C3.2) |
| `CloudInferenceEngine` | `TTFT[GEMINI] = NNNms` | Time-to-first-token for the analysis inference call |
| `CloudInferenceEngine` | `FallbackActivated TIMEOUT` | Cloud timed out; on-device fallback streaming |

### Filtering for a specific event type

```bash
adb logcat -s ConversationAnalyzer:V | grep -i "decision\|action_item\|confusion\|question"
```

---

## 4. Running Contract Tests

All contract tests run on the host JVM — no device or emulator required.

### Run all feature 008 contract tests

```bash
./gradlew testDebugUnitTest \
  --tests "*.ConversationAnalyzerTest" \
  --tests "*.AnalysisCadenceControllerTest" \
  --tests "*.SessionViewModelAnalysisTest"
```

### Run individual contract groups

```bash
# Contract Group 1 — ConversationAnalyzer.analyze()
./gradlew testDebugUnitTest --tests "*.ConversationAnalyzerTest"

# Contract Group 2 — AnalysisCadenceController
./gradlew testDebugUnitTest --tests "*.AnalysisCadenceControllerTest"

# Contract Groups 3, 4 — SessionViewModel integration + duplicate suppression
./gradlew testDebugUnitTest --tests "*.SessionViewModelAnalysisTest"

# Contract Group 5 — TranscriptWindowBuffer
./gradlew testDebugUnitTest --tests "*.TranscriptWindowBufferTest"
```

### Run all unit tests (full suite, includes prior features)

```bash
./gradlew testDebugUnitTest
```

### Expected output (all passing)

```
ConversationAnalyzerTest > C1_1 blank window emits NoSignal and calls no inference PASSED
ConversationAnalyzerTest > C1_2 question pattern delegates to cloud engine PASSED
ConversationAnalyzerTest > C1_3 action item emits ActionItem with non-null suggestion PASSED
ConversationAnalyzerTest > C1_4 decision keywords produce Decision event PASSED
ConversationAnalyzerTest > C1_5 confusion keywords produce Confusion event PASSED
ConversationAnalyzerTest > C1_6 text over 600 chars is truncated before inference PASSED
AnalysisCadenceControllerTest > C2_1 exactly 3 ticks in 45s with 15s interval PASSED
AnalysisCadenceControllerTest > C2_2 in-flight inference causes tick skip PASSED
AnalysisCadenceControllerTest > C2_3 stop prevents further ticks PASSED
AnalysisCadenceControllerTest > C2_4 sub-10s interval clamped to 10s PASSED
SessionViewModelAnalysisTest > C3_1 ActionItem from analyzer updates analysisEvents StateFlow PASSED
SessionViewModelAnalysisTest > C3_2 question takes priority over simultaneous analysis event PASSED
SessionViewModelAnalysisTest > C3_3 analysisEnabled false means zero analyze calls PASSED
SessionViewModelAnalysisTest > C4_1 duplicate within 60s window is suppressed PASSED
SessionViewModelAnalysisTest > C4_2 same text after 60s dedup window is allowed through PASSED
TranscriptWindowBufferTest > C5_1 segments outside window are excluded PASSED
TranscriptWindowBufferTest > C5_2 empty buffer returns empty string PASSED
```

### If a test fails

1. Check that `FakeConversationAnalyzer`, `FakeCloudInferenceEngine`, and
   `FakeAnalysisSettingsRepository` are in `app/src/test/kotlin/com/meetmind/assistant/helpers/`.
2. Confirm `TestScope(UnconfinedTestDispatcher())` is used in cadence tests — `StandardTestDispatcher`
   requires explicit `runCurrent()` calls and will cause C2.x tick-count assertions to fail.
3. For C4.x (dedup) tests, check that the `FakeClock` is wired into `SessionViewModel` via
   constructor injection; wall-clock time will make dedup window assertions non-deterministic.

---

## 5. Settings Screen: Adjusting Cadence and Window

### Navigate to analysis settings

1. Open the app.
2. Tap the gear icon (top-right of the Home screen or Session screen header).
3. Select **Conversation Analysis** from the settings list.

### Available controls

| Control | Default | Allowed range | Effect |
|---------|---------|---------------|--------|
| **Analysis enabled** | ON | Toggle | Stops all cadence timers when OFF; question-detection continues unaffected |
| **Analysis interval** | 20 s | 10–120 s | How often `ConversationAnalyzer.analyze()` is called. Values below 10s are clamped by `AnalysisCadenceController` (C2.4). |
| **Transcript window** | 60 s | 15–300 s | How many seconds of transcript history `TranscriptWindowBuffer.windowText()` returns. Longer windows increase context but also increase inference token count. |

### Recommended settings for manual testing

- **Interval = 10s** to iterate quickly during development (minimum allowed).
- **Window = 30s** to keep logcat output legible; 30s of speech is typically 400–500 chars, well
  within the 600-char truncation limit.

### Verifying settings are persisted

Settings are written to DataStore Preferences under keys `analysis_enabled`, `analysis_interval_s`,
and `analysis_window_s`. To inspect the raw values:

```bash
adb shell run-as com.meetmind.assistant \
  cat /data/data/com.meetmind.assistant/files/datastore/analysis_settings.preferences_pb \
  | strings | grep -E "analysis_"
```

Force-killing and relaunching the app should restore the same values.

---

## 6. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| No analysis cards appear | `analysisEnabled = false` | Settings → Conversation Analysis → toggle ON |
| Cards appear but no suggestion text | Model not loaded | Settings → On-Device Model → Load Model |
| Logcat shows `tick skipped` repeatedly | Inference slower than cadence interval | Increase interval to 30s or ensure model is GPU-offloaded (`nGpuLayers = -1`) |
| Duplicate cards appear | Dedup logic not wired in `SessionViewModel` | Verify `DuplicateSuppressor` is injected and `lastEmissionMs` clock uses `FakeClock` in tests |
| `C2_1` tick count test fails | Wrong test dispatcher | Use `UnconfinedTestDispatcher(testScheduler)`, not `StandardTestDispatcher` |
| Cloud badge shows TIMEOUT on every analysis | Network available but model prompt too long | Confirm 600-char truncation is applied before `CloudInferenceEngine.streamSuggestion()` call |
