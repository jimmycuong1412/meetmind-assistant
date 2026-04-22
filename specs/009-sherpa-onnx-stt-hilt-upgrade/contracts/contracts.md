# Feature 009 — Interface Contracts

**Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade`  
**Date**: 2026-04-21  
**Scope**: JVM-level contracts testable without a physical device or native libraries.

---

## Contract Group 1 — `SherpaOnnxDataSource`

### C1.1 — decodeMutex prevents concurrent decode() calls

| Field | Value |
|-------|-------|
| **Contract ID** | C1.1 |
| **Description** | When two coroutines call `decode()` concurrently on the same `SherpaOnnxDataSource`, the `decodeMutex` ensures they execute sequentially. Max concurrent decode calls must never exceed 1. |
| **Test approach** | Fake `OfflineRecognizer` wrapper that tracks concurrent calls via `AtomicInteger`. Launch two coroutines calling decode simultaneously. Assert `maxConcurrent == 1`. |
| **Expected** | `maxConcurrent == 1` (mutex serializes all decode calls) |
| **Test double** | `FakeOfflineRecognizer` — wraps a real or stub decoder, counts concurrent entries |

```kotlin
@Test
fun `C1_1 decodeMutex prevents concurrent decode calls`() = runTest {
    val concurrentCount = AtomicInteger(0)
    var maxConcurrent = 0
    val fakeDataSource = FakeSherpaOnnxDataSource { _ ->
        concurrentCount.incrementAndGet().also { maxConcurrent = maxOf(maxConcurrent, it) }
        delay(100)
        concurrentCount.decrementAndGet()
        RecognitionResult("text", isComplete = true)
    }
    val jobs = (1..3).map { launch { fakeDataSource.invokeDecodeOnce(FloatArray(16000)) } }
    jobs.forEach { it.join() }
    assertEquals("Max concurrent decode calls must be 1", 1, maxConcurrent)
}
```

---

### C1.2 — Empty audio produces no RecognitionResult emission

| Field | Value |
|-------|-------|
| **Contract ID** | C1.2 |
| **Description** | When `startRecording()` is called with an empty audio buffer (no speech detected by VAD), the `Flow<RecognitionResult>` emits no items. |
| **Expected** | Zero emissions from `recognitionFlow` |
| **Test double** | `FakeVad` returning no speech segments |

---

### C1.3 — recognized text is appended to TranscriptWindowBuffer

| Field | Value |
|-------|-------|
| **Contract ID** | C1.3 |
| **Description** | When `RecognitionResult(text = "finish the report", isComplete = true)` is emitted by `SherpaOnnxDataSource`, `AudioProcessingForegroundService.onTranscriptSegment()` appends it to `TranscriptWindowBuffer`. |
| **Expected** | `buffer.windowText(windowSizeMs).contains("finish the report") == true` |
| **Test double** | `FakeSherpaOnnxDataSource` emitting a fixed `RecognitionResult` |

---

## Contract Group 2 — `ModelDownloadManager`

### C2.1 — Progress emissions cover 0% to 100%

| Field | Value |
|-------|-------|
| **Contract ID** | C2.1 |
| **Description** | `downloadFile(url, destFile)` emits `DownloadProgress` with monotonically increasing `bytesDownloaded`, ending with `isComplete = true`. |
| **Expected** | First emission: `bytesDownloaded < totalBytes, isComplete = false`. Last emission: `isComplete = true`. All emissions ordered. |
| **Test double** | Mock HTTP server (MockWebServer or in-memory byte stream) |

### C2.2 — Resume skips already-downloaded bytes

| Field | Value |
|-------|-------|
| **Contract ID** | C2.2 |
| **Description** | If a `.partial` file exists with 1000 bytes, `downloadFile()` issues a `Range: bytes=1000-` request and starts emissions at `bytesDownloaded = 1000`. |
| **Expected** | First `DownloadProgress.bytesDownloaded >= 1000`. HTTP request headers contain `Range: bytes=1000-`. |
| **Test double** | MockWebServer asserting the Range header |

---

## Contract Group 3 — `ModelConfigRepository` (updated)

### C3.1 — setModelVariant persists and observeVariant reflects the change

| Field | Value |
|-------|-------|
| **Contract ID** | C3.1 |
| **Description** | After `setModelVariant(GemmaModelVariant.Q8_0)`, `observeVariant()` emits `Q8_0` on next collect. |
| **Expected** | `observeVariant().first() == GemmaModelVariant.Q8_0` |
| **Test double** | In-memory DataStore (same `testDataStore()` helper used in spec 008) |

```kotlin
@Test
fun `C3_1 setModelVariant persists variant`() = runTest {
    val repo = ModelConfigRepository(testDataStore(this))
    repo.setModelVariant(GemmaModelVariant.Q8_0)
    assertEquals(GemmaModelVariant.Q8_0, repo.observeVariant().first())
}
```

### C3.2 — Default variant is Q4_K_M on fresh DataStore

| Field | Value |
|-------|-------|
| **Contract ID** | C3.2 |
| **Description** | A freshly constructed `ModelConfigRepository` with empty DataStore returns `GemmaModelVariant.Q4_K_M` from `observeVariant()`. |
| **Expected** | `observeVariant().first() == GemmaModelVariant.Q4_K_M` |

---

## Contract Group 4 — `DeviceTierDetector`

### C4.1 — ≥8 GB RAM + SDK ≥ 33 → recommends Q4_K_M

| Field | Value |
|-------|-------|
| **Contract ID** | C4.1 |
| **Description** | `DeviceTierDetector.recommendedVariant(totalRamBytes, sdkVersion)` returns `Q4_K_M` when `totalRamBytes >= 8_000_000_000L && sdkVersion >= 33`. |
| **Expected** | `== GemmaModelVariant.Q4_K_M` |

```kotlin
@Test
fun `C4_1 flagship device recommends Q4_K_M`() {
    val detector = DeviceTierDetector()
    val result = detector.recommendedVariant(
        totalRamBytes = 12_000_000_000L,  // Y700 Gen 3 / Honor Magic 6 Pro
        sdkVersion = 34  // Android 14
    )
    assertEquals(GemmaModelVariant.Q4_K_M, result)
}
```

### C4.2 — < 8 GB RAM or SDK < 33 → recommends IQ4_NL

| Field | Value |
|-------|-------|
| **Contract ID** | C4.2 |
| **Description** | `recommendedVariant()` returns `IQ4_NL` when either condition fails. |
| **Expected** | `== GemmaModelVariant.IQ4_NL` for (4 GB, SDK 33) and (12 GB, SDK 32) |

---

## Contract Group 5 — `OnDeviceLlamaProvider` model switching

### C5.1 — loadModel with different path forces unload then reload

| Field | Value |
|-------|-------|
| **Contract ID** | C5.1 |
| **Description** | If `OnDeviceLlamaProvider` has model A loaded (`isLoaded() == true`) and `loadModel(pathB)` is called with a different path, `unload()` is called before the new model loads. The provider must never hold two model handles simultaneously. |
| **Expected** | After calling `loadModel(pathB)`: `unload()` was called once; `isLoaded() == true`; new `modelHandle` is valid. |
| **Test double** | `FakeLlamaJni` (already exists in test helpers) tracking `loadCount`, `unloadCount` |

```kotlin
@Test
fun `C5_1 loading different path forces unload first`() = runTest {
    val fake = FakeLlamaJni()
    val provider = OnDeviceLlamaProvider(fake, FakeModelConfigRepository())
    provider.loadModel("/models/q4km.gguf")
    assertTrue(provider.isLoaded())
    provider.loadModel("/models/q8.gguf")
    assertEquals("unload must be called once before second load", 1, fake.unloadCount)
    assertTrue(provider.isLoaded())
}
```

### C5.2 — loadModel with same path is idempotent (no unload)

| Field | Value |
|-------|-------|
| **Contract ID** | C5.2 |
| **Description** | Calling `loadModel(samePath)` twice does not call `unload()` — it returns immediately if the model is already loaded with the same path. |
| **Expected** | `fake.unloadCount == 0` after two loads with same path |

---

## Contract Group 6 — Hilt injection graph

### C6.1 — @Singleton scope returns same instance

| Field | Value |
|-------|-------|
| **Contract ID** | C6.1 |
| **Description** | `TranscriptWindowBuffer` and `AnalysisCadenceController` are `@Singleton` — two injection points resolve to the same object instance. |
| **Expected** | `instance1 === instance2` (identity equality) |
| **Test approach** | `@HiltAndroidTest` with `HiltAndroidRule`; inject the type into two test fields |

---

## Test Double Reference

| Fake | Replaces | Key API |
|------|---------|---------|
| `FakeSherpaOnnxDataSource` | `SherpaOnnxDataSource` | `decodeCallCount`, configurable `RecognitionResult` |
| `FakeModelDownloadManager` | `ModelDownloadManager` | `downloadCallCount`, emits fake `DownloadProgress` |
| `FakeDeviceTierDetector` | `DeviceTierDetector` | Constructor-injected `recommendedVariant` |
| `FakeLlamaJni` (existing) | `LlamaJni` / `LlamaBackend` | `loadCount`, `unloadCount` (already in test helpers) |

All fakes in `app/src/test/kotlin/com/meetmind/assistant/helpers/`.
