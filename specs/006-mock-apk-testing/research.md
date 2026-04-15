# Research: Mock APK Testing (No Physical Device)

**Feature**: 006-mock-apk-testing
**Date**: 2026-04-15
**Status**: Complete — all NEEDS CLARIFICATION resolved

---

## Decision 1: Test Framework

**Decision**: Robolectric 4.14 for all JVM unit and integration tests; headless AVD deferred to future spec (optional CI-only).

**Rationale**:
- Robolectric 4.14 is compatible with AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM 2024.09.00
- Runs entirely on JVM via `./gradlew test` — no emulator, no display, no Android Studio
- Shadows `AndroidKeyStore`, `ConnectivityManager`, `NotificationManager`, and other Android system services used by spec 005 code
- Headless AVD (for Compose rendering) is unnecessary for smoke tests 1, 4, 5, 6, 7 which are all logic/data-layer tests; visual tests (smoke tests 2, 3) remain manual

**Alternatives Considered**:
- **Pure emulator (AVD headless)**: Correct for Compose UI tests but requires 5–10 min spin-up per run; not viable for "under 5 minutes" target; deferred
- **Espresso + physical device**: Violates the "no physical device" constraint
- **JUnit 5 without Robolectric**: Cannot call Android APIs (Context, DataStore, Keystore); unsuitable for storage layer tests

**Versions pinned**:
```
robolectric            = "4.14.1"
junit                  = "4.13.2"
mockito-kotlin         = "5.4.0"
coroutines-test        = "1.8.1"        # same as prod coroutines version
mockwebserver          = "4.12.0"       # same as prod okhttp version
truth                  = "1.4.4"
androidx-test-core     = "1.6.1"
```

---

## Decision 2: Mocking Firebase AI Logic SDK (Gemini)

**Decision**: Interface abstraction (`CloudStreamingProvider`) injected into `CloudInferenceEngine`. `FakeGeminiProvider` implements the interface for tests. Firebase SDK is never initialized in tests.

**Rationale**:
- Firebase AI Logic SDK v17.0.0 requires a live Firebase project and `google-services.json` for initialization — both unavailable in test environment
- Abstracting behind an interface (`CloudStreamingProvider`) means `CloudInferenceEngine` is never coupled to `GeminiInferenceClient` at compile time in tests
- The interface is thin: `fun streamSuggestion(request): Flow<InferenceEvent>`
- `FakeGeminiProvider` emits configurable token sequences from a `List<String>` with optional per-token `delay()`

**Alternatives Considered**:
- **Mockito mock of `GeminiInferenceClient`**: Brittle — Firebase initialization is triggered in the constructor; must be static-mocked or shadowed
- **HTTP-level mock (intercept `*.googleapis.com`)**: Firebase AI Logic SDK does not route through OkHttp; uses gRPC internally — not interceptable via `MockWebServer`
- **Robolectric shadow of `Firebase.ai()`**: Requires writing a custom shadow class; maintenance burden outweighs the benefit

**Interface**:
```kotlin
// inference/CloudStreamingProvider.kt
interface CloudStreamingProvider {
    fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent>
    suspend fun validate(): ValidationResult
}
```

---

## Decision 3: Mocking Anthropic Java SDK Streaming

**Decision**: `FakeStreamResponse<RawMessageStreamEvent>` wraps a `List<RawMessageStreamEvent>` and implements `StreamResponse<T>`. `FakeAnthropicProvider` uses it. For network-level tests, `MockWebServer` returns a pre-canned SSE response body.

**Rationale**:
- `StreamResponse<T>.stream()` returns `java.util.stream.Stream<T>` — easy to fake with `list.stream()`
- Creating `RawMessageStreamEvent` directly requires internal constructors; instead, `FakeAnthropicProvider` implements `CloudStreamingProvider` and emits `InferenceEvent.Token` directly — bypassing the real SDK parsing chain entirely
- For the network audit test (ST7), `ClaudeInferenceClient` is used directly with a real `AnthropicOkHttpClient` pointing at a `MockWebServer` URL so request bodies are captured

**Fake implementation**:
```kotlin
class FakeAnthropicProvider(
    private val tokens: List<String> = listOf("Hello", " world"),
    private val delayMs: Long = 0L
) : CloudStreamingProvider {
    override fun streamSuggestion(request: CloudInferenceRequest) = flow {
        for (token in tokens) {
            if (delayMs > 0) delay(delayMs)
            emit(InferenceEvent.Token(request.requestId, token))
        }
        emit(InferenceEvent.Complete(request.requestId, tokens.joinToString(""), CloudProvider.CLAUDE))
    }
    override suspend fun validate() = ValidationResult.SUCCESS
}
```

**Alternatives Considered**:
- **Mockito spy on `AnthropicOkHttpClient`**: Final classes in the SDK cannot be spied without PowerMock; undesirable
- **Reusing production `ClaudeInferenceClient` with a mock OkHttpClient**: Correct for network audit test only; too fragile for logic-layer unit tests

---

## Decision 4: Tink Android Keystore on JVM

**Decision**: Robolectric 4.14 shadows `java.security.KeyStore` and `javax.crypto.KeyGenerator`. Tink v1.15.0 detects the Robolectric environment and uses software-backed keys automatically. No additional configuration required beyond adding `@RunWith(RobolectricTestRunner::class)`.

**Rationale**:
- Robolectric ships with `ShadowKeyStore` that satisfies Tink's `AndroidKeyStoreSpec` initialization without hardware
- Confirmed compatible: Tink 1.15.0 + Robolectric 4.14 + minSdk 23
- DataStore in tests uses `InMemoryDataStore` (provided by `androidx.datastore:datastore-preferences:1.1.1` test artifacts) to avoid file I/O

**Test setup**:
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class TinkApiKeyStoreTest {
    private lateinit var store: TinkApiKeyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        store = TinkApiKeyStore(context)  // Tink uses software keys via Robolectric shadow
    }
}
```

**Alternatives Considered**:
- **In-memory keyset (`KeysetHandle.generateNew()`) without Android Keystore**: Works for pure JUnit tests but doesn't exercise the full production code path (master key wrapping)
- **Instrumented tests on emulator**: Correct but slow; violates the "no emulator" target

---

## Decision 5: TTFT Measurement Without Network

**Decision**: Extract a `Clock` interface from `CloudInferenceEngine`. Production uses `object SystemClock : Clock { override fun nowMs() = System.currentTimeMillis() }`. Tests inject `FakeClock` which advances only on explicit `.advanceBy(ms)` calls.

**Rationale**:
- Eliminates non-determinism: TTFT assertions can be exact (`assertEquals(2000L, measured)`) rather than threshold-based
- Minimal production code change: one constructor parameter `clock: Clock = SystemClock` with default
- `FakeClock` advances are correlated with fake provider `delay()` calls via `TestCoroutineDispatcher`

**Clock interface**:
```kotlin
interface Clock { fun nowMs(): Long }
object SystemClock : Clock { override fun nowMs() = System.currentTimeMillis() }
class FakeClock(start: Long = 0L) : Clock {
    private var _now = start
    override fun nowMs() = _now
    fun advanceBy(ms: Long) { _now += ms }
}
```

**Alternatives Considered**:
- **`kotlinx.coroutines.test.TestCoroutineScheduler` virtual time**: Correct for coroutine-internal delays but does not cover wall-clock measurements already in production code (`System.currentTimeMillis()`)
- **Threshold assertions with real clock (`ttft < 5000`)**: Flaky on loaded CI machines

---

## Decision 6: Network Audit (No Audio in Requests)

**Decision**: `MockWebServer` (OkHttp 4.12.0) replaces the Anthropic endpoint for the network audit test. An `OkHttp Interceptor` added to the test client asserts no binary content before passing the request through. `MockWebServer.takeRequest()` then validates the captured body post-hoc.

**Rationale**:
- `MockWebServer` is already on the classpath (same OkHttp version as production); no new dependency
- `ClaudeInferenceClient` already accepts an `AnthropicOkHttpClient` that can be pointed at an arbitrary base URL via `AnthropicOkHttpClient.builder().baseUrl(server.url("/").toString())`
- Two-layer validation: interceptor fires synchronously during the request; `takeRequest()` allows post-test inspection of the full body
- Gemini uses gRPC (not OkHttp) so audit test is Claude-only; Gemini network audit remains a manual step

**Test structure**:
```kotlin
@Test
fun `no audio bytes in claude request body`() {
    val server = MockWebServer()
    server.enqueue(MockResponse().setBody(fakeClaudeStreamingSse))
    val client = AnthropicOkHttpClient.builder()
        .apiKey("test-key")
        .baseUrl(server.url("/").toString())
        .build()
    // Run ClaudeInferenceClient.streamSuggestion(request, "test-key")...
    val recorded = server.takeRequest()
    assertThat(recorded.body.readUtf8())
        .doesNotContainMatch(Regex("audio|wav|m4a|base64|\\x00[\\x01-\\xFF]{100,}"))
}
```

**Alternatives Considered**:
- **tcpdump / Charles Proxy**: Requires host-level setup, not runnable via `./gradlew test`
- **`HttpLoggingInterceptor` assertions**: Logs are strings; binary data may be truncated by the logger before inspection
- **Robolectric shadow of `OkHttpClient`**: Does not execute real HTTP path; would miss actual request serialization bugs
