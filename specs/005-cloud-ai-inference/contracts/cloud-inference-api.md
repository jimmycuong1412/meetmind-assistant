# Contract: Cloud Inference API Integration

**Feature**: 005-cloud-ai-inference
**Date**: 2026-04-14

This document describes the internal component contracts for the cloud AI inference
path. These are module boundaries within the app, not public APIs.

---

## CloudInferenceEngine

**Responsibility**: Route a `DetectedQuestion` to the appropriate cloud provider and
stream suggestion tokens back to the caller. Falls back to the on-device engine on
failure or timeout.

**Replaces / wraps**: `SuggestionEngine` from spec 004, which becomes the on-device
fallback path.

**Input**:
- `DetectedQuestion` (from spec 004)
- `Session` (contains `mode`, `profileRole` snapshot)
- `CloudProviderConfig` (current runtime config — decrypted key, provider, isEnabled)

**Output contract** (streaming):
- Emits `InferenceEvent` values on a cold `Flow`:
  - `InferenceEvent.Token(text: String, inferenceMode: InferenceMode)` — one per
    streamed token
  - `InferenceEvent.Complete(suggestion: Suggestion)` — final assembled suggestion
  - `InferenceEvent.FallbackActivated(reason: FallbackReason)` — cloud failed; events
    that follow are from on-device engine
  - `InferenceEvent.Error(cause: Throwable)` — unrecoverable error (rare)

**FallbackReason** (enum):
- `TIMEOUT` — no first token within 5 seconds
- `NETWORK_UNAVAILABLE` — no internet connectivity at dispatch time
- `AUTH_ERROR` — provider returned 401/403
- `PROVIDER_ERROR` — provider returned 5xx or malformed response
- `CLOUD_DISABLED` — `isEnabled == false` at time of call (routes directly to on-device)

**Timeout contract**:
- A `withTimeout(5000L)` coroutine scope wraps the cloud call
- On `TimeoutCancellationException`, emit `FallbackActivated(TIMEOUT)` then begin
  on-device generation
- The 5-second clock starts when the HTTP request is dispatched, not when the
  question is detected

**Data contract** (what is sent — enforced here, not by callers):
```
systemPrompt = role (≤80 tokens) + mode instruction
userMessage  = "Question: " + questionText (truncated to 150 tokens if needed)
maxTokens    = 200
```
Any attempt to send audio bytes, session history, or device identifiers MUST be
rejected at this layer with an `IllegalArgumentException`.

---

## ApiKeyStore

**Responsibility**: Persist and retrieve user API keys using Tink + DataStore
encryption backed by Android Keystore.

**Interface**:

```kotlin
interface ApiKeyStore {
    // Returns true if a key for the given provider is stored
    suspend fun hasKey(provider: CloudProvider): Boolean

    // Decrypt and return the key; returns null if absent
    suspend fun getKey(provider: CloudProvider): String?

    // Encrypt and persist the key; overwrites any existing key for this provider
    suspend fun saveKey(provider: CloudProvider, plaintext: String)

    // Delete the stored key for this provider
    suspend fun deleteKey(provider: CloudProvider)
}
```

**Security contract**:
- `getKey()` decrypts on each call — the plaintext key is NEVER cached in a field
- `saveKey()` stores only the ciphertext; plaintext is zeroed from memory
  (use `ByteArray` not `String` internally to allow zeroing)
- Neither method logs the key or includes it in any exception message
- Both methods throw `KeyStoreUnavailableException` if Android Keystore is
  inaccessible; callers MUST handle this and surface a user-friendly error

---

## CloudProviderConfigRepository

**Responsibility**: Manage `CloudProviderConfig` persistence and expose it as a
reactive stream.

**Interface**:

```kotlin
interface CloudProviderConfigRepository {
    val config: Flow<CloudProviderConfig>

    suspend fun setProvider(provider: CloudProvider)
    suspend fun setEnabled(enabled: Boolean)  // Throws if CONNECTED status not met
    suspend fun updateConnectionStatus(status: ConnectionStatus)
    suspend fun clearConfig()
}
```

**State rules**:
- `setEnabled(true)` reads current `connectionStatus`; throws
  `IllegalStateException("API key not configured or invalid")` if not `CONNECTED`
- `updateConnectionStatus(ERROR)` automatically sets `isEnabled = false`
- `clearConfig()` calls `ApiKeyStore.deleteKey()` before clearing the config record

---

## CloudKeyValidationService

**Responsibility**: Perform a lightweight validation call to the provider's API to
confirm the key is accepted, without generating a real suggestion.

**Input**: `provider: CloudProvider`, `plaintext key: String`

**Output contract**:
- Returns `ValidationResult.SUCCESS` if provider accepts the key
- Returns `ValidationResult.INVALID_KEY` on 401/403
- Returns `ValidationResult.NETWORK_ERROR` if no connectivity
- Returns `ValidationResult.PROVIDER_ERROR(statusCode)` on 5xx

**Validation request** (minimised — no real prompt data sent):
- Gemini: `generateContent("")` with empty content (cheapest possible call)
- Claude: `messages.create(maxTokens=1, messages=[{role:"user", content:"hi"}])`
- Neither call uses any meeting/transcript data

**Timeout**: 10 seconds maximum for the validation call.

---

## CloudBadgeController

**Responsibility**: Derive and expose `CloudBadgeState` for the UI layer based on
the current `CloudProviderConfig.isEnabled` and the most recent `Suggestion.inferenceMode`.

**Input**: Reactive streams from `CloudProviderConfigRepository` and the suggestion
stream.

**Output**: `StateFlow<CloudBadgeState>` consumed by the overlay and session screens.

**State transitions**:
```
isEnabled = false → HIDDEN
isEnabled = true, lastSuggestion.inferenceMode = CLOUD → CLOUD_ACTIVE
isEnabled = true, lastSuggestion.inferenceMode = ON_DEVICE → FALLBACK
isEnabled = true, no suggestion yet in session → CLOUD_ACTIVE (optimistic)
```

The badge MUST update within one UI frame (16ms) of the trigger condition changing.
It MUST NOT be dismissible by user gesture while Cloud mode is active.

---

## Updated: AudioProcessingForegroundService manifest additions

The existing service declaration from spec 004 gains `dataSync` to the
`foregroundServiceType` and the `INTERNET` + `FOREGROUND_SERVICE_DATA_SYNC`
permissions are added:

```xml
<!-- Additions to AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Updated service declaration (replaces spec 004 entry) -->
<service
    android:name=".service.AudioProcessingForegroundService"
    android:foregroundServiceType="microphone|dataSync"
    android:exported="false" />
```
