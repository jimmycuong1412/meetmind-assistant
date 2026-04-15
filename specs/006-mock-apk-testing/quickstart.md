# Quickstart: Mock APK Testing (No Physical Device)

**Feature**: 006-mock-apk-testing
**Date**: 2026-04-15

---

## Prerequisites

- `./gradlew assembleDebug` passes cleanly (spec 005 must be implemented)
- JDK 17 installed (`JAVA_HOME` set to JDK 17)
- No physical device, no emulator, no Android Studio required

---

## Run All Tests

```powershell
# Windows PowerShell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot'
$env:ANDROID_HOME = 'C:\Android\Sdk'
cd F:\Git\meetmind-assistant
.\gradlew.bat test --no-daemon
```

Expected output:
```
> Task :app:test
...
BUILD SUCCESSFUL in ~2-4 minutes
```

---

## Run Individual Test Classes

```powershell
# US1: API key storage
.\gradlew.bat test --tests "*.TinkApiKeyStoreTest" --no-daemon
.\gradlew.bat test --tests "*.CloudProviderConfigRepositoryTest" --no-daemon
.\gradlew.bat test --tests "*.CloudSettingsViewModelTest" --no-daemon

# US2: Inference + fallback
.\gradlew.bat test --tests "*.CloudInferenceEngineTest" --no-daemon

# US3: TTFT measurement
.\gradlew.bat test --tests "*.TtftTest" --no-daemon

# US4: Network audit
.\gradlew.bat test --tests "*.NetworkAuditTest" --no-daemon
```

---

## Dependency Setup (app/build.gradle.kts additions)

The following test dependencies are added by this spec:

```kotlin
// Testing
testImplementation(libs.junit)
testImplementation(libs.robolectric)
testImplementation(libs.mockito.kotlin)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.truth)
testImplementation(libs.okhttp.mockwebserver)
testImplementation(libs.androidx.test.core)
testImplementation(libs.androidx.test.core.ktx)

// Compose test support (for future UI tests)
testImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

Version catalog additions (`gradle/libs.versions.toml`):

```toml
[versions]
robolectric       = "4.14.1"
junit             = "4.13.2"
mockitoKotlin     = "5.4.0"
truth             = "1.4.4"
androidxTestCore  = "1.6.1"

[libraries]
junit                          = { group = "junit", name = "junit", version.ref = "junit" }
robolectric                    = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
mockito-kotlin                 = { group = "org.mockito.kotlin", name = "mockito-kotlin", version.ref = "mockitoKotlin" }
kotlinx-coroutines-test        = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
truth                          = { group = "com.google.truth", name = "truth", version.ref = "truth" }
okhttp-mockwebserver           = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
androidx-test-core             = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
androidx-test-core-ktx         = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-compose-ui-test-junit4   = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
```

---

## Robolectric Config (`app/src/test/resources/robolectric.properties`)

```properties
# Use Android API 33 (closest stable SDK with shadow support for Keystore)
sdk=33
# Suppress resource loading for tests that don't need it
manifest=NONE
```

---

## Key Test Patterns

### Tink/Keystore test (with Robolectric)

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TinkApiKeyStoreTest {
    @Test
    fun saveAndRetrieveKey() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = TinkApiKeyStore(context)
        store.saveKey(CloudProvider.CLAUDE, "sk-ant-test-key".toByteArray())
        val retrieved = store.getKey(CloudProvider.CLAUDE)
        assertThat(String(retrieved!!)).isEqualTo("sk-ant-test-key")
    }
}
```

### Fallback timeout test (with FakeClock)

```kotlin
@Test
fun fallback_timeout() = runTest {
    val fakeClock = FakeClock()
    val slowProvider = FakeCloudStreamingProvider(firstTokenDelayMs = 6_000)
    val engine = CloudInferenceEngine(
        context = ...,
        apiKeyStore = fakeKeyStore,
        configRepository = fakeRepo,
        geminiProvider = slowProvider,
        claudeProvider = { slowProvider },
        onDeviceFallback = { q -> flowOf(InferenceEvent.Token(UUID.randomUUID(), "fallback")) },
        clock = fakeClock
    )
    val events = engine.streamSuggestion(testRequest).toList()
    assertThat(events.first()).isInstanceOf(InferenceEvent.FallbackActivated::class.java)
    assertThat((events.first() as InferenceEvent.FallbackActivated).reason)
        .isEqualTo(FallbackReason.TIMEOUT)
}
```

### Network audit test (with MockWebServer)

```kotlin
@Test
fun noAudioInClaudeRequest() = runTest {
    val server = MockWebServer()
    server.enqueue(MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\ndata: [DONE]\n\n"))

    val client = ClaudeInferenceClient()
    val events = client.streamSuggestion(
        request = testRequest,
        apiKey = "sk-ant-test",
        baseUrl = server.url("/").toString()   // override endpoint
    ).toList()

    val recorded = server.takeRequest()
    val body = recorded.body.readUtf8()
    assertThat(body).doesNotContainMatch("audio|wav|m4a|base64")
    assertThat(body).contains("\"question\"")   // text field present
}
```

---

## Smoke Test Mapping

| Spec 005 Smoke Test | Automated Test | Status after spec 006 |
|---------------------|----------------|-----------------------|
| ST1: key config + persistence | `TinkApiKeyStoreTest`, `CloudSettingsViewModelTest` | ✅ Automated |
| ST2: cloud badge visibility | Manual (visual) | Manual |
| ST3: suggestion quality | Manual (subjective) | Manual |
| ST4: fallback on no network | `CloudInferenceEngineTest#fallback_networkUnavailable` | ✅ Automated |
| ST5: fallback on timeout | `CloudInferenceEngineTest#fallback_timeout_5s` | ✅ Automated |
| ST6: 401 auto-disable | `CloudSettingsViewModelTest#authError_disablesCloud` | ✅ Automated |
| ST7: no audio transmitted | `NetworkAuditTest#noAudioBytes_inClaudeRequest` | ✅ Automated |
