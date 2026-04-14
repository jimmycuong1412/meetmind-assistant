<!--
SYNC IMPACT REPORT
==================
Version change: 1.0.0 → 2.0.0 (MAJOR — Principle I redefined; Principle IV amended;
  new Principle VI added)
Modified principles:
  - I. "On-Device Only (NON-NEGOTIABLE)" → "Privacy-First Inference"
    Redefined from an absolute on-device mandate to a tiered model: on-device
    is the default and required for the core pipeline; cloud AI (Gemini /
    Claude via personal subscription) is an explicit opt-in mode with strict
    data minimisation and user-consent constraints.
  - IV. "Minimal Permissions Footprint" — INTERNET permission now permitted
    when cloud inference mode is enabled; all prior constraints otherwise
    unchanged.
  - II. "Real-Time Responsiveness" — reference to "Principle I" updated to
    "Principle I/VI" (cloud path has its own latency considerations).
Added sections:
  - Principle VI: Cloud Inference Mode (opt-in, personal subscription only)
Removed sections: none
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no structural change needed
  - .specify/templates/spec-template.md ✅ no structural change needed
  - .specify/templates/tasks-template.md ✅ no structural change needed
In-flight specs requiring re-evaluation:
  - specs/001-realtime-mic-suggestions ⚠️ Constitution Check must be re-run
    against new Principle I; on-device path is unaffected, but plan should
    note that the suggestion engine will be replaceable with cloud backend
  - specs/004-question-detection ⚠️ research.md and plan.md Technical Context
    should be updated to reflect cloud backend option when spec-005 lands
  - specs/002-floating-fullscreen-views — no impact
  - specs/003-session-modes-context — no impact (profile storage remains local)
Follow-up TODOs:
  - Write spec-005 for cloud AI inference mode (Gemini / Claude subscription)
  - Update specs/001 and specs/004 Constitution Check sections post spec-005
-->

# MeetMind Assistant Constitution

## Core Principles

### I. Privacy-First Inference

The app operates in two mutually exclusive inference modes: **On-Device** (default)
and **Cloud** (opt-in). The following rules apply unconditionally to both modes:

- Raw audio captured by the microphone MUST NEVER leave the device under any
  circumstances. Only the text transcript of a detected question MAY be transmitted
  externally, and only when Cloud mode is explicitly enabled by the user.
- On-Device mode MUST remain fully functional with no network connection. The app
  MUST be usable offline at all times in On-Device mode.
- Cloud mode is strictly opt-in. The user MUST explicitly enable it in settings.
  It MUST default to disabled on fresh install and after any app data wipe.
- When Cloud mode is active, a persistent, unambiguous visual indicator MUST be
  displayed on every screen that can generate a suggestion, so the user always
  knows data is leaving the device.
- The transcript text sent to a cloud provider MUST be the minimum necessary: the
  detected question text only (≤150 tokens). No session history, no profile data
  beyond role, no audio, no device identifiers MAY be included in any API call.
- Cloud mode uses only the user's own personal subscription API key (Gemini or
  Claude). The API key is stored in Android EncryptedSharedPreferences and is never
  transmitted anywhere except to the respective provider's official API endpoint.
- No third-party analytics, crash reporting, or telemetry service MAY receive any
  audio, transcript, or suggestion data from any mode.

**Rationale**: The original absolute on-device mandate was correct in principle but
overly restrictive given that (a) the user owns their own subscription, (b) only
transcript text — never audio — would be sent, and (c) cloud models produce
materially better suggestions. Privacy is preserved by strict opt-in, audio
containment, data minimisation, and user-owned keys.

### II. Real-Time Responsiveness

The suggestion pipeline (audio → transcript → context → suggestion) MUST produce
visible output within 3 seconds of the triggering utterance. This applies to both
inference modes:

- **On-Device mode**: Measured on a mid-range Android device (Snapdragon 700-series,
  6 GB RAM minimum). Streaming token display MUST be used so the first visible token
  appears within 1 second.
- **Cloud mode**: Measured from the moment the API request is dispatched to the moment
  the first response token is rendered. Network latency is accepted as a variable;
  the implementation MUST use streaming responses (Server-Sent Events or equivalent)
  to minimise perceived latency. A timeout of 5 seconds MUST trigger a fallback to
  On-Device mode for that suggestion.

If a model or algorithm cannot meet these targets, a lighter alternative MUST be
evaluated before shipping. Latency optimisations MUST NOT weaken the data-minimisation
constraints in Principle I.

**Rationale**: Suggestions delivered after the conversational moment has passed deliver
no value. Sub-3-second response (or fast first-token) is the product's core
differentiator regardless of inference mode.

### III. APK Distribution (Personal Use)

The deliverable is a self-contained APK sideloaded by the developer/owner. There is
no Play Store submission, no MDM distribution, and no multi-user account system.
Features requiring Play Services APIs that cannot be replaced with FOSS equivalents
MUST be flagged before implementation. Signing MUST use a developer debug keystore or
a personally managed release keystore — no CI/CD signing infrastructure is required.

**Rationale**: Personal-project scope keeps compliance overhead near zero and allows
rapid iteration without store review cycles.

### IV. Minimal Permissions Footprint

The app MUST request only the permissions required for active features:

- `RECORD_AUDIO`: always required (core audio capture).
- `INTERNET`: MUST be declared when Cloud inference mode is implemented (spec 005+).
  MUST NOT be declared before that spec is merged. When declared, it is used
  exclusively for API calls to Gemini or Claude endpoints — no other network
  communication is permitted.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`: required for background
  audio capture service.
- `SYSTEM_ALERT_WINDOW`: required for floating overlay (spec 002).
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: required to keep the foreground service
  alive on aggressive OEM battery optimizers.
- All other permissions (contacts, calendar, location, camera, etc.) are forbidden
  unless added by amendment with written justification.

Permission rationale MUST be documented in the spec for any feature that introduces
a new `uses-permission` entry.

**Rationale**: Unnecessary permissions erode user trust and widen the attack surface
on a device that handles sensitive meeting content.

### V. Incremental, Demo-Able Slices

Every feature MUST be scoped so that each user story is independently runnable and
demonstrable on-device before the next story begins. "Big bang" integrations that
require multiple unfinished stories to produce any observable output are forbidden.
Each phase MUST leave the app in a launchable, non-crashing state.

**Rationale**: Solo personal projects stall when intermediate states are invisible.
Incremental demos keep motivation high and surface integration issues early.

### VI. Cloud Inference Mode (Opt-In, Personal Subscription)

When Cloud inference mode is enabled by the user, the following rules apply in
addition to all constraints in Principle I:

- **Supported providers**: Google Gemini (via Gemini API) and Anthropic Claude (via
  Claude API). No other cloud AI providers are permitted without a further amendment.
- **API key management**: The user supplies their own API key in settings. Keys are
  stored in Android `EncryptedSharedPreferences` using the Android Keystore-backed
  encryption. Keys MUST NOT be logged, included in crash reports, or stored in
  plaintext anywhere.
- **Data sent per request**: detected question text (≤150 tokens) + user role
  (≤20 tokens) + session mode (`MEETING` or `INTERVIEW`). Nothing else.
- **Streaming**: Responses MUST use streaming APIs (Gemini streaming, Claude streaming)
  so that first-token display latency is minimised. Do not wait for a complete
  response before rendering.
- **Fallback**: If the API call fails, times out (>5s), or the device has no network
  connectivity, the suggestion engine MUST fall back to On-Device mode for that
  request and display a non-intrusive indicator that cloud was unavailable.
- **Mode indicator**: A persistent badge or label (e.g., "☁ Cloud") MUST be visible
  on the suggestion display screen whenever Cloud mode is active.
- **No key sharing**: API keys are personal credentials. The app MUST NOT expose
  keys in any shared storage, logs, or inter-app communication channel.

**Rationale**: A personal subscription to Gemini or Claude produces substantially
higher-quality suggestions than any model that can be run on-device within the 3s
latency budget. Since the user owns the subscription and controls the API key,
there is no third-party data brokering — it is equivalent to the user manually
pasting a question into a chat interface, but automated. The strict data minimisation
rules ensure that only the minimum transcript fragment is sent, never raw audio or
full session context.

## Platform Constraints

- **Target platform**: Android (APK only, API 26+ / Android 8.0 minimum)
- **Primary test devices**: Lenovo Legion Y700 Gen 3, Honor Magic 6 Pro
  (Snapdragon 8 Gen 3, 12–16 GB RAM)
- **Language**: Kotlin (primary); Java interop permitted for library bindings only
- **Build system**: Gradle with Kotlin DSL
- **UI framework**: Jetpack Compose (preferred); do not mix with XML Views on the
  same screen
- **On-device ML**: TFLite (Silero VAD); llama.cpp via JNI (Phi-3-mini Q4_0,
  OpenCL backend for Adreno 750); Vosk Android SDK for ASR
- **Audio**: Android `AudioRecord` API; VAD MUST gate transcription
- **Storage**: Room database for structured local persistence (ContextProfile,
  API key encrypted store); no remote database
- **Cloud AI** (when enabled): Gemini API or Claude API; user-supplied key;
  streaming responses only; transcript text only
- **No backend**: Zero server-side components owned by this project. Cloud AI
  calls go directly from the device to the provider's official endpoint

## Development Workflow

- All work MUST be branch-based (`git flow` or feature-branch off `develop`)
- Specs MUST be written and approved before implementation begins
- Each feature branch MUST include an APK smoke-test checkpoint (manual install +
  core path verification) before merge
- Performance profiling (Android Studio Profiler or `systrace`) is REQUIRED for any
  change to the audio pipeline or inference path
- Any feature that adds a network call MUST include a test verifying that the
  On-Device fallback activates when the network is unavailable
- Dependency upgrades that increase APK size by more than 5 MB MUST be justified in
  the PR description

## Governance

This constitution supersedes all other practices, README guidance, and verbal
agreements. Amendments require:

1. A written rationale explaining why the change is necessary
2. A version bump per semantic versioning (MAJOR for principle removal/redefinition,
   MINOR for new principle or section, PATCH for clarification/wording)
3. An updated `LAST_AMENDED_DATE`
4. Re-evaluation of any in-flight specs that may be affected

All plans and specs MUST include a **Constitution Check** section that cites the
principles governing the feature's constraints. Complexity MUST be justified against
these principles — if a design violates a principle, the violation MUST be documented
with rationale rather than silently ignored.

**Version**: 2.0.0 | **Ratified**: 2026-04-14 | **Last Amended**: 2026-04-14
