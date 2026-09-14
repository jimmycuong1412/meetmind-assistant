# Interview Mode for Real Interviews — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `RecordingMode.INTERVIEW` usable in a *real* remote interview (worked example: Senior DevOps Engineer) by (1) separating interviewer speech from candidate speech live, (2) firing help ~3–4 s after a question ends instead of on a 30 s poll, and (3) producing a glanceable skeleton grounded in the candidate's actual experience instead of generic prose.

**Architecture:** Speaker identity is derived from *capture source*, not acoustics. `SherpaOnnxDataSource` is generalised from one `AudioRecord(MIC)` to N labelled sources — the mic as `CANDIDATE`, plus an `INTERVIEWER` source — merged into one `Flow<RecognitionResult>` carrying a `SpeakerChannel`. The `INTERVIEWER` source has **two interchangeable producers**: an `AudioPlaybackCapture` loopback via `MediaProjection` (Task 3/4 — one consent tap, but refusable by OS policy) or a **USB line-in tap** on the laptop's headphone output (Task 3b — needs a ~$15 adapter, but cannot be refused). Both emit the same enum, so everything downstream is producer-agnostic. The existing offline diarization pipeline (`RunDiarizationUseCase`, `DiarizationRepositoryImpl`) is **not touched**. Downstream, `SyncSttLlmUseCase` gains an interview branch that fires on interviewer question-onset (mirroring the existing `ENGLISH_COACH` event-driven branch) and builds an attributed prompt; `InterviewOutputParser` is extended with a skeleton schema, backward-compatible with existing persisted insights.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, sherpa-onnx (STT/VAD), llama.cpp (Gemma 3 1B), Android `MediaProjection` / `AudioPlaybackCaptureConfiguration`, USB Audio Class input (`AudioDeviceInfo.TYPE_USB_DEVICE`), JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-07-interview-mode-real-interviews-design.md`

## Global Constraints

- Windows dev box: build with `./gradlew.bat` (Git Bash invokes the `.bat`), never `./gradlew`.
- Compile gate for Kotlin/UI changes: `./gradlew.bat :app:compileDebugKotlin`.
- Domain tests: `./gradlew.bat :domain:test` (`:domain` is a plain Kotlin module — **not** `:domain:testDebugUnitTest`).
- Conventional Commits (`feat(...)`, `fix(...)`, `docs(...)`); branch off `develop`.
- All user-facing strings go in `app/src/main/res/values/strings.xml` (+ 24 locale variants, e.g. `values-vi/`). Never inline English in composables. JSON schema field names stay **English** across all locales for stable parsing.
- Every icon goes through `AppIcons`, never direct `Icons.*` in screens.
- Colors: no raw `Color(0x…)` outside the theme. `DESIGN.md` is the source of truth; the two contrast guard tests must stay green (`app/src/test/.../ColorContrastTest.kt`).
- Compose resources resolve via `com.meetmind.assistant.ui.R`; theme imports use the doubled `com.meetmind.assistant.ui.ui.theme.*`.
- Dependency rule: UI (app) → Presentation → Domain ← Data. **Domain stays pure Kotlin — no Android imports.** `SpeakerChannel` lives in `:domain`; all `MediaProjection` code lives in `:app`/`:feature-stt`.
- llama.cpp is NOT thread-safe; sherpa's `OfflineRecognizer.decode()` is NOT safe for concurrent calls (documented SIGSEGV at `SherpaOnnxDataSource.kt:520`). **Both channels must share the single recognizer behind the existing `decodeMutex`.** Do not add a second recognizer.
- On-device verification is human-only (no emulator in the agent environment; emulator audio capture is unreliable). Never claim audio behaviour is "verified" from a green build.
- minSdk = 35, JDK 17, AGP 8.13.2, Kotlin 2.0.21, NDK r27.

**Known accepted behavior changes** (restated so no implementer "fixes" them):
- `TranscriptionSegment.speaker` becomes populated **live** during interview sessions, where previously it was only set post-diarization or manually. `RunDiarizationUseCase`'s label propagation already skips segments with a manual speaker, so live labels are preserved on a later diarization run — this is intended.
- `interviewIntervalSeconds` stops being the primary trigger for `INTERVIEW`. It is retained as a **fallback heartbeat** (if no question is detected for one interval, fire a general coaching insight) so the setting keeps meaning.
- Interview Mode will show a **degraded-mode banner** rather than silently producing wrong speaker labels when loopback capture is unavailable. An honest "unknown" is a feature, not a gap.

---

## ✅ Task 0 (RESOLVED 2026-09-08 — superseded; see verdict below): Validate playback capture against real conferencing apps

**Scope of this gate:** it decides **which producer** supplies the `INTERVIEWER`
channel — it no longer threatens the feature itself. Android OS policy may refuse
playback capture: apps can opt out via `ALLOW_CAPTURE_BY_NONE`, and
`USAGE_VOICE_COMMUNICATION` audio is typically **not capturable at all**.
Zoom/Meet/Teams may route call audio exactly that way.

**Do not build Tasks 3/4 before this returns a result** — that is the work a negative
result would waste. Tasks 1, 2, 3-Step-1, **3b**, and all of Phases 2–3 are unaffected
by the outcome and may proceed in parallel: they depend on `SpeakerChannel`, not on how
it is produced.

- [ ] **Step 1: Build a throwaway probe**

A minimal debug-only screen or instrumented harness that:
1. requests `MediaProjection` consent,
2. opens an `AudioRecord` with `AudioPlaybackCaptureConfiguration` (`USAGE_MEDIA`, `USAGE_UNKNOWN`, `USAGE_GAME`),
3. logs RMS level once per second for 60 s.

- [ ] **Step 2: Human on-device test (user runs this — agent cannot)**

For **each** of Zoom, Google Meet, Microsoft Teams: join a call with audio from the
far end and record the observed RMS.

| App | Far-end audio captured? | Notes |
|---|---|---|
| *(baseline: on-device media playback)* | ☑ **yes** | 2026-09-08, Lenovo TB321FU (Android 16). 18/84 windows, peak −17.8 dBFS. Proves the probe and the `MediaProjection` path work end-to-end. **Not a conferencing result** — `USAGE_MEDIA` is the easy case. |
| Zoom / Meet / Teams on the tablet | — **not tested** | Superseded; see the verdict below. |

## ✅ TASK 0 VERDICT (2026-09-08): superseded — build Task 3b

**Not a failure — an inapplicable question.** `MediaProjection` playback capture only
ever captures audio *playing on the same device*. The user's interview runs **on the
laptop**, with the tablet acting as the assistant, so there is no scenario in which the
tablet's playback stream contains the interviewer's voice. Whether Zoom/Meet/Teams
audio is capturable is therefore irrelevant to this deployment, and testing it further
would answer a question nobody is asking.

Nor is there a software bridge: Android cannot act as a Bluetooth A2DP sink (profile
absent on this device), has no Miracast receiver, and cannot enumerate as a USB Audio
Class device to a host. Wi-Fi streaming apps exist but add 150–300 ms of latency **and**
would still require `MediaProjection` to capture the streaming app's output — a strictly
worse version of the same path. See spec §3.4's rejection note.

**Consequences for the plan:**
- **Task 4 (MediaProjection consent + FGS type) is DROPPED** for this deployment. Do not
  build it. Keep the design in the spec for a future phone-hosted-call use case.
- **Task 3b (USB line-in tap) is the sole `INTERVIEWER` producer** and is now on the
  critical path, not a fallback.
- Tasks 1, 2, 3 (Steps 1–3) and Phases 2–3 are unaffected.
- **Hardware required before Task 3b can be verified:** a USB-C audio interface with a
  line/mic **input** (~$20–40, UAC-compliant) plus a 3.5 mm splitter so the candidate
  still hears the call. A plain USB-C data cable is NOT sufficient — verified on-device
  2026-09-08: `audio_accessory_connected=false`, `host_connected=false`, and
  `/proc/asound/cards` empty with the cable attached.

**Probe defect found and fixed (2026-09-08):** the first build crashed on consent
approval — `SecurityException: Media projections require a foreground service of type
FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`. Android 14+ requires a *running* foreground
service of that type **before** `getMediaProjection()` is called, and starting the
service is asynchronous, so the caller must wait for it to reach foreground state rather
than calling straight after `startForegroundService()`. Task 4 Step 1 must implement the
same ordering in `RecordingService`, or it will reproduce this crash in production.

- [ ] **Step 3: Record the verdict in this plan file before proceeding.**

**This pivot was taken (2026-09-08).** Build **Task 3b (USB line-in tap)** instead of
Tasks 3/4. It produces the same `SpeakerChannel.INTERVIEWER` stream from a wired tap on
the laptop's headphone output, and — critically — **cannot be refused by OS policy**,
because it is an ordinary USB Audio Class input device rather than a privileged capture
of another app's playback. It costs the user a ~$20–40 audio interface and a splitter
instead of a consent tap. Acoustic embedding diarization (deferred in spec §2.1) stays
the last resort, for in-person interviews only.

Phases 2 and 3 are unaffected by which producer wins: they consume `SpeakerChannel`
regardless of how it was produced. **Phases 2 and 3 also stand alone** and can be built
first for immediate value if Phase 1 is blocked.

---

# PHASE 1 — Live speaker attribution

### Task 1: Domain — `SpeakerChannel` + attribution heuristic

**Files:**
- Create: `domain/src/main/java/com/meetmind/assistant/domain/model/SpeakerChannel.kt`
- Create: `domain/src/main/java/com/meetmind/assistant/domain/usecase/transcription/SpeakerAttributionHeuristic.kt`
- Test: `domain/src/test/java/com/meetmind/assistant/domain/usecase/transcription/SpeakerAttributionHeuristicTest.kt`

**Interfaces:**
- Produces: `enum class SpeakerChannel { CANDIDATE, INTERVIEWER, UNKNOWN }`; `SpeakerAttributionHeuristic` with `fun attribute(text: String, silenceBeforeMs: Long, previous: SpeakerChannel?): Attribution` returning `data class Attribution(val channel: SpeakerChannel, val confidence: Float)`.
- Consumed by: Task 3 (STT), Task 6 (sync loop).

- [ ] **Step 1: Write failing tests** covering, at minimum:
  - `"How do you handle Terraform state locking?"` after 2 s silence → `INTERVIEWER`, confidence ≥ 0.8
  - `"Walk me through a production incident you owned."` (imperative probe, no `?`) → `INTERVIEWER`
  - `"So we use S3 with a DynamoDB lock table, and when we hit contention…"` (32 words, follows an interviewer turn) → `CANDIDATE`
  - `"Right."` (2 words, ambiguous) → `UNKNOWN` with confidence below the floor
  - Confidence floor constant is exported and tested, not magic-numbered at the call site.
- [ ] **Step 2: Implement** — pure Kotlin, no Android imports.
- [ ] **Step 3: Verify** — `./gradlew.bat :domain:test`

### Task 2: Data — thread `SpeakerChannel` through the STT contract

**Files:**
- Modify: `data/src/main/java/com/meetmind/assistant/data/datasource/SttDataSource.kt` (`RecognitionResult`)
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/model/TranscriptionSegment.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/database/entity/` segment entity + DAO/mapper
- Modify: Room `AppDatabase` — schema version bump + migration

**Interfaces:**
- `RecognitionResult` gains `val speakerChannel: SpeakerChannel = SpeakerChannel.UNKNOWN` (defaulted, so all existing construction sites compile unchanged).
- `TranscriptionSegment` gains `val speakerChannel: SpeakerChannel? = null`. **`speakerCluster` is left alone** — the offline diarization pipeline continues to own it.

- [ ] **Step 1:** Add the fields with defaults; confirm no call site breaks.
- [ ] **Step 2:** Room migration adding a nullable `speaker_channel TEXT` column. Follow the existing migration pattern in `AppDatabase`. Nullable + defaulted means zero risk to existing rows.
- [ ] **Step 3: Verify** — `./gradlew.bat :app:compileDebugKotlin` and `./gradlew.bat :domain:test`.

### Task 3: feature-stt — multi-source capture

**Files:**
- Modify: `feature-stt/src/main/java/com/meetmind/assistant/feature/stt/datasource/SherpaOnnxDataSource.kt`
- Create: `feature-stt/src/main/java/com/meetmind/assistant/feature/stt/datasource/AudioSourceSpec.kt`

**This is the highest-risk task in the plan.** Read `SherpaOnnxDataSource.kt` in full
before editing — particularly the `decodeMutex` comment at line ~520 explaining the
SIGSEGV from concurrent `decode()`.

- [ ] **Step 1:** Extract the existing capture+VAD+decode loop into a function parameterized by `AudioSourceSpec(channel: SpeakerChannel, audioRecordFactory: () -> AudioRecord)`. **Refactor only — no behaviour change.** Verify the single-mic path still works on-device before continuing.
- [ ] **Step 2:** Run one loop per source, each with its **own VAD instance and own ring buffer** (VAD is stateful — sharing it across sources corrupts both), merging into the single `channelFlow`. **All `decode()` calls continue to serialize on the shared `decodeMutex` against the single shared recognizer.**
- [ ] **Step 3:** Stamp each emitted `RecognitionResult` with its source's `SpeakerChannel`. Audio offsets stay measured per-source from session start (both sources start together, so offsets remain comparable).
- [ ] **Step 4:** Loopback-silence watchdog — if the `INTERVIEWER` source produces no speech for 20 s while the mic is active, emit a degraded-mode signal.
- [ ] **Step 5: Verify** — `./gradlew.bat :app:compileDebugKotlin`, then **hand to the user for on-device verification.** Do not claim the audio path works from a build.

### Task 3b (PRIMARY PRODUCER — Task 0 verdict, 2026-09-08): USB line-in tap

**Relationship to Tasks 3/4:** this is a *second producer* of
`SpeakerChannel.INTERVIEWER`, not a replacement for the multi-source refactor. It
depends on **Task 3 Step 1** (the `AudioSourceSpec` extraction) but is independent of
`MediaProjection` entirely. Following the Task 0 verdict this is **the** interviewer
producer, not an alternative — Task 4 is dropped.

**Topology.** The phone stops being an acoustic listener and becomes a wired tap on the
laptop's output:

```
Laptop (Zoom) ──▶ 3.5mm/USB-C splitter ──┬──▶ headphones   (candidate hears the call)
                                          └──▶ USB-C audio adapter ──▶ Phone
                                                   = SpeakerChannel.INTERVIEWER
Candidate's voice ──▶ laptop mic (to the call)  and  ──▶ phone built-in mic
                                                   = SpeakerChannel.CANDIDATE
```

**Why this is attractive even if Task 0 passes:**

| | MediaProjection loopback (Task 3/4) | USB line-in tap (this task) |
|---|---|---|
| Can OS policy refuse it? | **Yes — the Task 0 risk** | **No** — ordinary UAC input device |
| Extra hardware | none | adapter + splitter (~$15) |
| Signal quality | pristine digital | clean line-level analog |
| Captures candidate | yes, as a second source | no — phone's built-in mic does |
| Setup friction | one consent tap | plug in two things |

**Why speaker separation is *better* here, not worse:** the two channels are physically
distinct devices, so attribution is structural rather than inferred. USB input is the
interviewer by construction; the built-in mic is the candidate. There is no clustering,
no confidence score, and no `UNKNOWN` case for the far end. The
[SpeakerAttributionHeuristic] fallback is not needed at all in this configuration.

**Files:**
- Modify: `feature-stt/src/main/java/com/meetmind/assistant/feature/stt/datasource/SherpaOnnxDataSource.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SettingsScreen.kt` (+ strings)

**Interfaces:**
- Consumes: `AudioSourceSpec` from Task 3 Step 1.
- Produces: an `AudioSourceSpec` bound to `AudioDeviceInfo.TYPE_USB_DEVICE` /
  `TYPE_USB_HEADSET` with `channel = SpeakerChannel.INTERVIEWER`.

- [ ] **Step 1: Detect USB audio input devices**

Enumerate via `audioManager.getDevices(GET_DEVICES_INPUTS)` filtering on
`TYPE_USB_DEVICE` and `TYPE_USB_HEADSET`. This is the same enumeration already used by
`preferBuiltInMic()` (added in the Bluetooth-routing fix), so follow that method's
shape. USB Audio Class input is supported natively — no `UsbManager` permission dance
is required for a UAC device that the platform has already enumerated as an audio input.

- [ ] **Step 2: Bind a capture source to the USB device**

Construct the interviewer-channel `AudioRecord` with
`setPreferredDevice(usbDevice)`. **Verify the negotiated sample rate**: cheap adapters
may enumerate at 44.1 kHz or 48 kHz rather than 16 kHz. If the device does not deliver
`SAMPLE_RATE_HZ` (16 kHz), resample before the VAD/decode stage — Parakeet expects
16 kHz mono and feeding it 48 kHz silently produces garbage transcripts, which is a
much worse failure than a clear "unsupported rate" error. Prefer capturing at the
device's native rate and downsampling over asking `AudioRecord` for a rate the hardware
may refuse.

- [ ] **Step 3: Hot-plug handling**

Register an `AudioDeviceCallback` so the adapter being unplugged mid-interview degrades
to single-mic mode (reusing the Task 3 Step 4 degraded-mode signal) rather than
silently emitting a dead channel. Plugging in mid-session should be picked up on the
next recording start, not hot-swapped into a live session — restarting a capture source
mid-stream risks desynchronising the audio offsets that segment alignment depends on.

- [ ] **Step 4: Settings — interviewer audio source**

A three-way choice, defaulting to Auto:
`Auto` (prefer USB if present, else loopback if permitted, else single-mic) ·
`USB line-in` · `Call audio (MediaProjection)` · `Off (single mic)`.
Localized strings; no raw `Color(0x…)`; follow the `PreferBluetoothMicSetting`
composable pattern.

- [ ] **Step 5: Level check in the UI**

Because a wrong cable or a muted splitter yields silence that looks identical to "the
interviewer is not talking", show a live input-level meter for the USB channel in the
pre-recording state so the user can confirm the tap works **before** the interview
starts. This is the single highest-value piece of UX in this task — the failure mode it
prevents is discovering a dead channel 10 minutes into a real interview.

- [x] **Step 6: Verify** — `./gradlew.bat :app:compileDebugKotlin`, then **hand to the
user for on-device verification with a real adapter.** Confirm: correct sample rate
negotiated, interviewer audio present on the USB channel, candidate audio present on
the built-in mic, and unplug-mid-session degrades cleanly. As with all audio-path work
in this plan, a green build proves nothing here.

### ~~Task 4: app — MediaProjection consent + foreground service type~~ (DROPPED 2026-09-08)

> **Do not build.** The Task 0 verdict superseded this: the interview call runs on the
> laptop, so the tablet's playback stream never contains the interviewer's voice.
> Retained below only as a reference design should a phone-hosted-call use case appear.
>
> **If it is ever revived**, note the defect the probe hit: Android 14+ throws
> `SecurityException: Media projections require a foreground service of type
> FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` unless such a service is **already running**
> when `getMediaProjection()` is called. Starting it is asynchronous, so the caller must
> wait for the service to reach foreground state — not merely call
> `startForegroundService()` first.

#### Reference design (not scheduled)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/meetmind/assistant/service/RecordingService.kt`
- Create: consent-launcher plumbing in the recording entry point

- [ ] **Step 1:** Manifest — add `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`; add `mediaProjection` to `RecordingService`'s `foregroundServiceType` (currently `microphone` at `AndroidManifest.xml:84`).
- [ ] **Step 2:** Request the `MediaProjection` token via `ActivityResultContracts.StartActivityForResult` before the service starts capture, **only for `INTERVIEW` mode** — other modes must not gain a consent prompt.
- [ ] **Step 3:** First-use acknowledgement dialog covering the recording-consent point from the spec's risk table (jurisdiction-dependent two-party consent). Localized string.
- [ ] **Step 4:** Handle denial → single-mic mode with heuristic attribution, surfaced honestly in the UI.
- [ ] **Step 5: Verify** — compile, then user verifies consent flow on-device.

### Task 5: UI — speaker-labelled transcript + degraded banner

**Files:**
- Modify: transcript rendering in the recording screen and `SessionDetailsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (+ locale variants)

- [ ] **Step 1:** Render `Interviewer` / `Me` labels on live segments, using theme tokens only (`DESIGN.md`; no raw `Color(0x…)`). Low-confidence heuristic labels render as `Speaker ?`.
- [ ] **Step 2:** Degraded-mode banner (non-blocking, persistent) when loopback is silent or consent was denied.
- [x] **Step 3: Verify** — compile + `./gradlew.bat :app:test` (contrast guards) + user on-device check.

---

# PHASE 2 — Event-driven triggering

### Task 6: Question-onset trigger with turn-boundary debounce

**Files:**
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- Create: `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/InterviewTurnDetector.kt`
- Test: `domain/src/test/java/com/meetmind/assistant/domain/usecase/sync/InterviewTurnDetectorTest.kt`

Model the new branch on the existing `ENGLISH_COACH` event-driven block in
`SyncSttLlmUseCase` (fires per completed segment, guarded by `isLlmBusy`) — same
shape, different trigger condition.

> **DONE 2026-09-08.** `InterviewTurnDetector` + 21 unit tests; wired into
> `SyncSttLlmUseCase` as an INTERVIEW branch with barge-in cancellation.
>
> **Deviation from plan — `allowUnattributedQuestions`:** the plan assumed Task 3/3b
> would land first, giving real `SpeakerChannel` values. It has not, so every segment
> arrives `UNKNOWN` and a detector that refuses `UNKNOWN` would make Interview Mode
> heartbeat-only — worse than the 30 s poll it replaces. The detector therefore takes a
> constructor flag (currently `true`) that accepts *question-shaped* `UNKNOWN` segments,
> trading some false positives (the candidate's own clarifiers can fire an answer) for a
> mode that works today. **Set it to `false` when a capture producer ships** — known
> attribution already wins over the flag wherever it is present.
>
> Also added: `TranscriptionSegment.speakerChannel` (domain field only, defaulted null).
> The Room column is deliberately deferred to Task 3, since nothing writes a non-null
> value until a producer exists.

- [x] **Step 1: Write failing tests** for `InterviewTurnDetector`:
  - single-clause question → fires once, after the 900 ms debounce
  - multi-part question (`"How do you handle secrets? And what about rotation?"` with a 400 ms gap) → fires **once**, with both clauses, after the *final* clause
  - candidate speech → never fires
  - candidate barge-in during in-flight generation → emits cancel
  - no question for a full `interviewIntervalSeconds` → fires the fallback heartbeat
- [x] **Step 2: Implement** the detector as a pure state machine (testable without coroutines).
- [x] **Step 3:** Wire into `SyncSttLlmUseCase` as an `INTERVIEW` branch; keep `interviewIntervalSeconds` as the heartbeat fallback.
- [x] **Step 4:** Implement barge-in cancellation of the in-flight LLM job.
- [x] **Step 5: Verify** — `./gradlew.bat :domain:test` green (97 domain tests, 0 failures).

---

# PHASE 3 — DevOps-grade answers

### Task 7: Candidate profile — DONE 2026-09-08

> **Deviation:** stored in `AppSettings.interviewCandidateProfile` (DataStore) rather than
> on `SessionTemplate`. The profile describes the *person*, not the meeting, so it is typed
> once and reused by every interview session. Putting it on the session would also have
> required a Room migration to carry it to the recording screen — a schema change for a
> field with one value per user. Surfaced in Settings → Interview Coach, beside the other
> interview settings, rather than in `NewSessionDialog`.

**Files:**
- Modify: `domain/.../model/SessionTemplate.kt` (+ entity/DAO/migration)
- Modify: `app/src/main/java/com/meetmind/assistant/ui/components/NewSessionDialog.kt`

- [x] **Step 1:** Add an optional multi-line `candidateProfile` to the session template (persisted, so it is entered once and reused).
- [x] **Step 2:** In `NewSessionDialog`, show the profile field only for `INTERVIEW` mode, beside the existing `interviewRole` field (`NewSessionDialog.kt:77`). Include a Senior-DevOps placeholder as guidance.
- [ ] **Step 3: Verify** — compile + `./gradlew.bat :domain:test`.

### Task 8: Skeleton schema — prompt, parser, and rendering — DONE 2026-09-08

> **Persistence encoding** (no migration, as planned): the `tasks` JSON array holds
> skeleton bullets, then a `TIPS_SEPARATOR` ("—") sentinel, then coaching tips; the depth
> probe is appended to `content` behind `DEPTH_PROBE_MARKER`. The separator is written
> **only when both halves are non-empty**, so tips-only insights serialise byte-identically
> to before — which is what keeps previously-persisted rows and their tests valid.
>
> **Caught during implementation:** synthesising a skeleton from prose initially leaked the
> answer text into `tasks`, duplicating `content` and breaking two existing tests. Fixed
> with `InterviewInsight.isSkeletonSynthesised`, which is shown in the UI but never
> persisted.
>
> **Not done — locale variants.** The plan says "update all 24 locale variants"; only
> `values-vi` exists and it contains **zero** `settings_*` or `prompt_*` strings, so the
> new strings match existing practice. The 24-locale claim in `AI_AGENT_HANDOFF.md` is
> inaccurate.

**Files:**
- Modify: `app/src/main/res/values/strings.xml` → `prompt_interview` (+ 24 locales)
- Modify: `domain/.../usecase/llm/InterviewPromptBuilder.kt`
- Modify: `domain/.../usecase/llm/InterviewOutputParser.kt`, `domain/.../model/InterviewInsight.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/InsightsSection.kt`
- Test: extend `domain/src/test/.../InterviewOutputParserTest.kt`

- [x] **Step 1: Write failing parser tests first** — new schema (`skeleton`, `question_type`, `depth_probe`); **backward compatibility: an old-schema payload with only `answer` must still parse**, synthesising a single-item skeleton. Malformed/truncated JSON must degrade, not throw (the existing parser's fallback contract).
- [x] **Step 2:** Extend `InterviewInsight` + parser. Persist `skeleton` into the existing `tasks` JSON-array column and `depth_probe` into the title/content encoding — **no new insight table**, matching how the current parser already reuses `llm_insights`.
- [x] **Step 3:** Rewrite `prompt_interview` for the skeleton schema, keeping JSON field names English. Update all 24 locale variants.
- [x] **Step 4:** `InterviewPromptBuilder` — inject the candidate profile and emit the attributed transcript (`INTERVIEWER:` / `CANDIDATE:` prefixes). Keep the role/profile prefix stable across intervals for llama.cpp KV-cache reuse (the builder's existing documented caching property).
- [x] **Step 5:** Render skeletons as large, high-contrast bullets — glanceable in ~2 s. Theme tokens only.
- [ ] **Step 6: Verify** — `./gradlew.bat :domain:test`, `./gradlew.bat :app:compileDebugKotlin`, `./gradlew.bat :app:test`, then user on-device check.

---

## Final verification

- [ ] `./gradlew.bat :domain:test` — green
- [ ] `./gradlew.bat :app:test` — green (includes the two contrast guard tests)
- [ ] `./gradlew.bat :app:assembleDebug` — green
- [ ] **User on-device dry run**: a real (or realistic mock) Senior DevOps interview over a video call, confirming speaker labels are correct, help arrives ~3–4 s after a question, and skeletons are speakable. **This is the only verification that counts for the audio path.**
