# Feature Specification: Cloud AI Inference Mode

**Feature Branch**: `feature/005-cloud-ai-inference`
**Created**: 2026-04-14
**Status**: Draft
**Input**: User direction: "use gemini and claude pro subscription for ai models"
**Constitution version at creation**: 2.0.0

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure a Cloud AI Provider (Priority: P1)

A user opens Settings and sees a "Cloud AI" section. They select their preferred
provider (Gemini or Claude), enter their personal API key from their existing
subscription, and save. The app validates that the key is accepted by the provider.
From this point forward, Cloud mode is available to enable for sessions.

**Why this priority**: Without a valid, stored API key there is no cloud path at all.
This is the gate that unlocks everything else in this feature. It must be done first
and must work independently of any session.

**Independent Test**: Navigate to Settings → Cloud AI. Select Gemini. Enter a valid
API key. Tap Save. Verify the app shows a "Connected" confirmation and the key is
retained after closing and reopening the app. Repeat with a deliberately wrong key
and verify an error is shown.

**Acceptance Scenarios**:

1. **Given** the user opens Settings for the first time, **When** they view the
   Cloud AI section, **Then** Cloud mode shows as "Disabled" and no provider is
   configured.

2. **Given** the user selects a provider and enters a valid API key and taps Save,
   **Then** the app performs a lightweight validation call (e.g., a minimal test
   request) and, on success, displays a "Connected" confirmation.

3. **Given** the user enters an invalid or expired API key and taps Save, **Then**
   the app displays a clear error message ("Key invalid or unauthorised") and does
   not save the key.

4. **Given** a valid key has been saved, **When** the user reopens the app, **Then**
   the key is still present (not lost on restart) and the provider shows as
   "Connected".

5. **Given** a key is saved, **When** the user taps "Remove key", **Then** the key
   is permanently deleted and Cloud mode reverts to disabled.

---

### User Story 2 - Enable Cloud Mode for a Session (Priority: P1)

Before starting a session, the user can toggle Cloud mode on or off. When on, a
"☁ Cloud" badge is prominently displayed on the session screen throughout the
session. When off, the session uses on-device inference as before (spec 001/004).

**Why this priority**: The toggle is the runtime control that activates the cloud path.
Without it, there is no way to use the configured API key. The badge is non-optional —
the constitution requires it.

**Independent Test**: With a valid API key configured, go to the session home screen.
Toggle Cloud mode ON. Start a session. Verify the "☁ Cloud" badge is visible on the
listening screen. Speak a question. Verify a suggestion appears that is observably
different from (and higher-quality than) the on-device suggestion for the same input.

**Acceptance Scenarios**:

1. **Given** a valid API key is configured, **When** the user views the session home
   screen, **Then** a Cloud mode toggle is visible and shows its current state (ON/OFF).

2. **Given** Cloud mode is toggled ON, **When** the session screen is active, **Then**
   a persistent "☁ Cloud" badge or label is visible and cannot be dismissed.

3. **Given** no API key is configured, **When** the user attempts to toggle Cloud mode
   ON, **Then** the toggle is disabled and a prompt directs the user to Settings to
   add a key first.

4. **Given** Cloud mode is ON, **When** the user stops the session and starts a new
   one, **Then** the Cloud mode preference is remembered (it persists across sessions
   until the user explicitly toggles it off).

---

### User Story 3 - Receive a Cloud-Generated Suggestion (Priority: P1)

During a Cloud mode session, when a question is detected (spec 004), the app sends
only the question text to the configured cloud provider and displays the streamed
response as a suggestion. The first words of the suggestion appear on screen within
3 seconds. The "☁ Cloud" badge remains visible while the suggestion is displayed.

**Why this priority**: This is the end-to-end value delivery. The entire feature
exists to produce better suggestions — this story proves it works.

**Independent Test**: With Cloud mode ON (Gemini or Claude key configured), start a
session and ask: "Tell me about a time you had to make a difficult technical decision
under pressure." Verify a suggestion appears within 3 seconds (first token) and is
noticeably more detailed and contextually relevant than what the on-device model
produces for the same question.

**Acceptance Scenarios**:

1. **Given** Cloud mode is ON and a question is detected, **When** the API call is
   dispatched, **Then** the first suggestion tokens appear on screen within 3 seconds
   (measured from question-end to first visible word).

2. **Given** a cloud suggestion is being streamed, **When** tokens arrive, **Then**
   they are appended to the suggestion card in real time (not displayed all at once
   after completion).

3. **Given** a cloud suggestion is displayed, **Then** the "☁ Cloud" badge remains
   visible on the suggestion card for its entire display lifetime.

4. **Given** the same question is processed in Interview Mode vs Meeting Mode,
   **Then** the cloud suggestion is framed appropriately for each mode (first-person
   answer vs collaborative contribution), using the user's saved role as context.

---

### User Story 4 - Graceful Fallback When Cloud Is Unavailable (Priority: P1)

If the cloud API call fails (no network, timeout > 5 seconds, rate limit, invalid
key), the app silently falls back to the on-device suggestion engine for that
question and shows a small, non-intrusive indicator that cloud was unavailable for
that suggestion. The session continues uninterrupted.

**Why this priority**: Cloud availability is not guaranteed. A session in a basement,
on a plane, or with a temporarily revoked key must still work. On-device is always
the safety net. This is not optional — it is a constitutional requirement.

**Independent Test**: Configure Cloud mode ON. Disable Wi-Fi and mobile data on the
device. Start a session and ask a question. Verify a suggestion still appears (from
on-device inference) and a small indicator shows "⚡ On-device (cloud unavailable)".
Re-enable network. Ask another question. Verify the cloud badge resumes.

**Acceptance Scenarios**:

1. **Given** Cloud mode is ON but the device has no network connectivity, **When** a
   question is detected, **Then** the on-device engine generates the suggestion and
   a non-intrusive indicator shows "cloud unavailable".

2. **Given** Cloud mode is ON and the API call does not return a first token within
   5 seconds, **Then** the request is cancelled, the on-device engine begins
   generating a suggestion, and a non-intrusive indicator shows "cloud timed out".

3. **Given** Cloud mode is ON and the provider returns an authentication error (401),
   **Then** Cloud mode is automatically disabled for the session and the user is
   notified with a prompt to check their API key in Settings.

4. **Given** a fallback to on-device occurred, **When** the next question is detected
   and network is available, **Then** the cloud path is attempted again (the fallback
   is per-suggestion, not permanent).

---

### Edge Cases

- What happens if the user's API key hits its rate limit mid-session?
- What if the cloud provider returns a content-filtered response (empty or blocked)?
- What happens if the user switches providers (Gemini → Claude) while a session is
  active?
- What if the device is on a metered mobile connection — is there a data-usage
  warning?
- What if the API key is valid but the user's subscription has been downgraded and
  the model they expect is no longer accessible?
- What happens if the suggestion from the cloud provider is unusually long (e.g., 500
  words)? Is it truncated?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support configuration of exactly one cloud AI provider
  at a time: Google Gemini or Anthropic Claude. Switching provider requires re-entering
  a key for the new provider.
- **FR-002**: The user's API key MUST be stored using encrypted, hardware-backed
  storage on the device. It MUST NOT be stored in plaintext or accessible outside
  the app's sandbox.
- **FR-003**: Cloud mode MUST default to disabled on fresh install. It MUST only
  activate when the user explicitly enables it via a toggle in settings or on the
  session screen.
- **FR-004**: When Cloud mode is active, a persistent "☁ Cloud" visual indicator
  MUST be displayed on the listening and suggestion screens. It MUST NOT be
  dismissible by the user while Cloud mode is active.
- **FR-005**: The data sent per cloud API request MUST be limited to: detected question
  text (≤150 tokens), user role (≤20 tokens), and session mode (`MEETING` or
  `INTERVIEW`). No raw audio, session history, device identifiers, or additional
  profile data MAY be included.
- **FR-006**: The app MUST use the provider's streaming API to begin displaying
  suggestion tokens as they arrive, rather than waiting for a complete response.
- **FR-007**: If no cloud response first-token is received within 5 seconds of the
  API request being dispatched, the request MUST be cancelled and the on-device
  engine MUST be used for that suggestion.
- **FR-008**: If the cloud API returns an error (network failure, 4xx, 5xx, timeout),
  the system MUST fall back to the on-device suggestion engine for that request and
  display a non-intrusive fallback indicator.
- **FR-009**: If the cloud API returns a 401 Unauthorised response, the system MUST
  automatically disable Cloud mode for the remainder of the session and surface a
  notification prompting the user to verify their API key.
- **FR-010**: Cloud-generated suggestions MUST be capped at 200 tokens (approximately
  3–4 sentences). Any response exceeding this length MUST be truncated at a sentence
  boundary before display.
- **FR-011**: The Cloud mode toggle state MUST persist across sessions (once enabled,
  it stays enabled until the user turns it off), but MUST default to disabled on
  first install.
- **FR-012**: Raw audio MUST NEVER be transmitted to any cloud endpoint under any
  circumstances, including when Cloud mode is active.
- **FR-013**: The `INTERNET` permission MUST be declared in the manifest when this
  feature is merged. It is used exclusively for calls to the configured provider's
  official API endpoint.

### Key Entities

- **CloudProviderConfig**: The user's cloud AI configuration. One per device.
  Attributes: `provider` (enum: `GEMINI`, `CLAUDE`), `encryptedApiKey` (String, stored
  in EncryptedSharedPreferences), `isEnabled` (Boolean), `connectionStatus` (enum:
  `UNCONFIGURED`, `CONNECTED`, `ERROR`), `lastValidatedAt` (Long timestamp).
- **CloudInferenceRequest**: A single cloud API request for one suggestion.
  Attributes: `questionText` (String, ≤150 tokens), `userRole` (String, ≤20 tokens),
  `sessionMode` (SessionMode), `provider` (CloudProvider), `dispatchedAt` (Long),
  `firstTokenAt` (Long?), `completedAt` (Long?).
- **InferenceMode** (enum): `ON_DEVICE`, `CLOUD`. The active mode for the current
  suggestion. Stored on each `Suggestion` entity (extends spec 001/004 model).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can configure a valid API key and receive their first cloud
  suggestion within 5 minutes of opening the settings screen for the first time.
- **SC-002**: The first suggestion token is visible on screen within 3 seconds of a
  detected question ending, measured on a Wi-Fi connection (typical round-trip to
  Gemini/Claude APIs is 500–800ms; streaming begins immediately).
- **SC-003**: When the device has no network connectivity with Cloud mode ON, a
  suggestion still appears (from on-device fallback) within 5 seconds of a detected
  question, and a fallback indicator is visible.
- **SC-004**: A network capture during a cloud session confirms that only text data
  (no audio bytes) is transmitted to the cloud provider endpoint.
- **SC-005**: Cloud-generated suggestions for interview questions are observably more
  detailed and contextually relevant than on-device suggestions for the same input,
  as assessed by the user across 5 test questions.
- **SC-006**: After a 401 error is returned by the provider, the app automatically
  disables Cloud mode and surfaces a key-check notification within 2 seconds, without
  crashing or requiring a restart.

## Assumptions

- The user has an active personal subscription to Gemini (Google AI Studio API or
  Gemini Advanced) or Claude (Anthropic API / Claude.ai Pro) and can obtain an API key.
- Only one provider is active at a time; support for simultaneous multi-provider
  calls is out of scope for v1.
- The app does not manage subscription billing, tier selection, or model version
  selection within the app — the user manages this through their provider's dashboard.
- The specific model version used per provider is fixed in code for v1 (e.g.,
  `gemini-2.0-flash` or `claude-3-5-haiku-latest`) and not user-selectable. The
  chosen models are fast, low-latency, and available on the relevant subscription tiers.
- Cloud mode is only usable when the device has network access; the app does not
  queue or buffer requests for later transmission.
- The data-usage cost per suggestion is small (≤400 tokens round-trip); no in-app
  data usage warning is required for v1, but this may be revisited based on usage.
- Long cloud responses (>200 tokens) are truncated at a sentence boundary; the
  truncation logic is a planning/implementation concern.
- This feature builds on spec 001 (audio capture), spec 003 (mode + profile), and
  spec 004 (question detection). The suggestion engine is extended, not replaced —
  on-device inference remains fully functional as the default and fallback.
- The app does not implement its own retry logic beyond the 5-second timeout; if a
  cloud call fails, it falls back immediately to on-device.
