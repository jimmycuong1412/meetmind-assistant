# Feature Specification: Floating and Full-Screen Views

**Feature Branch**: `feature/002-floating-fullscreen-views`
**Created**: 2026-04-14
**Status**: Draft
**Input**: User description: "The app must support both floating and full-screen views"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Use App in Full-Screen Mode (Priority: P1)

A user opens the app normally. It launches in full-screen mode, occupying the entire
display. All suggestion content, controls, and session indicators are fully visible.
This is the primary entry point and the mode with the richest display area for
reading suggestions during a meeting or interview.

**Why this priority**: Full-screen is the natural default app experience. Without a
functional full-screen view, the floating mode has nothing to fall back to, and the
core suggestion feature (spec 001) has no complete home.

**Independent Test**: Launch the app cold. Verify it opens full-screen, shows the
Start Listening button and suggestion area, and all UI elements are accessible with
no cropping or overlap.

**Acceptance Scenarios**:

1. **Given** the app is launched from the home screen or app drawer, **When** it
   opens, **Then** it fills the entire screen with all controls and suggestion
   content fully visible.

2. **Given** the app is in full-screen mode, **When** suggestions are generated
   (per spec 001), **Then** they display with full readability — no truncation due
   to limited screen area.

3. **Given** the app is in full-screen mode, **When** the user presses the device
   Back button or Home button, **Then** the app behaves per standard Android
   navigation (back closes/minimizes; home goes to launcher).

---

### User Story 2 - Switch to Floating (Overlay) Mode (Priority: P1)

While in full-screen mode during an active session, the user taps a "Float" button.
The app shrinks to a compact floating overlay that hovers over other apps. The user
can now switch to another app (e.g., their video call client, notes app, or browser)
while the floating window remains visible on top, continuing to display incoming
suggestions.

**Why this priority**: This is the primary use-case differentiator. In a real meeting
or interview, the user is almost always in another app (video call, browser, document).
Without floating mode, the suggestion feature is blocked by the need to context-switch
back to the app to read suggestions.

**Independent Test**: Launch the app, start listening, tap Float. Open a different
app (e.g., the device browser). Verify the floating window is visible on top of the
browser and shows new suggestions as they arrive.

**Acceptance Scenarios**:

1. **Given** the app is in full-screen mode, **When** the user taps "Float",
   **Then** the app transitions to a compact floating window visible over other apps.

2. **Given** the floating window is active, **When** the user opens another app,
   **Then** the floating window remains visible on top without being obscured.

3. **Given** the floating window is active and a suggestion is generated, **Then**
   the suggestion text appears in the floating window within the same 3-second SLA
   as full-screen mode.

4. **Given** the floating window is active, **When** the user taps it,
   **Then** the app returns to full-screen mode.

---

### User Story 3 - Move and Resize Floating Window (Priority: P2)

The user can drag the floating window to any position on the screen to avoid blocking
important content in the underlying app (e.g., a video call participant's face, or
a key section of a document).

**Why this priority**: Position control is essential for usability — a floating window
stuck in a fixed corner may block critical content. However, the app is still usable
without repositioning, so this is P2.

**Independent Test**: In floating mode, drag the window from one corner of the screen
to the opposite corner. Verify it follows the drag gesture and stays in the new
position after release.

**Acceptance Scenarios**:

1. **Given** the app is in floating mode, **When** the user touches and drags the
   floating window, **Then** the window follows the drag gesture in real time.

2. **Given** the user releases the drag, **Then** the window stays at the released
   position (does not snap back to a fixed location).

3. **Given** the user drags the window to a screen edge, **Then** the window stops
   at the screen boundary and does not go off-screen.

---

### User Story 4 - Grant Overlay Permission (Priority: P1)

Before the floating mode can be used for the first time, the user must grant the
"Display over other apps" permission. The app guides the user through this one-time
setup step with a clear explanation of why it is needed.

**Why this priority**: Without this permission, floating mode is blocked entirely.
The permission flow must be smooth and non-confusing to avoid users abandoning the
feature before it's enabled.

**Independent Test**: On a fresh install (or with the permission revoked), tap Float.
Verify the app shows an explanation screen, deep-links to the system permission screen,
and returns to floating mode once permission is granted.

**Acceptance Scenarios**:

1. **Given** the overlay permission has not been granted, **When** the user taps
   "Float", **Then** the app shows a one-screen explanation of why the permission
   is needed before navigating to the system settings.

2. **Given** the user grants the permission in system settings and returns to the app,
   **Then** floating mode activates immediately without the user needing to tap Float
   again.

3. **Given** the user denies the permission, **When** they return to the app,
   **Then** the app returns to full-screen mode and shows a brief message that
   floating mode requires the permission.

---

### Edge Cases

- What happens if the system revokes the overlay permission while the app is in
  floating mode (e.g., the user revokes it from settings while the session is live)?
- What happens when the floating window is active and an incoming phone call arrives
  (system overlay priority conflict)?
- What is the minimum screen size / aspect ratio that the floating window must
  support without becoming unreadable?
- What happens when the device is rotated while in floating mode?
- If the floating window is dragged off-screen partially, is there a "snap back"
  or "bring to front" rescue mechanism?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST launch in full-screen mode by default.
- **FR-002**: The app MUST provide a clearly labeled control to switch from
  full-screen mode to floating (overlay) mode.
- **FR-003**: In floating mode, the app's window MUST display on top of other
  running applications.
- **FR-004**: The floating window MUST continue to display incoming suggestions
  with the same 3-second SLA as in full-screen mode (per spec 001 SC-001).
- **FR-005**: The floating window MUST be draggable to any position within the
  visible screen boundaries.
- **FR-006**: The floating window MUST NOT be draggable partially or fully off-screen.
- **FR-007**: Tapping the floating window MUST return the app to full-screen mode.
- **FR-008**: Before activating floating mode for the first time, the app MUST
  present a permission explanation screen and guide the user to grant the "display
  over other apps" system permission.
- **FR-009**: If the overlay permission is denied, the app MUST remain functional
  in full-screen mode and display a non-blocking notice that floating mode is
  unavailable.
- **FR-010**: The floating window MUST display at minimum the most recent suggestion
  in a readable font size without requiring the user to interact with it.
- **FR-011**: The "Float" control MUST be accessible from the main full-screen UI
  at all times (idle and active session states).

### Key Entities

- **View Mode**: An enumerated state of the app's display — either `FULL_SCREEN`
  or `FLOATING`. Persisted for the duration of the session; resets to `FULL_SCREEN`
  on app restart.
- **Floating Window Position**: The (x, y) coordinates of the floating window on
  screen. Defaults to a defined corner; updated on drag. Not persisted across
  sessions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The transition from full-screen to floating mode completes in under
  1 second from the moment the user taps the Float button.
- **SC-002**: Suggestions continue to appear in the floating window within 3 seconds
  of a detected utterance, matching the full-screen SLA (spec 001 SC-001).
- **SC-003**: The user can reposition the floating window to any quadrant of the
  screen using a single drag gesture with no additional confirmation steps.
- **SC-004**: A first-time user who has never granted the overlay permission
  successfully reaches the system settings screen within 2 taps from the Float button.
- **SC-005**: The floating window remains visible on top of at least 3 different
  foreground apps tested (e.g., video call client, browser, notes app) without
  being obscured.

## Assumptions

- "Floating" means an always-on-top overlay window, not a picture-in-picture (PiP)
  mode — the two are distinct Android mechanisms; overlay is assumed here.
- The floating window displays suggestions in a read-only, compact format; it does
  not replicate all controls from the full-screen view (Start/Stop/Clear are only
  accessible in full-screen).
- The floating window size is fixed (not user-resizable) in v1; a single compact
  size is chosen during design.
- View mode state is not persisted across app restarts; the app always opens in
  full-screen mode.
- The floating window position resets to a default corner on each app launch; per-
  session repositioning only.
- Floating mode requires the Android "Display over other apps" permission; this is
  a known Android restriction and not a design limitation of this feature.
- The feature depends on spec 001 (real-time mic suggestions) for the suggestion
  content it displays; both features are expected to be developed in tandem.
