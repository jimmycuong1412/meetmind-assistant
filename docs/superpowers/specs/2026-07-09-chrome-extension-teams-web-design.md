# MeetMind Web — Chrome Extension Design (v1, lean core)

> **Status:** Approved design, pre-implementation.
> **Date:** 2026-07-09
> **Home:** This spec lives here temporarily; it moves to the new `meetmind-web` repo
> once that repo is created. The Android repo is **not** modified by this project.

---

## 1. Goal

A Chrome extension (Manifest V3) that brings MeetMind's core value — live transcript +
periodic on-device AI insights — to browser-based meetings, with **MS Teams web** as the
primary test target. Inference runs **fully in-browser** (WASM/WebGPU): no cloud, no
account, no companion desktop app. This preserves the privacy-first identity of the
Android app.

**This is a rewrite, not a port.** No Kotlin, Compose, JNI, or native code is reused.
What transfers from the Android repo is conceptual: system prompts, the JSON insight
schema and parsing rules, insight-tick cadence logic, VAD tuning knowledge, and UX design.

## 2. Decisions already made

| Decision | Choice | Rationale |
|---|---|---|
| Repo | **Separate repo** (`meetmind-web`), not a subdirectory of the Android repo | Zero shared build tooling (Gradle/NDK/LFS vs. npm/WASM); different CI, stores, release cadence. KMP `:domain` sharing (rejected for now) remains possible later if the products converge. |
| Inference location | **Fully in-browser** | Matches the on-device privacy story. Companion app / built-in Chrome AI / cloud all rejected. |
| v1 scope | **Lean core** | Capture → live transcript → periodic insights. English only. No session history, no camera vision, no 25-locale support. |
| Capture targets | **Any tab** | `tabCapture` is site-agnostic; Teams web is the primary test target, Meet/Zoom web come for free. |

## 3. Runtime architecture (MV3)

Four components, dictated by Manifest V3 constraints:

1. **Service worker (background)** — orchestration only. Starts/stops capture via
   `chrome.tabCapture.getMediaStreamId()`, creates/destroys the offscreen document,
   relays state to the side panel. No audio and no inference here: service workers
   cannot use Web Audio and are killed after ~30 s idle.
2. **Offscreen document** — the engine room. Consumes the tab-capture stream
   (`getUserMedia` with the stream ID), runs the audio graph, and hosts the STT and LLM
   Web Workers. This is the MV3-sanctioned home for long-running media work.
3. **Side panel UI** (`chrome.sidePanel`) — live transcript + insight cards, docked next
   to the meeting tab. Chosen over a popup because popups close on focus loss, which is
   fatal for a meeting assistant.
4. **Web Workers** (inside the offscreen document) — one for STT, one for LLM, so
   inference never blocks the audio graph.

**Message flow:** side panel → service worker → offscreen document → workers, and state
updates flow back the same way via `chrome.runtime` messaging. The service worker holds
no in-memory session state it cannot rebuild (it may be killed and restarted); transcript
and insight state live in the offscreen document while a session is active.

**Capture-mute gotcha (handled by design):** `tabCapture` mutes the tab for the user
unless the captured stream is routed back to the speakers. The offscreen document pipes
the stream to an `AudioContext` destination so the user still hears the meeting.

## 4. Audio capture

- **Tab audio** carries the *other participants* (Teams plays remote audio through the
  tab). The user's own voice is **not** in tab audio.
- v1 captures **tab audio + optional microphone**, mixed in the offscreen document's
  audio graph. A simple toggle in the side panel enables/disables mic mixing; mic
  permission is requested only when the toggle is first enabled.
- Output of the graph: mono PCM at the STT engine's expected sample rate (16 kHz),
  delivered to the STT worker in fixed-size chunks via an `AudioWorklet`.

## 5. Engines

Both engines sit behind small TypeScript interfaces so either can be swapped without
touching the pipeline — the same discipline as the Android repo's repository interfaces.

### 5.1 STT — `SttEngine`

- **Implementation: sherpa-onnx WASM** with a **streaming Zipformer English** model and
  sherpa-onnx's built-in **Silero VAD**.
- Deliberately the same library family as the Android app (which uses sherpa-onnx with
  Parakeet TDT); the VAD tuning knowledge in the Android README transfers. Parakeet TDT
  itself is offline/non-streaming, so the streaming Zipformer is the correct browser
  choice for live captions.
- English only in v1.
- Runs in a dedicated Web Worker; input is PCM chunks, output is
  `{ text, isFinal, timestamp }` segments.

### 5.2 LLM — `InsightEngine`

- **Implementation: WebLLM (MLC) on WebGPU**, running a Gemma-class ~1B instruct model.
- **v1 requires WebGPU.** Machines without it get a clear "unsupported" screen at first
  run — no degraded CPU fallback (wllama/WASM is too slow for ≥1B models in real time).
- Runs in a dedicated Web Worker (WebLLM supports worker execution).
- Interface: `generateInsight(context: string) → Promise<string>` (raw model output;
  parsing is the pipeline's job, mirroring the Android split between inference and
  `InsightOutputParser`).

**Verification caveat:** WebLLM's model catalog and sherpa-onnx's WASM streaming builds
must be verified against current upstream versions at implementation start. The engine
interfaces exist precisely so a substitution (e.g., a different streaming STT model, or
a different WebGPU runtime) does not ripple into the pipeline.

## 6. Insight pipeline

Same shape as the Android app's `SyncSttLlmUseCase`, reimplemented in TypeScript:

1. Final STT segments accumulate into the session transcript.
2. On a periodic tick (default 60 s, only if new final text arrived since the last
   tick), a **windowed context** (the most recent transcript slice that fits the model's
   context budget) is sent to the LLM with the ported prompt.
3. The prompt is a port of `prompt_short_meeting` from the Android `strings.xml`
   (English only in v1). JSON field names stay **English** (`"title"`, `"summary"`,
   `"action_items"`) — same stable-parsing rule as Android.
4. The parser extracts and validates the JSON, tolerating malformed output (fenced code
   blocks, leading prose, truncation). **Parser behavior and test cases are ported from
   `InsightOutputParserTest`** — that suite is the most valuable transferable asset in
   the Android repo.
5. Parsed insights render as cards in the side panel (newest first). Failed parses are
   logged and skipped; the next tick retries with fresh context. Ticks are serialized —
   a tick is skipped if the previous inference is still running (llama-family engines
   are not safely concurrent; same rule as Android's single-threaded inference
   dispatcher).

## 7. Storage & scope boundaries

- **Model weights:** WebLLM manages its own weights via the Cache API. The sherpa-onnx
  model files live in **OPFS**. Total ≈ 1 GB, downloaded on first run with a progress
  UI (the `ModelDownloadManager` UX pattern, reimplemented; downloads are resumable and
  partially-downloaded OPFS files are cleaned up or resumed on next launch).
- **Settings:** `chrome.storage.local` (mic-toggle default, insight interval).
- **No session history in v1.** Transcript and insights exist only for the active
  session, held in the offscreen document. A **copy/download-transcript button** in the
  side panel is included (nearly free). IndexedDB session history is v2.
- **Out of scope for v1:** session history/search, camera/vision, translation and other
  recording modes, 25-locale support, non-English STT, options page beyond the two
  settings above.

## 8. Error handling

The four failure modes that matter, and the required behavior:

| Failure | Behavior |
|---|---|
| WebGPU unavailable | Upfront capability check at first run; clear "unsupported" screen naming the requirement. No silent degradation. |
| Model download interrupted | Resume on next attempt; clean up or resume OPFS partials; progress UI reflects retry state. |
| Meeting tab closed / stream ends mid-session | Detect stream end, finalize the transcript gracefully, keep the side panel content available for copy/download until the panel closes. |
| LLM emits malformed JSON | Parser tolerates and skips (see §6); never crashes the pipeline; next tick proceeds. |

## 9. Testing

- **Vitest** unit tests for the insight parser (test cases ported from
  `InsightOutputParserTest`) and the transcript-windowing/tick logic.
- **Manual test checklist** for capture, checked on Teams web (primary), Meet, and Zoom
  web: tab audio captured, tab not muted for the user, mic mixing works, transcript
  latency acceptable, insights appear on cadence.
- Real-time audio capture cannot be meaningfully CI-tested. The honest gate — stated in
  the new repo's docs, as in the Android repo — is: build + unit tests green, then human
  verification of the capture path.

## 10. Stack summary & repo skeleton

- **TypeScript + Vite + CRXJS** (MV3 bundling), plain DOM or a lightweight UI lib for
  the side panel (decided at implementation; no heavy framework requirement).
- New repo `meetmind-web`:

```
meetmind-web/
├─ src/
│  ├─ background/     # service worker (orchestration)
│  ├─ offscreen/      # audio graph, worker hosting
│  ├─ sidepanel/      # UI
│  ├─ workers/        # stt-worker, llm-worker
│  ├─ engines/        # SttEngine, InsightEngine interfaces + impls
│  └─ pipeline/       # transcript window, tick scheduler, insight parser
├─ tests/             # vitest (parser, pipeline)
└─ manifest (via CRXJS config)
```

- **Cross-repo rule:** prompts and the JSON insight schema are copied from the Android
  repo's `strings.xml` / parser. Each copy carries a comment naming the other repo as
  the sibling. If either side changes the schema, the change must be mirrored — there is
  deliberately no shared package in v1.

## 11. Alternatives considered (rejected)

- **Same repo, `extension/` subdirectory** — monorepo costs (entangled CI, LFS bloat for
  web contributors) with zero build-time code sharing to justify them.
- **Kotlin Multiplatform shared `:domain`** — single source of truth for parsing, but
  requires refactoring a shipping Android app's core module and taking on KMP-JS tooling
  friction to share a few hundred lines. Revisit only if the extension grows toward
  feature parity.
- **Local companion app (native messaging)** — best inference performance, but requires
  a desktop install, changing the product story.
- **Chrome built-in Prompt API (Gemini Nano)** — no download management, but limited
  quality/control and Chrome-version dependence.
- **Cloud inference** — abandons the on-device privacy-first premise.
