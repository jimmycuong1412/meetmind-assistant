# Research: On-Device Gemma 4 E4B Inference (spec 007)

## Decision 1: Model selection — Gemma 4 E4B IT Q4_K_M

**Decision**: Use `gemma-4-E4B-it-Q4_K_M.gguf` as the primary on-device model.

**Rationale**:
- Gemma 4 E ("Edge") series is explicitly designed for mobile/IoT. E4B at 4B params is the
  sweet spot for the Y700 Gen 3 (Snapdragon 8 Gen 3, 16 GB LPDDR5X).
- Runtime RAM: ~6–8 GB (model weights ~5.3 GB + KV cache ~0.5–1 GB + overhead). Safe on 16 GB.
- Decode speed: 8–15 tok/s on Adreno 750 — fast enough for Principle II (first token ≤ 1s).
- Gemma 4 E4B outperforms 2–3× larger dense models on reasoning benchmarks.
- llama.cpp officially supports Gemma 4 as of April 2, 2026 (PR #21343 + stability patches).

**Alternatives considered**:
- Phi-3-mini Q4_K_M (spec 004 plan): Acceptable, but weaker reasoning. Gemma 4 E4B is a
  strict upgrade for the same RAM cost.
- Gemma 4 E2B Q8 (~5 GB): Faster, but lower quality for business meeting Q&A.
- Gemma 4 26B A4B Q4_K_M (~16.8 GB): Active params ~4B so speed is near-4B, but weights
  alone exceed safe RAM budget (16 GB total - OS ~3 GB - app - model = too tight).

**GGUF source**: `https://huggingface.co/ggml-org/gemma-4-E4B-it-GGUF`
**File**: `gemma-4-E4B-it-Q4_K_M.gguf` (~5.0–5.3 GB)

---

## Decision 2: llama.cpp integration strategy — build from source, JNI

**Decision**: Build llama.cpp from source with Android NDK + CMake; package `.so` into
`jniLibs/arm64-v8a/`; expose a Kotlin `LlamaJni` object with `external fun` declarations.

**Rationale**:
- No official AAR ships from llama.cpp. Community AARs (Ai-Core) exist but are not actively
  maintained for Gemma 4. Building from source guarantees Gemma 4 support (April 2026+ build).
- Reference projects: `kotlin-llama.cpp` (ljcamargo), `Llamatik` (ferranpons), `Ai-Core`
  (Siddhesh2377) — all demonstrate the JNI pattern successfully.

**Alternatives considered**:
- Community AAR (Ai-Core): Too far behind April 2026 Gemma 4 support.
- whoezy/llama-android: Less documentation, unknown Gemma 4 status.

**Build flags**:
```bash
cmake .. -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DBUILD_SHARED_LIBS=OFF \
  -DGGML_OPENCL=ON \
  -DGGML_OPENCL_USE_ADRENO_KERNELS=ON \
  -DGGML_OPENCL_EMBED_KERNELS=ON \
  -DGGML_OPENCL_SMALL_ALLOC=ON \
  -DGGML_OPENMP=OFF
```

---

## Decision 3: OpenCL GPU acceleration — enabled for Adreno 750

**Decision**: Enable OpenCL with Adreno-optimised kernels. Set `n_gpu_layers = -1` (offload all).

**Rationale**:
- Adreno 750 (Snapdragon 8 Gen 3) is officially supported by Qualcomm + llama.cpp maintainers.
- GPU offload provides 2–4× throughput over CPU-only, critical for Principle II (≤ 3s total).
- With GPU offload, set `n_threads = 4–6` (big cores only; GPU handles matrix math).
- Known issues: early OpenCL had pooling bugs — use llama.cpp build from April 2026+.

---

## Decision 4: Model delivery — pre-push via adb for personal sideloaded APK

**Decision**: Model is NOT bundled in the APK. User pre-pushes GGUF to device storage via adb,
OR the app downloads it on first run with a progress UI. APK ships a `ModelSetupScreen`
that guides the user through one of these two paths.

**Rationale**:
- Gemma 4 E4B Q4_K_M is ~5 GB — far exceeds APK limits (150 MB compressed).
- This is a personal sideloaded APK (Principle III). No Play Store constraints apply, but
  the practical solution is the same: separate model delivery.
- adb push is the simplest path for the developer/owner:
  `adb push gemma-4-E4B-it-Q4_K_M.gguf /sdcard/Download/`
  Then the app reads from `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`.
- Optional: in-app OkHttp download with SHA256 verification for convenience.

**Model path**: `/sdcard/Download/gemma-4-E4B-it-Q4_K_M.gguf` (default)
  Configurable via a text field in `ModelSetupScreen`.

---

## Decision 5: Kotlin Flow + JNI bridge — callbackFlow

**Decision**: Use `callbackFlow` to bridge JNI token callbacks to `Flow<InferenceEvent>`.

**Rationale**:
- `callbackFlow` is designed for callback-based sources. `trySend()` is non-blocking and
  safe to call from native (JNI) threads.
- `awaitClose { }` handles lifecycle cleanup when the Flow collector cancels.
- No Channel or shared state needed — the pattern is clean and testable with a fake.

**Pattern**:
```kotlin
callbackFlow {
    val callback = object : TokenCallback {
        override fun onToken(token: String) { trySend(InferenceEvent.Token(requestId, token)) }
        override fun onComplete(full: String) { trySend(InferenceEvent.Complete(...)); close() }
        override fun onError(msg: String) { close(Exception(msg)) }
    }
    LlamaJni.inferenceAsync(modelHandle, prompt, callback)
    awaitClose { /* optionally cancel native inference */ }
}
```

---

## Decision 6: Model lifecycle — lazy singleton in AppContainer

**Decision**: `OnDeviceLlamaProvider` is a lazy singleton in `AppContainer`. Model loads on
first inference call. Unload on `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`.

**Rationale**:
- Lazy avoids startup delay (model load takes ~2–5s for 5 GB GGUF).
- Singleton ensures model stays in RAM across multiple inference requests (avoids reload cost).
- `onTrimMemory` hook lets the OS reclaim RAM when needed without crashing.

---

## Decision 7: Threading — 6 threads, GPU offload

**Decision**: `n_threads = 6` (prime + 5 big A720 cores), `n_gpu_layers = -1`.

**Rationale**: Snapdragon 8 Gen 3 has 1x Cortex-X4 + 5x Cortex-A720 + 2x A520 little cores.
Use 6 big cores for CPU work; skip 2 little A520 cores. GPU handles matrix multiplication.
Memory bandwidth (77 GB/s LPDDR5X) is the bottleneck — more threads beyond 6 don't help.

---

## Decision 8: System prompt for meeting Q&A

**Decision**: Craft a focused ≤ 320-char system prompt for meeting suggestion context.
Apply existing data minimisation (question text ≤ 600 chars) per `CloudInferenceEngine`
conventions — same limits apply to on-device path for consistency.

**Prompt template**:
```
You are a real-time meeting assistant. Answer the question concisely in 1-3 sentences.
Role: {role}. Mode: {mode}.
```

---

## Unresolved

- SHA256 hash of `gemma-4-E4B-it-Q4_K_M.gguf` (to be filled in once file is sourced from HF)
- Exact llama.cpp git commit SHA to pin (use latest April 2026 tag)
- First-token latency benchmark on real Y700 Gen 3 hardware (target ≤ 1s per Principle II)
