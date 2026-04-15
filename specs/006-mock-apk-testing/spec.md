# Feature Specification: Mock APK Testing (No Physical Device)

**Feature Branch**: `feature/006-mock-apk-testing`
**Created**: 2026-04-15
**Status**: Draft
**Input**: User direction: "set up mock test APK file without the physical device"
**Constitution version at creation**: 2.0.0

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automate API Key Configuration Tests (Priority: P1)

A developer runs `./gradlew test` on any Windows machine without a phone attached.
Tests verify that API key save, encrypt, persist (across simulated restarts), and
delete all work correctly. Invalid keys are rejected before storage. A corrupt key
triggers 401 auto-disable and cloud mode is disabled automatically.

**Why this priority**: Spec 005 Smoke Tests 1 and 6 have no automated coverage.
These are the highest-risk paths (user credential handling) and must pass before
any cloud feature can ship safely.

**Independent Test**: Run `./gradlew test --tests "*ApiKeyStoreTest*"` and
`./gradlew test --tests "*CloudSettingsViewModelTest*"` — both pass with no device.

**Acceptance Scenarios**:

1. **Given** no key is stored, **When** a valid Claude key is saved, **Then** the
   key is retrievable after simulated app restart and `connectionStatus == CONNECTED`.
2. **Given** a stored key exists, **When** the provider receives a 401 response,
   **Then** `connectionStatus` becomes `ERROR`, `isEnabled` becomes `false`, and
   a system notification is enqueued.
3. **Given** an invalid (blank) key is submitted, **When** save is attempted,
   **Then** `ValidationResult.INVALID_KEY` is returned and nothing is persisted.

---

### User Story 2 - Automate Cloud Inference and Fallback Tests (Priority: P1)

A developer runs `./gradlew test` and gets automated coverage of: cloud suggestion
streaming with fake provider responses, fallback on simulated timeout (5 s),
fallback on network unavailable, and per-suggestion retry behaviour (cloud is
retried on the next question after a fallback, not session-disabled).

**Why this priority**: Spec 005 Smoke Tests 4 and 5 cover the most complex runtime
paths. Without automation a regression in fallback logic could go undetected.

**Independent Test**: Run `./gradlew test --tests "*CloudInferenceEngineTest*"` —
all fallback and streaming scenarios pass with no device or network.

**Acceptance Scenarios**:

1. **Given** cloud mode ON and no network, **When** a question is detected,
   **Then** `FallbackActivated(NETWORK_UNAVAILABLE)` is emitted immediately and
   on-device fallback tokens follow.
2. **Given** cloud mode ON and a slow fake provider (>5 s first token), **When** a
   question is detected, **Then** `FallbackActivated(TIMEOUT)` is emitted at the
   5 s mark and on-device fallback tokens follow.
3. **Given** a fallback occurred on question N, **When** question N+1 is detected,
   **Then** cloud is attempted again (fallback is per-suggestion, not per-session).

---

### User Story 3 - TTFT Latency Verification (Priority: P2)

A developer can assert that the time from request dispatch to first emitted token is
within the 5 000 ms budget using a deterministic fake clock — no network required.
The `CloudInferenceEngine` TTFT log line is also asserted in an integration test.

**Why this priority**: T043 (Network Inspector profiling) requires a real device.
A clock-based unit test provides ongoing regression protection for the TTFT budget.

**Independent Test**: Run `./gradlew test --tests "*TtftTest*"` — passes in <1 s.

**Acceptance Scenarios**:

1. **Given** a fake clock and a provider that emits first token at T+2 000 ms,
   **When** `streamSuggestion()` is collected, **Then** the measured TTFT is
   exactly 2 000 ms and the assertion `ttft <= 5000` passes.
2. **Given** a fake clock and a provider that emits first token at T+6 000 ms,
   **When** `streamSuggestion()` is collected, **Then** `TIMEOUT` fallback fires
   before the first token and the TTFT budget assertion would fail if measured.

---

### User Story 4 - Network Audit — No Audio Leaves Device (Priority: P2)

A developer runs an integration test that routes a full cloud inference call through
a `MockWebServer` and asserts that no binary/audio content appears in any request
body. The test fails fast if audio bytes are accidentally included.

**Why this priority**: Spec 005 Smoke Test 7 is the app's core privacy guarantee
per Constitution Principle I. It must be machine-verifiable, not manually checked.

**Independent Test**: Run `./gradlew test --tests "*NetworkAuditTest*"` — inspects
`MockWebServer` captured requests for audio content.

**Acceptance Scenarios**:

1. **Given** a `MockWebServer` replacing the Anthropic endpoint, **When** a cloud
   inference request is dispatched for a text question, **Then** the captured
   request body contains only JSON text fields and no binary/audio data.
2. **Given** a question text of >600 chars, **When** dispatched, **Then** the
   request body question field is truncated to ≤600 chars (data minimisation).

---

### Edge Cases

- What if `AndroidKeyStore` is unavailable in Robolectric? → Fall back to in-memory
  Tink keyset for tests; document the limitation.
- What if a `FakeStreamResponse` emits zero tokens? → `InferenceEvent.Complete` with
  empty `fullText` must still be emitted; test this explicitly.
- What if two tests run concurrently and share `DataStore`? → Each test uses a
  unique in-memory DataStore instance via a test rule.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST add Robolectric 4.14+ and JUnit 4/5 to `testImplementation` dependencies in `app/build.gradle.kts`.
- **FR-002**: `TinkApiKeyStore` MUST be testable on JVM via Robolectric shadow for `AndroidKeyStore` (no hardware required).
- **FR-003**: A `FakeCloudInferenceProvider` interface and fake implementations for Gemini and Claude MUST be injectable into `CloudInferenceEngine`.
- **FR-004**: `CloudInferenceEngine` MUST accept an injectable `TestClock` interface so TTFT measurement is deterministic in tests.
- **FR-005**: Integration tests MUST use `MockWebServer` to capture and inspect all outbound HTTP request bodies.
- **FR-006**: Five smoke test equivalents MUST be implemented as JVM unit tests runnable via `./gradlew test`.
- **FR-007**: Test suite MUST complete in under 5 minutes on a standard developer machine (no emulator spin-up).

### Key Entities

- **FakeStreamResponse**: Test double for `StreamResponse<RawMessageStreamEvent>`; returns a configurable list of token strings with optional per-token delay.
- **FakeClock**: Implements a `Clock` interface; time advances only when explicitly called, making TTFT tests deterministic.
- **FakeConnectivityChecker**: Test double for `CloudInferenceEngine`'s network availability check; returns configurable boolean.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `./gradlew test` passes in under 5 minutes with zero test failures, no emulator, no device.
- **SC-002**: All 5 automated smoke test equivalents (ST1, ST4, ST5, ST6, ST7) pass on every run.
- **SC-003**: TTFT unit test asserts first token within 5 000 ms using fake clock with deterministic timing.
- **SC-004**: Network audit test asserts zero audio bytes in 100% of captured mock server requests.

## Assumptions

- Spec 005 implementation is complete and `./gradlew assembleDebug` passes cleanly (confirmed).
- `google-services.json` is gitignored; test suite does not depend on Firebase project credentials.
- The Anthropic and Firebase APIs are never contacted during tests — all external calls are mocked.
- Robolectric 4.14 shadows correctly handle `AndroidKeyStore` backed Tink keyset initialisation on Windows JVM.
- No CI infrastructure is required in this spec; tests run locally via Gradle CLI.
