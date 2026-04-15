---
description: "Task list for Cloud AI Inference Mode"
---

# Tasks: Cloud AI Inference Mode

**Input**: Design documents from `specs/005-cloud-ai-inference/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Not explicitly requested — no test tasks generated. Add `/speckit.tasks --tdd`
to regenerate with TDD tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation
and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add new dependencies, model classes, and project scaffolding so all
user stories can build on a common foundation.

- [X] T001 Add Firebase AI Logic, Anthropic SDK, OkHttp 4.12.0, Tink Android, DataStore Preferences dependencies to `app/build.gradle.kts`; add `com.google.gms.google-services` plugin and root project plugin; enable `coreLibraryDesugaringEnabled`
- [X] T002 Add `google-services.json` to `app/` (obtain from Firebase console); add it to `.gitignore`
- [X] T003 Add cloud AI ProGuard rules (Anthropic SDK + OkHttp + Tink) to `app/proguard-rules.pro`
- [X] T004 Add `res/xml/backup_rules.xml` to exclude Tink keyset SharedPreferences and DataStore file from Android cloud backup
- [X] T005 [P] Create `CloudProvider.kt` enum (`GEMINI`, `CLAUDE`) in `app/src/main/kotlin/.../data/model/`
- [X] T006 [P] Create `InferenceMode.kt` enum (`ON_DEVICE`, `CLOUD`) in `app/src/main/kotlin/.../data/model/`
- [X] T007 [P] Create `FallbackReason.kt` enum (`TIMEOUT`, `NETWORK_UNAVAILABLE`, `AUTH_ERROR`, `PROVIDER_ERROR`, `CLOUD_DISABLED`) in `app/src/main/kotlin/.../data/model/`
- [X] T008 [P] Create `CloudBadgeState.kt` enum (`HIDDEN`, `CLOUD_ACTIVE`, `FALLBACK`) in `app/src/main/kotlin/.../data/model/`
- [X] T009 [P] Create `InferenceEvent.kt` sealed class (`Token`, `Complete`, `FallbackActivated`, `Error`) in `app/src/main/kotlin/.../data/model/`
- [X] T010 [P] Create `CloudProviderConfig.kt` data class (fields: provider, encryptedApiKey, isEnabled, connectionStatus, lastValidatedAt) in `app/src/main/kotlin/.../data/model/`
- [X] T011 [P] Create `CloudInferenceRequest.kt` data class (fields: id, questionText, userRole, sessionMode, provider, dispatchedAt, firstTokenAt, completedAt, status) in `app/src/main/kotlin/.../data/model/`
- [X] T012 Extend existing `Suggestion.kt` to add `inferenceMode: InferenceMode` and `cloudRequestId: UUID?` fields in `app/src/main/kotlin/.../data/model/Suggestion.kt`

**Checkpoint**: Project compiles with new dependencies and all data model classes present.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Encrypted key storage and config repository must be complete before any
user story can be implemented — all stories depend on reading/writing the
`CloudProviderConfig`.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T013 Implement `ApiKeyStore.kt` interface + `TinkApiKeyStore` implementation using Tink AES-256-GCM + Android Keystore + DataStore Preferences in `app/src/main/kotlin/.../storage/ApiKeyStore.kt`; include `hasKey()`, `getKey()`, `saveKey()`, `deleteKey()` methods; wrap Keystore init in try/catch; use ByteArray internally for plaintext to allow zeroing
- [X] T014 Implement `CloudProviderConfigRepository.kt` with `Flow<CloudProviderConfig>` reactive stream, `setProvider()`, `setEnabled()` (throws if not CONNECTED), `updateConnectionStatus()`, `clearConfig()` in `app/src/main/kotlin/.../storage/CloudProviderConfigRepository.kt`; `setEnabled(true)` must reject when `connectionStatus != CONNECTED`
- [X] T015 Wire `ApiKeyStore` and `CloudProviderConfigRepository` into the app's DI graph (manual DI or Hilt) so they are accessible from service and UI layers
- [X] T016 Update `AndroidManifest.xml`: add `INTERNET` permission, `FOREGROUND_SERVICE_DATA_SYNC` permission, update `AudioProcessingForegroundService` `foregroundServiceType` to `"microphone|dataSync"`, add `android:dataExtractionRules="@xml/backup_rules"` to `<application>`

**Checkpoint**: `ApiKeyStore` can encrypt and decrypt a test string; `CloudProviderConfigRepository` emits config changes reactively. Foundation ready.

---

## Phase 3: User Story 1 - Configure a Cloud AI Provider (Priority: P1) 🎯 MVP

**Goal**: User can enter and save an API key for Gemini or Claude in Settings;
key survives restart; invalid keys are rejected.

**Independent Test**: Navigate to Settings → Cloud AI; enter a valid Gemini key;
save; force-stop and reopen — key still shows "Connected ✓". Enter a bad key;
verify error and key is not saved.

### Implementation for User Story 1

- [X] T017 [US1] Implement `CloudKeyValidationService.kt` — perform a minimal test API call per provider (Gemini: empty `generateContent`; Claude: 1-token message) to validate the key; return `ValidationResult` enum (`SUCCESS`, `INVALID_KEY`, `NETWORK_ERROR`, `PROVIDER_ERROR`); 10-second timeout in `app/src/main/kotlin/.../inference/CloudKeyValidationService.kt`
- [X] T018 [P] [US1] Implement `GeminiInferenceClient.kt` stub with Firebase AI Logic SDK init (`Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash")`) and a `validate(apiKey)` method in `app/src/main/kotlin/.../inference/GeminiInferenceClient.kt`
- [X] T019 [P] [US1] Implement `ClaudeInferenceClient.kt` stub with Anthropic SDK init (`AnthropicOkHttpClient.builder().apiKey(key).build()`) and a `validate(apiKey)` method in `app/src/main/kotlin/.../inference/ClaudeInferenceClient.kt`
- [X] T020 [US1] Create `CloudSettingsScreen.kt` Compose screen: provider radio buttons (Gemini / Claude), API key text field (password input), Save button, status indicator (`UNCONFIGURED` / `Connected ✓` / `Error: invalid key`), Remove key button; wire to `CloudSettingsViewModel` in `app/src/main/kotlin/.../ui/screens/CloudSettingsScreen.kt`
- [X] T021 [US1] Create `CloudSettingsViewModel.kt`: expose `configState: StateFlow<CloudProviderConfig>`, `validationState: StateFlow<ValidationState>`, `onSaveKey(provider, keyText)` (calls `CloudKeyValidationService` then `ApiKeyStore.saveKey` + `updateConnectionStatus(CONNECTED)` on success), `onDeleteKey()` in `app/src/main/kotlin/.../viewmodel/CloudSettingsViewModel.kt`
- [X] T022 [US1] Add navigation route to `CloudSettingsScreen` from the main settings entry point (settings icon on `HomeScreen` or `SessionScreen`) in the app's nav graph

**Checkpoint**: User Story 1 fully functional — key config, validation, persistence, and removal all work independently.

---

## Phase 4: User Story 2 - Enable Cloud Mode Toggle and Badge (Priority: P1)

**Goal**: User can toggle Cloud mode on/off on the session home screen; the "☁ Cloud"
badge is persistently visible on the session screen when enabled; the toggle is
disabled when no key is configured.

**Independent Test**: With a valid key configured, toggle Cloud ON; start a session;
verify "☁ Cloud" badge is always visible. Toggle OFF; verify badge disappears.

### Implementation for User Story 2

- [X] T023 [P] [US2] Create `CloudBadge.kt` reusable Compose component: renders "☁ Cloud" (primary colour), "⚡ On-device (cloud unavailable)" (muted), or nothing based on `CloudBadgeState` input in `app/src/main/kotlin/.../ui/components/CloudBadge.kt`
- [X] T024 [P] [US2] Implement `CloudBadgeController.kt`: combines `CloudProviderConfigRepository.config` flow and the suggestion stream; derives and emits `CloudBadgeState` via `StateFlow`; badge resets to `CLOUD_ACTIVE` (optimistic) when a new session starts with Cloud enabled in `app/src/main/kotlin/.../inference/CloudBadgeController.kt`
- [X] T025 [US2] Update `HomeScreen.kt` to add Cloud mode toggle switch: shown only when `connectionStatus == CONNECTED`; disabled with tooltip "Add API key in Settings" when not configured; toggle calls `CloudProviderConfigRepository.setEnabled()`; persists across sessions in `app/src/main/kotlin/.../ui/screens/HomeScreen.kt`
- [X] T026 [US2] Update `SessionScreen.kt` to display `CloudBadge` component in a fixed position (e.g., top-right of suggestion area); observe `cloudBadgeState` from `SessionViewModel`; badge is non-dismissible in `app/src/main/kotlin/.../ui/screens/SessionScreen.kt`
- [X] T027 [US2] Update `SessionViewModel.kt` to expose `cloudBadgeState: StateFlow<CloudBadgeState>` by collecting from `CloudBadgeController` in `app/src/main/kotlin/.../viewmodel/SessionViewModel.kt`

**Checkpoint**: Toggle visible and functional; badge appears/disappears correctly; toggle disabled when no key configured.

---

## Phase 5: User Story 3 - Receive a Cloud-Generated Suggestion (Priority: P1)

**Goal**: During a Cloud mode session, detected questions are routed to the cloud
provider; response tokens stream to screen in real time; first token appears within
3 seconds; suggestion is visually distinct from on-device suggestions.

**Independent Test**: Cloud mode ON, valid key, start a session, ask an interview
question — first token visible within 3 seconds, full suggestion within 5 seconds,
"☁ Cloud" badge on suggestion card.

### Implementation for User Story 3

- [X] T028 [US3] Implement `GeminiInferenceClient.kt` full streaming: `streamSuggestion(question, role, mode)` calls `model.generateContentStream(content { systemInstruction = ...; text(question) })` with `thinkingBudget = 0`, `maxOutputTokens = 200`; collect `Flow<GenerateContentResponse>` tokens; emit as `InferenceEvent.Token` in `app/src/main/kotlin/.../inference/GeminiInferenceClient.kt`
- [X] T029 [US3] Implement `ClaudeInferenceClient.kt` full streaming: `streamSuggestion(question, role, mode)` calls `client.messages().createStreaming(params)` with `maxTokens = 200`; wrap iterator in `flow { ... }.flowOn(Dispatchers.IO)`; emit text deltas as `InferenceEvent.Token` in `app/src/main/kotlin/.../inference/ClaudeInferenceClient.kt`
- [X] T030 [US3] Implement `CloudInferenceEngine.kt`: selects provider client based on `CloudProviderConfig.provider`; enforces data minimisation (question text truncated to 150 tokens, role truncated to 20 tokens — no other data sent); wraps cloud call in `withTimeout(5000L)` coroutine; emits `InferenceEvent` stream; falls back to existing `SuggestionEngine` on `TimeoutCancellationException`, network failure, or 4xx/5xx in `app/src/main/kotlin/.../inference/CloudInferenceEngine.kt`
- [X] T031 [US3] Update `AudioProcessingForegroundService.kt` to inject `CloudInferenceEngine`; replace direct `SuggestionEngine` call with `CloudInferenceEngine.streamSuggestion()`; route `InferenceEvent.Token` to the overlay token stream; route `InferenceEvent.Complete` to build final `Suggestion` with correct `inferenceMode` in `app/src/main/kotlin/.../service/AudioProcessingForegroundService.kt`
- [X] T032 [US3] Update `SessionViewModel.kt` to expose `currentSuggestionTokens: StateFlow<String>` that accumulates `InferenceEvent.Token` values (reset to `""` on each new question detection) in `app/src/main/kotlin/.../viewmodel/SessionViewModel.kt`
- [X] T033 [US3] Update `SuggestionCard.kt` Compose component to: display `inferenceMode` badge ("☁ Cloud" or nothing for on-device); stream-render `currentSuggestionTokens` as text builds up in real time in `app/src/main/kotlin/.../ui/components/SuggestionCard.kt`

**Checkpoint**: Cloud suggestion streams to screen within 3s first-token; "☁ Cloud" badge on suggestion card; suggestion framing differs per mode.

---

## Phase 6: User Story 4 - Graceful Fallback When Cloud Unavailable (Priority: P1)

**Goal**: When cloud is unavailable (no network, timeout, auth error), the on-device
engine produces the suggestion; a fallback indicator is shown; the session never stalls.

**Independent Test**: Cloud ON, disable network, ask question — on-device suggestion
appears, fallback indicator visible. Re-enable network, ask again — cloud badge resumes.

### Implementation for User Story 4

- [X] T034 [US4] Implement network availability check in `CloudInferenceEngine.kt`: before dispatching the API request, check `ConnectivityManager.activeNetworkInfo?.isConnected`; if no connectivity, emit `InferenceEvent.FallbackActivated(NETWORK_UNAVAILABLE)` immediately and route to on-device engine (avoids waiting for a 5s timeout) in `app/src/main/kotlin/.../inference/CloudInferenceEngine.kt`
- [X] T035 [US4] Implement 401/403 auto-disable in `CloudInferenceEngine.kt`: catch HTTP auth errors; call `CloudProviderConfigRepository.updateConnectionStatus(ERROR)` (which sets `isEnabled = false`); emit `InferenceEvent.FallbackActivated(AUTH_ERROR)`; surface notification "Cloud AI disabled — check your API key in Settings" via `NotificationManager` in `app/src/main/kotlin/.../inference/CloudInferenceEngine.kt`
- [X] T036 [US4] Update `CloudBadgeController.kt` to react to `FallbackReason` from `InferenceEvent.FallbackActivated`: set badge state to `FALLBACK` for the affected suggestion; reset to `CLOUD_ACTIVE` on the next successful cloud token in `app/src/main/kotlin/.../inference/CloudBadgeController.kt`
- [X] T037 [US4] Verify per-suggestion fallback behaviour in `CloudInferenceEngine.kt`: after a fallback on one suggestion, the next question still attempts cloud first (fallback is per-suggestion, not per-session); add an explicit comment and guard logic in `app/src/main/kotlin/.../inference/CloudInferenceEngine.kt`

**Checkpoint**: All four fallback scenarios work (no network, timeout, 401, provider error); session continues uninterrupted; fallback indicator visible; re-attempts cloud on next question.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Hardening, UX details, and verification across all user stories.

- [X] T038 [P] Verify `google-services.json` is listed in `.gitignore`; add if missing; document the manual step in project README or `quickstart.md`
- [X] T039 [P] Implement `OkHttpClient` streaming config for Claude: `readTimeout(0, TimeUnit.MILLISECONDS)` on the streaming client instance; ensure a separate non-streaming client retains a normal timeout for `CloudKeyValidationService` calls in `app/src/main/kotlin/.../inference/ClaudeInferenceClient.kt`
- [X] T040 [P] Add `android:allowBackup` guard: verify `app/res/xml/backup_rules.xml` excludes Tink keyset pref file and DataStore preferences file; run `adb backup` smoke test to confirm API key is not included in backup
- [ ] T041 Run APK smoke test per `quickstart.md` — execute all 7 smoke tests on Lenovo Y700 Gen 3 or Honor Magic 6 Pro; document results → ✅ Automated in spec 006: TinkApiKeyStoreTest, CloudProviderConfigRepositoryTest, CloudSettingsViewModelTest, CloudInferenceEngineTest
- [X] T042 [P] Add `FOREGROUND_SERVICE_DATA_SYNC` permission to the existing spec 004 lint baseline (if one exists) so the new permission is tracked
- [ ] T043 Profile network request timing with Android Studio Network Inspector: verify first token ≤3s on Wi-Fi for both Gemini and Claude; log TTFT per request to Logcat for tuning → ✅ Automated in spec 006: TtftTest

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately; T005–T011 fully parallel
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — key validation and storage ready
- **US2 (Phase 4)**: Depends on Phase 2 + US1 (toggle requires key config to exist)
- **US3 (Phase 5)**: Depends on Phase 2 + US2 (cloud engine needs config + badge controller)
- **US4 (Phase 6)**: Depends on Phase 5 (fallback is an extension of the cloud engine)
- **Polish (Phase 7)**: Depends on all user stories

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational — no dependency on other stories
- **US2 (P1)**: Starts after US1 — needs key config UI to exist for toggle to reference
- **US3 (P1)**: Starts after US2 — needs the toggle and badge controller wired before full streaming
- **US4 (P1)**: Starts after US3 — fallback logic lives inside `CloudInferenceEngine` (T030)

### Within Each User Story

- Model/enum classes before services
- Services before ViewModels
- ViewModels before Compose screens
- Client stubs (T018, T019) can be implemented in parallel with each other
- Full streaming clients (T028, T029) can be implemented in parallel with each other

### Parallel Opportunities

```bash
# Phase 1 parallel batch (all different files):
T005 CloudProvider.kt
T006 InferenceMode.kt
T007 FallbackReason.kt
T008 CloudBadgeState.kt
T009 InferenceEvent.kt
T010 CloudProviderConfig.kt
T011 CloudInferenceRequest.kt

# US1 parallel batch:
T018 GeminiInferenceClient.kt (stub)
T019 ClaudeInferenceClient.kt (stub)

# US2 parallel batch:
T023 CloudBadge.kt component
T024 CloudBadgeController.kt

# US3 parallel batch:
T028 GeminiInferenceClient.kt (full streaming)
T029 ClaudeInferenceClient.kt (full streaming)
```

---

## Implementation Strategy

### MVP (US1 + US2 + US3 only — US4 deferred)

1. Complete Phase 1: Setup (all T001–T012)
2. Complete Phase 2: Foundational (T013–T016) — CRITICAL
3. Complete Phase 3: US1 key config (T017–T022) — test independently
4. Complete Phase 4: US2 toggle + badge (T023–T027) — test independently
5. Complete Phase 5: US3 cloud suggestion streaming (T028–T033) — test end-to-end
6. **STOP and VALIDATE**: cloud suggestion works; badge shows; key config persists

### Full Delivery (add US4)

7. Complete Phase 6: US4 fallback (T034–T037)
8. Complete Phase 7: Polish (T038–T043)
9. APK smoke test (T041) — run all 7 quickstart smoke tests

### Incremental Demo Points

| After | Demo |
|-------|------|
| Phase 2 | Key stored encrypted; config persists |
| US1 | Full Settings → Cloud AI screen; key entry + validation + removal |
| US2 | Toggle on session home; "☁ Cloud" badge on session screen |
| US3 | Live cloud suggestion streaming from Gemini or Claude |
| US4 | Fallback to on-device + indicator when network drops |

---

## Notes

- `[P]` tasks have no file conflicts and can run in parallel
- `[US?]` label maps to the user story for traceability
- T028 and T029 (full streaming clients) replace the stubs from T018/T019 — the stubs exist only to unblock `CloudKeyValidationService` (T017) earlier
- `google-services.json` is never committed; document as a manual step
- The Anthropic SDK bundles OkHttp — pin `okhttp:4.12.0` explicitly in Gradle to prevent version conflicts
- `EncryptedSharedPreferences` is deprecated — use Tink + DataStore (T013) as specified; do not revert to ESP
