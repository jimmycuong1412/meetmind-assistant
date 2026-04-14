# Feature Specification: Session Modes and Context Profiles

**Feature Branch**: `feature/003-session-modes-context`
**Created**: 2026-04-14
**Status**: Draft
**Input**: User description: "the app should have 2 modes, meeting mode and interview mode,
both of mode requires user input context information like role, context (context input only
require from the first time then saved then can edit it later) to implement the suggest
answer more accurate"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First-Time Context Setup (Priority: P1)

A brand-new user opens the app for the first time. Before they can start a session, the
app prompts them to enter their context profile: their role (e.g., "Senior Software
Engineer", "Product Manager") and a freeform context note (e.g., "I work at a fintech
startup focused on payment APIs. We are interviewing for a senior backend role."). This
information is saved locally and used by the suggestion engine to generate relevant,
personalised responses.

**Why this priority**: Without a saved context profile, the suggestion engine has no
grounding information and produces generic, low-value suggestions. This is the one-time
setup that unlocks the core value of the app.

**Independent Test**: Fresh install — open the app. Verify the context setup screen is
shown before the main session screen. Fill in role and context, save. Verify the main
screen appears and subsequent suggestions mention or align with the entered context.

**Acceptance Scenarios**:

1. **Given** the app is launched for the first time with no saved profile, **When** the
   app opens, **Then** a context setup screen is displayed before any session can start.

2. **Given** the context setup screen is displayed, **When** the user enters their role
   and context and taps Save, **Then** the profile is persisted locally and the app
   navigates to the main session screen.

3. **Given** a profile has been saved, **When** the app is reopened (subsequent launches),
   **Then** the context setup screen is NOT shown; the app goes directly to the session
   screen.

4. **Given** the context setup screen is displayed, **When** the user attempts to proceed
   without filling in the required role field, **Then** the app shows a validation message
   and does not navigate away.

---

### User Story 2 - Select Session Mode (Priority: P1)

Each time the user starts a new session, they choose between **Meeting Mode** and
**Interview Mode** before starting to listen. The selected mode is shown as an active
label throughout the session and influences how suggestions are framed (e.g., meeting
mode suggestions are collaborative and action-oriented; interview mode suggestions are
answer-focused and self-promotional).

**Why this priority**: The two modes produce fundamentally different suggestion styles.
Selecting the wrong mode would actively mislead the user mid-conversation. The mode
selector is therefore required before each session start, not just once.

**Independent Test**: On the session screen, verify two distinct mode options are
visible. Select Meeting Mode, start listening, speak a question. Verify the suggestion
style aligns with a meeting context. Repeat for Interview Mode.

**Acceptance Scenarios**:

1. **Given** the user is on the session home screen, **When** they view it, **Then**
   both "Meeting" and "Interview" mode options are visible and selectable.

2. **Given** the user selects a mode, **When** they tap Start Listening, **Then**
   the active mode label is displayed on the listening screen for the duration of
   the session.

3. **Given** the user is in an active session, **When** they view the suggestion,
   **Then** the suggestion text is contextually appropriate for the selected mode
   (meeting: collaborative/action language; interview: personal/achievement language).

4. **Given** the user finishes a session and starts a new one, **Then** no mode is
   pre-selected — the user must choose again each time.

---

### User Story 3 - Edit Saved Context Profile (Priority: P2)

A returning user wants to update their context — for example, they have switched jobs
or are preparing for a different type of interview. They access the profile settings
screen, update their role and/or context note, and save. All subsequent suggestions
use the updated context.

**Why this priority**: Context goes stale. A user preparing for a DevOps interview
has different context than when they were a junior dev. Editability is essential
for long-term usefulness, but it's not blocking the first-use or core session flow.

**Independent Test**: Navigate to settings/profile, change the role field, save.
Start a new session and speak a question. Verify the suggestion reflects the updated
role.

**Acceptance Scenarios**:

1. **Given** the user has a saved profile, **When** they navigate to the profile/
   settings screen, **Then** their current role and context values are pre-filled
   in editable fields.

2. **Given** the user edits one or more fields and taps Save, **Then** the updated
   values are persisted and immediately used for the next session's suggestions.

3. **Given** the user edits the profile and taps Cancel (or navigates back without
   saving), **Then** the original values are retained unchanged.

4. **Given** the user clears the role field entirely and tries to save, **Then** a
   validation message is shown and the save is blocked.

---

### User Story 4 - Profile Context Feeds Into Suggestions (Priority: P1)

During an active session, the suggestion engine incorporates the saved profile (role +
context note) and selected mode when generating suggestions. The user can observe that
suggestions are more specific and relevant than they would be without context — for
instance, referencing the user's stated domain or framing answers from their stated
role's perspective.

**Why this priority**: This is the "so what" of the entire feature. Modes and profiles
only matter if they visibly improve suggestion quality. This story validates the
end-to-end connection between stored context and generated output.

**Independent Test**: Set role = "Data Engineer", context = "Working on a real-time
pipeline for an e-commerce company". Start Interview Mode. Ask "Tell me about a
challenging technical problem you solved." Verify the suggestion references data
engineering or pipeline concepts rather than a generic answer.

**Acceptance Scenarios**:

1. **Given** a profile with a specific role and context is saved, **When** a session
   is active in Interview Mode and a question is spoken, **Then** the generated
   suggestion references or aligns with the user's stated role or context.

2. **Given** the same spoken question is processed in Meeting Mode vs Interview Mode
   with the same profile, **Then** the two suggestions are observably different in
   tone and framing.

3. **Given** a user edits their profile between sessions, **When** a new session
   is started, **Then** suggestions reflect the updated profile, not the old one.

---

### Edge Cases

- What happens if the context note is left blank (only role is entered)?
- What if the user enters an unusually long context note (e.g., 2000+ characters)?
  Is there a character limit?
- What if the profile data on disk becomes corrupted — does the app re-show the
  first-time setup screen?
- If the user is mid-session and navigates to edit their profile, does the active
  session immediately use the new context or only on the next session?
- Can the user switch modes mid-session, or is the mode locked once a session starts?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST require a saved context profile (role + context note) before
  any session can be started.
- **FR-002**: On first launch (no saved profile), the app MUST display a setup screen
  to collect the user's role and context note before proceeding.
- **FR-003**: The role field is REQUIRED; the app MUST NOT allow saving a profile
  with an empty role.
- **FR-004**: The context note field is optional; the app MUST allow saving with only
  a role and no context note.
- **FR-005**: The saved profile MUST be persisted locally on the device and survive
  app restarts, device reboots, and app updates.
- **FR-006**: Subsequent app launches MUST skip the first-time setup screen when a
  valid profile exists.
- **FR-007**: The app MUST provide an accessible route to edit the saved profile at
  any time from the main screen (e.g., a settings or profile icon).
- **FR-008**: Profile edits MUST be cancellable — navigating away without saving MUST
  leave the original profile unchanged.
- **FR-009**: The app MUST present a clear mode selection (Meeting / Interview) on the
  session home screen before each session.
- **FR-010**: The selected mode MUST be visible as a persistent label on the
  listening/suggestion screen throughout the active session.
- **FR-011**: The suggestion engine MUST receive and incorporate both the active mode
  and the saved profile (role + context note) when generating each suggestion.
- **FR-012**: The mode selection MUST NOT be persisted across sessions; the user
  selects a mode fresh each time.
- **FR-013**: Switching modes mid-session is NOT supported in v1; the mode selector
  is disabled once a session is active.

### Key Entities

- **Context Profile**: The user's persistent identity data for suggestion grounding.
  Attributes: `role` (required, string, max 100 chars), `context_note` (optional,
  string, max 1000 chars), `updated_at` (timestamp). Only one profile exists at a time.
- **Session Mode**: An enumerated selection made at the start of each session.
  Values: `MEETING`, `INTERVIEW`. Not persisted; reset each session.
- **Session**: (from spec 001) Extended with an associated `mode` and a snapshot of
  the `context_profile` active at session start.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time user can complete the context setup (role + context entry +
  save) and reach the session screen in under 2 minutes without any instructions.
- **SC-002**: A returning user can update their context profile and start a new session
  in under 60 seconds from opening the app.
- **SC-003**: Suggestions generated in Interview Mode are observably different in tone
  from suggestions generated for the identical spoken input in Meeting Mode — verified
  by 3 independent test utterances producing distinct outputs.
- **SC-004**: The saved context profile survives a device reboot and an app force-stop,
  confirmed by reopening the app and verifying the profile fields contain the previously
  entered values.
- **SC-005**: The user can complete a full session (setup → mode select → listen →
  suggestion → stop) without the app prompting them to re-enter their context, once
  it has been saved.

## Assumptions

- There is exactly one context profile per device (no multi-profile support in v1).
- The profile is stored entirely on-device; no cloud sync or backup is in scope
  (consistent with the on-device-only constitution principle).
- Role is a free-text field; there is no predefined dropdown of roles in v1.
- The context note character limit is 1000 characters — balancing richness of context
  with on-device inference constraints.
- "Meeting mode" suggestions are framed for collaborative, group-discussion contexts
  (e.g., action items, follow-up questions, summaries). "Interview mode" suggestions
  are framed for candidate-side responses (e.g., STAR-format answers, achievements,
  clarifying questions).
- The exact prompt engineering / suggestion framing strategy for each mode is a
  planning / implementation concern, not a spec concern.
- Profile data is not encrypted beyond the standard Android application sandbox;
  if encryption is needed it will be added via amendment.
- Switching modes mid-session is not supported; this is an intentional scope boundary
  for v1 to keep the session state model simple.
- This feature depends on spec 001 (real-time mic suggestions) — the suggestion engine
  described here is the same engine from spec 001, extended with mode and profile inputs.
