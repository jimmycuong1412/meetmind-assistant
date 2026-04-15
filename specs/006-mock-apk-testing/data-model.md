# Data Model: Mock APK Testing

**Feature**: 006-mock-apk-testing
**Date**: 2026-04-15

---

## New Test Infrastructure Entities

### Clock (interface)

Abstracts wall-clock time to make TTFT measurement deterministic in tests.

| Component | Type | Notes |
|-----------|------|-------|
| `nowMs()` | `Long` | Current time in milliseconds |

**Implementations**:
- `SystemClock` — singleton object; delegates to `System.currentTimeMillis()`
- `FakeClock(start: Long = 0)` — test-only; time frozen until `advanceBy(ms)` called

**Injection point**: `CloudInferenceEngine` constructor parameter (default `SystemClock`).

---

### CloudStreamingProvider (interface)

Abstracts the per-provider streaming client so `CloudInferenceEngine` is testable
without Firebase or Anthropic SDK initialization.

| Method | Signature | Notes |
|--------|-----------|-------|
| `streamSuggestion` | `(CloudInferenceRequest) → Flow<InferenceEvent>` | Emits Token* then Complete |
| `validate` | `suspend () → ValidationResult` | Returns SUCCESS/INVALID_KEY/NETWORK_ERROR/PROVIDER_ERROR |

**Implementations**:
- `GeminiInferenceClient` — production (Firebase AI Logic SDK)
- `ClaudeInferenceClient` (wrapping, with apiKey param) — production (Anthropic SDK)
- `FakeCloudStreamingProvider` — test-only; configurable token list + delay

---

### FakeCloudStreamingProvider

Test double for `CloudStreamingProvider`. Used by all unit and integration tests.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `tokens` | `List<String>` | `["Hello", " world"]` | Tokens to emit in order |
| `delayPerTokenMs` | `Long` | `0` | Simulated per-token network delay |
| `firstTokenDelayMs` | `Long` | `0` | Extra delay before first token (for TTFT tests) |
| `validateResult` | `ValidationResult` | `SUCCESS` | Returned by `validate()` |
| `throwOnStream` | `Exception?` | `null` | If non-null, thrown when `streamSuggestion()` is called (for error path tests) |

**State transitions tested**:
```
streamSuggestion() called
  → if throwOnStream != null: throws exception
  → else: delay(firstTokenDelayMs), emit Token[0], delay(delayPerTokenMs), emit Token[1..n], emit Complete
```

---

### FakeConnectivityChecker

Test double for the network availability check inside `CloudInferenceEngine`.

| Field | Type | Default |
|-------|------|---------|
| `isAvailable` | `Boolean` | `true` |

**Usage**: Set `isAvailable = false` before test to simulate no-network condition.

**Injection point**: `CloudInferenceEngine` constructor parameter
`connectivityChecker: () -> Boolean`.

---

### FakeStreamResponse\<T\>

Test double for `com.anthropic.core.http.StreamResponse<T>` (used only in
`ClaudeInferenceClient` unit tests where the full interface is needed).

| Field | Type |
|-------|------|
| `items` | `List<T>` |

**Methods**: `stream()` returns `items.stream()`; `close()` is a no-op.

---

## Modified Production Entities

### CloudInferenceEngine (constructor changes)

Two new injectable parameters with production defaults:

| Parameter | Type | Production Default | Test Injection |
|-----------|------|--------------------|----------------|
| `clock` | `Clock` | `SystemClock` | `FakeClock()` |
| `connectivityChecker` | `() -> Boolean` | `{ isNetworkAvailable() }` | `{ fakeChecker.isAvailable }` |
| `geminiProvider` | `CloudStreamingProvider` | `geminiClient` | `FakeCloudStreamingProvider()` |
| `claudeProvider` | `(String) -> CloudStreamingProvider` | `{ key -> claudeClient.forKey(key) }` | `{ FakeCloudStreamingProvider() }` |

> **Note**: The internal `isNetworkAvailable()` method is extracted to a lambda
> parameter to make it injectable without subclassing.

---

## Test Source Layout

```
app/src/test/kotlin/com/meetmind/assistant/
├── helpers/
│   ├── FakeClock.kt
│   ├── FakeCloudStreamingProvider.kt
│   ├── FakeConnectivityChecker.kt
│   ├── FakeStreamResponse.kt
│   └── TestDataStore.kt               # in-memory DataStore factory
├── storage/
│   ├── TinkApiKeyStoreTest.kt          # US1 — key encrypt/decrypt/persist
│   └── CloudProviderConfigRepositoryTest.kt
├── inference/
│   ├── CloudInferenceEngineTest.kt     # US2 — streaming, fallback, retry
│   ├── TtftTest.kt                     # US3 — TTFT measurement
│   └── NetworkAuditTest.kt            # US4 — no audio in requests
└── viewmodel/
    └── CloudSettingsViewModelTest.kt   # US1 — 401 auto-disable flow
```
