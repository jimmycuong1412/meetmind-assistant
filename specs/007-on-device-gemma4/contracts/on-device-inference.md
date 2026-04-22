# Contracts: On-Device Inference (spec 007)

## Contract 1 — OnDeviceFallback interface (existing, unchanged)

`OnDeviceLlamaProvider` MUST implement the existing `OnDeviceFallback` functional interface:

```kotlin
fun interface OnDeviceFallback {
    fun generate(questionText: String): Flow<InferenceEvent>
}
```

### C1.1 — Emits at least one Token before Complete
Given a loaded model and a non-empty questionText, the flow MUST emit ≥ 1
`InferenceEvent.Token` before `InferenceEvent.Complete`.

### C1.2 — Complete carries full assembled text
`InferenceEvent.Complete.fullText` MUST equal the concatenation of all prior
`InferenceEvent.Token.text` values emitted in the same flow.

### C1.3 — Model not loaded emits Error
If the model handle is invalid (not loaded), `generate()` MUST emit
`InferenceEvent.Error` and close normally (no exception propagation to collector).

### C1.4 — Empty questionText emits Error
`generate("")` MUST emit `InferenceEvent.Error(message = "empty question")`.

### C1.5 — Flow is cancellable
Cancelling the collector MUST stop native inference within 500 ms (or the next
token boundary). The JNI layer MUST honour a cancellation flag.

---

## Contract 2 — ModelConfigRepository

### C2.1 — Default modelPath exists in DataStore after first write
After `setModelPath(path)`, `observeModelConfig().first().modelPath == path`.

### C2.2 — isModelReady reflects file existence
`isModelReady` MUST return `true` iff the file at `modelPath` exists on disk AND
`LlamaJni.canLoad(modelPath)` returns `true` (header check, not full load).

### C2.3 — nThreads clamped to 1..16
`setNThreads(0)` MUST clamp to 1. `setNThreads(99)` MUST clamp to 16.

---

## Contract 3 — LlamaJni native bridge

### C3.1 — loadModel returns non-zero handle on success
`LlamaJni.loadModel(validPath)` MUST return a handle `> 0`.

### C3.2 — loadModel returns -1 on missing file
`LlamaJni.loadModel("/nonexistent/path.gguf")` MUST return `-1` (not throw).

### C3.3 — unloadModel is idempotent
Calling `unloadModel(handle)` twice MUST NOT crash.

### C3.4 — inferenceAsync invokes onToken for each generated token
Each generated token MUST trigger exactly one `onToken(token)` call.

### C3.5 — inferenceAsync invokes onComplete exactly once
`onComplete` MUST be called exactly once per `inferenceAsync` invocation, after
all `onToken` calls.

---

## Contract 4 — ModelSetupScreen

### C4.1 — Shows "Model ready" when isModelReady == true
When `ModelLoadState.Ready`, the screen MUST display a green status indicator
and the "Start Session" button MUST be enabled.

### C4.2 — Shows setup instructions when model not found
When file at configured path does not exist, the screen MUST show the adb push
command with the correct path pre-filled.

### C4.3 — Load button triggers LlamaJni.loadModel
Tapping "Load Model" MUST transition state to `ModelLoadState.Loading` before
the JNI call completes, then to `Ready` or `Error` depending on result.

---

## Contract 5 — Data Minimisation (Constitution Principle I)

### C5.1 — questionText truncated to 600 chars before JNI call
`OnDeviceLlamaProvider.generate(text)` MUST silently truncate `text` to 600
characters before passing to `LlamaJni.inferenceAsync`. (Mirrors cloud path limit.)

### C5.2 — No audio bytes passed to JNI
The JNI bridge MUST NOT accept or store audio data of any form. Only string input
is permitted.

### C5.3 — System prompt is ≤ 320 chars
The system prompt assembled inside `OnDeviceLlamaProvider` (role + mode context)
MUST be ≤ 320 characters at runtime.
