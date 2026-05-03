# Data Model: Interview Coach Enhancements

**Feature**: 010-interview-coach-enhancements  
**Date**: 2026-05-03

---

## Modified Entities

### LlmInsight (domain model)

**File**: `domain/src/main/java/com/meetmind/assistant/domain/model/LlmInsight.kt`

```kotlin
data class LlmInsight(
    val id: String,
    val sessionId: String,
    val title: String? = null,
    val content: String,             // For STAR answers: "Situation: … ||| Task: … ||| Action: … ||| Result: …"
    val tasks: String? = null,       // JSON array of coaching tips (unchanged)
    val timestamp: Long,
    val sourceSegmentIds: List<String>,
    val questionType: String? = null // NEW: "behavioural" | "technical" | "situational" | null
)
```

**Change**: Add nullable `questionType: String?` field. Default `null` preserves backward compat with all existing callers.

---

### LlmInsightEntity (Room entity)

**File**: `data/src/main/java/com/meetmind/assistant/data/database/entity/LlmInsightEntity.kt`

```kotlin
// Existing columns unchanged. New column added:
@ColumnInfo(name = "question_type") val questionType: String? = null
```

**Migration**: `MIGRATION_9_10` in `AppDatabase.kt`:
```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE llm_insights ADD COLUMN question_type TEXT")
    }
}
```

**Database version**: 9 → 10.

---

## New Entities

### FillerWordStats (domain model)

**File**: `domain/src/main/java/com/meetmind/assistant/domain/model/FillerWordStats.kt`  
**Lifecycle**: In-memory only. Not persisted. Reset on session start.

```kotlin
data class FillerWordStats(
    val totalCount: Int = 0,
    val ratePerMinute: Float = 0f   // computed: totalCount / max(1f, durationMin)
)
```

**Colour thresholds** (UI only, not stored):
- `ratePerMinute < 3f` → neutral
- `ratePerMinute in 3f..< 6f` → amber `Color(0xFFF59E0B)`
- `ratePerMinute >= 6f` → `MaterialTheme.colorScheme.error`

---

### CardTimerEntry (UI state only)

Not a domain or database entity. Held in `MainUiState.cardTimers: Map<String, CardTimerEntry>`.

```kotlin
data class CardTimerEntry(
    val elapsedMs: Long = 0L,
    val nudge: String? = null  // "Consider elaborating" | null
)
```

**Key**: `LlmInsight.id`  
**Lifecycle**: Entries created when an insight is added while recording. Incremented by 1-second ticker coroutine. `nudge` set when recording ends and `elapsedMs < 30_000`.

**Colour thresholds** (UI only):
- `elapsedMs < 90_000` → no border tint
- `elapsedMs in 90_000..< 120_000` → amber border
- `elapsedMs >= 120_000` → error-red border

---

### InterviewInsight (intermediate parse result)

**File**: `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/InterviewOutputParser.kt`  
**Existing class — add one field**:

```kotlin
data class InterviewInsight(
    val questionDetected: Boolean,
    val detectedQuestion: String?,
    val answerSuggestion: String,   // For STAR: contains ||| separators when questionType == "behavioural"
    val coachingTips: List<String>,
    val role: String,
    val questionType: String? = null  // NEW
)
```

---

## State Changes

### MainUiState

**File**: `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt`

New fields added to the existing `data class`:

```kotlin
val fillerWordStats: FillerWordStats = FillerWordStats(),  // NEW
val cardTimers: Map<String, CardTimerEntry> = emptyMap()   // NEW
```

---

## Validation Rules

| Rule | Where enforced |
|------|----------------|
| `questionType` must be one of `"behavioural"`, `"technical"`, `"situational"`, or `null` | `InterviewOutputParser` — unknown values coerced to `null` |
| STAR separator `|||` only present when `questionType == "behavioural"` | `InterviewOutputParser` — enforced at parse time |
| `FillerWordStats.ratePerMinute` ≥ 0 | `MainViewModel` — computed from non-negative counts |
| `CardTimerEntry.elapsedMs` ≥ 0 | `MainViewModel` ticker — clamped |
| Reactive trigger debounce ≥ `MIN_REACTIVE_DEBOUNCE_MS` (10 000 ms) | `SyncSttLlmUseCase` |

---

## State Transitions

### Per-Card Answer Timer

```
[Insight added while recording]
        │
        ▼
CardTimerEntry created (elapsedMs=0, nudge=null)
        │
        ▼ (first completed segment after insight.timestamp)
Timer running — incremented +1000ms each tick
        │
        ├─ isRecording → false AND elapsedMs < 30_000 → nudge = "Consider elaborating"
        │
        └─ isRecording → false (any duration) → timer freezes
```

### Speaker Cluster Mapping

```
Session start → speakerMapping cleared
        │
        ▼ first completed segment with speakerCluster != null
speakerMapping[cluster] = "[Interviewer]"
        │
        ▼ subsequent segments
speakerMapping[cluster] = "[Interviewer]" if cluster already mapped
speakerMapping[cluster] = "[You]"         if cluster is new
        │
        ▼ all segments have speakerCluster == null
fall back to unlabelled build()
```
