# Research: Cloud AI Inference Mode

**Feature**: 005-cloud-ai-inference
**Date**: 2026-04-14
**Branch**: `feature/005-cloud-ai-inference`

---

## 1. Gemini API on Android

**Decision**: Firebase AI Logic SDK (`com.google.firebase:firebase-ai`) with model
`gemini-2.5-flash`

**Rationale**:
- The standalone `com.google.ai.client.generativeai` library is deprecated (superseded
  by Firebase AI Logic). Firebase AI Logic is the production path as of 2026.
- `generateContentStream()` returns a native `Flow<GenerateContentResponse>` — no
  manual SSE plumbing required.
- `gemini-2.5-flash` is the current recommended low-latency model; `gemini-2.0-flash`
  reaches EOL June 2026 — do not use it.
- Use `thinkingConfig { thinkingBudget = 0 }` to suppress chain-of-thought tokens
  and protect the 5-second TTFT SLA.
- Minimum API level: 21 (Android 5.0). Only `INTERNET` permission required.
- ProGuard/R8 rules are bundled in the AAR — no manual rules needed.
- Requires `google-services.json` and the `com.google.gms.google-services` plugin.

**API key handling**:
- Firebase AI Logic reads the AI Studio API key from the Firebase project config
  (set in Firebase console under "AI Logic" settings). The key is NOT in source code.
- For the Gemini Developer API backend: `Firebase.ai(backend = GenerativeBackend.googleAI())`

**Alternatives considered**:
- **Google Gen AI Java SDK (`com.google.genai:google-genai:1.47.0`)**: Also viable;
  no Firebase project required; streams via `generateContentStream()` returning
  `Iterable` (needs manual `flow {}` wrap). Less battle-tested on Android. Use if
  Firebase project setup is undesirable.
- **Direct REST + OkHttp**: Works but requires manual SSE framing and JSON parsing.
  No advantage over the SDK paths. Eliminated.

**Risks & mitigations**:
- Firebase project setup adds a `google-services.json` build step — ensure this file
  is gitignored and not committed to the repo.
- The API key stored via Firebase console is not in `EncryptedSharedPreferences` —
  it is managed by Firebase SDK internally. The user-entered key flow (Settings UI)
  will need the Gen AI Java SDK path OR the Firebase AI Logic SDK with runtime key
  override. Prefer runtime key injection (see §3 for storage).

---

## 2. Claude (Anthropic) API on Android

**Decision**: Official Anthropic Java SDK (`com.anthropic:anthropic-java:2.20.0`)
with model `claude-haiku-4-5-20251001` (`Model.CLAUDE_HAIKU_4_5`)

**Rationale**:
- Official JVM SDK, Java 8+ compatible, works on Android API 21+.
- Uses OkHttp under the hood (`anthropic-java-client-okhttp` artifact).
- `createStreaming()` returns a `StreamResponse<RawMessageStreamEvent>` —
  wrap in `flow {}` on `Dispatchers.IO` for Compose observation.
- Claude Haiku 4.5 benchmarks at **~597ms TTFT** on direct Anthropic API — well
  within the 5-second SLA. Haiku is the correct tier for low-latency use.
- API key passed to `AnthropicOkHttpClient.builder().apiKey(key)` — sets the
  `x-api-key` header automatically.

**Alternatives considered**:
- **Raw OkHttp SSE**: Works but requires manual SSE framing. The official SDK
  handles this internally. Eliminated.
- **Unofficial Kotlin Multiplatform SDK (`io.github.xemantic:anthropic-sdk-kotlin`)**:
  Not production-ready for Android. Eliminated.

**Required ProGuard rules** (SDK does not bundle consumer rules — add manually):
```
-dontwarn com.anthropic.**
-keep class com.anthropic.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
```

**OkHttp version pinning**: Pin to `4.12.0` to avoid version conflicts between the
Anthropic SDK's bundled OkHttp and any app-level OkHttp usage.

**Risks & mitigations**:
- Jackson dependency pulled in by the SDK — enable `coreLibraryDesugaringEnabled = true`
  if targeting API < 26.
- OkHttp version conflict: pin explicitly in `build.gradle.kts`.

---

## 3. API Key Storage on Android

**Decision**: Proto DataStore + Google Tink (`com.google.crypto.tink:tink-android:1.15.0`)
backed by Android Keystore

**Rationale**:
- `androidx.security:security-crypto` (`EncryptedSharedPreferences`) was officially
  deprecated in `1.1.0-alpha07` (Dec 2025) with known blocking-thread and OEM
  Keystore corruption issues. Do not use.
- Tink + DataStore is the recommended 2026 migration path: non-blocking async I/O,
  stable on all Android OEMs, AES-256-GCM encryption with hardware-backed key.
- Minimum API level: **23** (Android 6.0) for hardware-backed Android Keystore keys.
- AAD (additional authenticated data) on each encrypted value prevents cross-key
  ciphertext substitution attacks.

**Key storage pattern**:
```kotlin
// Encrypt on save:
val aead = AndroidKeysetManager.Builder()
    .withSharedPref(context, "api_key_keyset_name", "api_key_keyset_pref")
    .withKeyTemplate(PredefinedAeadParameters.AES256_GCM)
    .withMasterKeyUri("android-keystore://api_key_master")
    .build()
    .keysetHandle.getPrimitive(Aead::class.java)

val ciphertext = aead.encrypt(
    secret.toByteArray(),
    keyName.toByteArray()   // AAD = key identifier
)
// Persist Base64(ciphertext) to DataStore Preferences
```

**Gotchas**:
- Set `android:allowBackup="false"` or add backup exclusion rules for the DataStore
  file and keyset SharedPreferences — backed-up ciphertext on a new device is
  undecryptable without the original Keystore key.
- Do NOT set `UserAuthenticationRequired = true` for API keys — Keystore keys bound
  to biometrics are invalidated when the user enrolls new fingerprints.
- Wrap all Tink/Keystore init in `try/catch` and surface a user-facing error if
  Keystore is unavailable (rare OEM issue).

---

## 4. Streaming Response Handling

**Decision**: Provider SDK native streaming (Firebase AI Logic `Flow` / Anthropic
`createStreaming()`) bridged to `StateFlow` via `viewModelScope` for Compose

**Rationale**:
- Firebase AI Logic: `generateContentStream()` returns `Flow<GenerateContentResponse>`
  natively — collect directly, no SSE plumbing.
- Anthropic SDK: `createStreaming()` handles SSE internally via OkHttp — wrap the
  iterator in `flow { stream.stream().forEach { emit(it) } }` on `Dispatchers.IO`.
- Both paths avoid manual SSE framing, which is error-prone.

**Bridge pattern** (Anthropic example):
```kotlin
// In ViewModel / inference manager:
private val _currentSuggestion = MutableStateFlow("")
val currentSuggestion: StateFlow<String> = _currentSuggestion.asStateFlow()

fun streamClaudeSuggestion(question: String, role: String, mode: SessionMode) {
    inferenceScope.launch {
        _currentSuggestion.value = ""
        flow {
            client.messages().createStreaming(params).use { stream ->
                stream.stream().forEach { event ->
                    if (event is RawContentBlockDeltaEvent) {
                        (event.delta() as? TextDelta)?.text()?.let { emit(it) }
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .collect { token -> _currentSuggestion.update { it + token } }
    }
}
```

**Fallback alternative (raw OkHttp SSE)**: Use `okhttp-sse:4.12.0`
(`EventSources.createFactory`) + `callbackFlow {}` if SDK-level streaming is
insufficient for any reason. More boilerplate but fully controllable.

**Streaming client OkHttp config** (for SSE connections):
```kotlin
val streamingClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)  // no read timeout for SSE
    .build()
```

---

## 5. Network Calls from a Foreground Service

**Decision**: `Dispatchers.IO` coroutine scope with `SupervisorJob()`, single
`OkHttpClient` instance per service lifecycle; foreground service type `dataSync`

**Rationale**:
- No OS-level restriction on HTTP calls from a Foreground Service once it is running.
- `FOREGROUND_SERVICE_DATA_SYNC` is the correct service type for API inference calls
  (manifest + `startForeground()` call).
- `Dispatchers.IO` (blocking I/O thread pool) is the correct dispatcher; never
  `Dispatchers.Main` or `Dispatchers.Default` for network I/O.
- Single `OkHttpClient` instance created at service `onCreate()` — reused for all
  calls. Connection pool (default: 5 idle connections, 5-min TTL) is sufficient for
  this use case.
- `readTimeout(0)` on the streaming client prevents OkHttp from closing an active
  SSE connection mid-stream.

**Service scope pattern**:
```kotlin
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
}
```

**Manifest** (in addition to spec 004 entries):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".service.AudioProcessingForegroundService"
    android:foregroundServiceType="microphone|dataSync"
    android:exported="false" />
```

---

## 6. Model & Provider Summary

| Provider | SDK | Model | TTFT (est.) | Tier required |
|----------|-----|-------|-------------|---------------|
| Gemini | Firebase AI Logic / Gen AI SDK | `gemini-2.5-flash` | ~400–800ms | Google AI Studio (free) or Gemini Advanced |
| Claude | Anthropic Java SDK | `claude-haiku-4-5-20251001` | ~600ms | Anthropic API (pay-as-you-go) or Claude.ai Pro API access |

Both models are well within the 5-second TTFT SLA. Streaming begins within ~500ms
on typical Wi-Fi; first tokens visible to user within 1 second.

---

## 7. Final Dependency Block

```kotlin
// Gemini — Firebase AI Logic (recommended)
implementation("com.google.firebase:firebase-ai:17.0.0")
// OR Gemini — Google Gen AI Java SDK (no Firebase)
// implementation("com.google.genai:google-genai:1.47.0")

// Claude
implementation("com.anthropic:anthropic-java:2.20.0")

// OkHttp (pin to avoid version conflicts)
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")  // fallback SSE only

// Secure key storage
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("com.google.crypto.tink:tink-android:1.15.0")
```

---

## NEEDS CLARIFICATION — Resolved

All technical unknowns resolved. No open questions remain.
