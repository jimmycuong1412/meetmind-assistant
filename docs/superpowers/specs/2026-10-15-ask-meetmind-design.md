# Ask MeetMind — chat with saved meetings

**Status:** Implemented (first slice), 2026-10-15. Not yet verified on a device.

## What it does

The user picks one or more saved sessions and asks questions about them. The
on-device LLM answers **only** from those meetings and cites the transcript passages
each claim comes from. Tapping a citation opens the transcript around that moment.
Chats are saved locally and can be renamed, pinned and deleted.

Entry point: the Ask button (question-answer icon) in the Sessions top bar.

Screens (`app/.../ui/screens/ask/`):

| Screen | Route | ViewModel |
|---|---|---|
| Pick meetings (also "change meetings" for a chat) | `ask_pick?chatId=&sessionId=` | `AskPickerViewModel` |
| Chat (answers, citations, source sheet) | `ask_chat/{chatId}` | `AskChatViewModel` |
| Saved chats (rename + undo, pin, delete) | `ask_history` | `AskHistoryViewModel` |

## How an answer is produced

1. **Retrieval** — `AskContextBuilder` (domain, pure Kotlin) splits each meeting into
   passages: runs of consecutive segments by one speaker (~420 chars) plus the
   meeting's AI insights. Passages are scored against the question with a small
   tf-idf model (Unicode word split, English/Vietnamese stopwords, 5-char prefix as a
   stand-in for stemming). The previous question is added to the query so follow-ups
   like "who owns it?" still retrieve.
2. **Budget** — the model context is 4 096 tokens (`ai_chat.cpp`), shared by system
   prompt, excerpts, history and answer. Excerpts are capped at 7 000 chars
   (~1 700 tokens): matching passages first, then AI summaries, then passages spread
   evenly through every meeting. A selection that fits is sent whole.
3. **Prompt** — numbered excerpts `[n] "Meeting" · 12:04 · Speaker: text`, the last two
   exchanges (citation markers stripped, since numbering differs per turn) and the
   question. The system prompt (`AskMeetingsUseCase.SYSTEM_PROMPT`) is English on
   purpose and tells the model to answer in the question's language, cite `[n]`, and
   start with `NOT_FOUND` when the excerpts don't contain the answer. It is not a
   user-editable prompt, so it lives in code rather than `strings.xml`.
4. **Inference** — one stateless call through `LlmRepository.generateInsight(...,
   rawPrompt = true)`. `rawPrompt` (new, default false) skips the
   `Transcription: "…"` wrapper used by the recording modes.
5. **Parsing** — `AskAnswerParser` renumbers citations by first appearance, drops
   out-of-range ones, accepts `[2, 3]` / `[Excerpt 2]` / a trailing "Sources:" list,
   and detects `NOT_FOUND`. Each citation snapshots its quote, speaker and offset so it
   still reads correctly after the transcript is edited or the meeting deleted.

## Storage (Room v13, `MIGRATION_12_13`)

- `ask_chats(id, title?, pinned, created_at, updated_at)` — null title means "use the
  first question".
- `ask_chat_sessions(chat_id, session_id, position)` — composite PK, FK to
  `ask_chats` only. **No FK to `transcription_sessions` on purpose**: a deleted meeting
  stays referenced so Saved chats can say "1 of 2 meetings deleted".
- `ask_messages(id, chat_id, role, text, created_at, citations_json?, not_found)` —
  citations stored as a JSON array (org.json, same as action items).

A chat row is created when meetings are picked; chats with no question are hidden
from Saved chats and deleted when the chat screen closes.

## Model lifecycle

Same pattern as history insight in Session Details: `InitializeLlmUseCase(..., loadImmediately = false)`
sets the model path, the use case's `reloadModel()` loads it lazily, the foreground
`LlmProcessingServiceController` keeps inference alive in the background, and the
model is released (`cleanup()`) when the chat screen is closed. If another inference
is running (`LlmRepository.isGenerating`, e.g. a live recording), asking shows a
"model is busy" message instead of queueing.

## Not in this slice

- Voice input in the composer, "Play from 14:32" in the source sheet.
- "Save as insight" / "Add to tasks" on answers.
- "Suggest a name" in the rename dialog (needs a second model call).
- Suggested questions generated from the notes — the empty chat shows four fixed
  prompts instead.
- An "Ask about this meeting" action in Session Details (the picker already accepts a
  `sessionId` argument to pre-select it).
- Mode/date filter chips in the picker (search by name/topic only).

## Tests

`domain/src/test/.../usecase/ask/`: `AskContextBuilderTest`, `AskAnswerParserTest`,
`AskMeetingsUseCaseTest` (fake `LlmRepository`). The Room migration, ViewModels and
Compose screens have no automated coverage — verify on device.
