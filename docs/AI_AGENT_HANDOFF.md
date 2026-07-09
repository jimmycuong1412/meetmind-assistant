# AI Agent Handoff — MeetMind Assistant

> **Purpose:** Get a fresh AI agent productive on this codebase fast, without re-deriving
> facts from scratch. Read this first, then `README.md` only for the deep architecture
> sections you actually need. Last updated: **2026-07-08**.

---

## 1. What this project is (one paragraph)

MeetMind Assistant is an **Android (Jetpack Compose) app** that does **on-device** audio
transcription (STT) + **on-device** LLM insight generation — fully offline, no cloud, no
account. STT = Sherpa-ONNX (NeMo Parakeet TDT 0.6B). LLM = llama.cpp (Gemma 3 1B GGUF).
It is a privacy-first, 100%-free, Apache-2.0 app, forked from
[HearoPilot](https://github.com/Helldez/HearoPilot-App). The deep technical reference
(STT/LLM pipelines, VAD tuning, native llama.cpp flags, token budgets) lives in
[`README.md`](../README.md) — **do not duplicate it; link to it.**

---

## 2. Read-order for a new agent (minimize tokens)

1. **This file** — orientation, conventions, gotchas.
2. **`README.md` §Architecture + §Module Layout** — the dependency rule and where code lives.
3. Only then, the README pipeline sections (STT / LLM / native) **if your task touches them**.
4. `docs/superpowers/specs/` and `docs/superpowers/plans/` — design + plan docs for recent
   feature work (one per feature; read the relevant one before extending that feature).

Skip the README's STT/LLM tuning tables unless your task is in `feature-stt`,
`feature-llm`, or the native layer — they are large and rarely relevant to UI work.

---

## 3. Module map (Clean Architecture)

Dependency rule: **UI (app) → Presentation → Domain ← Data**. Only interfaces cross
boundaries; no outer layer imports an inner layer's implementation.

| Module | Responsibility | When you touch it |
|---|---|---|
| `:app` | Compose UI, navigation, Hilt entry points, services, notifications | Most UI/UX work |
| `:presentation` | ViewModels, `UiState`, `StateFlow` | State/logic for a screen |
| `:domain` | Pure Kotlin: models, repository **interfaces**, use cases | Business rules, parsing |
| `:data` | Repository **impls**, Room DB, DataStore, `ModelDownloadManager` | Persistence, downloads |
| `:feature-stt` | Sherpa-ONNX STT + `AudioRecord` pipeline | Transcription internals |
| `:feature-llm` | llama.cpp inference wrapper | LLM inference internals |
| `:lib-sherpa-onnx` | JNI binding for Sherpa-ONNX | Rarely (native binding) |
| `:lib-llama-android` | llama.cpp Android lib, compiled from C++ via CMake | Rarely (native build) |

Package root: `com.meetmind.assistant`. UI lives under
`app/src/main/java/com/meetmind/assistant/ui/` — `screens/`, `components/`, `icons/`,
`asr/` (legacy/ASR screens), and `ui/theme/` (imported as
`com.meetmind.assistant.ui.ui.theme.*` — note the doubled `ui.ui`, that is correct).

> **R-class gotcha:** Compose resources resolve via `com.meetmind.assistant.ui.R`
> (note the `.ui.R`), not the app package R. `android.nonTransitiveRClass=true` is set.

---

## 4. Build, run, verify

**Prerequisites** (from `app/build.gradle.kts` + `gradle/libs.versions.toml`):

| Tool | Version |
|---|---|
| JDK | 17 (`sourceCompatibility`/`targetCompatibility` = 17) |
| AGP | 8.13.2 |
| Kotlin | 2.0.21 |
| Hilt | 2.51 |
| Android SDK | compile/target **35** |
| NDK | r27 (`27.2.12479018`) — needed for the native llama.cpp build |
| CMake | 3.31+ |
| Git LFS | required (model/asset objects) |

**minSdk note:** `app/build.gradle.kts:21` sets `minSdk = 35`; the README was reconciled to
match (2026-06-25). The build file remains the authoritative source for what compiles — if
you change minSdk, update the README badge + Build table in the same change.

**Commands** (Windows shell — this is a Windows dev box; use `gradlew.bat`):

```bash
# Fast compile check (use this as the default verification gate for Kotlin/UI changes)
./gradlew.bat :app:compileDebugKotlin

# Full debug APK (slower; native modules) — use before claiming a build is green
./gradlew.bat :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Unit tests (see §5 — most live in :domain)
./gradlew.bat :domain:test
```

> First clean build is slow because `:lib-llama-android` compiles llama.cpp from C++ via
> CMake/NDK. Incremental builds are fast. Allow up to ~10 min for a cold native build.

**On-device verification is human-only here.** The agent environment cannot launch an
Android emulator. For any visual/behavioral change, build to confirm compilation, then ask
the user to verify on a physical device (emulator audio capture is unreliable; ~1.7 GB free
storage needed for both models). State this limitation plainly — never claim a layout/UX was
"verified" when only the build ran.

**Firebase:** `app/google-services.json` is required to build, but the committed
`.example` file is sufficient for debug (Analytics/Crashlytics only activate in release).

---

## 5. Testing reality (don't over-trust the suite)

- **Real unit tests live in `:domain`** — LLM output parsers:
  `domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/` (`InsightOutputParserTest`,
  `InterviewOutputParserTest`, etc.). These are the meaningful tests; run them when you touch
  parsing/use-case logic. The camera vision feature added two more:
  `PhotoContextQueueTest` and `AnalyzePhotoUseCaseTest` (same directory tree, `usecase/sync`
  and `usecase/llm` respectively).
- `app/src/test` and `app/src/androidTest` contain only **placeholder `Example*Test.kt`** —
  there is no broad UI test coverage. Do **not** assume a green app test run means UI is
  verified.
- `domain/bin/test/...` is stale build output, **not** source — ignore it; edit
  `domain/src/test/...`.
- **Practical gate for UI work:** `:app:compileDebugKotlin` (or `:app:assembleDebug`) +
  Compose `@Preview` + human on-device check. For domain/logic work: the `:domain` unit tests —
  run with `./gradlew.bat :domain:test` (not `:domain:testDebugUnitTest`; `:domain` is a plain
  Kotlin module, not an Android module, so it uses the standard `test` task name).

---

## 6. Conventions & single-sources-of-truth

Follow existing patterns — this codebase centralizes deliberately:

- **`SupportedLanguages.ALL`** — the one list of 25 supported locales (UI + LLM prompt
  substitution). Don't hardcode language lists elsewhere.
- **`ModelConfig` / `DefaultModelConfig`** — all model URLs/filenames. No scattered constants.
- **`AppIcons`** — every icon reference goes through this object, not direct `Icons.*`.
- **System prompts live in `strings.xml`** (`prompt_simple_listening`, `prompt_short_meeting`,
  `prompt_long_meeting`, `prompt_translation`), translated for all 25 locales; loaded at
  startup into DataStore. JSON field names (`"title"`, `"summary"`, `"action_items"`) stay
  **English** across all locales for stable parsing.
- **Strings are localized** — UI text goes in `app/src/main/res/values/strings.xml` (+ the 24
  locale variants like `values-vi/`). Don't inline user-facing English.
- **Theme tokens** — colors/typography in `ui/ui/theme/`; reference `MaterialTheme.*` or the
  brand color objects (`BrandPrimary`, `BrandPurpleDark`, `AccentSuccess`, …).
- **Commit style** — Conventional Commits (`feat(ui):`, `fix(ui):`, `docs(...)`). The repo
  default branch is **`develop`** (not `main`/`master`). Branch off `develop` for features.

---

## 7. Responsive UI (current as of 2026-06-25)

A "safe responsive foundation" pass shipped (merge `49b5b71`). Key facts for future UI work:

- **`ui/components/ResponsiveContent.kt`** is the single entry point for large-screen width
  policy. `ContentWidth.Reading` caps content at **600.dp** + centers; `ContentWidth.Wide`
  (`Dp.Infinity`) is full-bleed. It also takes an optional `verticalArrangement` (applied to
  the **inner** content column) — pass the screen's spacing here, and **remove**
  `verticalArrangement` from the outer scrolling Column, or spacing collapses/doubles.
- **Pattern:** outer Column keeps `verticalScroll` + insets (`statusBarsPadding`,
  `navigationBarsPadding`) + padding; `ResponsiveContent` goes **inside** it. Never put it
  outside the scroll (breaks scrolling).
- **Reading/list screens are capped** (Welcome, Settings, Setup, STT/LLM download, Licenses,
  Sessions, Search). **Intentionally full-bleed:** recording/transcription view
  (`TranscriptionSection`), insights view (`InsightsSection`), MainScreen content area, and
  the `SupportedLanguagesScreen` `LazyVerticalGrid` (an adaptive grid should use extra columns
  on wide screens — do not cap it).
- **For `LazyColumn` list screens**, cap the `LazyColumn` itself
  (`Modifier.fillMaxWidth().widthIn(max = 600.dp)`) and center via the parent
  `Box(contentAlignment = Alignment.TopCenter)` — never wrap individual items.
- **Font scaling:** text sizes use `.sp` (never `.dp`). Avoid fixed `.height()` around a
  growable `Text` (it clips at large system font scale) — use `heightIn(min = …)`.

Design + plan: `docs/superpowers/specs/2026-06-25-responsive-foundation-design.md`,
`docs/superpowers/plans/2026-06-25-responsive-foundation.md`.

---

## 8. Camera vision insight (2026-07-07)

A camera button in the recording top bar (next to the screen-lock button; visible only while
recording **and** the active LLM variant is vision-capable) captures a photo via the system
camera app and merges an on-device description into the next AI insight. Key facts:

- **Native (mtmd) build** — `lib-llama-android/src/main/cpp/CMakeLists.txt` adds a static
  `mtmd` library built from the vendored llama.cpp tree's `tools/mtmd/*.cpp` (multimodal/vision
  support), linked into the existing native target. `ai_chat.cpp` gained two JNI entry points:
  `loadMmprojNative` (loads the mmproj vision adapter alongside the base GGUF) and
  `processImagePrompt` (runs a vision inference call). Do **not** edit the vendored
  `F:\Git\llama.cpp-master` tree — it lives outside this repo; suppress any mtmd compiler
  warnings via CMake flags in our own `CMakeLists.txt` instead.
- **`imagePath`/`mmprojPath` threading pattern** — optional, default-`null` parameters added to
  `InferenceEngine` → `LlmDataSource` → `LlmRepository`. Callers that don't pass a photo see no
  behavior change; this is how vision stayed backward-compatible with the five text-only
  variants.
- **`DefaultModelConfig` = Gemma 3 4B + mmproj** — the default (`LlmModelVariant.Q8_0`, name
  kept for historical settings persistence — it used to mean "Gemma 3 1B Q8_0") now downloads
  Gemma 3 4B Q4_K_M (~2.5 GB) plus `gemma-3-4b-mmproj-f16.gguf` (~850 MB) as a pair. The other
  five variants (`IQ4_NL`, `QWEN3_5_Q8_0`, `GEMMA3_4B_Q4`, `QWEN3_4B_Q4`, `PHI4_MINI_Q4`) remain
  text-only. `LlmModelVariant.supportsVision` (true only for `Q8_0`) gates the camera button's
  visibility — check it before assuming a device/variant combination can take photos.
- **`PhotoContextQueue`** (domain, `usecase/sync/PhotoContextQueue.kt`) is a small thread-safe
  FIFO buffer (bounded at 10) of photo descriptions. `SyncSttLlmUseCase.queuePhotoDescription()`
  is the producer API (called by `MainViewModel` after vision analysis completes); the queue is
  drained once per insight tick and folded into the next analysis-mode prompt. A dropped queue
  entry (buffer overflow) never loses data — the description is still persisted per-photo below.
- **`PhotoAnalysisQueue`** (domain, `usecase/llm/PhotoAnalysisQueue.kt`, added 2026-07-08) —
  FIFO serial worker for the analyses themselves: capture is **never blocked** while a photo is
  analyzing; each capture is persisted immediately and its analysis job queues behind earlier
  ones (llama.cpp can't parallelize). `MainViewModel` launches `process()` in `viewModelScope`
  and mirrors `pending` into `MainUiState.pendingPhotoAnalysisCount` (`isAnalyzingPhoto` is now
  a derived val, count > 0), which drives the "Analyzing photo… N more in queue" banner
  (`photo_analyzing_queued`). Don't reintroduce a capture guard on `isAnalyzingPhoto`. Spec/plan:
  `docs/superpowers/{specs,plans}/2026-07-08-concurrent-photo-capture*`. Tests:
  `PhotoAnalysisQueueTest`. Photos can also be **uploaded from device files** (2026-07-08):
  a gallery button next to the camera launches the Photo Picker, and
  `ui/util/PhotoImport.kt` re-encodes the pick as JPEG into `filesDir/photos/` before the
  same `onPhotoCaptured` path — always transcode, because gallery images are often
  HEIC/WebP, which the native stb_image loader can't read. Spec/plan:
  `docs/superpowers/{specs,plans}/2026-07-08-photo-upload-from-files*`.
- **`session_photos` table (Room DB v10)** — `MIGRATION_9_10` in `AppDatabase.kt` adds
  `session_photos` (`id`, `session_id` FK → `transcription_sessions` with cascade delete,
  `file_path`, nullable `description`, `timestamp`) plus an index on `session_id`. New DAO:
  `SessionPhotoDao`.
- **Gotcha — Room versions 10–12 are a reconciled fork, and migrations 10→11 and 11→12 are
  deliberately defensive.** Two branches independently shipped a "version 10": develop's v10 =
  `session_photos` (above), while `feature/012-english-coach` shipped its own v10
  (`llm_insights.question_type`) and v11 (`session_groups` + `transcription_sessions.group_id`).
  The merged chain renumbers the coach changes as `MIGRATION_10_11` (question_type) and
  `MIGRATION_11_12` (session_groups + group_id), final `version = 12`. Because a device may
  arrive at version 10 or 11 from *either* lineage, these migrations check column existence
  (`hasColumn`) / use `CREATE TABLE IF NOT EXISTS`, and 11→12 also re-creates `session_photos`
  for coach-lineage devices that never ran develop's 9→10. Don't "clean up" the guards — they
  are what lets both lineages upgrade without a migration crash.
- **Display placement (known deviation from the original plan)** — the design/plan docs said
  photos would show "after insights / before transcript" in Session Details, but that screen is
  tab-based, not a single scroll. Photos actually render via `SessionPhotosSection` as the first
  item inside the **Transcript tab's** `LazyColumn` (`SessionDetailsScreen.kt`), not as a
  separate section between tabs.
- **Gotcha — no `CAMERA` permission is declared, by design.** The feature launches the system
  camera app via `ActivityResultContracts.TakePicture()` (see `MainScreen.kt`), which handles
  its own permission; MeetMind never touches `android.hardware.camera` or `Manifest.CAMERA`.
  Don't add the permission "for completeness" — it isn't needed and would be a needless
  manifest addition.
- **New domain tests:** `PhotoContextQueueTest` (`usecase/sync/`) and `AnalyzePhotoUseCaseTest`
  (`usecase/llm/`) — run via `./gradlew.bat :domain:test` (see §5).

Design + plan: `docs/superpowers/specs/2026-07-07-camera-vision-insight-design.md`,
`docs/superpowers/plans/2026-07-07-camera-vision-insight.md`.

---

## 9. Gotchas that waste tokens if unknown

- **`gradlew.bat`** on Windows, not `./gradlew`. Bash tool runs Git Bash (POSIX), but the
  Gradle wrapper invoked is the `.bat`.
- **Native build is part of every build** — `libai-chat.so` (`ai_chat.cpp`) is compiled from
  source via `externalNativeBuild`. A "simple Kotlin change" can still trigger a long native
  step on a cold build.
- **`ui.ui.theme`** double segment is real, not a typo.
- **Compose R class** is `com.meetmind.assistant.ui.R`.
- **llama.cpp is not thread-safe** — all JNI inference is serialized on
  `Dispatchers.Default.limitedParallelism(1)`. Don't parallelize it.
- **Translation mode sends raw text only** (no "Context/Analyze" wrapper) — small models echo
  wrapper keywords otherwise. See README §LLM Pipeline.
- **`docs/superpowers/`** is the project's spec/plan home (brainstorm → spec → plan →
  subagent-driven execution workflow). New features should leave a spec + plan there.

---

## 10. Where to look first, by task type

| Task | Start here |
|---|---|
| Change a screen's layout/UX | `app/.../ui/screens/<Screen>.kt`; `ui/components/` for shared widgets |
| Add/adjust large-screen behavior | `ui/components/ResponsiveContent.kt` + §7 above |
| New user-facing string | `res/values/strings.xml` (+ locale variants) |
| Change a recording mode's AI behavior | `strings.xml` prompts + `SyncSttLlmUseCase` (domain) + README §Recording Modes |
| LLM output parsing/format | `:domain` use cases + `domain/src/test/.../llm/` tests |
| STT/audio tuning | `:feature-stt`, README §STT Pipeline (VAD tables) |
| Model download/storage | `:data` `ModelDownloadManager`, `ModelConfig`/`DefaultModelConfig` |
| Native llama.cpp flags | `lib-llama-android/.../ai_chat.cpp`, README §llama.cpp Optimizations |
| Camera/vision photo analysis | §8 above; `PhotoContextQueue`, `AnalyzePhotoUseCase`, `SessionPhotoDao` |

---

## 11. Maintaining this doc

When you finish a non-trivial feature: update §7 (or add a sibling section) with the new
single-sources-of-truth and any new gotchas, bump the "Last updated" date, and reconcile any
fact that drifted (especially the §4 version table and the minSdk discrepancy). Keep it
**agent-oriented** — file locations, commands, conventions, traps — and let `README.md` own the
deep architecture so the two don't fight.
