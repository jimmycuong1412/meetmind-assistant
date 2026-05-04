# Implementation Plan: English Coach Mode

**Feature Branch**: `feature/012-english-coach`  
**Spec**: `specs/012-english-coach/spec.md`  
**Created**: 2026-05-04

---

## Architecture Decisions

### New `RecordingMode.ENGLISH_COACH`
Added to the existing enum. All `when` exhaustive branches in `SyncSttLlmUseCase`, `GenerateFinalInsightUseCase`, `GenerateBatchInsightUseCase`, `InsightOutputParser`, and `SyncSttLlmUseCase.buildSystemPrompt` must handle the new value.

### Per-segment trigger (no interval)
English Coach fires after every completed segment ≥ 5 words. The interval path (`timeSinceLastInference >= effectiveIntervalMs`) is still present but set to a very short value (5 s) so it acts as a fallback; the primary path is the segment-reactive trigger — same mechanism as the Interview Coach reactive question trigger, but without the `isLikelyQuestion` guard.

### New domain model: `EnglishCoachInsight`
```kotlin
data class EnglishCoachInsight(
    val original: String,
    val corrected: String,
    val isCorrect: Boolean,
    val polish: String?,
    val coachingTip: String?,
    val context: String  // "daily" | "professional"
)
```

### New parser: `EnglishCoachOutputParser`
Same pattern as `InterviewOutputParser` — object with `parse()` + `toLlmInsight()`. Reuses `InsightOutputParser.stripCodeFences` / `stripThinkingBlock`.

JSON schema the LLM returns:
```json
{
  "original": "I goes to the office yesterday",
  "corrected": "I went to the office yesterday",
  "is_correct": false,
  "polish": "I headed to the office yesterday.",
  "coaching_tip": "Use past tense 'went' not present 'goes' for yesterday."
}
```

### Persistence via `LlmInsight` (no migration)
- `title`    ← `original` (truncated to 80 chars)
- `content`  ← `corrected` (or original when `isCorrect = true`)
- `tasks`    ← JSON array `[polish, coaching_tip]` (null when both absent)
- `questionType` ← context string `"daily"` / `"professional"`

### UI: `EnglishCoachInsightItem` composable
Green accent (`Color(0xFF22C55E)`). Four labeled rows:
1. **Original** — grey italic
2. **Corrected** — green, shown only when `isCorrect = false`
3. **More Natural** — blue, polish suggestion
4. **Why** — coaching tip text

Stored in `InsightsSection.kt` alongside `InterviewInsightItem`.

### Context selector in new session dialog
`EnglishCoachContextPicker` composable — two chips (Daily / Professional). Shown conditionally when `selectedMode == RecordingMode.ENGLISH_COACH`. Selection written to `topic` field on session creation.

---

## Files Changed

### New files
```
domain/src/main/java/com/meetmind/assistant/domain/model/
    EnglishCoachInsight.kt

domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/
    EnglishCoachOutputParser.kt

domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/
    EnglishCoachOutputParserTest.kt
```

### Modified files
```
domain/src/main/java/com/meetmind/assistant/domain/model/
    RecordingMode.kt                        ← add ENGLISH_COACH

domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/
    SyncSttLlmUseCase.kt                    ← ENGLISH_COACH interval, trigger, prompt path

domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/
    GenerateFinalInsightUseCase.kt          ← ENGLISH_COACH branch
    GenerateBatchInsightUseCase.kt          ← ENGLISH_COACH branch
    InsightOutputParser.kt                  ← ENGLISH_COACH when branch

domain/src/main/java/com/meetmind/assistant/domain/model/
    AppSettings.kt                          ← englishCoachIntervalSeconds, englishCoachDefaultStrategy

presentation/src/main/java/com/meetmind/assistant/presentation/main/
    MainUiState.kt                          ← no change needed (mode already in state)
    MainViewModel.kt                        ← ENGLISH_COACH guard in filler / card-timer blocks

app/src/main/java/com/meetmind/assistant/ui/screens/
    InsightsSection.kt                      ← EnglishCoachInsightItem composable
    MainScreen.kt                           ← no change needed (InsightsSection already receives mode)

app/src/main/res/values/
    strings.xml                             ← mode name, context labels, card labels, system prompt

app/src/main/java/com/meetmind/assistant/ui/screens/
    NewSessionDialog.kt (or equivalent)     ← EnglishCoachContextPicker
```

---

## System Prompt (stored in strings.xml)

```
You are an expert English language coach. The user is practising {context} English.

Analyse the EXACT phrase they just said and return a JSON object with these fields:
"original": the phrase as spoken (copy verbatim).
"corrected": the grammatically correct version. If already correct, copy original verbatim.
"is_correct": true if the phrase has no grammar errors, false otherwise.
"polish": a more natural, native-speaker phrasing appropriate for {context} English. 
          Omit (null) if the original is already fully idiomatic.
"coaching_tip": one concise sentence explaining what changed and why. Omit (null) if is_correct is true and polish is null.

Context guidance:
- daily: use contractions, informal vocabulary, everyday idioms, friendly tone.
- professional: use formal vocabulary, hedging language, business register, no slang.

Output ONLY valid JSON. No markdown. No code fences. No backticks:
{"original":"…","corrected":"…","is_correct":false,"polish":"…","coaching_tip":"…"}
```

---

## Prompt Template in strings.xml
`{context}` replaced at runtime with "daily conversation" or "professional / work" (full English phrase, not the stored key).
