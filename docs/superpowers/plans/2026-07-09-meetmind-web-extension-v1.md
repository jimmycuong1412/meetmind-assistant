# MeetMind Web (Chrome Extension v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `meetmind-web`, a Manifest V3 Chrome extension that captures meeting-tab audio (MS Teams web as primary target), produces a live transcript via sherpa-onnx WASM, and generates periodic on-device AI insights via WebLLM (WebGPU), shown in a side panel.

**Architecture:** Service worker orchestrates; an offscreen document owns the audio graph and hosts STT/LLM Web Workers; a side panel renders transcript + insight cards. Pure pipeline logic (parser, transcript window, tick scheduler) is TDD'd in Vitest; browser-only layers (capture, WASM engines) get thin adapters + manual verification.

**Tech Stack:** TypeScript (strict), Vite + `@crxjs/vite-plugin`, Vitest, sherpa-onnx WASM (streaming Zipformer EN + Silero VAD), `@mlc-ai/web-llm`, plain DOM side panel.

**Spec:** `docs/superpowers/specs/2026-07-09-chrome-extension-teams-web-design.md` (in the `meetmind-assistant` repo).

## Global Constraints

- **New repo root is `F:\Git\meetmind-web`** — every task's file paths are relative to that root. Task 1 creates the repo. Nothing in the Android repo (`F:\Git\meetmind-assistant`) is modified.
- **Fully in-browser inference.** No cloud calls, no native messaging, no telemetry. The only network requests permitted are model-weight downloads (Hugging Face / MLC CDN) at first run.
- **v1 requires WebGPU** — show the unsupported screen, never a degraded CPU fallback.
- **`minimum_chrome_version: "124"`** (offscreen documents + side panel + `tabCapture.getMediaStreamId` + WebGPU in extension workers).
- **English only** in v1: one STT model, one prompt. JSON field names in the prompt/parser stay English: `"title"`, `"summary"`, `"action_items"`.
- **The insight prompt is a verbatim port** of `prompt_short_meeting` from the Android repo's `strings.xml`; the parser behavior is a verbatim port of `InsightOutputParser.kt`. Do not "improve" either — the test cases pin known production bugs.
- **LLM calls are serialized** — never run two inferences concurrently; a tick is skipped if the previous one is still running.
- **TypeScript strict mode**, no `any` except the two documented `chrome`-API casts (tab-capture `getUserMedia` constraints, Emscripten `Module`).
- Node ≥ 20 for the toolchain. Windows dev box: npm commands run in PowerShell or Git Bash — both fine.
- Conventional Commits (`feat:`, `test:`, `chore:`, `docs:`). Default branch: `main` (new repo; the Android repo's `develop` convention does not carry over).
- **Upstream-verification rule:** Tasks 7 and 8 each begin with a verification step against current upstream releases (sherpa-onnx WASM artifacts, WebLLM model catalog). If an exact artifact/model ID named in this plan no longer exists, pick the closest current equivalent, record the substitution in the README, and continue — the engine interfaces are designed so this never ripples past the task.

---

### Task 1: Repo scaffold — buildable, loadable, testable empty extension

**Files:**
- Create: `package.json`, `tsconfig.json`, `vite.config.ts`, `manifest.config.ts`, `.gitignore`
- Create: `src/background/index.ts`, `src/sidepanel/index.html`, `src/sidepanel/main.ts`, `src/offscreen/index.html`, `src/offscreen/main.ts`
- Create: `tests/smoke.test.ts`
- Create: `public/icons/icon128.png` (any 128×128 placeholder PNG is fine for v1)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a repo where `npm run build` emits `dist/` loadable via `chrome://extensions` → "Load unpacked", and `npm test` runs Vitest. Later tasks add code under `src/` and tests under `tests/`.

- [ ] **Step 1: Create the repo**

```bash
mkdir F:/Git/meetmind-web && cd F:/Git/meetmind-web
git init -b main
npm init -y
npm install -D typescript vite @crxjs/vite-plugin vitest @types/chrome
npm install @mlc-ai/web-llm
```

Note: if `@crxjs/vite-plugin@latest` is incompatible with the installed Vite major, pin the versions the CRXJS README currently documents as its supported pair — that pairing changes over time; the README is authoritative.

- [ ] **Step 2: Write config files**

`.gitignore`:
```
node_modules/
dist/
```

`tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "noEmit": true,
    "types": ["chrome", "vite/client"],
    "lib": ["ES2022", "DOM", "WebWorker"]
  },
  "include": ["src", "tests", "manifest.config.ts", "vite.config.ts"]
}
```

`manifest.config.ts`:
```ts
import { defineManifest } from "@crxjs/vite-plugin";

export default defineManifest({
  manifest_version: 3,
  name: "MeetMind Web",
  version: "0.1.0",
  minimum_chrome_version: "124",
  icons: { "128": "icons/icon128.png" },
  action: { default_title: "MeetMind Web" },
  permissions: ["tabCapture", "sidePanel", "offscreen", "storage"],
  background: { service_worker: "src/background/index.ts", type: "module" },
  side_panel: { default_path: "src/sidepanel/index.html" },
});
```

`vite.config.ts` (the offscreen page is not referenced by the manifest, so it must be an explicit build input):
```ts
import { defineConfig } from "vite";
import { crx } from "@crxjs/vite-plugin";
import manifest from "./manifest.config";

export default defineConfig({
  plugins: [crx({ manifest })],
  build: {
    rollupOptions: {
      input: { offscreen: "src/offscreen/index.html" },
    },
  },
});
```

`package.json` scripts (merge into the generated file):
```json
{
  "scripts": {
    "build": "tsc && vite build",
    "dev": "vite",
    "test": "vitest run"
  }
}
```

- [ ] **Step 3: Write minimal entry points**

`src/background/index.ts`:
```ts
chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(console.error);
```

`src/sidepanel/index.html`:
```html
<!doctype html>
<html>
  <head><meta charset="utf-8" /><title>MeetMind Web</title></head>
  <body>
    <h1>MeetMind Web</h1>
    <script type="module" src="./main.ts"></script>
  </body>
</html>
```

`src/sidepanel/main.ts`:
```ts
console.log("MeetMind Web side panel loaded");
```

`src/offscreen/index.html`:
```html
<!doctype html>
<html>
  <head><meta charset="utf-8" /></head>
  <body><script type="module" src="./main.ts"></script></body>
</html>
```

`src/offscreen/main.ts`:
```ts
console.log("MeetMind Web offscreen document loaded");
```

`tests/smoke.test.ts`:
```ts
import { describe, it, expect } from "vitest";

describe("toolchain", () => {
  it("runs", () => {
    expect(1 + 1).toBe(2);
  });
});
```

- [ ] **Step 4: Verify build + tests**

Run: `npm run build` — Expected: `dist/` created, no TypeScript errors.
Run: `npm test` — Expected: 1 passing test.

- [ ] **Step 5: Verify the extension loads**

Manual: open `chrome://extensions`, enable Developer mode, "Load unpacked" → select `F:\Git\meetmind-web\dist`. Expected: extension appears without manifest errors; clicking the toolbar icon opens the side panel showing "MeetMind Web".

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: scaffold MV3 extension with vite + crxjs + vitest"
```

---

### Task 2: Insight parser (TDD — ported from `InsightOutputParser.kt`)

**Files:**
- Create: `src/pipeline/insightParser.ts`
- Test: `tests/insightParser.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `stripThinkingBlock(text: string): string`
  - `stripCodeFences(text: string): string`
  - `extractJsonField(json: string, field: string): string | null`
  - `extractJsonArray(json: string, field: string): string[]`
  - `parseInsight(rawOutput: string): ParsedInsight` where `interface ParsedInsight { title: string; summary: string; actionItems: string[] }`
  - Task 9 calls `parseInsight`.

The Android test suite pins behaviors that caused production bugs (`<think>` leakage, code fences, unescaped inner quotes, commas inside action items). Port all of them.

- [ ] **Step 1: Write the failing tests**

`tests/insightParser.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import {
  stripThinkingBlock,
  stripCodeFences,
  extractJsonField,
  extractJsonArray,
  parseInsight,
} from "../src/pipeline/insightParser";

describe("stripThinkingBlock", () => {
  it("removes complete think block", () => {
    const input = "<think>Reasoning step 1. Reasoning step 2.</think>\nThe summary.";
    expect(stripThinkingBlock(input)).toBe("The summary.");
  });
  it("leaves text unchanged when no think block", () => {
    expect(stripThinkingBlock("Just a plain summary.")).toBe("Just a plain summary.");
  });
  it("discards everything after unclosed think tag", () => {
    const input = "Header.\n<think>Endless reasoning that never closes…";
    expect(stripThinkingBlock(input)).toBe("Header.");
  });
  it("keeps text on both sides of the block", () => {
    expect(stripThinkingBlock("Before.<think>middle</think>After.")).toBe("Before.After.");
  });
});

describe("stripCodeFences", () => {
  it("removes json fence", () => {
    expect(stripCodeFences('```json\n{"summary": "hello"}\n```')).toBe('{"summary": "hello"}');
  });
  it("removes plain fence", () => {
    expect(stripCodeFences("```\nplain content\n```")).toBe("plain content");
  });
  it("leaves non-fenced text alone", () => {
    expect(stripCodeFences('{"summary": "no fence"}')).toBe('{"summary": "no fence"}');
  });
  it("handles uppercase JSON tag", () => {
    expect(stripCodeFences('```JSON\n{"x": 1}\n```')).toBe('{"x": 1}');
  });
});

describe("extractJsonField", () => {
  it("pulls a simple string value", () => {
    expect(extractJsonField('{"summary": "hello world", "tasks": []}', "summary")).toBe("hello world");
  });
  it("returns null for missing field", () => {
    expect(extractJsonField('{"summary": "x"}', "missing")).toBeNull();
  });
  it("handles inner unescaped quotes followed by content", () => {
    // A small model emitted: "summary": "the "quoted" word in middle"
    // The closing quote of the value is the one followed by , or }.
    const input = '{"summary": "the "quoted" word in middle", "tasks": []}';
    expect(extractJsonField(input, "summary")).toBe("the quoted word in middle");
  });
  it("handles backslash-escaped quotes", () => {
    expect(extractJsonField('{"summary": "she said \\"hi\\""}', "summary")).toBe('she said "hi"');
  });
});

describe("extractJsonArray", () => {
  it("pulls quoted string items", () => {
    expect(extractJsonArray('{"tasks": ["first", "second", "third"]}', "tasks")).toEqual([
      "first",
      "second",
      "third",
    ]);
  });
  it("returns empty for missing field", () => {
    expect(extractJsonArray('{"tasks": []}', "absent")).toEqual([]);
  });
  it("returns empty for empty array", () => {
    expect(extractJsonArray('{"tasks": []}', "tasks")).toEqual([]);
  });
  it("keeps items containing commas as single entries", () => {
    // Commas inside quoted strings must NOT split the item.
    const input = '{"tasks": ["Prepare report, review data", "Send email"]}';
    expect(extractJsonArray(input, "tasks")).toEqual(["Prepare report, review data", "Send email"]);
  });
  it("ignores blank items", () => {
    expect(extractJsonArray('{"tasks": ["valid", "", "  ", "another"]}', "tasks")).toEqual([
      "valid",
      "another",
    ]);
  });
});

describe("parseInsight", () => {
  it("parses a well-formed insight", () => {
    const raw = '{"title": "Sprint Planning", "summary": "The team agreed.", "action_items": ["Ship it"]}';
    expect(parseInsight(raw)).toEqual({
      title: "Sprint Planning",
      summary: "The team agreed.",
      actionItems: ["Ship it"],
    });
  });
  it("falls back to defaults on malformed output", () => {
    const raw = "The model just rambled with no JSON at all.";
    expect(parseInsight(raw)).toEqual({
      title: "Meeting Notes",
      summary: "The model just rambled with no JSON at all.",
      actionItems: [],
    });
  });
  it("handles fenced output with think block", () => {
    const raw = '<think>hmm</think>```json\n{"title": "T", "summary": "S", "action_items": []}\n```';
    expect(parseInsight(raw)).toEqual({ title: "T", summary: "S", actionItems: [] });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run tests/insightParser.test.ts`
Expected: FAIL — cannot resolve `../src/pipeline/insightParser`.

- [ ] **Step 3: Write the implementation**

`src/pipeline/insightParser.ts` (direct port of `InsightOutputParser.kt`; the field scanner deliberately tolerates unescaped inner quotes — a `"` only closes the value when the next non-whitespace char is `,` or `}`):
```ts
export interface ParsedInsight {
  title: string;
  summary: string;
  actionItems: string[];
}

export function stripThinkingBlock(text: string): string {
  const start = text.indexOf("<think>");
  if (start === -1) return text;
  const end = text.indexOf("</think>", start);
  if (end === -1) return text.slice(0, start).trim();
  return (text.slice(0, start) + text.slice(end + "</think>".length)).trim();
}

export function stripCodeFences(text: string): string {
  const trimmed = text.trim();
  const match = trimmed.match(/^```[a-zA-Z]*\s*\n?([\s\S]*?)\n?```$/);
  return match ? match[1].trim() : trimmed;
}

export function extractJsonField(json: string, field: string): string | null {
  const keyPattern = new RegExp(`"${field}"\\s*:\\s*"`);
  const match = keyPattern.exec(json);
  if (!match) return null;
  let i = match.index + match[0].length;
  let out = "";
  while (i < json.length) {
    const c = json[i];
    if (c === "\\" && i + 1 < json.length) {
      out += json[i + 1];
      i += 2;
      continue;
    }
    if (c === '"') {
      const rest = json.slice(i + 1).trimStart();
      if (rest.startsWith(",") || rest.startsWith("}")) return out;
      i++;
      continue;
    }
    out += c;
    i++;
  }
  return out.trim() === "" ? null : out;
}

export function extractJsonArray(json: string, field: string): string[] {
  const match = new RegExp(`"${field}"\\s*:\\s*\\[([^\\]]*)\\]`).exec(json);
  if (!match) return [];
  return splitJsonArrayItems(match[1])
    .map((item) => removeSurroundingQuotes(item.trim()))
    .filter((item) => item.trim() !== "");
}

function removeSurroundingQuotes(s: string): string {
  return s.length >= 2 && s.startsWith('"') && s.endsWith('"') ? s.slice(1, -1) : s;
}

function splitJsonArrayItems(arrayContent: string): string[] {
  const items: string[] = [];
  let current = "";
  let inString = false;
  for (let i = 0; i < arrayContent.length; i++) {
    const c = arrayContent[i];
    if (c === "\\" && inString && i + 1 < arrayContent.length) {
      current += c + arrayContent[i + 1];
      i++;
      continue;
    }
    if (c === '"') {
      inString = !inString;
      current += c;
    } else if (c === "," && !inString) {
      items.push(current);
      current = "";
    } else {
      current += c;
    }
  }
  if (current.trim() !== "") items.push(current);
  return items;
}

export function parseInsight(rawOutput: string): ParsedInsight {
  const cleaned = stripCodeFences(stripThinkingBlock(rawOutput));
  const title = extractJsonField(cleaned, "title") ?? "Meeting Notes";
  const summary = extractJsonField(cleaned, "summary") ?? cleaned;
  const actionItems = extractJsonArray(cleaned, "action_items");
  return { title, summary, actionItems };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run tests/insightParser.test.ts`
Expected: PASS — 19 tests.

- [ ] **Step 5: Commit**

```bash
git add src/pipeline/insightParser.ts tests/insightParser.test.ts
git commit -m "feat: insight parser ported from Android InsightOutputParser with full test suite"
```

---

### Task 3: Transcript store + tick scheduler (TDD)

**Files:**
- Create: `src/pipeline/transcriptStore.ts`, `src/pipeline/tickScheduler.ts`, `src/pipeline/types.ts`
- Test: `tests/transcriptStore.test.ts`, `tests/tickScheduler.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces (used by Task 9's session controller and Task 10's UI):
  - `interface TranscriptSegment { text: string; isFinal: boolean; timestamp: number }`
  - `interface Insight { title: string; summary: string; actionItems: string[]; createdAt: number }`
  - `class TranscriptStore` with `append(segment: TranscriptSegment): void`, `fullText(): string`, `consumeTickWindow(maxChars: number): string | null` (returns `null` when no new final text arrived since the last consume — the "skip empty tick" rule lives here).
  - `class TickScheduler` with `constructor(intervalMs: number, onTick: () => Promise<void>)`, `start(): void`, `stop(): void`. Never overlaps ticks: if `onTick` is still pending when the interval fires, that firing is skipped (the serialized-LLM rule).

- [ ] **Step 1: Write the shared types**

`src/pipeline/types.ts`:
```ts
export interface TranscriptSegment {
  text: string;
  isFinal: boolean;
  timestamp: number;
}

export interface Insight {
  title: string;
  summary: string;
  actionItems: string[];
  createdAt: number;
}
```

- [ ] **Step 2: Write the failing tests**

`tests/transcriptStore.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { TranscriptStore } from "../src/pipeline/transcriptStore";

const seg = (text: string) => ({ text, isFinal: true, timestamp: 0 });

describe("TranscriptStore", () => {
  it("accumulates only final segments into fullText", () => {
    const store = new TranscriptStore();
    store.append(seg("Hello."));
    store.append({ text: "partial…", isFinal: false, timestamp: 0 });
    store.append(seg("World."));
    expect(store.fullText()).toBe("Hello. World.");
  });

  it("returns null from consumeTickWindow when nothing new arrived", () => {
    const store = new TranscriptStore();
    expect(store.consumeTickWindow(1000)).toBeNull();
    store.append(seg("First."));
    expect(store.consumeTickWindow(1000)).toBe("First.");
    // No new final text since last consume:
    expect(store.consumeTickWindow(1000)).toBeNull();
  });

  it("returns a window again once new text arrives", () => {
    const store = new TranscriptStore();
    store.append(seg("First."));
    store.consumeTickWindow(1000);
    store.append(seg("Second."));
    expect(store.consumeTickWindow(1000)).toBe("First. Second.");
  });

  it("caps the window at maxChars, keeping the most recent text", () => {
    const store = new TranscriptStore();
    store.append(seg("A".repeat(50)));
    store.append(seg("B".repeat(50)));
    const window = store.consumeTickWindow(60);
    expect(window).not.toBeNull();
    expect(window!.length).toBeLessThanOrEqual(60);
    expect(window!.endsWith("B".repeat(50))).toBe(true);
  });
});
```

`tests/tickScheduler.test.ts`:
```ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { TickScheduler } from "../src/pipeline/tickScheduler";

describe("TickScheduler", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("fires onTick every interval", async () => {
    const onTick = vi.fn().mockResolvedValue(undefined);
    const s = new TickScheduler(1000, onTick);
    s.start();
    await vi.advanceTimersByTimeAsync(3000);
    expect(onTick).toHaveBeenCalledTimes(3);
    s.stop();
  });

  it("skips a firing while the previous tick is still running", async () => {
    let release!: () => void;
    const onTick = vi.fn(() => new Promise<void>((r) => (release = r)));
    const s = new TickScheduler(1000, onTick);
    s.start();
    await vi.advanceTimersByTimeAsync(1000); // tick 1 starts, never resolves yet
    await vi.advanceTimersByTimeAsync(2000); // two firings while busy → skipped
    expect(onTick).toHaveBeenCalledTimes(1);
    release();
    await vi.advanceTimersByTimeAsync(1000);
    expect(onTick).toHaveBeenCalledTimes(2);
    s.stop();
  });

  it("stops firing after stop()", async () => {
    const onTick = vi.fn().mockResolvedValue(undefined);
    const s = new TickScheduler(1000, onTick);
    s.start();
    await vi.advanceTimersByTimeAsync(1000);
    s.stop();
    await vi.advanceTimersByTimeAsync(5000);
    expect(onTick).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `npx vitest run tests/transcriptStore.test.ts tests/tickScheduler.test.ts`
Expected: FAIL — modules not found.

- [ ] **Step 4: Write the implementations**

`src/pipeline/transcriptStore.ts`:
```ts
import type { TranscriptSegment } from "./types";

export class TranscriptStore {
  private finalTexts: string[] = [];
  private consumedCount = 0;

  append(segment: TranscriptSegment): void {
    if (!segment.isFinal || segment.text.trim() === "") return;
    this.finalTexts.push(segment.text.trim());
  }

  fullText(): string {
    return this.finalTexts.join(" ");
  }

  /** Returns the most recent transcript slice (≤ maxChars), or null if no new
   *  final text arrived since the last successful consume. */
  consumeTickWindow(maxChars: number): string | null {
    if (this.finalTexts.length === this.consumedCount) return null;
    this.consumedCount = this.finalTexts.length;
    const full = this.fullText();
    return full.length <= maxChars ? full : full.slice(full.length - maxChars);
  }
}
```

`src/pipeline/tickScheduler.ts`:
```ts
export class TickScheduler {
  private timer: ReturnType<typeof setInterval> | null = null;
  private busy = false;

  constructor(
    private readonly intervalMs: number,
    private readonly onTick: () => Promise<void>,
  ) {}

  start(): void {
    if (this.timer !== null) return;
    this.timer = setInterval(() => {
      if (this.busy) return;
      this.busy = true;
      this.onTick()
        .catch((err) => console.error("tick failed:", err))
        .finally(() => (this.busy = false));
    }, this.intervalMs);
  }

  stop(): void {
    if (this.timer !== null) clearInterval(this.timer);
    this.timer = null;
  }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `npx vitest run`
Expected: PASS — all suites (smoke + parser + store + scheduler).

- [ ] **Step 6: Commit**

```bash
git add src/pipeline tests/transcriptStore.test.ts tests/tickScheduler.test.ts
git commit -m "feat: transcript store with tick windowing and non-overlapping tick scheduler"
```

---

### Task 4: Messaging protocol + service-worker orchestration

**Files:**
- Create: `src/shared/messages.ts`
- Modify: `src/background/index.ts`

**Interfaces:**
- Consumes: `TranscriptSegment`, `Insight` from `src/pipeline/types.ts`.
- Produces (every later task speaks this protocol):
  - `type SessionState = "idle" | "downloading" | "loading" | "recording" | "stopped"`
  - `type FatalCode = "WEBGPU_UNSUPPORTED" | "CAPTURE_FAILED" | "DOWNLOAD_FAILED" | "ENGINE_FAILED"`
  - `type AppMessage` — discriminated union with a `target: "background" | "offscreen" | "sidepanel"` field (see code). Helpers `sendToOffscreen(msg)`, `sendToSidepanel(msg)`, `sendToBackground(msg)`.
  - Service worker behavior: `START_SESSION` → resolve stream ID → ensure offscreen document → forward `OFFSCREEN_START_CAPTURE`; `STOP_SESSION` / `SET_MIC_ENABLED` → forward to offscreen.

- [ ] **Step 1: Write the protocol module**

`src/shared/messages.ts`:
```ts
import type { TranscriptSegment, Insight } from "../pipeline/types";

export type SessionState = "idle" | "downloading" | "loading" | "recording" | "stopped";
export type FatalCode = "WEBGPU_UNSUPPORTED" | "CAPTURE_FAILED" | "DOWNLOAD_FAILED" | "ENGINE_FAILED";

export type AppMessage =
  // side panel → background
  | { target: "background"; type: "START_SESSION"; tabId: number; micEnabled: boolean }
  | { target: "background"; type: "STOP_SESSION" }
  | { target: "background"; type: "SET_MIC_ENABLED"; enabled: boolean }
  // background → offscreen
  | { target: "offscreen"; type: "OFFSCREEN_START_CAPTURE"; streamId: string; micEnabled: boolean }
  | { target: "offscreen"; type: "OFFSCREEN_STOP_CAPTURE" }
  | { target: "offscreen"; type: "OFFSCREEN_SET_MIC"; enabled: boolean }
  // offscreen → side panel (state + data)
  | { target: "sidepanel"; type: "SEGMENT"; segment: TranscriptSegment }
  | { target: "sidepanel"; type: "INSIGHT"; insight: Insight }
  | { target: "sidepanel"; type: "SESSION_STATE"; state: SessionState }
  | { target: "sidepanel"; type: "DOWNLOAD_PROGRESS"; file: string; received: number; total: number }
  | { target: "sidepanel"; type: "FATAL_ERROR"; code: FatalCode; detail: string };

export function sendToBackground(msg: Extract<AppMessage, { target: "background" }>): Promise<unknown> {
  return chrome.runtime.sendMessage(msg);
}
export function sendToOffscreen(msg: Extract<AppMessage, { target: "offscreen" }>): Promise<unknown> {
  return chrome.runtime.sendMessage(msg);
}
export function sendToSidepanel(msg: Extract<AppMessage, { target: "sidepanel" }>): Promise<unknown> {
  // Side panel may be closed; a rejected sendMessage is expected and harmless.
  return chrome.runtime.sendMessage(msg).catch(() => undefined);
}
```

- [ ] **Step 2: Implement the service worker**

Replace `src/background/index.ts`:
```ts
import { sendToOffscreen, sendToSidepanel, type AppMessage } from "../shared/messages";

chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(console.error);

const OFFSCREEN_URL = "src/offscreen/index.html";

async function ensureOffscreenDocument(): Promise<void> {
  if (await chrome.offscreen.hasDocument()) return;
  await chrome.offscreen.createDocument({
    url: OFFSCREEN_URL,
    reasons: [chrome.offscreen.Reason.USER_MEDIA],
    justification: "Capture tab audio and run on-device STT/LLM inference.",
  });
}

chrome.runtime.onMessage.addListener((msg: AppMessage, _sender, sendResponse) => {
  if (msg.target !== "background") return;
  handle(msg).then(
    () => sendResponse({ ok: true }),
    (err) => {
      console.error("background handler failed:", err);
      sendToSidepanel({
        target: "sidepanel",
        type: "FATAL_ERROR",
        code: "CAPTURE_FAILED",
        detail: String(err),
      });
      sendResponse({ ok: false, error: String(err) });
    },
  );
  return true; // async response
});

async function handle(msg: Extract<AppMessage, { target: "background" }>): Promise<void> {
  switch (msg.type) {
    case "START_SESSION": {
      const streamId = await chrome.tabCapture.getMediaStreamId({ targetTabId: msg.tabId });
      await ensureOffscreenDocument();
      await sendToOffscreen({
        target: "offscreen",
        type: "OFFSCREEN_START_CAPTURE",
        streamId,
        micEnabled: msg.micEnabled,
      });
      break;
    }
    case "STOP_SESSION":
      await sendToOffscreen({ target: "offscreen", type: "OFFSCREEN_STOP_CAPTURE" });
      break;
    case "SET_MIC_ENABLED":
      await sendToOffscreen({ target: "offscreen", type: "OFFSCREEN_SET_MIC", enabled: msg.enabled });
      break;
  }
}
```

- [ ] **Step 3: Add a temporary offscreen echo handler (proves the wiring; replaced in Task 5)**

Replace `src/offscreen/main.ts`:
```ts
import type { AppMessage } from "../shared/messages";

chrome.runtime.onMessage.addListener((msg: AppMessage) => {
  if (msg.target !== "offscreen") return;
  console.log("offscreen received:", msg.type);
});
```

- [ ] **Step 4: Add a temporary start button in the side panel (replaced in Task 10)**

Replace `src/sidepanel/main.ts`:
```ts
import { sendToBackground } from "../shared/messages";

const btn = document.createElement("button");
btn.textContent = "Start (dev)";
btn.onclick = async () => {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.id === undefined) return;
  await sendToBackground({ target: "background", type: "START_SESSION", tabId: tab.id, micEnabled: false });
};
document.body.append(btn);
```

Also add `"tabs"` to `permissions` in `manifest.config.ts` (needed for `chrome.tabs.query` from the side panel):
```ts
permissions: ["tabCapture", "sidePanel", "offscreen", "storage", "tabs"],
```

- [ ] **Step 5: Verify**

Run: `npm run build` — Expected: no TS errors.
Manual: reload the unpacked extension, open any normal website tab (not `chrome://`), open the side panel, click "Start (dev)". Expected: in the offscreen document's console (chrome://extensions → service worker / offscreen inspect views) the log `offscreen received: OFFSCREEN_START_CAPTURE`.

- [ ] **Step 6: Commit**

```bash
git add src/shared/messages.ts src/background/index.ts src/offscreen/main.ts src/sidepanel/main.ts manifest.config.ts
git commit -m "feat: typed message protocol and service-worker session orchestration"
```

---

### Task 5: Offscreen audio capture (tab + optional mic → 16 kHz PCM chunks)

**Files:**
- Create: `src/offscreen/audioCapture.ts`, `src/offscreen/pcm-chunker.ts` (AudioWorklet)
- Modify: `src/offscreen/main.ts`

**Interfaces:**
- Consumes: `OFFSCREEN_START_CAPTURE` / `OFFSCREEN_STOP_CAPTURE` / `OFFSCREEN_SET_MIC` messages (Task 4).
- Produces:
  - `class AudioCapture` with `start(streamId: string, micEnabled: boolean, onPcm: (samples: Float32Array) => void, onStreamEnded: () => void): Promise<void>`, `stop(): void`, `setMicEnabled(enabled: boolean): Promise<void>`.
  - Emits mono Float32 PCM at 16 kHz in 3200-sample chunks (200 ms). Task 7's STT worker consumes these chunks.
  - Routes tab audio back to the speakers (the tabCapture-mutes-the-tab gotcha).

- [ ] **Step 1: Write the AudioWorklet processor**

`src/offscreen/pcm-chunker.ts`:
```ts
// AudioWorklet: accumulates 128-frame render quanta into 3200-sample (200 ms @ 16 kHz)
// chunks and posts them to the main thread.
const CHUNK_SIZE = 3200;

class PcmChunker extends AudioWorkletProcessor {
  private buffer = new Float32Array(CHUNK_SIZE);
  private offset = 0;

  process(inputs: Float32Array[][]): boolean {
    const channel = inputs[0]?.[0];
    if (!channel) return true;
    let read = 0;
    while (read < channel.length) {
      const n = Math.min(channel.length - read, CHUNK_SIZE - this.offset);
      this.buffer.set(channel.subarray(read, read + n), this.offset);
      this.offset += n;
      read += n;
      if (this.offset === CHUNK_SIZE) {
        this.port.postMessage(this.buffer.slice());
        this.offset = 0;
      }
    }
    return true;
  }
}

registerProcessor("pcm-chunker", PcmChunker);
```

Add worklet globals to the TS config since `AudioWorkletProcessor` isn't in `lib.dom`. Create `src/offscreen/worklet.d.ts`:
```ts
declare abstract class AudioWorkletProcessor {
  readonly port: MessagePort;
  abstract process(inputs: Float32Array[][], outputs: Float32Array[][], parameters: Record<string, Float32Array>): boolean;
}
declare function registerProcessor(
  name: string,
  ctor: new () => AudioWorkletProcessor,
): void;
```

- [ ] **Step 2: Write the capture module**

`src/offscreen/audioCapture.ts`:
```ts
export class AudioCapture {
  private ctx: AudioContext | null = null;
  private tabStream: MediaStream | null = null;
  private micStream: MediaStream | null = null;
  private mixer: GainNode | null = null;
  private micSource: MediaStreamAudioSourceNode | null = null;

  async start(
    streamId: string,
    micEnabled: boolean,
    onPcm: (samples: Float32Array) => void,
    onStreamEnded: () => void,
  ): Promise<void> {
    // Chrome-specific constraint shape for consuming a tabCapture stream ID.
    this.tabStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        mandatory: { chromeMediaSource: "tab", chromeMediaSourceId: streamId },
      },
    } as MediaStreamConstraints);

    this.tabStream.getAudioTracks()[0].addEventListener("ended", onStreamEnded);

    this.ctx = new AudioContext({ sampleRate: 16000 });
    const tabSource = this.ctx.createMediaStreamSource(this.tabStream);

    // Route tab audio back to the speakers — tabCapture mutes the tab otherwise.
    tabSource.connect(this.ctx.destination);

    this.mixer = this.ctx.createGain();
    tabSource.connect(this.mixer);
    if (micEnabled) await this.setMicEnabled(true);

    await this.ctx.audioWorklet.addModule(new URL("./pcm-chunker.ts", import.meta.url));
    const chunker = new AudioWorkletNode(this.ctx, "pcm-chunker");
    // Chunker is a sink: connect mixer → chunker, but NOT chunker → destination
    // (its output is silence; connecting it would add nothing).
    this.mixer.connect(chunker);
    chunker.port.onmessage = (e: MessageEvent<Float32Array>) => onPcm(e.data);
  }

  async setMicEnabled(enabled: boolean): Promise<void> {
    if (!this.ctx || !this.mixer) return;
    if (enabled && !this.micStream) {
      this.micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.micSource = this.ctx.createMediaStreamSource(this.micStream);
      this.micSource.connect(this.mixer);
    } else if (!enabled && this.micStream) {
      this.micSource?.disconnect();
      this.micStream.getTracks().forEach((t) => t.stop());
      this.micStream = null;
      this.micSource = null;
    }
  }

  stop(): void {
    this.tabStream?.getTracks().forEach((t) => t.stop());
    this.micStream?.getTracks().forEach((t) => t.stop());
    this.ctx?.close();
    this.ctx = null;
    this.tabStream = null;
    this.micStream = null;
    this.mixer = null;
    this.micSource = null;
  }
}
```

- [ ] **Step 3: Wire into the offscreen message handler**

Replace `src/offscreen/main.ts`:
```ts
import { AudioCapture } from "./audioCapture";
import { sendToSidepanel, type AppMessage } from "../shared/messages";

const capture = new AudioCapture();
let chunkCount = 0; // dev logging; the STT worker replaces onPcm in Task 7

chrome.runtime.onMessage.addListener((msg: AppMessage, _sender, sendResponse) => {
  if (msg.target !== "offscreen") return;
  handle(msg).then(
    () => sendResponse({ ok: true }),
    (err) => {
      console.error("offscreen handler failed:", err);
      sendToSidepanel({
        target: "sidepanel",
        type: "FATAL_ERROR",
        code: "CAPTURE_FAILED",
        detail: String(err),
      });
      sendResponse({ ok: false, error: String(err) });
    },
  );
  return true;
});

async function handle(msg: Extract<AppMessage, { target: "offscreen" }>): Promise<void> {
  switch (msg.type) {
    case "OFFSCREEN_START_CAPTURE":
      await capture.start(
        msg.streamId,
        msg.micEnabled,
        (samples) => {
          if (++chunkCount % 25 === 0) console.log(`pcm chunks: ${chunkCount}`);
        },
        () => {
          capture.stop();
          sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "stopped" });
        },
      );
      sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "recording" });
      break;
    case "OFFSCREEN_STOP_CAPTURE":
      capture.stop();
      sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "stopped" });
      break;
    case "OFFSCREEN_SET_MIC":
      await capture.setMicEnabled(msg.enabled);
      break;
  }
}
```

- [ ] **Step 4: Verify manually**

Run: `npm run build`, reload the extension.
Manual: open a YouTube video (audio playing), open the side panel, click "Start (dev)". Expected, in the offscreen document console: `pcm chunks: 25`, `pcm chunks: 50`, … increasing at ~5 s per 25 chunks; **the tab's audio remains audible** (mute-routing works). Closing the tab logs stream end and the state message.

- [ ] **Step 5: Commit**

```bash
git add src/offscreen manifest.config.ts
git commit -m "feat: offscreen tab-audio capture with mic mixing and 16kHz PCM chunking"
```

---

### Task 6: Model download manager (OPFS, resumable) — TDD for logic, thin OPFS adapter

**Files:**
- Create: `src/engines/downloadPlanner.ts` (pure logic), `src/engines/opfsDownloader.ts` (browser adapter)
- Test: `tests/downloadPlanner.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces (Task 7 uses this for STT model files; WebLLM manages its own weights via the Cache API and does NOT use this):
  - `interface ModelFile { url: string; name: string; sizeBytes: number }`
  - `planDownload(files: ModelFile[], existing: Map<string, number>): DownloadPlan` where `interface DownloadPlan { toFetch: { file: ModelFile; resumeFrom: number }[]; alreadyComplete: ModelFile[]; totalBytes: number; alreadyBytes: number }`
  - `class OpfsDownloader` with `existingSizes(names: string[]): Promise<Map<string, number>>`, `download(plan: DownloadPlan, onProgress: (received: number, total: number, file: string) => void): Promise<void>`, `readFile(name: string): Promise<Uint8Array>`.
  - All files live under OPFS directory `models/`.

- [ ] **Step 1: Write the failing tests for the pure planner**

`tests/downloadPlanner.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { planDownload, type ModelFile } from "../src/engines/downloadPlanner";

const files: ModelFile[] = [
  { url: "https://x/encoder.onnx", name: "encoder.onnx", sizeBytes: 1000 },
  { url: "https://x/tokens.txt", name: "tokens.txt", sizeBytes: 100 },
];

describe("planDownload", () => {
  it("fetches everything when nothing exists", () => {
    const plan = planDownload(files, new Map());
    expect(plan.toFetch).toEqual([
      { file: files[0], resumeFrom: 0 },
      { file: files[1], resumeFrom: 0 },
    ]);
    expect(plan.totalBytes).toBe(1100);
    expect(plan.alreadyBytes).toBe(0);
  });

  it("skips complete files", () => {
    const plan = planDownload(files, new Map([["tokens.txt", 100]]));
    expect(plan.toFetch.map((f) => f.file.name)).toEqual(["encoder.onnx"]);
    expect(plan.alreadyComplete.map((f) => f.name)).toEqual(["tokens.txt"]);
    expect(plan.alreadyBytes).toBe(100);
  });

  it("resumes partial files from their current size", () => {
    const plan = planDownload(files, new Map([["encoder.onnx", 400]]));
    expect(plan.toFetch[0]).toEqual({ file: files[0], resumeFrom: 400 });
    expect(plan.alreadyBytes).toBe(400);
  });

  it("re-downloads files larger than expected (corrupt/stale)", () => {
    const plan = planDownload(files, new Map([["encoder.onnx", 5000]]));
    expect(plan.toFetch[0]).toEqual({ file: files[0], resumeFrom: 0 });
    expect(plan.alreadyBytes).toBe(0);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run tests/downloadPlanner.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the planner**

`src/engines/downloadPlanner.ts`:
```ts
export interface ModelFile {
  url: string;
  name: string;
  sizeBytes: number;
}

export interface DownloadPlan {
  toFetch: { file: ModelFile; resumeFrom: number }[];
  alreadyComplete: ModelFile[];
  totalBytes: number;
  alreadyBytes: number;
}

export function planDownload(files: ModelFile[], existing: Map<string, number>): DownloadPlan {
  const plan: DownloadPlan = { toFetch: [], alreadyComplete: [], totalBytes: 0, alreadyBytes: 0 };
  for (const file of files) {
    plan.totalBytes += file.sizeBytes;
    const have = existing.get(file.name) ?? 0;
    if (have === file.sizeBytes) {
      plan.alreadyComplete.push(file);
      plan.alreadyBytes += file.sizeBytes;
    } else if (have > 0 && have < file.sizeBytes) {
      plan.toFetch.push({ file, resumeFrom: have });
      plan.alreadyBytes += have;
    } else {
      // 0 bytes, or larger than expected (corrupt/stale) → restart from scratch.
      plan.toFetch.push({ file, resumeFrom: 0 });
    }
  }
  return plan;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run tests/downloadPlanner.test.ts`
Expected: PASS — 4 tests.

- [ ] **Step 5: Implement the OPFS adapter (browser-only, no unit test)**

`src/engines/opfsDownloader.ts`:
```ts
import type { DownloadPlan } from "./downloadPlanner";

const DIR = "models";

async function modelsDir(): Promise<FileSystemDirectoryHandle> {
  const root = await navigator.storage.getDirectory();
  return root.getDirectoryHandle(DIR, { create: true });
}

export class OpfsDownloader {
  async existingSizes(names: string[]): Promise<Map<string, number>> {
    const dir = await modelsDir();
    const sizes = new Map<string, number>();
    for (const name of names) {
      try {
        const handle = await dir.getFileHandle(name);
        sizes.set(name, (await handle.getFile()).size);
      } catch {
        // file doesn't exist — leave unset
      }
    }
    return sizes;
  }

  async download(
    plan: DownloadPlan,
    onProgress: (received: number, total: number, file: string) => void,
  ): Promise<void> {
    const dir = await modelsDir();
    let received = plan.alreadyBytes;
    for (const { file, resumeFrom } of plan.toFetch) {
      const headers: HeadersInit = resumeFrom > 0 ? { Range: `bytes=${resumeFrom}-` } : {};
      const res = await fetch(file.url, { headers });
      if (!res.ok || !res.body) throw new Error(`download failed: ${file.url} → HTTP ${res.status}`);
      // A server ignoring Range returns 200 with the full body → restart the file.
      const effectiveOffset = res.status === 206 ? resumeFrom : 0;
      if (effectiveOffset === 0 && resumeFrom > 0) received -= resumeFrom;

      const handle = await dir.getFileHandle(file.name, { create: true });
      const writable = await handle.createWritable({ keepExistingData: effectiveOffset > 0 });
      if (effectiveOffset > 0) await writable.seek(effectiveOffset);

      const reader = res.body.getReader();
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        await writable.write(value);
        received += value.byteLength;
        onProgress(received, plan.totalBytes, file.name);
      }
      await writable.close();
    }
  }

  async readFile(name: string): Promise<Uint8Array> {
    const dir = await modelsDir();
    const handle = await dir.getFileHandle(name);
    return new Uint8Array(await (await handle.getFile()).arrayBuffer());
  }
}
```

- [ ] **Step 6: Build + commit**

Run: `npm run build` — Expected: no TS errors.

```bash
git add src/engines tests/downloadPlanner.test.ts
git commit -m "feat: resumable OPFS model downloader with tested download planner"
```

---

### Task 7: STT engine — sherpa-onnx WASM streaming recognizer in a worker

**Files:**
- Create: `src/engines/sttEngine.ts` (interface + worker-client impl), `src/workers/stt-worker.ts`, `src/engines/sttModelManifest.ts`
- Create: `public/sherpa/` (vendored WASM glue — see Step 0)
- Modify: `src/offscreen/main.ts`

**Interfaces:**
- Consumes: `OpfsDownloader` + `planDownload` (Task 6), PCM chunks from `AudioCapture` (Task 5), `TranscriptSegment` (Task 3).
- Produces (Task 9 consumes):
  - `interface SttEngine { init(onProgress: (received: number, total: number, file: string) => void): Promise<void>; acceptPcm(samples: Float32Array): void; onSegment: (segment: TranscriptSegment) => void; dispose(): void }`
  - `class SherpaSttEngine implements SttEngine` — spawns `stt-worker`, forwards PCM, surfaces final segments.

- [ ] **Step 0: Verify upstream artifacts (per the Global Constraints verification rule)**

1. Check sherpa-onnx GitHub releases (`k2-fsa/sherpa-onnx`) for the current **WASM ASR** artifact and its JS API file (historically `sherpa-onnx-wasm-main-asr.js` + `.wasm`, built with streaming-zipformer support). Download and vendor the JS glue + `.wasm` into `public/sherpa/`, and record the exact release tag in `public/sherpa/VERSION.txt`.
2. Check the Hugging Face repo `csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26` (or the closest current streaming-zipformer English model) for exact file names and byte sizes; fill them into `sttModelManifest.ts` below. Expected files: `encoder-*.int8.onnx`, `decoder-*.onnx`, `joiner-*.int8.onnx`, `tokens.txt`.
3. Confirm the vendored glue exposes a recognizer-creation API (historically `createOnlineRecognizer(Module, config)`) and adjust the worker code below to the actual signatures. **If the API differs, adapt the worker only — `SttEngine` must not change.**

- [ ] **Step 1: Write the model manifest**

`src/engines/sttModelManifest.ts` (sizes are placeholders **verified and corrected in Step 0** — the planner needs real byte counts for resume/progress):
```ts
import type { ModelFile } from "./downloadPlanner";

const HF = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main";

// Byte sizes MUST match the actual files (Step 0 verification) — the download
// planner uses them for resume and progress computation.
export const STT_MODEL_FILES: ModelFile[] = [
  { url: `${HF}/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx`, name: "stt-encoder.onnx", sizeBytes: 0 /* Step 0 */ },
  { url: `${HF}/decoder-epoch-99-avg-1-chunk-16-left-128.onnx`, name: "stt-decoder.onnx", sizeBytes: 0 /* Step 0 */ },
  { url: `${HF}/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx`, name: "stt-joiner.onnx", sizeBytes: 0 /* Step 0 */ },
  { url: `${HF}/tokens.txt`, name: "stt-tokens.txt", sizeBytes: 0 /* Step 0 */ },
];
```

- [ ] **Step 2: Write the STT worker**

`src/workers/stt-worker.ts` — receives model bytes once, then a stream of PCM chunks; posts final segments on endpoint detection. The sherpa-onnx WASM glue is loaded via `importScripts` from the vendored `public/sherpa/` files (classic worker):
```ts
// Classic worker (importScripts) because Emscripten glue is not an ES module.
// Messages in:  { type: "INIT", files: { name: string; data: Uint8Array }[] }
//               { type: "PCM", samples: Float32Array }
//               { type: "DISPOSE" }
// Messages out: { type: "READY" }
//               { type: "SEGMENT", text: string, isFinal: boolean, timestamp: number }
//               { type: "ERROR", detail: string }

declare function importScripts(...urls: string[]): void;
declare const Module: EmscriptenModule; // provided by the vendored glue
interface EmscriptenModule {
  FS: { writeFile(path: string, data: Uint8Array): void };
  [key: string]: unknown;
}
// createOnlineRecognizer comes from the vendored sherpa glue — verify the exact
// name/signature in Step 0 and adjust here if upstream renamed it.
declare function createOnlineRecognizer(module: EmscriptenModule, config: unknown): SherpaRecognizer;
interface SherpaRecognizer {
  createStream(): SherpaStream;
  isReady(s: SherpaStream): boolean;
  decode(s: SherpaStream): void;
  isEndpoint(s: SherpaStream): boolean;
  getResult(s: SherpaStream): { text: string };
  reset(s: SherpaStream): void;
}
interface SherpaStream {
  acceptWaveform(sampleRate: number, samples: Float32Array): void;
}

let recognizer: SherpaRecognizer | null = null;
let stream: SherpaStream | null = null;
let lastText = "";

self.onmessage = (e: MessageEvent) => {
  const msg = e.data;
  try {
    if (msg.type === "INIT") {
      importScripts(chrome.runtime.getURL("sherpa/sherpa-onnx-wasm-main-asr.js"));
      for (const f of msg.files) Module.FS.writeFile(`/${f.name}`, f.data);
      recognizer = createOnlineRecognizer(Module, {
        modelConfig: {
          transducer: { encoder: "/stt-encoder.onnx", decoder: "/stt-decoder.onnx", joiner: "/stt-joiner.onnx" },
          tokens: "/stt-tokens.txt",
        },
        enableEndpoint: true,
        // Endpoint tuning ported from the Android app's VAD experience:
        // trailing silence ends an utterance.
        rule1MinTrailingSilence: 2.4,
        rule2MinTrailingSilence: 1.2,
        rule3MinUtteranceLength: 20,
      });
      stream = recognizer.createStream();
      self.postMessage({ type: "READY" });
    } else if (msg.type === "PCM" && recognizer && stream) {
      stream.acceptWaveform(16000, msg.samples);
      while (recognizer.isReady(stream)) recognizer.decode(stream);
      const text = recognizer.getResult(stream).text.trim();
      if (text !== "" && text !== lastText) {
        lastText = text;
        self.postMessage({ type: "SEGMENT", text, isFinal: false, timestamp: Date.now() });
      }
      if (recognizer.isEndpoint(stream)) {
        if (text !== "") self.postMessage({ type: "SEGMENT", text, isFinal: true, timestamp: Date.now() });
        recognizer.reset(stream);
        lastText = "";
      }
    } else if (msg.type === "DISPOSE") {
      self.close();
    }
  } catch (err) {
    self.postMessage({ type: "ERROR", detail: String(err) });
  }
};
```

- [ ] **Step 3: Write the engine client**

`src/engines/sttEngine.ts`:
```ts
import type { TranscriptSegment } from "../pipeline/types";
import { planDownload } from "./downloadPlanner";
import { OpfsDownloader } from "./opfsDownloader";
import { STT_MODEL_FILES } from "./sttModelManifest";

export interface SttEngine {
  init(onProgress: (received: number, total: number, file: string) => void): Promise<void>;
  acceptPcm(samples: Float32Array): void;
  onSegment: (segment: TranscriptSegment) => void;
  dispose(): void;
}

export class SherpaSttEngine implements SttEngine {
  onSegment: (segment: TranscriptSegment) => void = () => {};
  private worker: Worker | null = null;

  async init(onProgress: (received: number, total: number, file: string) => void): Promise<void> {
    const downloader = new OpfsDownloader();
    const names = STT_MODEL_FILES.map((f) => f.name);
    const plan = planDownload(STT_MODEL_FILES, await downloader.existingSizes(names));
    await downloader.download(plan, onProgress);

    const files = await Promise.all(
      STT_MODEL_FILES.map(async (f) => ({ name: f.name, data: await downloader.readFile(f.name) })),
    );

    this.worker = new Worker(new URL("../workers/stt-worker.ts", import.meta.url));
    const ready = new Promise<void>((resolve, reject) => {
      this.worker!.onmessage = (e) => {
        if (e.data.type === "READY") resolve();
        else if (e.data.type === "ERROR") reject(new Error(e.data.detail));
      };
    });
    this.worker.postMessage({ type: "INIT", files }, files.map((f) => f.data.buffer));
    await ready;

    this.worker.onmessage = (e) => {
      if (e.data.type === "SEGMENT") {
        this.onSegment({ text: e.data.text, isFinal: e.data.isFinal, timestamp: e.data.timestamp });
      } else if (e.data.type === "ERROR") {
        console.error("stt worker error:", e.data.detail);
      }
    };
  }

  acceptPcm(samples: Float32Array): void {
    this.worker?.postMessage({ type: "PCM", samples }, [samples.buffer]);
  }

  dispose(): void {
    this.worker?.postMessage({ type: "DISPOSE" });
    this.worker?.terminate();
    this.worker = null;
  }
}
```

Note: the vendored `.wasm`/`.js` in `public/sherpa/` must be listed in `web_accessible_resources` if the worker loads them via `chrome.runtime.getURL`. Add to `manifest.config.ts`:
```ts
web_accessible_resources: [
  { resources: ["sherpa/*"], matches: ["<all_urls>"] },
],
```

- [ ] **Step 4: Wire STT into the offscreen document for a live-caption smoke test**

In `src/offscreen/main.ts`, replace the dev chunk counter: create `const stt = new SherpaSttEngine()` at module level; in `OFFSCREEN_START_CAPTURE`, before `capture.start(...)`:
```ts
sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "downloading" });
await stt.init((received, total, file) =>
  sendToSidepanel({ target: "sidepanel", type: "DOWNLOAD_PROGRESS", file, received, total }),
);
```
and pass `(samples) => stt.acceptPcm(samples)` as the `onPcm` callback. Forward segments:
```ts
stt.onSegment = (segment) => sendToSidepanel({ target: "sidepanel", type: "SEGMENT", segment });
```
In `OFFSCREEN_STOP_CAPTURE`, also call `stt.dispose()`.

- [ ] **Step 5: Verify manually (the critical integration checkpoint)**

Run: `npm run build`, reload extension.
Manual: open a YouTube video with clear English speech, side panel → "Start (dev)". Expected in the side panel console (`chrome.runtime.onMessage` logging or temporarily `console.log` in the panel): `DOWNLOAD_PROGRESS` events on first run, then `SEGMENT` messages whose `text` roughly matches the spoken audio, with `isFinal: true` segments arriving at pauses. **This step is the go/no-go for the sherpa-onnx WASM approach** — if the vendored artifact cannot be made to work here, stop and swap the `SttEngine` implementation (the interface holds; candidate fallback: whisper/moonshine via transformers.js) before proceeding.

- [ ] **Step 6: Commit**

```bash
git add src/engines src/workers public/sherpa manifest.config.ts src/offscreen/main.ts
git commit -m "feat: sherpa-onnx WASM streaming STT engine with OPFS model download"
```

---

### Task 8: LLM engine — WebLLM (WebGPU) in a worker + capability check

**Files:**
- Create: `src/engines/insightEngine.ts`, `src/workers/llm-worker.ts`, `src/shared/prompt.ts`, `src/shared/webgpu.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces (Task 9 consumes):
  - `interface InsightEngine { init(onProgress: (text: string) => void): Promise<void>; generateInsight(context: string): Promise<string>; dispose(): void }` — returns the **raw** model output; parsing is the pipeline's job.
  - `class WebLlmInsightEngine implements InsightEngine`
  - `SHORT_MEETING_PROMPT: string` (verbatim port from Android `strings.xml`)
  - `isWebGpuAvailable(): Promise<boolean>`

- [ ] **Step 0: Verify the model catalog (per the Global Constraints verification rule)**

Run in the repo:
```bash
node -e "import('@mlc-ai/web-llm').then(m => console.log(m.prebuiltAppConfig.model_list.map(x => x.model_id).filter(id => /gemma|qwen/i.test(id)).join('\n')))"
```
Pick the smallest current **Gemma instruct** model ≥1B (expected: a `gemma-3-1b-it` or `gemma-2-2b-it` quantized variant, e.g. `gemma-2-2b-it-q4f16_1-MLC`). Set `MODEL_ID` below to the verified ID and record the choice in the README. If no Gemma fits, a Qwen ~1.5B instruct variant is the approved fallback.

- [ ] **Step 1: Write the prompt module (verbatim port — do not edit the wording)**

`src/shared/prompt.ts`:
```ts
// Verbatim port of prompt_short_meeting from the Android app's strings.xml
// (meetmind-assistant repo). JSON field names stay English for stable parsing.
export const SHORT_MEETING_PROMPT =
  "You are a meeting assistant analyzing discussions.\n" +
  "Write a title (max 8 words), a summary of key decisions and discussion points (at least 3-5 sentences, proportional to input length), and a list of actionable tasks.\n" +
  "Output ONLY valid JSON with this exact structure. No markdown. No code fences. No backticks:\n" +
  '{"title": "...", "summary": "...", "action_items": ["...", "..."]}\n' +
  "Be direct. Extract actionable tasks clearly.";
```

- [ ] **Step 2: Write the WebGPU check**

`src/shared/webgpu.ts`:
```ts
export async function isWebGpuAvailable(): Promise<boolean> {
  const gpu = (navigator as Navigator & { gpu?: { requestAdapter(): Promise<unknown | null> } }).gpu;
  if (!gpu) return false;
  try {
    return (await gpu.requestAdapter()) !== null;
  } catch {
    return false;
  }
}
```

- [ ] **Step 3: Write the LLM worker and engine**

`src/workers/llm-worker.ts` (WebLLM's stock worker pattern):
```ts
import { WebWorkerMLCEngineHandler } from "@mlc-ai/web-llm";

const handler = new WebWorkerMLCEngineHandler();
self.onmessage = (msg: MessageEvent) => handler.onmessage(msg);
```

`src/engines/insightEngine.ts`:
```ts
import { CreateWebWorkerMLCEngine, type MLCEngineInterface } from "@mlc-ai/web-llm";
import { SHORT_MEETING_PROMPT } from "../shared/prompt";

// Verified against prebuiltAppConfig in Task 8 Step 0 — update if the catalog moved.
const MODEL_ID = "gemma-2-2b-it-q4f16_1-MLC";

export interface InsightEngine {
  init(onProgress: (text: string) => void): Promise<void>;
  generateInsight(context: string): Promise<string>;
  dispose(): void;
}

export class WebLlmInsightEngine implements InsightEngine {
  private engine: MLCEngineInterface | null = null;

  async init(onProgress: (text: string) => void): Promise<void> {
    this.engine = await CreateWebWorkerMLCEngine(
      new Worker(new URL("../workers/llm-worker.ts", import.meta.url), { type: "module" }),
      MODEL_ID,
      { initProgressCallback: (p) => onProgress(p.text) },
    );
  }

  async generateInsight(context: string): Promise<string> {
    if (!this.engine) throw new Error("InsightEngine not initialized");
    const res = await this.engine.chat.completions.create({
      messages: [
        { role: "system", content: SHORT_MEETING_PROMPT },
        { role: "user", content: context },
      ],
      temperature: 0.7,
      max_tokens: 512,
    });
    return res.choices[0]?.message?.content ?? "";
  }

  dispose(): void {
    this.engine?.unload();
    this.engine = null;
  }
}
```

- [ ] **Step 4: Smoke-test in the offscreen console**

Run: `npm run build`, reload extension. Temporarily expose the engine in `src/offscreen/main.ts`:
```ts
import { WebLlmInsightEngine } from "../engines/insightEngine";
(globalThis as Record<string, unknown>).__testLlm = async () => {
  const e = new WebLlmInsightEngine();
  await e.init(console.log);
  console.log(await e.generateInsight("Alice said the release slips to Friday. Bob will update the customer."));
};
```
Manual: trigger the offscreen document (start a dev session), open its console, run `__testLlm()`. Expected: progress logs (first run downloads weights), then a JSON string containing `"title"`, `"summary"`, `"action_items"`. Remove the `__testLlm` block after verifying.

- [ ] **Step 5: Commit**

```bash
git add src/engines/insightEngine.ts src/workers/llm-worker.ts src/shared/prompt.ts src/shared/webgpu.ts
git commit -m "feat: WebLLM insight engine with ported meeting prompt and WebGPU check"
```

---

### Task 9: Session controller — wire capture → STT → transcript → tick → LLM → parser

**Files:**
- Create: `src/offscreen/session.ts`
- Modify: `src/offscreen/main.ts` (delegate everything to the controller)

**Interfaces:**
- Consumes: `AudioCapture` (5), `TranscriptStore`/`TickScheduler` (3), `SherpaSttEngine` (7), `WebLlmInsightEngine` (8), `parseInsight` (2), messages (4).
- Produces: `class SessionController` with `start(streamId: string, micEnabled: boolean): Promise<void>`, `stop(): void`, `setMicEnabled(enabled: boolean): Promise<void>`. `src/offscreen/main.ts` becomes a thin message→controller adapter.

Constants (from the spec): tick interval **default 60 s, user-configurable** (read from `chrome.storage.local` key `tickIntervalSeconds` at session start; Task 10 adds the setting UI), context window **6 000 chars** (~1.5k tokens, safely inside a small model's context alongside the prompt).

- [ ] **Step 1: Write the controller**

`src/offscreen/session.ts`:
```ts
import { AudioCapture } from "./audioCapture";
import { TranscriptStore } from "../pipeline/transcriptStore";
import { TickScheduler } from "../pipeline/tickScheduler";
import { parseInsight } from "../pipeline/insightParser";
import { SherpaSttEngine } from "../engines/sttEngine";
import { WebLlmInsightEngine } from "../engines/insightEngine";
import { sendToSidepanel } from "../shared/messages";

const DEFAULT_TICK_INTERVAL_S = 60;
const CONTEXT_WINDOW_CHARS = 6_000;

export class SessionController {
  private capture = new AudioCapture();
  private stt = new SherpaSttEngine();
  private llm = new WebLlmInsightEngine();
  private store = new TranscriptStore();
  private scheduler: TickScheduler | null = null;
  private running = false;

  async start(streamId: string, micEnabled: boolean): Promise<void> {
    if (this.running) return;
    this.running = true;

    const { tickIntervalSeconds } = await chrome.storage.local.get({
      tickIntervalSeconds: DEFAULT_TICK_INTERVAL_S,
    });
    const scheduler = new TickScheduler(tickIntervalSeconds * 1000, () => this.tick());
    this.scheduler = scheduler;

    sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "downloading" });
    await this.stt.init((received, total, file) =>
      sendToSidepanel({ target: "sidepanel", type: "DOWNLOAD_PROGRESS", file, received, total }),
    );

    sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "loading" });
    await this.llm.init(() => {});

    this.stt.onSegment = (segment) => {
      this.store.append(segment);
      sendToSidepanel({ target: "sidepanel", type: "SEGMENT", segment });
    };

    await this.capture.start(
      streamId,
      micEnabled,
      (samples) => this.stt.acceptPcm(samples),
      () => this.handleStreamEnded(),
    );

    scheduler.start();
    sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "recording" });
  }

  private async tick(): Promise<void> {
    const window = this.store.consumeTickWindow(CONTEXT_WINDOW_CHARS);
    if (window === null) return; // no new final text since last tick
    const raw = await this.llm.generateInsight(window);
    const parsed = parseInsight(raw);
    sendToSidepanel({
      target: "sidepanel",
      type: "INSIGHT",
      insight: { ...parsed, createdAt: Date.now() },
    });
  }

  private handleStreamEnded(): void {
    // Meeting tab closed / capture ended: finalize gracefully, keep panel content.
    this.stop();
  }

  stop(): void {
    if (!this.running) return;
    this.running = false;
    this.scheduler?.stop();
    this.scheduler = null;
    this.capture.stop();
    this.stt.dispose();
    this.llm.dispose();
    sendToSidepanel({ target: "sidepanel", type: "SESSION_STATE", state: "stopped" });
  }

  setMicEnabled(enabled: boolean): Promise<void> {
    return this.capture.setMicEnabled(enabled);
  }
}
```

- [ ] **Step 2: Reduce `src/offscreen/main.ts` to an adapter**

```ts
import { SessionController } from "./session";
import { sendToSidepanel, type AppMessage } from "../shared/messages";

const session = new SessionController();

chrome.runtime.onMessage.addListener((msg: AppMessage, _sender, sendResponse) => {
  if (msg.target !== "offscreen") return;
  handle(msg).then(
    () => sendResponse({ ok: true }),
    (err) => {
      console.error("offscreen handler failed:", err);
      sendToSidepanel({
        target: "sidepanel",
        type: "FATAL_ERROR",
        code: "ENGINE_FAILED",
        detail: String(err),
      });
      sendResponse({ ok: false, error: String(err) });
    },
  );
  return true;
});

async function handle(msg: Extract<AppMessage, { target: "offscreen" }>): Promise<void> {
  switch (msg.type) {
    case "OFFSCREEN_START_CAPTURE":
      await session.start(msg.streamId, msg.micEnabled);
      break;
    case "OFFSCREEN_STOP_CAPTURE":
      session.stop();
      break;
    case "OFFSCREEN_SET_MIC":
      await session.setMicEnabled(msg.enabled);
      break;
  }
}
```

- [ ] **Step 3: Verify end-to-end in the console**

Run: `npm run build`, reload, YouTube English speech tab, "Start (dev)".
Expected: `SEGMENT` messages stream; after ~60 s an `INSIGHT` message arrives whose payload has non-empty `title`/`summary`. (For faster verification, temporarily set `DEFAULT_TICK_INTERVAL_S = 20`, verify, set it back — or once Task 10 lands, just pick 30 s in the UI.)

- [ ] **Step 4: Run the full unit suite**

Run: `npm test` — Expected: all suites PASS (nothing in this task should break pure logic).

- [ ] **Step 5: Commit**

```bash
git add src/offscreen
git commit -m "feat: session controller wiring capture, STT, tick scheduler, LLM and parser"
```

---

### Task 10: Side panel UI

**Files:**
- Create: `src/sidepanel/style.css`
- Modify: `src/sidepanel/index.html`, `src/sidepanel/main.ts`

**Interfaces:**
- Consumes: all `target: "sidepanel"` messages (4), `sendToBackground` (4), `isWebGpuAvailable` (8).
- Produces: the complete v1 UI — no other task depends on it.

Screens/states: **unsupported** (no WebGPU), **idle** (Start button + mic toggle), **downloading** (progress bar), **loading**, **recording** (live transcript + insight cards + Stop), **stopped** (content retained + copy/download buttons).

- [ ] **Step 1: Write the markup**

`src/sidepanel/index.html`:
```html
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <title>MeetMind Web</title>
    <link rel="stylesheet" href="./style.css" />
  </head>
  <body>
    <header>
      <h1>MeetMind Web</h1>
      <span id="state-badge" class="badge">idle</span>
    </header>

    <section id="screen-unsupported" hidden>
      <p><strong>WebGPU is not available on this device.</strong></p>
      <p>MeetMind Web runs its AI fully on your device and requires WebGPU
         (Chrome 124+ with a supported GPU). No data ever leaves your machine.</p>
    </section>

    <section id="screen-main" hidden>
      <div class="controls">
        <button id="btn-start">Start</button>
        <button id="btn-stop" hidden>Stop</button>
        <label><input type="checkbox" id="chk-mic" /> Include my microphone</label>
        <label>Insight every
          <select id="sel-interval">
            <option value="30">30 s</option>
            <option value="60" selected>60 s</option>
            <option value="120">2 min</option>
          </select>
        </label>
      </div>

      <div id="download" hidden>
        <p id="download-label">Downloading models…</p>
        <progress id="download-bar" max="100" value="0"></progress>
      </div>

      <h2>Insights</h2>
      <div id="insights" class="cards"></div>

      <h2>Transcript</h2>
      <div id="transcript"></div>
      <div class="controls">
        <button id="btn-copy" disabled>Copy transcript</button>
        <button id="btn-download" disabled>Download .txt</button>
      </div>
    </section>

    <p id="error" class="error" hidden></p>
    <script type="module" src="./main.ts"></script>
  </body>
</html>
```

- [ ] **Step 2: Write the styles**

`src/sidepanel/style.css`:
```css
:root { font-family: system-ui, sans-serif; font-size: 14px; }
body { margin: 0; padding: 12px; }
header { display: flex; align-items: center; gap: 8px; }
h1 { font-size: 16px; margin: 0; flex: 1; }
h2 { font-size: 13px; text-transform: uppercase; color: #666; margin: 16px 0 6px; }
.badge { background: #eee; border-radius: 10px; padding: 2px 10px; font-size: 12px; }
.badge.recording { background: #fde8e8; color: #b91c1c; }
.controls { display: flex; gap: 8px; align-items: center; margin: 8px 0; flex-wrap: wrap; }
button { padding: 6px 14px; cursor: pointer; }
.cards { display: flex; flex-direction: column; gap: 8px; }
.card { border: 1px solid #ddd; border-radius: 8px; padding: 10px; }
.card h3 { margin: 0 0 6px; font-size: 14px; }
.card ul { margin: 6px 0 0; padding-left: 18px; }
#transcript { white-space: pre-wrap; max-height: 40vh; overflow-y: auto;
  border: 1px solid #eee; border-radius: 8px; padding: 8px; min-height: 60px; }
#transcript .partial { color: #999; }
.error { color: #b91c1c; }
progress { width: 100%; }
@media (prefers-color-scheme: dark) {
  body { background: #1b1b1f; color: #e4e4e7; }
  .badge { background: #333; }
  .card, #transcript { border-color: #3a3a40; }
  h2 { color: #9a9aa3; }
}
```

- [ ] **Step 3: Write the panel logic**

Replace `src/sidepanel/main.ts`:
```ts
import { sendToBackground, type AppMessage, type SessionState } from "../shared/messages";
import { isWebGpuAvailable } from "../shared/webgpu";
import type { Insight } from "../pipeline/types";

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;

const stateBadge = $<HTMLSpanElement>("state-badge");
const btnStart = $<HTMLButtonElement>("btn-start");
const btnStop = $<HTMLButtonElement>("btn-stop");
const chkMic = $<HTMLInputElement>("chk-mic");
const download = $<HTMLDivElement>("download");
const downloadLabel = $<HTMLParagraphElement>("download-label");
const downloadBar = $<HTMLProgressElement>("download-bar");
const insightsEl = $<HTMLDivElement>("insights");
const transcriptEl = $<HTMLDivElement>("transcript");
const btnCopy = $<HTMLButtonElement>("btn-copy");
const btnDownload = $<HTMLButtonElement>("btn-download");
const errorEl = $<HTMLParagraphElement>("error");

const finalLines: string[] = [];
let partialLine = "";

init();

async function init(): Promise<void> {
  if (!(await isWebGpuAvailable())) {
    $<HTMLElement>("screen-unsupported").hidden = false;
    return;
  }
  $<HTMLElement>("screen-main").hidden = false;

  const stored = await chrome.storage.local.get({ micEnabled: false, tickIntervalSeconds: 60 });
  chkMic.checked = stored.micEnabled;
  const selInterval = $<HTMLSelectElement>("sel-interval");
  selInterval.value = String(stored.tickIntervalSeconds);
  // Applies at the next session start (the controller reads it in start()).
  selInterval.onchange = () =>
    chrome.storage.local.set({ tickIntervalSeconds: Number(selInterval.value) });

  btnStart.onclick = async () => {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab?.id === undefined) return showError("No active tab to capture.");
    errorEl.hidden = true;
    await sendToBackground({
      target: "background",
      type: "START_SESSION",
      tabId: tab.id,
      micEnabled: chkMic.checked,
    });
  };
  btnStop.onclick = () => sendToBackground({ target: "background", type: "STOP_SESSION" });
  chkMic.onchange = async () => {
    await chrome.storage.local.set({ micEnabled: chkMic.checked });
    await sendToBackground({ target: "background", type: "SET_MIC_ENABLED", enabled: chkMic.checked });
  };
  btnCopy.onclick = () => navigator.clipboard.writeText(fullTranscript());
  btnDownload.onclick = () => {
    const blob = new Blob([fullTranscript()], { type: "text/plain" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `meetmind-transcript-${new Date().toISOString().slice(0, 19).replaceAll(":", "-")}.txt`;
    a.click();
    URL.revokeObjectURL(a.href);
  };
}

chrome.runtime.onMessage.addListener((msg: AppMessage) => {
  if (msg.target !== "sidepanel") return;
  switch (msg.type) {
    case "SESSION_STATE":
      applyState(msg.state);
      break;
    case "DOWNLOAD_PROGRESS":
      download.hidden = false;
      downloadLabel.textContent = `Downloading ${msg.file}…`;
      downloadBar.value = msg.total > 0 ? (msg.received / msg.total) * 100 : 0;
      break;
    case "SEGMENT":
      if (msg.segment.isFinal) {
        finalLines.push(msg.segment.text);
        partialLine = "";
      } else {
        partialLine = msg.segment.text;
      }
      renderTranscript();
      break;
    case "INSIGHT":
      renderInsight(msg.insight);
      break;
    case "FATAL_ERROR":
      showError(`${msg.code}: ${msg.detail}`);
      applyState("idle");
      break;
  }
});

function applyState(state: SessionState): void {
  stateBadge.textContent = state;
  stateBadge.className = `badge ${state === "recording" ? "recording" : ""}`;
  btnStart.hidden = state === "recording" || state === "downloading" || state === "loading";
  btnStop.hidden = !btnStart.hidden;
  if (state === "recording" || state === "stopped") download.hidden = true;
  const hasContent = finalLines.length > 0;
  btnCopy.disabled = !hasContent;
  btnDownload.disabled = !hasContent;
}

function renderTranscript(): void {
  transcriptEl.textContent = finalLines.join("\n");
  if (partialLine !== "") {
    const span = document.createElement("span");
    span.className = "partial";
    span.textContent = (finalLines.length ? "\n" : "") + partialLine;
    transcriptEl.append(span);
  }
  transcriptEl.scrollTop = transcriptEl.scrollHeight;
  btnCopy.disabled = finalLines.length === 0;
  btnDownload.disabled = finalLines.length === 0;
}

function renderInsight(insight: Insight): void {
  const card = document.createElement("div");
  card.className = "card";
  const h = document.createElement("h3");
  h.textContent = insight.title;
  const p = document.createElement("p");
  p.textContent = insight.summary;
  card.append(h, p);
  if (insight.actionItems.length > 0) {
    const ul = document.createElement("ul");
    for (const item of insight.actionItems) {
      const li = document.createElement("li");
      li.textContent = item;
      ul.append(li);
    }
    card.append(ul);
  }
  insightsEl.prepend(card); // newest first
}

function fullTranscript(): string {
  return finalLines.join("\n");
}

function showError(text: string): void {
  errorEl.textContent = text;
  errorEl.hidden = false;
}
```

- [ ] **Step 4: Verify manually**

Run: `npm run build`, reload.
Manual checklist: unsupported screen appears if you launch Chrome with `--disable-features=WebGPU` (or on a WebGPU-less machine); otherwise idle screen → Start on a YouTube tab → progress bar (first run) → badge turns `recording`, partial text renders grey and finalizes; after a tick an insight card appears newest-first; Stop → badge `stopped`, copy + download buttons work and produce the full transcript.

- [ ] **Step 5: Run the full suite + commit**

Run: `npm test` — Expected: PASS.

```bash
git add src/sidepanel
git commit -m "feat: side panel UI with transcript, insight cards, download progress and export"
```

---

### Task 11: README + manual test checklist + Teams-web verification

**Files:**
- Create: `README.md`, `docs/MANUAL_TESTS.md`

**Interfaces:**
- Consumes: everything (this is the documentation + acceptance pass).
- Produces: docs; the recorded results of the manual checklist run against MS Teams web.

- [ ] **Step 1: Write `README.md`**

Content requirements (write actual prose, not placeholders):
- One-paragraph description mirroring the spec's §1 (on-device, privacy-first, sibling of the Android app at `github.com/<owner>/meetmind-assistant` — use the actual remote URL of the Android repo).
- Requirements: Chrome ≥ 124, WebGPU-capable GPU, ~1 GB disk for models.
- Dev setup: `npm install`, `npm run build`, load `dist/` unpacked; `npm test`.
- Architecture sketch: the four-component MV3 layout (copy the component list from the spec §3).
- **Cross-repo rule** (from the spec §10): the prompt (`src/shared/prompt.ts`) and parser (`src/pipeline/insightParser.ts`) are ports of the Android repo's `strings.xml` / `InsightOutputParser.kt`; schema changes must be mirrored in both repos.
- Record of upstream versions chosen in Tasks 7–8 Step 0 (sherpa release tag, HF model repo, WebLLM model ID) and any substitutions made.
- Honest verification note: capture cannot be CI-tested; `docs/MANUAL_TESTS.md` is the acceptance gate.

- [ ] **Step 2: Write `docs/MANUAL_TESTS.md`**

```markdown
# Manual Test Checklist

Run before every release. Chrome ≥ 124 with WebGPU.

## Capture targets
For each of: **MS Teams web (primary)**, Google Meet, Zoom web, YouTube (control):
- [ ] Start captures tab audio (segments appear within ~10 s of speech)
- [ ] Tab audio remains audible to the user (no mute)
- [ ] Mic toggle mixes local voice into the transcript
- [ ] Stop finalizes; transcript retained; copy + download work

## Pipeline
- [ ] First run: model download progress shown; interrupt (kill network) → restart resumes without redownloading completed files
- [ ] Insight card appears within ~90 s of continuous speech; JSON parsed into title/summary/action items
- [ ] Silence for a full tick interval → no duplicate/empty insight
- [ ] Closing the meeting tab mid-session → session stops gracefully, content retained

## Failure modes
- [ ] `--disable-features=WebGPU` → unsupported screen (no crash, no start button)
- [ ] Malformed model output (unplug… not injectable — covered by unit tests) — N/A manual
```

- [ ] **Step 3: Run the checklist against MS Teams web**

Join a real or test Teams meeting in the browser (`teams.microsoft.com`) and execute the "Capture targets" section for Teams. Record results (pass/fail + notes) at the bottom of `docs/MANUAL_TESTS.md` with the date. **Teams is the primary target — a failure here is a release blocker, not a footnote.**

- [ ] **Step 4: Final full verification**

Run: `npm test` — Expected: PASS.
Run: `npm run build` — Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/MANUAL_TESTS.md
git commit -m "docs: README, manual test checklist and Teams-web verification results"
```

---

## Post-plan notes

- **v2 candidates (explicitly out of scope, do not build):** IndexedDB session history/search, non-English STT models, locale-aware prompts, options page, Firefox port.
- **Chrome Web Store packaging** is intentionally not a task — v1 ends at a locally verified unpacked extension; store submission is its own follow-up with listing assets and privacy declarations.
