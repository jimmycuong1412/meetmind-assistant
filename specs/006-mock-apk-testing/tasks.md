---
description: "Task list for Mock APK Testing (No Physical Device)"
---

# Tasks: Mock APK Testing (No Physical Device)

**Input**: Design documents from `specs/006-mock-apk-testing/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: This feature IS the test suite — all tasks produce test code or the
production refactors required to make tests compile.

**Organization**: Tasks are grouped by user story to enable independent implementation
and testing of each story.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add test dependencies to the build, configure Robolectric, and create
the test source set. No production code changes yet.

- [X] T001 Add test dependency versions to `gradle/libs.versions.toml`: `robolectric = "4.14.1"`, `junit = "4.13.2"`, `mockitoKotlin = "5.4.0"`, `truth = "1.4.4"`, `androidxTestCore = "1.6.1"`
- [X] T002 Add test library entries to `gradle/libs.versions.toml` under `[libraries]`: `junit`, `robolectric`, `mockito-kotlin`, `kotlinx-coroutines-test`, `truth`, `okhttp-mockwebserver`, `androidx-test-core`, `androidx-test-core-ktx`, `androidx-compose-ui-test-junit4`, `androidx-compose-ui-test-manifest` (see quickstart.md for exact coordinates)
- [X] T003 Add `testImplementation` blocks to `app/build.gradle.kts` for all 8 test libraries listed in `quickstart.md`; add `debugImplementation(libs.androidx.compose.ui.test.manifest)`
- [X] T004 Create `app/src/test/resources/robolectric.properties` with content: `sdk=33` and `manifest=NONE`
- [X] T005 Create `app/src/test/kotlin/com/meetmind/assistant/` directory structure as per `data-model.md` test source layout: `helpers/`, `storage/`, `inference/`, `viewmodel/` packages
- [X] T006 [P] Verify `./gradlew test --no-daemon` compiles the empty test source set without errors (no test classes yet — just dependency resolution check)

**Checkpoint**: `./gradlew test` compiles cleanly. Robolectric is on the classpath.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Production code refactors that all test phases depend on. Must be
complete before any test class can compile.

**⚠️ CRITICAL**: No test code can be written until this phase is complete.

- [X] T007 Create `app/src/main/kotlin/com/meetmind/assistant/inference/CloudStreamingProvider.kt` interface with two methods: `fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent>` and `suspend fun validate(): ValidationResult`
- [X] T008 Make `GeminiInferenceClient` implement `CloudStreamingProvider` in `app/src/main/kotlin/com/meetmind/assistant/inference/GeminiInferenceClient.kt`; rename `validate()` signature to match interface (no apiKey param — already correct)
- [X] T009 Create `app/src/main/kotlin/com/meetmind/assistant/inference/Clock.kt` with: `interface Clock { fun nowMs(): Long }` and `object SystemClock : Clock { override fun nowMs() = System.currentTimeMillis() }`
- [X] T010 Refactor `CloudInferenceEngine` constructor in `app/src/main/kotlin/com/meetmind/assistant/inference/CloudInferenceEngine.kt` to accept: `geminiProvider: CloudStreamingProvider`, `claudeProviderFactory: (apiKey: String) -> CloudStreamingProvider`, `clock: Clock = SystemClock`, `connectivityChecker: () -> Boolean = { isNetworkAvailable() }`; replace internal direct calls to `geminiClient` and `claudeClient` with the injected parameters
- [X] T011 Update `app/src/main/kotlin/com/meetmind/assistant/di/AppContainer.kt` to wire `CloudInferenceEngine` with the new constructor — pass `geminiClient` as `geminiProvider`, `{ key -> claudeClient.withApiKey(key) }` as `claudeProviderFactory` (add `withApiKey(key): CloudStreamingProvider` adapter on `ClaudeInferenceClient` if needed)
- [X] T012 Add optional `baseUrl: String? = null` parameter to `ClaudeInferenceClient.streamSuggestion()` in `app/src/main/kotlin/com/meetmind/assistant/inference/ClaudeInferenceClient.kt`; when non-null, pass it to `AnthropicOkHttpClient.builder().baseUrl(baseUrl)` — enables `NetworkAuditTest` to redirect to `MockWebServer`
- [X] T013 [P] Run `./gradlew assembleDebug --no-daemon` to confirm production code still compiles after all Phase 2 refactors

**Checkpoint**: Production build passes. `CloudStreamingProvider`, `Clock`, and
`ClaudeInferenceClient.baseUrl` are all in place. Test helpers can now be written.

---

## Phase 3: User Story 1 — API Key Configuration Tests (Priority: P1) 🎯 MVP

**Goal**: Automated coverage of spec 005 Smoke Tests 1 and 6 — key save/encrypt/
persist/delete, invalid key rejection, and 401 auto-disable.

**Independent Test**: `./gradlew test --tests "*.TinkApiKeyStoreTest" --tests "*.CloudProviderConfigRepositoryTest" --tests "*.CloudSettingsViewModelTest" --no-daemon`

### Implementation for User Story 1

- [X] T014 [P] [US1] Create `app/src/test/kotlin/com/meetmind/assistant/helpers/TestDataStore.kt`: factory function `fun testDataStore(context: Context): DataStore<Preferences>` that creates a DataStore backed by a unique temp file per test (use `PreferenceDataStoreFactory.create { File(System.getProperty("java.io.tmpdir"), "test_${UUID.randomUUID()}.preferences_pb") }`)
- [X] T015 [P] [US1] Create `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeCloudStreamingProvider.kt`: data class implementing `CloudStreamingProvider` with fields `tokens: List<String>`, `firstTokenDelayMs: Long = 0`, `validateResult: ValidationResult = SUCCESS`, `throwOnStream: Exception? = null`; `streamSuggestion()` emits Token events then Complete; `validate()` returns `validateResult`
- [X] T016 [US1] Create `app/src/test/kotlin/com/meetmind/assistant/storage/TinkApiKeyStoreTest.kt` with `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [33])`; implement all 7 test cases from `contracts/test-contracts.md` Contract 1: `saveAndRetrieveKey_gemini`, `saveAndRetrieveKey_claude`, `keyPersistsAfterStoreRecreation`, `deleteKey_removesKey`, `blankKey_notSaved`, `plaintextZeroedAfterSave`, `observeHasKey_emitsTrue_afterSave`
- [X] T017 [US1] Create `app/src/test/kotlin/com/meetmind/assistant/storage/CloudProviderConfigRepositoryTest.kt` with `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [33])`; implement all 5 test cases from Contract 2: `defaultConfig_isUnconfigured`, `updateConnectionStatus_connected`, `setEnabled_true_whenConnected`, `setEnabled_true_whenNotConnected_throws`, `updateConnectionStatus_false_disablesCloud`; use `testDataStore()` helper for isolation
- [X] T018 [US1] Create `app/src/test/kotlin/com/meetmind/assistant/viewmodel/CloudSettingsViewModelTest.kt` with `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [33])` and `TestCoroutineDispatcher`; implement 4 test cases from Contract 6: `saveKey_valid_setsConnected`, `saveKey_invalid_setsError`, `deleteKey_clearsConfig`, `authError_during_session_disablesCloud`; inject `FakeCloudStreamingProvider` as the validation service
- [X] T019 [US1] Run `./gradlew test --tests "*.TinkApiKeyStoreTest" --tests "*.CloudProviderConfigRepositoryTest" --tests "*.CloudSettingsViewModelTest" --no-daemon` and fix any failures; document any Robolectric/Tink shadow workarounds in a comment at the top of `TinkApiKeyStoreTest.kt`

**Checkpoint**: US1 green. Spec 005 Smoke Tests 1 and 6 are automated.

---

## Phase 4: User Story 2 — Cloud Inference and Fallback Tests (Priority: P1)

**Goal**: Automated coverage of spec 005 Smoke Tests 4 and 5 — fallback on no
network, fallback on timeout, per-suggestion cloud retry.

**Independent Test**: `./gradlew test --tests "*.CloudInferenceEngineTest" --no-daemon`

### Implementation for User Story 2

- [X] T020 [P] [US2] Create `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeClock.kt`: `class FakeClock(start: Long = 0L) : Clock` with `private var _now = start`, `override fun nowMs() = _now`, `fun advanceBy(ms: Long) { _now += ms }`
- [X] T021 [P] [US2] Create `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeConnectivityChecker.kt`: `class FakeConnectivityChecker(var isAvailable: Boolean = true)` with a `val asLambda: () -> Boolean get() = { isAvailable }`
- [X] T022 [US2] Create `app/src/test/kotlin/com/meetmind/assistant/inference/CloudInferenceEngineTest.kt` with `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [33])` and `runTest { }`; implement all 8 test cases from Contract 3: `streamCloud_emitsTokensThenComplete`, `fallback_networkUnavailable`, `fallback_timeout_5s`, `fallback_perSuggestion_retriesCloudNextQuestion`, `fallback_authError_disablesCloud`, `fallback_providerError_doesNotDisableCloud`, `dataTruncation_questionOver600Chars`, `dataTruncation_systemPromptOver320Chars`; use `FakeCloudStreamingProvider`, `FakeClock`, `FakeConnectivityChecker`
- [X] T023 [US2] For `fallback_timeout_5s`: use `runTest { advanceTimeBy(5001) }` (TestCoroutineScheduler virtual time) to trigger the `withTimeout(5000)` cancellation deterministically — do NOT use `Thread.sleep()` or `FakeClock.advanceBy()` for this test (coroutine timeout requires virtual time, not wall clock)
- [X] T024 [US2] Run `./gradlew test --tests "*.CloudInferenceEngineTest" --no-daemon` and fix any failures

**Checkpoint**: US2 green. Spec 005 Smoke Tests 4 and 5 are automated.

---

## Phase 5: User Story 3 — TTFT Latency Verification (Priority: P2)

**Goal**: Deterministic unit-level assertion that first-token latency is within the
5 000 ms budget using `FakeClock`.

**Independent Test**: `./gradlew test --tests "*.TtftTest" --no-daemon`

### Implementation for User Story 3

- [X] T025 [P] [US3] Create `app/src/test/kotlin/com/meetmind/assistant/inference/TtftTest.kt` (standard JUnit4, no Robolectric needed); implement 3 test cases from Contract 4: `ttft_withinBudget`, `ttft_exceedsBudget_triggersFallback`, `ttft_logged_toLogcat`; use `FakeClock` + `FakeCloudStreamingProvider(firstTokenDelayMs = N)` to control timing; for `ttft_logged_toLogcat` use `ShadowLog.getLogs()` to assert the `TTFT[...]` log entry exists
- [X] T026 [US3] Verify `CloudInferenceEngine` TTFT logging (added in spec 005) emits `Log.d("CloudInferenceEngine", "TTFT[${provider}] = ${firstTokenMs}ms")` at the correct moment — add/adjust if the log tag or format differs from what `TtftTest` expects
- [X] T027 [US3] Run `./gradlew test --tests "*.TtftTest" --no-daemon` and fix any failures

**Checkpoint**: US3 green. TTFT budget is machine-verified.

---

## Phase 6: User Story 4 — Network Audit (No Audio Leaves Device) (Priority: P2)

**Goal**: `MockWebServer` integration test that asserts no audio bytes appear in
any outbound Anthropic API request body.

**Independent Test**: `./gradlew test --tests "*.NetworkAuditTest" --no-daemon`

### Implementation for User Story 4

- [X] T028 [P] [US4] Create `app/src/test/kotlin/com/meetmind/assistant/helpers/FakeStreamResponse.kt`: `class FakeStreamResponse<T>(private val items: List<T>) : StreamResponse<T>` with `override fun stream() = items.stream()` and `override fun close() {}`; this is used only by `ClaudeInferenceClient` unit tests that need the raw SDK interface
- [X] T029 [US4] Create `app/src/test/kotlin/com/meetmind/assistant/inference/NetworkAuditTest.kt` (standard JUnit4, no Robolectric); implement 4 test cases from Contract 5: `noAudioBytes_inClaudeRequest`, `questionText_isTruncated_to600Chars`, `systemPrompt_isTruncated_to320Chars`, `noUserIdentity_inRequest`; use `MockWebServer` with a canned SSE response; pass `server.url("/").toString()` as `baseUrl` to `ClaudeInferenceClient.streamSuggestion()`
- [X] T030 [US4] Canned SSE response for `MockWebServer` in `NetworkAuditTest`: set `Content-Type: text/event-stream` and body: `"data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n"` — verify this parses correctly through `ClaudeInferenceClient`'s iterator
- [X] T031 [US4] Run `./gradlew test --tests "*.NetworkAuditTest" --no-daemon` and fix any failures

**Checkpoint**: US4 green. Spec 005 Smoke Test 7 (no audio) is machine-verified.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full suite validation, CI-readiness, and documentation updates.

- [X] T032 Run complete test suite `./gradlew test --no-daemon` and confirm all test classes pass with zero failures; record total test count and duration in a comment at the top of this file
- [X] T033 [P] Update `specs/005-cloud-ai-inference/tasks.md`: mark T041 (smoke test) and T043 (TTFT profiling) with a note linking to spec 006 automated equivalents
- [X] T034 [P] Update `specs/005-cloud-ai-inference/quickstart.md` Smoke Test mapping table: mark ST1, ST4, ST5, ST6, ST7 as "✅ Automated — see spec 006"
- [X] T035 [P] Verify `.gitignore` does not accidentally exclude `*.kt` test files or `src/test/` directory; add explicit `!src/test/` exclusion if needed
- [ ] T036 Add `testOptions { unitTests.isReturnDefaultValues = true }` block to `android { }` in `app/build.gradle.kts` if any Robolectric tests fail with "Method not mocked" errors for Android framework stubs

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all test phases
- **Phase 3 (US1)**: Depends on Phase 2 — can start once `CloudStreamingProvider` and `Clock` exist
- **Phase 4 (US2)**: Depends on Phase 2 — can run in parallel with Phase 3
- **Phase 5 (US3)**: Depends on Phase 2 + T026 (TTFT log verification) — can run in parallel with Phase 3/4
- **Phase 6 (US4)**: Depends on T012 (`ClaudeInferenceClient.baseUrl`) — can run in parallel with Phase 3/4/5
- **Phase 7 (Polish)**: Depends on all test phases passing

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2. Requires `TinkApiKeyStore`, `CloudProviderConfigRepository`, `CloudSettingsViewModel`.
- **US2 (P1)**: Starts after Phase 2. Requires `CloudInferenceEngine` with injected `Clock` and `connectivityChecker`.
- **US3 (P2)**: Starts after Phase 2 + US2 (shares `CloudInferenceEngine`). Extends engine tests with TTFT focus.
- **US4 (P2)**: Starts after T012. Requires only `ClaudeInferenceClient` with `baseUrl` override.

### Parallel Opportunities

Within Phase 1: T001–T005 can all run in parallel (different files).
Within Phase 2: T007, T008, T009 can run in parallel; T010 depends on T007–T009; T011 depends on T010.
Within Phase 3: T014 and T015 can run in parallel (different helper files).
Within Phase 4: T020 and T021 can run in parallel (different helper files).
Phases 3, 4, 5, 6 can all run in parallel once Phase 2 is complete.

---

## Parallel Example: Phase 2

```
# All can start together:
T007: Create CloudStreamingProvider interface
T008: Make GeminiInferenceClient implement CloudStreamingProvider
T009: Create Clock interface + SystemClock object

# After T007+T009 complete:
T010: Refactor CloudInferenceEngine constructor

# After T010 completes:
T011: Update AppContainer wiring
T012: Add baseUrl param to ClaudeInferenceClient
```

---

## Implementation Strategy

### MVP First (US1 + US2 only — highest-risk smoke tests)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational refactors
3. Complete Phase 3: US1 (key config tests)
4. Complete Phase 4: US2 (fallback tests)
5. **STOP and VALIDATE**: `./gradlew test` green for ST1, ST4, ST5, ST6
6. Ship — covers the 4 highest-priority automated smoke tests

### Full Coverage

1. MVP above, then:
2. Phase 5 (US3): TTFT verification
3. Phase 6 (US4): Network audit
4. Phase 7: Polish + documentation updates
5. Final: `./gradlew test` green for all 5 automated smoke test equivalents

---

## Notes

- All test tasks produce `src/test/` files — none touch `androidTest/` (no emulator needed)
- `FakeClock` is used for wall-clock measurements; `advanceTimeBy()` (coroutine virtual time) is used for `withTimeout()` — these are different mechanisms; do not mix them
- Robolectric `@Config(sdk = [33])` is required for Tink `AndroidKeyStore` shadow support
- `MockWebServer` tests (US4) do NOT need Robolectric — they run as plain JUnit4 on JVM
- If Tink throws `GeneralSecurityException: No key found` under Robolectric, add `@Config(application = Application::class)` to ensure Application context is initialised before Tink keyset creation
