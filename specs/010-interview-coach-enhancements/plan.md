# Implementation Plan: Interview Coach Enhancements

**Branch**: `feature/010-interview-coach-enhancements` | **Date**: 2026-05-03 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/010-interview-coach-enhancements/spec.md`

## Summary

Five targeted enhancements to make Interview Coach mode viable for real interviews: (1) a question-reactive instant trigger that fires within 1 second of a `?`-terminated segment instead of waiting 30 seconds; (2) diarization-aware speaker labelling in the LLM prompt so only the interviewer's questions trigger coaching cards; (3) a real-time filler-word counter badge; (4) a per-card answer-duration timer with colour thresholds; (5) STAR-structured answer cards for behavioural questions. All changes are additive to the existing pipeline — on-device only, no network calls, no new permissions.

## Technical Context

**Language/Version**: Kotlin 1.9 (JVM target 17)  
**Primary Dependencies**: Jetpack Compose, Room 2.6, llama.cpp JNI (Gemma 3 1B Q4_0), sherpa-onnx JNI (speaker diarization), Kotlin Coroutines + Flow  
**Storage**: Room DB v9 → v10 migration required (add `question_type TEXT` nullable column to `llm_insights`)  
**Testing**: JUnit 4 + Robolectric; existing `InterviewOutputParserTest` covers parser; new unit tests for `FillerWordCounter`, reactive trigger deduplication  
**Target Platform**: Android APK, API 26+; primary devices Lenovo Legion Y700 Gen 3 / Honor Magic 6 Pro  
**Project Type**: Android mobile app (Kotlin + Jetpack Compose, multi-module Gradle)  
**Performance Goals**: Coaching card visible within 3 seconds of `?`-terminated segment; filler word badge updates within 1 second of completed segment; zero ANR on Legion Y700  
**Constraints**: On-device only (no INTERNET permission added); no new third-party dependencies; APK size delta < 50 KB; Room migration must preserve all existing rows  
**Scale/Scope**: Single-user personal APK; affects 3 source modules (domain, data, app/presentation)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Privacy-First Inference** | ✅ PASS | All enhancements are on-device. No audio or transcript leaves the device. Cloud mode (spec 005) is explicitly out of scope for this spec. |
| **II. Real-Time Responsiveness** | ✅ PASS | Reactive trigger directly targets the 3-second goal. Answer timer and filler badge are purely incremental UI updates with no inference cost. |
| **III. APK Distribution** | ✅ PASS | No Play Services APIs introduced. No signing changes. |
| **IV. Minimal Permissions Footprint** | ✅ PASS | No new `uses-permission` entries. INTERNET is not declared. |
| **V. Incremental, Demo-able Slices** | ✅ PASS | Each user story (P1–P5) is independently runnable. P1 alone (reactive trigger) is a complete demo-able slice. |
| **VI. Cloud Inference Mode** | ✅ N/A | This spec does not touch cloud inference. |

**Post-design re-check**: Constitution still passes after Phase 1 design. The Room migration (v9→v10) adds a nullable column only — no data loss risk.

## Project Structure

### Documentation (this feature)

```text
specs/010-interview-coach-enhancements/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (affected files)

```text
domain/src/main/java/com/meetmind/assistant/domain/
├── model/
│   ├── LlmInsight.kt                          # + questionType: String? field
│   └── FillerWordStats.kt                     # NEW — FillerWordStats data class
├── usecase/
│   ├── llm/
│   │   ├── InterviewOutputParser.kt           # + question_type extraction
│   │   ├── InterviewPromptBuilder.kt          # + buildWithSpeakerLabels() overload
│   │   └── FillerWordCounter.kt               # NEW — pure function, counts filler words
│   └── sync/
│       └── SyncSttLlmUseCase.kt               # + reactive trigger logic + deduplication guard

data/src/main/java/com/meetmind/assistant/data/
├── database/
│   ├── entity/
│   │   └── LlmInsightEntity.kt               # + question_type column
│   └── AppDatabase.kt                         # version 9 → 10, MIGRATION_9_10

app/src/main/java/com/meetmind/assistant/
├── ui/screens/
│   └── InsightsSection.kt                    # + filler badge overlay, STAR card variant,
│                                             #   answer timer on InterviewInsightItem
└── presentation/main/
    ├── MainUiState.kt                         # + fillerWordStats, cardTimers map
    └── MainViewModel.kt                       # + filler accumulation, timer ticks
```

**Structure Decision**: Multi-module Android project. Domain module owns all business logic (parser, prompt builder, filler counter); Data module owns persistence (Room entity + migration); App module owns UI (Compose composables, ViewModel state).

## Complexity Tracking

> No constitution violations. Table omitted per template guidance.

---

## Phase 0: Research

*See [research.md](research.md) for full findings. Key decisions below.*

---

## Phase 1: Design & Contracts

### Data Model

*See [data-model.md](data-model.md) for full entity definitions.*

Key schema change: `llm_insights` table gains one nullable column `question_type TEXT` (Room migration v9→v10). All existing rows default to `NULL`. No other table changes.

### Interface Contracts

This feature has no external API surface (no REST endpoints, no inter-app communication). Internal contracts are captured in [data-model.md](data-model.md) as Kotlin data class signatures.

### Implementation Phases

#### Story 1 — Question-Reactive Trigger (P1)

**Files**: `SyncSttLlmUseCase.kt`

**Approach**: After each completed segment is appended to `newSegmentsSinceLastLlm` (line ~202), check if the segment text (trimmed) ends with `?` or matches a set of question-opener patterns. If yes, and if `(System.currentTimeMillis() - lastInferenceTimestamp) >= MIN_REACTIVE_DEBOUNCE_MS` (proposed: 10 000 ms), immediately call `runInference()` and reset the interval clock. The 30-second interval continues to run as a fallback.

**Deduplication guard**: `lastInferenceTimestamp` already exists. The reactive path simply checks it before firing. If the interval fires within the debounce window after a reactive trigger, it skips (the `timeSinceLastInference < effectiveIntervalMs` check already handles this implicitly).

**Question-pattern detection** (pure Kotlin, no LLM):
```kotlin
private fun isLikelyQuestion(text: String): Boolean {
    val t = text.trim()
    if (t.endsWith("?")) return true
    val openers = listOf("tell me about", "walk me through", "describe a", "give me an example",
                         "how would you", "what would you", "why did you", "can you explain")
    return openers.any { t.lowercase().contains(it) }
}
```

---

#### Story 2 — Speaker-Aware Prompt Labelling (P2)

**Files**: `InterviewPromptBuilder.kt`, `SyncSttLlmUseCase.kt`

**Approach**: Add a `buildWithSpeakerLabels(role, segments)` overload to `InterviewPromptBuilder`. The overload maps `TranscriptionSegment.speakerCluster` to `[Interviewer]` / `[You]` using a simple heuristic: the **first speaker cluster observed** is labelled `[Interviewer]` (assumption: interviewer speaks first to open the session). Subsequent segments from that cluster → `[Interviewer]`; all other clusters → `[You]`.

Speaker mapping is maintained as a `Map<Int, String>` in `SyncSttLlmUseCase` (reset on session start). When `speakerCluster` is null on all segments, fall back to existing unlabelled `build()`.

**System prompt addition**: Add one sentence: *"Speaker labels `[Interviewer]` and `[You]` prefix each turn. Only flag questions from `[Interviewer]` turns."*

---

#### Story 3 — Filler Word Counter (P3)

**Files**: `FillerWordCounter.kt` (new), `MainUiState.kt`, `MainViewModel.kt`, `InsightsSection.kt`

**FillerWordCounter** — pure function, no coroutine:
```kotlin
object FillerWordCounter {
    private val FILLERS = setOf("um", "uh", "like", "you know", "basically", "literally", "so")
    fun count(text: String): Int { /* tokenise, match against FILLERS */ }
}
```

**ViewModel accumulation**: On each completed segment update in `MainViewModel`, if `isRecording && recordingMode == INTERVIEW`, call `FillerWordCounter.count(segment.text)` and accumulate into `_uiState.fillerWordStats`. Rate = `totalCount / (recordingDurationMillis / 60_000f)`.

**UI badge**: Inside `InsightsSection`, floating `Box` at `Alignment.TopEnd` with `padding(end = 12.dp, top = 8.dp)`. Visible only when `isRecording && recordingMode == INTERVIEW`. Colour: neutral < 3/min, amber ≥ 3/min, red ≥ 6/min.

---

#### Story 4 — Answer Duration Timer (P4)

**Files**: `MainUiState.kt`, `MainViewModel.kt`, `InsightsSection.kt`

**State**: Add `val cardTimers: Map<String, Long> = emptyMap()` to `MainUiState` where key = `insightId`, value = answer duration in milliseconds. Updated by a 1-second `ticker` coroutine in `MainViewModel` while recording.

**Timer start**: When a new coaching card is generated (insight added to `insights`), record `questionTimestamp = insight.timestamp`. When the next completed segment after that timestamp arrives, set `answerStart = segment.endOffsetMs`. Timer = `now - answerStart`.

**UI rendering**: Pass `cardTimers[insight.id]` into `InterviewInsightItem`. Render a small `Text` in the card header showing elapsed time. Change card border `strokeWidth` and colour at 90s (amber) and 120s (red). Show `"Consider elaborating"` nudge chip if timer stops before 30s (detected when recording ends or next question fires).

---

#### Story 5 — STAR-Structured Answer Cards (P5)

**Files**: `strings.xml`, `InterviewOutputParser.kt`, `LlmInsight.kt`, `LlmInsightEntity.kt`, `AppDatabase.kt`, `InsightsSection.kt`

**Prompt change** — add to system prompt:
```
"question_type": "behavioural" if question starts with "Tell me about a time", "Give an example", "Describe a situation", "Walk me through a time"; "technical" for implementation/architecture/code questions; "situational" for "What would you do if..."; null if no question detected.
When question_type is "behavioural", structure "answer" as four labelled sections separated by |||: "Situation: ... ||| Task: ... ||| Action: ... ||| Result: ..."
```

**Parser**: Extract `question_type` string field. When parsing `answer`, if `question_type == "behavioural"` and answer contains `|||`, split into `StarAnswer(situation, task, action, result)` and store as structured content.

**Storage**: Store `question_type` in the new `question_type TEXT` column (Room migration v9→v10). STAR sections stored inline in `content` with `|||` separator — no additional column needed.

**UI**: New `StarAnswerSection` composable renders four labelled `Surface` blocks (Situation/Task/Action/Result) with distinct left-border accent. Used only when `insight.questionType == "behavioural"` and content contains `|||`.

---

### Room Migration v9→v10

```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE llm_insights ADD COLUMN question_type TEXT")
    }
}
```

Added to `AppDatabase.kt` migrations list. `@Database(version = 10)`.
