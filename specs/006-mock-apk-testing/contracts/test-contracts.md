# Test Contracts: Mock APK Testing

**Feature**: 006-mock-apk-testing
**Date**: 2026-04-15

These contracts define the expected behaviour of each test class. Each contract
maps to one or more spec 005 smoke tests.

---

## Contract 1: TinkApiKeyStoreTest

**Maps to**: Spec 005 Smoke Test 1 (partial)
**Runner**: `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`

| Test | Input | Expected Output |
|------|-------|----------------|
| `saveAndRetrieveKey_gemini` | Valid Gemini key string | `getKey(GEMINI)` returns equal byte array; `hasKey(GEMINI)` = true |
| `saveAndRetrieveKey_claude` | Valid Claude key string | `getKey(CLAUDE)` returns equal byte array; `hasKey(CLAUDE)` = true |
| `keyPersistsAfterStoreRecreation` | Key saved, store re-instantiated | New store instance returns same key bytes |
| `deleteKey_removesKey` | Key saved then deleted | `hasKey()` = false, `getKey()` = null |
| `blankKey_notSaved` | Empty string | `saveKey()` throws `IllegalArgumentException` |
| `plaintextZeroedAfterSave` | ByteArray passed to `saveKey()` | ByteArray is filled with zeros after call |
| `observeHasKey_emitsTrue_afterSave` | Cold start then save | Flow emits false, then true |

---

## Contract 2: CloudProviderConfigRepositoryTest

**Maps to**: Spec 005 Smoke Test 1 (config persistence)
**Runner**: `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`

| Test | Input | Expected Output |
|------|-------|----------------|
| `defaultConfig_isUnconfigured` | Fresh DataStore | `connectionStatus == UNCONFIGURED`, `isEnabled == false` |
| `updateConnectionStatus_connected` | `updateConnectionStatus(GEMINI, connected=true)` | `connectionStatus == CONNECTED` |
| `setEnabled_true_whenConnected` | `connectionStatus == CONNECTED`, then `setEnabled(true)` | `isEnabled == true` |
| `setEnabled_true_whenNotConnected_throws` | `connectionStatus == UNCONFIGURED`, then `setEnabled(true)` | Throws `IllegalStateException` |
| `updateConnectionStatus_false_disablesCloud` | `isEnabled=true`, then `updateConnectionStatus(connected=false)` | `isEnabled == false`, `connectionStatus == ERROR` |

---

## Contract 3: CloudInferenceEngineTest

**Maps to**: Spec 005 Smoke Tests 4, 5, and retry behaviour (US2)
**Runner**: `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [33])`
**Test dispatcher**: `StandardTestDispatcher` with `runTest { }`

| Test | Setup | Expected Events |
|------|-------|----------------|
| `streamCloud_emitsTokensThenComplete` | FakeProvider(tokens=["Hi", " there"], delay=0) | Token("Hi"), Token(" there"), Complete |
| `fallback_networkUnavailable` | `connectivityChecker = { false }` | FallbackActivated(NETWORK_UNAVAILABLE), then on-device tokens |
| `fallback_timeout_5s` | FakeProvider(firstTokenDelayMs = 6000) with `CLOUD_TIMEOUT_MS = 5000` | FallbackActivated(TIMEOUT) after 5 s virtual time |
| `fallback_perSuggestion_retriesCloudNextQuestion` | First call: network=false; second call: network=true | First: fallback; Second: cloud tokens (not fallback) |
| `fallback_authError_disablesCloud` | FakeProvider throws Exception("401 Unauthorized") | FallbackActivated(AUTH_ERROR), `configRepository.connectionStatus == ERROR` |
| `fallback_providerError_doesNotDisableCloud` | FakeProvider throws Exception("Service unavailable") | FallbackActivated(PROVIDER_ERROR), `connectionStatus` unchanged |
| `dataTruncation_questionOver600Chars` | 700-char question text | Captured request questionText length == 600 |
| `dataTruncation_systemPromptOver320Chars` | 400-char system prompt | Captured request systemPrompt length == 320 |

---

## Contract 4: TtftTest

**Maps to**: Spec 005 T043 (TTFT measurement, US3)
**Runner**: Standard JUnit4 + coroutines `runTest`

| Test | FakeClock setup | FakeProvider setup | Expected |
|------|----------------|-------------------|---------|
| `ttft_withinBudget` | starts at 0; advances 2000 ms at first token | firstTokenDelayMs=2000 | measured TTFT == 2000; assert ≤ 5000 passes |
| `ttft_exceedsBudget_triggersFallback` | starts at 0 | firstTokenDelayMs = 6000 (exceeds 5000 timeout) | FallbackActivated(TIMEOUT) emitted; no Token events |
| `ttft_logged_toLogcat` | any | any valid | Log.d("CloudInferenceEngine", "TTFT[...]=...ms") appears in ShadowLog output |

---

## Contract 5: NetworkAuditTest

**Maps to**: Spec 005 Smoke Test 7 (no audio, US4)
**Runner**: Standard JUnit4 (no Robolectric needed — pure OkHttp/MockWebServer)

| Test | Request | Assertion |
|------|---------|-----------|
| `noAudioBytes_inClaudeRequest` | text question via `ClaudeInferenceClient` pointed at `MockWebServer` | `body.readUtf8()` matches JSON-only pattern; no `audio/`, `wav`, `m4a`, or suspicious base64 blobs |
| `questionText_isTruncated_to600Chars` | 800-char question | Body `question` JSON field length ≤ 600 |
| `systemPrompt_isTruncated_to320Chars` | 400-char systemPrompt | Body `system` JSON field length ≤ 320 |
| `noUserIdentity_inRequest` | Any request | Body does not contain `deviceId`, `userId`, `email`, or `phone` fields |

---

## Contract 6: CloudSettingsViewModelTest

**Maps to**: Spec 005 Smoke Test 6 (401 auto-disable, US1)
**Runner**: `@RunWith(RobolectricTestRunner::class)` + `TestCoroutineDispatcher`

| Test | Trigger | Expected ViewModel State |
|------|---------|--------------------------|
| `saveKey_valid_setsConnected` | `onSaveKey(CLAUDE, "sk-ant-valid")` with FakeProvider(validateResult=SUCCESS) | `validationState == Success`, `claudeConfig.connectionStatus == CONNECTED` |
| `saveKey_invalid_setsError` | `onSaveKey(CLAUDE, "bad-key")` with FakeProvider(validateResult=INVALID_KEY) | `validationState == Failure(INVALID_KEY)`, key NOT stored |
| `deleteKey_clearsConfig` | Key saved, then `onDeleteKey(CLAUDE)` | `claudeConfig.encryptedApiKey == null`, `connectionStatus == UNCONFIGURED` |
| `authError_during_session_disablesCloud` | 401 exception emitted from FakeProvider during stream | `claudeConfig.connectionStatus == ERROR`, `isEnabled == false`, notification enqueued |
