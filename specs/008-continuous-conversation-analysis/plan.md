# Implementation Plan: Continuous Conversation Analysis

**Branch**: `feature/008-continuous-conversation-analysis` | **Date**: 2026-04-22 | **Spec**: `specs/008-continuous-conversation-analysis/spec.md`
**Input**: Feature specification from `specs/008-continuous-conversation-analysis/spec.md`

## Summary

Adds a repeating sliding-window analysis pipeline (`AnalysisCadenceController`) that fires every
20 s (configurable, minimum 10 s), classifies the last N seconds of transcript into one of four
enriched event types (`ActionItem`, `Decision`, `Confusion`, `Question`), generates a contextually
appropriate suggestion via the existing `CloudInferenceEngine` / `OnDeviceLlamaProvider` stack, and
surfaces type-labelled suggestion cards in `SessionScreen`. All analysis state is in-memory; only
`AnalysisSettings` is persisted (DataStore Preferences).

## Technical Context

**Language/Version**: Kotlin 2.0.21 (JVM target 17)
**Primary Dependencies**:
- Jetpack Compose BOM 2024.02 (existing)
- kotlinx.coroutines 1.8.x (existing; `Mutex`, `StateFlow`, `advanceTimeBy`)
- DataStore Preferences 1.1.1 (existing; for `AnalysisSettings`)
- `CloudInferenceEngine` (spec 005, existing)
- `OnDeviceLlamaProvider` (spec 007, existing)
- Hilt 2.56.2 (spec 009, existing; `@Singleton` binding for `TranscriptWindowBuffer`, `AnalysisCadenceController`)
- Room 2.6 (existing; **not extended** by this feature)

**Storage**: DataStore Preferences only (`analysis_enabled`, `analysis_interval_s`, `analysis_window_s`). Room DB is unchanged.

**Testing**: JVM unit tests (Robolectric 4.12 + `hilt-android-testing`); `./gradlew testDebugUnitTest`; `TestScope(UnconfinedTestDispatcher)` + `advanceTimeBy` for cadence tests.

**Target Platform**: Android API 26+ (APK sideload); primary test devices: Lenovo Legion Y700 Gen 3, Honor Magic 6 Pro (Snapdragon 8 Gen 3).

**Project Type**: Mobile app (Android / Jetpack Compose).

**Performance Goals**:
- Analysis cadence fires within ±500 ms of the configured interval on Y700 Gen 3 under normal load (SC-001)
- First visible analysis card ≤ 3 s after cadence tick fires (SC-002, Principle II)
- Text passed to inference ≤ 600 chars per tick (SC-003)

**Constraints**:
- Audio MUST NOT leave the device (Principle I). Only transcript text (≤ 600 chars) sent to cloud when Cloud mode is enabled.
- Cloud mode is opt-in and already guarded by `CloudInferenceEngine` (spec 005).
- Minimum analysis cadence enforced at 10 s (FR-007).
- Duplicate suppression: leading-64-char fingerprint, 60 s retention (FR-011).
- No new inference engine — reuses existing stack (spec 005 + 007).
- No new Room tables — all analysis state is in-memory.

**Scale/Scope**: Single-screen SessionViewModel + analysis layer additions; ~7 new source files in `analysis/` package; ~3 new test files; `SessionScreen` card extensions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| **I. Privacy-First Inference** | ✅ PASS | Only transcript text (≤ 600 chars) is sent to cloud. Raw audio never leaves device. Cloud mode is opt-in and already guarded by spec 005 infrastructure. |
| **II. Real-Time Responsiveness** | ✅ PASS | 3 s first-card budget enforced by SC-002. Two-step heuristic+LLM approach keeps latency within budget (research Q4). On-device fallback path remains if cloud exceeds 5 s. |
| **III. APK Distribution** | ✅ PASS | No Play Services dependencies introduced. APK smoke-test checkpoint required before merge (T036). |
| **IV. Minimal Permissions Footprint** | ✅ PASS | No new permissions. `INTERNET` was already declared by spec 005. |
| **V. Incremental, Demo-able Slices** | ✅ PASS | US1 (cadence + cards) is independently runnable before US2 (enriched types). Each story leaves the app launchable. |
| **VI. Cloud Inference Mode** | ✅ PASS | Analysis reuses existing `CloudInferenceEngine` streaming path. `FallbackReason` badge reused as-is. No new cloud endpoints or keys introduced. |

**Post-design re-evaluation**: No violations found. The two-step heuristic+LLM approach (research Q4) specifically addresses the 5 s latency constraint while satisfying data minimisation (only the window text, already ≤ 600 chars, is sent).

## Project Structure

### Documentation (this feature)

```text
specs/008-continuous-conversation-analysis/
├── plan.md          # This file
├── research.md      # Phase 0 — cadence, debounce, buffer, prompts, dedup, keywords
├── data-model.md    # Phase 1 — AnalysisEvent, EventType, AnalysisSettings, entities
├── quickstart.md    # Phase 1 — integration scenarios and test quick-start
├── contracts/
│   └── contracts.md # Phase 1 — interface contracts for all analysis components
└── tasks.md         # Phase 2 (/speckit.tasks output)
```

### Source Code

```text
app/src/main/kotlin/com/meetmind/assistant/
├── analysis/
│   ├── AnalysisEvent.kt               # sealed class hierarchy (NoSignal, Question, ActionItem, Decision, Confusion)
│   ├── EventType.kt                   # enum with priority ordering and tie-breaking
│   ├── ConversationAnalyzer.kt        # interface + FakeConversationAnalyzer
│   ├── DefaultConversationAnalyzer.kt # production impl: heuristic → LLM classify+suggest
│   ├── KeywordHeuristicClassifier.kt  # regex-based EventType classifier (object)
│   ├── TranscriptWindowBuffer.kt      # rolling ArrayDeque<TimestampedSegment>; 600-char truncation
│   ├── DuplicateSuppressor.kt         # 64-char fingerprint; 60 s retention
│   └── AnalysisCadenceController.kt   # ticker coroutine; Mutex debounce; minimum 10 s
├── data/
│   └── model/
│       ├── AnalysisSettings.kt        # data class; DataStore Preferences persistence
│       └── TimestampedSegment.kt      # data class; timestamp + ASR text
├── storage/
│   └── AnalysisSettingsRepository.kt  # Flow<AnalysisSettings>; read/write DataStore
├── di/
│   └── AnalysisModule.kt              # @Module @InstallIn(SingletonComponent) Hilt bindings
├── viewmodel/
│   └── SessionViewModel.kt            # +analysisEvent: StateFlow<AnalysisEvent?>; +analysis scope
└── ui/
    └── session/
        └── SessionScreen.kt           # +ActionItem / Decision / Confusion card composables

app/src/test/kotlin/com/meetmind/assistant/
├── analysis/
│   ├── ConversationAnalyzerTest.kt    # C1.1–C1.6 classification contracts
│   ├── AnalysisCadenceControllerTest.kt # C2.1–C2.4 timer + debounce contracts
│   └── AnalysisSettingsRepositoryTest.kt # C3.1–C3.2 DataStore persistence
├── viewmodel/
│   └── SessionViewModelAnalysisTest.kt  # C4.1–C4.3 ViewModel integration
└── di/
    └── HiltSingletonScopeTest.kt         # C6.1 — @Singleton identity checks
```

## Complexity Tracking

No constitution violations. No unjustified complexity.

| Decision | Rationale |
|---|---|
| Two-step heuristic+LLM (not single LLM call) | Latency: single on-device LLM call on Y700 Gen 3 is ~2–4 s cold; two calls would exceed 5 s budget (research Q4) |
| `Mutex.tryLock()` debounce (not `AtomicBoolean`) | Idiomatic coroutines-aware non-blocking guard; composes correctly with `UnconfinedTestDispatcher` (research Q2) |
| `ArrayDeque<TimestampedSegment>` (not Room) | In-memory only per spec assumptions; Room adds I/O latency to a 20 s hot path; spec 004 already established in-memory session data policy |
| 64-char normalised prefix fingerprint (not SHA) | Human-readable in logs; collision-free for natural-language outputs; zero dependency cost (research Q5) |

## Phase 0: Research Summary

All NEEDS CLARIFICATION items resolved in `research.md`. Key decisions:

| Decision Area | Resolution |
|---|---|
| Cadence implementation | `delay()` loop in `while(isActive)` coroutine — testable via `advanceTimeBy` |
| Debounce / skip-if-busy | `Mutex.tryLock()` — non-blocking, idiomatic, correct under `UnconfinedTestDispatcher` |
| Transcript window buffer | `ArrayDeque<TimestampedSegment>` wrapped in `TranscriptWindowBuffer` — O(1) append/trim |
| LLM prompt design | Two-step: keyword heuristic pre-filter → single LLM classify+suggest call |
| Duplicate suppression | Leading-64-char normalised prefix, 60 s `LinkedHashMap<String, Long>` retention |
| Keyword heuristic fallback | Three `Regex` groups (ActionItem, Decision, Confusion) compiled at object init |

## Phase 1: Design Summary

All design artifacts generated in prior planning pass:

- **`data-model.md`**: `AnalysisEvent`, `EventType`, `AnalysisSettings`, `TimestampedSegment`, `TranscriptWindowBuffer`, `ConversationAnalyzer`, `AnalysisCadenceController` — all entities, invariants, and persistence rules defined.
- **`contracts/contracts.md`**: Interface contracts C1–C6 for `ConversationAnalyzer`, `AnalysisCadenceController`, `TranscriptWindowBuffer`, `AnalysisSettingsRepository`, `SessionViewModel`, and Hilt singleton scope.
- **`quickstart.md`**: Integration scenarios and manual smoke-test steps for Y700 Gen 3.

### Hilt Bindings (`AnalysisModule`)

| Binding | Scope | Notes |
|---|---|---|
| `TranscriptWindowBuffer` | `@Singleton` | Shared across `SessionViewModel` and ASR feed |
| `AnalysisCadenceController` | `@Singleton` | Owns ticker coroutine; single instance per process |
| `ConversationAnalyzer` | `@Singleton` | `DefaultConversationAnalyzer` in production; `FakeConversationAnalyzer` in tests |
| `AnalysisSettingsRepository` | `@Singleton` | DataStore-backed; single DataStore instance |

### Priority Tie-breaking (FR-002, clarified 2026-04-22)

When multiple event types match in the same window:
`DECISION` > `ACTION_ITEM` > `CONFUSION` > `QUESTION`

### QUESTION Routing (FR-005, clarified 2026-04-22)

`EventType.QUESTION` → delegates to existing `CloudInferenceEngine.streamSuggestion()` path → routes to `suggestionEvents` StateFlow only. `analysisEvent` StateFlow is NOT updated.

### Heuristic NoSignal (FR-014, clarified 2026-04-22)

When the keyword heuristic finds no pattern match (under no-model + cloud-disabled conditions): return `AnalysisEvent.NoSignal`, `analysisEvent` stays `null`, no card shown.
