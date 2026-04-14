# Implementation Plan: Question Detection and Timely Suggestions

**Branch**: `feature/004-question-detection` | **Date**: 2026-04-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-question-detection/spec.md`

## Summary

Implement an on-device pipeline that classifies incoming speech as a question or
non-question, then generates a contextual suggested answer using a local LLM. The
pipeline runs entirely on the Android device (Foreground Service + overlay window),
uses Vosk for streaming ASR, Silero VAD for sentence-boundary detection, rule-based
heuristics for question classification, and llama.cpp (Phi-3-mini Q4_K_M) for
suggestion generation — all within a 3-second end-to-end latency budget.

## Technical Context

**Language/Version**: Kotlin 1.9 (JVM target 17); Java interop for native JNI bindings
**Primary Dependencies**: Vosk Android SDK 0.3.47, TFLite 2.14 (Silero VAD), llama.cpp (JNI, Phi-3-mini Q4_K_M), Room 2.6, Jetpack Compose BOM 2024.02
**Storage**: Room DB (ContextProfile only); session/suggestion data in-memory only
**Testing**: Android instrumented tests (JUnit4 + Espresso); unit tests for classifier and data-model logic (JUnit5 + MockK)
**Target Platform**: Android API 26+ (Android 8.0); primary test targets: Lenovo Legion Y700 Gen 3 and Honor Magic 6 Pro (Snapdragon 8 Gen 3, 12–16 GB RAM)
**Project Type**: Mobile app (APK-only, sideloaded)
**Performance Goals**: End-to-end latency ≤3s; first visible token ≤1s (streaming); suggestion capped at 40 tokens (2 concise sentences); LLM prefill via OpenCL Adreno 750 (~85 t/s), decode via CPU (~10–15 t/s)
**Constraints**: Fully offline — no INTERNET permission; model loaded once at service start; keep LLM prompt ≤100 tokens total; use OpenCL backend (`-ngl 99`, Q4_0 with `--pure`); cap `max_new_tokens` at 40–50; `android:largeHeap="true"` required; APK size budget (model downloaded post-install, not bundled)
**Scale/Scope**: Single-user, single-device; no concurrent sessions; v1 targets conversational English only

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. On-Device Only | ✅ PASS | All ASR, VAD, classification, and LLM inference run on-device. No INTERNET permission declared. Model downloaded once on first launch (network used for download only — no ongoing data transmission). |
| II. Real-Time Responsiveness | ✅ PASS | Pipeline budget: VAD ~0ms + Vosk ~300ms + classifier <1ms + Phi-3-mini ~2–2.5s = ~2.8–3.0s. Tight but within SLA on target hardware. Primary mitigation: keep prompt ≤250 tokens. |
| III. APK Distribution | ✅ PASS | APK-only, no Play Store. Model (2.2 GB GGUF) is NOT bundled in APK — downloaded to `getFilesDir()` on first launch. llama.cpp uses no Play Services APIs. |
| IV. Minimal Permissions Footprint | ✅ PASS | Permissions required: `RECORD_AUDIO` (audio capture), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` (background service), `SYSTEM_ALERT_WINDOW` (floating overlay — spec 002 dependency), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (keep service alive). No INTERNET, contacts, calendar, or location. All documented in spec. |
| V. Incremental, Demo-Able Slices | ✅ PASS | User stories are independently deliverable: US4 (non-question silence) → US1 (basic question detection) → US2 (mode-aware framing) → US3 (freshness indicator). Each leaves app in a launchable state. |

*Post-design re-check: All principles still pass. No violations to document.*

## Project Structure

### Documentation (this feature)

```text
specs/004-question-detection/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── audio-pipeline.md   # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
app/
├── src/
│   └── main/
│       ├── kotlin/com/meetmind/assistant/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   └── dao/ContextProfileDao.kt
│       │   │   └── model/
│       │   │       ├── AudioChunk.kt
│       │   │       ├── TranscriptSegment.kt
│       │   │       ├── DetectedQuestion.kt
│       │   │       ├── Suggestion.kt
│       │   │       ├── Session.kt
│       │   │       ├── SessionMode.kt
│       │   │       └── ContextProfile.kt
│       │   ├── pipeline/
│       │   │   ├── AudioCaptureSource.kt
│       │   │   ├── VadPipeline.kt
│       │   │   ├── StreamingRecogniser.kt
│       │   │   ├── QuestionClassifier.kt
│       │   │   └── SuggestionEngine.kt
│       │   ├── service/
│       │   │   └── AudioProcessingForegroundService.kt
│       │   ├── overlay/
│       │   │   └── OverlayWindowManager.kt
│       │   └── ui/
│       │       ├── screens/
│       │       │   ├── HomeScreen.kt
│       │       │   ├── SessionScreen.kt
│       │       │   └── ProfileSetupScreen.kt
│       │       └── components/
│       │           ├── SuggestionCard.kt
│       │           └── ModeSelector.kt
│       └── res/
│           └── ...
└── src/
    └── test/ and androidTest/
        ├── pipeline/
        │   ├── QuestionClassifierTest.kt
        │   └── SuggestionEngineTest.kt
        └── data/
            └── ContextProfileDaoTest.kt
```

**Structure Decision**: Single Android project (Option 3 mobile variant — no separate
API). All processing lives inside the app process via a Foreground Service.

## Complexity Tracking

> No constitution violations — this table is left intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
