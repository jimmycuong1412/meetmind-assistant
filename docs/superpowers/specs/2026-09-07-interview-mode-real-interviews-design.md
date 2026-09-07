# Interview Mode for Real Interviews — Design

> **Status:** Approved for phased implementation (2026-09-07).
> **Plan:** `docs/superpowers/plans/2026-09-07-interview-mode-real-interviews.md`
> **Worked example throughout:** a **Senior DevOps Engineer** interview.

---

## 1. Problem

Interview Mode today is a *mock-interview coach*. It works on a 30-second poll,
sees an undifferentiated blob of text, and answers with generic prose. In a real
interview that fails in three specific ways:

| # | Failure | Consequence in a real senior-DevOps interview |
|---|---|---|
| **F1** | **No speaker separation.** The LLM cannot tell interviewer from candidate. | The model "detects a question" in the candidate's own sentence and coaches them on words the interviewer never said. Answers get generated for the candidate's own rhetorical asides. Coaching tips grade the interviewer's speech. |
| **F2** | **Fixed 30 s polling.** `interviewIntervalSeconds = 30`. | A question lands at t=1 s; help arrives at t=30 s — long after the silence became awkward. Worst case the answer arrives while the candidate is already mid-answer, which is *actively harmful*: it pulls attention at exactly the wrong moment. |
| **F3** | **Shallow prompt.** `"Role: $role\n[TRANSCRIPT]"` + a generic coach system prompt. | "Senior DevOps Engineer" yields textbook answers ("use Infrastructure as Code, monitor everything") that a senior interviewer identifies as non-experience-backed within one follow-up. Nothing in the prompt carries the candidate's actual stack, scale, or incidents. |

There is a fourth, subtler failure that only shows up in real use:

| **F4** | **Prose output.** `answer` is a paragraph the candidate is implicitly invited to read aloud. | Reading generated prose in a live interview is obvious to the interviewer (cadence flattens, eye-line shifts) and produces *worse* answers than the candidate's own words. The assistant must supply **structure to speak from**, not sentences to recite. |

## 2. Decisions

Decided with the user on 2026-09-07:

| Decision | Choice | Rationale |
|---|---|---|
| **Speaker separation method** | **Channel split** — speaker identity from *capture source*, never from acoustics | Target setting is remote video calls. Interviewer and candidate audio arrive on separate sources, so identity is known with certainty and zero inference cost. No new model, no new JNI, no clustering error. |
| **Interviewer-channel producer** | **Two supported: playback loopback (primary) and USB line-in tap (§3.4)** | Loopback needs only a consent tap but can be refused by OS policy; the USB tap needs a ~$15 adapter but cannot be. Both populate the same `SpeakerChannel`, so downstream phases are identical either way and the feature survives a negative Task 0. |
| **Primary setting** | **Remote video call** (Zoom / Meet / Teams) | Matches the channel-split approach and the sibling `meetmind-web` Chrome-extension work. |
| **Live output form** | **Skeleton, not prose** + **grounded in the candidate's own experience** | Directly addresses F4 and F3. |
| **Scope** | Phased plan; implement Phase 1 after approval | — |

### 2.1 Explicitly out of scope

- **Acoustic (embedding) diarization for in-person interviews.** `lib-sherpa-onnx`
  exposes only `SpeakerEmbeddingExtractorConfig` — the config data class — with no
  standalone `SpeakerEmbeddingExtractor` JNI class. Live embedding clustering would
  need a new native binding, a ~30 MB model download, and a voice-enrollment flow.
  Deferred; the design keeps the door open (see §3.5).
- **Routing call audio *through* the phone** (laptop → phone → headphones, and
  headphone mic → phone → laptop). Not achievable on Android, and self-defeating
  if forced — see the rejection note at the end of §3.4.
- **Changing the existing offline diarization pipeline.** `RunDiarizationUseCase`
  and `DiarizationRepositoryImpl` stay exactly as they are, for post-session
  review of meeting recordings. This design adds a *parallel live* path and does
  not touch the offline one.

---

## 3. Design

### 3.1 Phase 1 — Live speaker attribution via dual-source capture

**The core idea:** stop asking "who is speaking?" and instead record "where did this
audio come from?" — which is free.

Today `SherpaOnnxDataSource` opens exactly one `AudioRecord` on
`MediaRecorder.AudioSource.MIC` and runs one VAD + decode loop over it
(`SherpaOnnxDataSource.kt:366`). Phase 1 generalises this to *N* labelled audio
sources feeding one merged recognition flow.

```
┌─ AudioRecord(MIC) ─────────────┐   SpeakerChannel.CANDIDATE
│                                 ├──▶ VAD + decode ──▶ RecognitionResult(speaker=CANDIDATE)
├─ AudioRecord(MediaProjection) ─┘   SpeakerChannel.INTERVIEWER
                                     VAD + decode ──▶ RecognitionResult(speaker=INTERVIEWER)
                                                             │
                                                             ▼
                                              merged Flow<RecognitionResult>
                                                             │
                                    TranscriptionSegment.speaker = "Interviewer" | "Me"
```

**Capture of the far-end audio** uses `AudioPlaybackCaptureConfiguration`
(API 29+; the app's `minSdk = 35` so it is unconditionally available), which
requires a `MediaProjection` token obtained from a user-facing system consent
dialog, and a foreground service typed `mediaProjection`.

**Critical platform constraint — must be stated to the user in the UI, not discovered
in the interview:** playback capture only yields audio from apps whose
`AudioAttributes` allow it. Apps may opt out by setting
`ALLOW_CAPTURE_BY_NONE`, and **voice/telephony usages
(`USAGE_VOICE_COMMUNICATION`) are not capturable at all on most devices.** Zoom,
Meet and Teams route call audio through paths that are frequently non-capturable.
This is not a bug to be fixed in code — it is an OS policy.

Therefore Phase 1 ships with **graceful degradation** as a first-class state, not
an error path:

| Capture state | Behaviour |
|---|---|
| **Dual-source active** | Full separation. Segments labelled `Interviewer` / `Me`. |
| **Loopback silent > 20 s while mic is active** | Detected automatically; UI surfaces a persistent, non-blocking banner: *"Interviewer audio not captured — falling back to single-mic mode."* Mode continues to work in degraded form. |
| **Consent denied / unavailable** | Single-mic mode from the start, with an upfront explanation and the heuristic fallback (§3.1.1) enabled. |

**A note on speakerphone:** in single-mic mode with the call on speakerphone, the
mic hears *both* parties. That is the degraded case the heuristic fallback exists
for. The design does not pretend otherwise.

#### 3.1.1 Heuristic fallback (single-mic mode only)

When only one source exists, attribute turns with cheap, explainable rules —
never presented as certain:

- An utterance ending in `?`, or opening with an interrogative
  (`what|how|why|when|can you|tell me|walk me through|describe|explain`), after
  ≥ 1.5 s of silence → likely `Interviewer`.
- A long utterance (> 25 words) immediately following a likely-interviewer turn
  → likely `Candidate`.
- Confidence is stored, and any label below the confidence floor renders as
  `Speaker ?` rather than a confident wrong label.

**Design principle:** a wrong-but-confident speaker label is worse than an
honest unknown, because the LLM prompt is built from these labels.

#### 3.1.2 Data model changes

`RecognitionResult` (`data/.../SttDataSource.kt:72`) and `TranscriptionSegment`
gain a channel field. `TranscriptionSegment.speaker` already exists as a
`String?` manual label and is already displayed and propagated — Phase 1 populates
it live instead of only post-diarization. `speakerCluster` is untouched, so the
offline pipeline keeps working on meeting recordings.

A new `SpeakerChannel` enum (`CANDIDATE`, `INTERVIEWER`, `UNKNOWN`) is the typed
carrier; the human-readable `speaker` string is derived from it via localized
strings.

### 3.2 Phase 2 — Event-driven triggering

Replace the 30 s poll for `INTERVIEW` with question-onset firing, mirroring the
pattern `ENGLISH_COACH` already uses in `SyncSttLlmUseCase.kt` (fire on completed
segment, guarded by `isLlmBusy`).

Trigger when **all** hold:
1. The completed segment is attributed to `INTERVIEWER` (or, in single-mic mode,
   passes the heuristic question test);
2. it looks like a question (interrogative form, or an imperative probe —
   *"walk me through…"*, *"tell me about a time…"*);
3. no inference is already in flight.

**Turn-boundary debounce.** Interviewers ask multi-part questions
(*"How do you handle secrets? And what about rotation?"*). Firing on the first
clause wastes the inference and shows a partial answer. Wait for **900 ms of
interviewer silence** before firing, and cancel-and-refire if the interviewer
resumes within that window.

**Latency budget.** The whole point is arriving before the awkward pause:

| Stage | Budget |
|---|---|
| VAD end-of-speech → segment finalized | ~300 ms (existing) |
| Turn-boundary debounce | 900 ms |
| LLM prefill + generate (skeleton is short — see §3.3) | ~1.5–2.5 s |
| **Total** | **~3–4 s after the question ends** |

That lands inside a natural thinking pause. The current design's worst case is 30 s.

**Barge-in cancellation.** If the candidate starts speaking while generation is in
flight, **cancel it**. Late help is a distraction, not help. This is a behavioural
requirement, not an optimization.

### 3.3 Phase 3 — DevOps-grade answers

#### 3.3.1 Candidate profile

A short, structured, pre-session profile is the single biggest lever on answer
quality with a 1B model. Stored per session template so it is entered once.

For the Senior DevOps example:

```
Clouds:      AWS (primary, 6 yrs), GCP (2 yrs)
Orchestr.:   EKS, 40-node prod cluster, ~200 services
IaC:         Terraform (mono-repo, Atlantis), Helm
CI/CD:       GitLab CI → ArgoCD (GitOps)
Observ.:     Datadog, Prometheus, SLO-based alerting
Scale:       ~12k req/s peak, 99.95% SLO
War stories: Postgres failover cascade (2024) · Terraform state
             corruption during a multi-region migration · cut deploy
             time 45m→8m via layer caching
```

Injected into the system prompt so the model reaches for *the candidate's own*
Terraform-state-corruption story instead of inventing a textbook one. This is the
difference between an answer that survives a follow-up and one that does not.

#### 3.3.2 Skeleton output schema

The JSON schema changes from prose to structure. New fields, parsed by an extended
`InterviewOutputParser`:

```json
{
  "question_detected": true,
  "question_type": "technical_deep_dive",
  "detected_question": "How do you handle Terraform state locking across teams?",
  "skeleton": [
    "S3 backend + DynamoDB lock table",
    "per-env state separation, not per-team",
    "war story: state corruption, multi-region migration",
    "→ moved to Atlantis for serialized applies"
  ],
  "depth_probe": "Expect a follow-up on what happens when a lock is orphaned",
  "coaching_tips": ["Lead with the incident, not the tooling"]
}
```

- `skeleton` — 3–5 keyword bullets, glanceable in ~2 s, spoken *from* rather than
  read. Rendered large, high contrast, one bullet per line.
- `question_type` — one of `technical_deep_dive` · `behavioral` ·
  `system_design` · `incident_retro` · `culture_fit`. Drives the answer shape:
  a behavioral question gets a STAR skeleton; a system-design question gets a
  constraints→tradeoffs→decision skeleton.
- `depth_probe` — the senior-specific failure mode. Senior interviews are won and
  lost on the *second* and *third* follow-up, so cue what is coming.

Backward compatibility: the parser keeps accepting the existing `answer` field and
synthesises a single-item skeleton from it, so old persisted insights still render.

#### 3.3.3 Prompt uses speaker labels

With Phase 1 landed, the transcript block becomes attributed, which is what makes
question detection reliable rather than guessy:

```
Role: Senior DevOps Engineer
[CANDIDATE PROFILE]
…
[TRANSCRIPT]
INTERVIEWER: How do you handle Terraform state locking across teams?
CANDIDATE: So we, uh, we use S3 for the backend…
```

### 3.4 USB line-in tap — the un-blockable producer

Because Phase 1's premise depends on OS policy that may refuse it (§4, and Task 0
of the plan), the design carries a second producer of the same `SpeakerChannel`
that **no policy can veto**: a wired tap on the laptop's headphone output, read
through a USB-C audio adapter as an ordinary USB Audio Class input.

```
Laptop (Zoom) ──▶ splitter ──┬──▶ headphones            (candidate hears the call)
                              └──▶ USB-C adapter ──▶ Phone  = INTERVIEWER
Candidate voice ──▶ laptop mic (into the call)
                └──▶ phone built-in mic                      = CANDIDATE
```

This is not a downgrade. The two channels are **physically distinct devices**, so
attribution becomes structural rather than inferred — the far end can never be
`UNKNOWN`, and the heuristic of §3.1.1 is not needed in this configuration at all.
It trades a consent tap for a ~$15 adapter and a splitter.

Note what it does *not* do: it never carries the candidate's voice, which arrives
only via the phone's built-in mic. That asymmetry is a feature here — it is what
makes the separation exact.

Two constraints matter in implementation. Cheap adapters commonly enumerate at
44.1/48 kHz rather than 16 kHz, so the stream must be resampled before Parakeet
sees it — feeding a 48 kHz stream to a 16 kHz model produces fluent-looking
garbage rather than an error, which is the worse failure. And silence from a
mis-wired tap is indistinguishable from "the interviewer isn't talking", so a
pre-recording level meter is required, not optional.

**Rejected: routing audio *through* the phone.** The tempting version of this idea
is to make the phone a pass-through — laptop → phone → headphones, and headphone
mic → phone → laptop — so both directions are digital. Android cannot do this. A
phone cannot enumerate as a USB Audio Class *device* to a host (no gadget-mode
audio support is exposed), and it is an A2DP/HFP sink for its own audio rather
than a headset a laptop can pair to. Wi-Fi virtual-audio workarounds add 100–300 ms
of latency, which is unusable for live conversation. Worse, the return path would
run over HFP — the same narrowband telephony codec the built-in-mic routing fix
exists to avoid — so it would reintroduce the accuracy problem it was meant to solve.

### 3.5 Door left open for in-person

`SpeakerChannel` is an abstraction over *source*, not over *acoustics*. Both
producers above (loopback, USB tap) populate it, and if embedding-based
diarization is added later for in-person interviews it becomes a third, with
Phases 2–3 unchanged.

---

## 4. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Conferencing apps mark call audio non-capturable** | ~~High~~ → **Medium**, now that a fallback exists | Detect silent-loopback within 20 s and degrade honestly (§3.1). **Validate on-device against Zoom/Meet/Teams before building the loopback capture path** — Task 0 of the plan. If it fails, the **USB line-in tap (§3.4)** produces the same `SpeakerChannel` and cannot be refused by OS policy, so the feature survives the worst case rather than dying with it. |
| **USB adapter negotiates a non-16 kHz rate** | Medium | Verify the negotiated rate and resample before VAD/decode. Unhandled, this produces fluent-sounding nonsense rather than a visible error — so treat a wrong rate as a hard failure, not a warning. |
| **Mis-wired or muted USB tap reads as silence** | Medium | Silence is indistinguishable from "not speaking", and discovering it mid-interview is costly. Require a pre-recording input-level meter for the USB channel. |
| Two concurrent `AudioRecord`s + 2× STT decode raises CPU/thermal load | Medium | The recognizer already serializes on `decodeMutex`; reuse one recognizer across both channels rather than instantiating two. Existing `ThermalMonitor` throttling applies. |
| Ethical/legal: recording an interviewer | Medium | Consent is jurisdiction-dependent (two-party-consent states/countries). Ship an explicit acknowledgement on first use of dual-capture. Everything stays on-device — nothing is uploaded — which is the app's existing privacy stance. |
| 1B model quality on senior-level content | Medium | Skeletons are far easier to generate well than prose. Profile grounding raises specificity. Offer the larger variant for interview use. |
| Assistant becomes a crutch / candidate stares at phone | Low–Medium | Skeleton-not-prose is itself the mitigation; glanceable output minimizes eye-line drift. |

## 5. Verification

- **Domain logic** — unit tests in `:domain` (the only meaningful test surface in
  this repo): heuristic attribution, question detection, debounce state machine,
  extended parser, profile prompt assembly.
- **On-device — human only.** The agent environment cannot run an emulator, and
  emulator audio capture is unreliable regardless. Every audio-path claim in this
  design must be confirmed by the user on a physical device against a real
  conferencing app. **No build-green claim substitutes for that.**
