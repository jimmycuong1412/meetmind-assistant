# Quickstart: Interview Coach Enhancements

**Feature**: 010-interview-coach-enhancements  
**Date**: 2026-05-03  
**Branch**: `feature/010-interview-coach-enhancements`

---

## Build & Run

No new setup required. Standard build:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Room migration (v9→v10) runs automatically on first launch after install. Existing data is preserved.

---

## Manual Smoke Tests (per story)

### Story 1 — Reactive Trigger

1. Open app → Start new session → Select **Interview** mode → any role → tap record.
2. Say: *"Tell me about a challenge you've faced."* (ends without `?` but matches opener)
3. Observe: coaching card appears **within 3 seconds** — not after 30.
4. Say another non-question sentence. Observe: **no duplicate card** within 10 seconds.
5. Wait 30 seconds in silence. Observe: interval fallback fires if ≥5 new words exist.

### Story 2 — Speaker Labels

1. Requires two voices (or use a recording playback on a second device into the mic).
2. Voice A (interviewer): *"Why did you leave your last job?"*
3. Voice B (you): *"Can I ask what the interview format is?"*
4. Observe: only Voice A's question generates a coaching card. Voice B's question does **not** trigger a card.
5. Stop recording. Observe: no crash, session saved normally.

### Story 3 — Filler Word Badge

1. Start Interview recording.
2. Say: *"Um, I think, like, basically, you know, it was, uh, a good experience."*
3. Observe: filler badge appears top-right of Insights tab, count updates within 1 second of segment completion.
4. Continue speaking fillers until rate ≥ 3/min. Observe: badge turns amber.
5. Continue until rate ≥ 6/min. Observe: badge turns red.

### Story 4 — Answer Duration Timer

1. Start Interview recording. Wait for a coaching card to appear (trigger a question).
2. Begin speaking. Observe: timer starts on the card.
3. Speak for > 90 seconds. Observe: card border turns amber.
4. Speak for > 120 seconds. Observe: card border turns red.
5. Stop within 30 seconds on a new card. Observe: "Consider elaborating" nudge appears.

### Story 5 — STAR Cards

1. Say a behavioural question: *"Tell me about a time you handled a conflict on your team."*
2. Wait for coaching card.
3. Observe: card shows four labelled blocks — **Situation**, **Task**, **Action**, **Result** — instead of bullet points.
4. Say a technical question: *"How would you design a rate limiter?"*
5. Observe: card shows standard bullet-point layout (not STAR).

---

## Rollback

If the Room migration causes issues, uninstall and reinstall the app (personal-use APK — no user data to preserve). The migration is a single `ALTER TABLE ADD COLUMN` and is not destructive.

---

## Key Files to Watch

| File | What changed |
|------|-------------|
| `SyncSttLlmUseCase.kt` | Reactive trigger, speaker mapping |
| `InterviewPromptBuilder.kt` | `buildWithSpeakerLabels()` overload |
| `InterviewOutputParser.kt` | `question_type` + STAR extraction |
| `strings.xml` | Updated `prompt_interview` system prompt |
| `AppDatabase.kt` | Version 10, `MIGRATION_9_10` |
| `LlmInsightEntity.kt` | `question_type` column |
| `LlmInsight.kt` | `questionType` field |
| `FillerWordCounter.kt` | New — pure filler counting |
| `FillerWordStats.kt` | New — data class |
| `MainUiState.kt` | `fillerWordStats`, `cardTimers` |
| `MainViewModel.kt` | Filler accumulation, timer ticker |
| `InsightsSection.kt` | Filler badge, STAR card, answer timer |
