# Quickstart: Question Detection and Timely Suggestions

**Feature**: 004-question-detection
**Date**: 2026-04-14

---

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android device or emulator running API 26+ (Android 8.0+)
- Physical device strongly recommended for audio testing (emulator microphone
  is unreliable for VAD tuning)
- ~3 GB free storage for the Phi-3-mini model download on first launch

---

## Project Setup

### 1. Gradle dependencies to add

```kotlin
// app/build.gradle.kts

dependencies {
    // Vosk ASR (streaming on-device speech recognition)
    implementation("com.alphacephei:vosk-android:0.3.47")

    // TFLite for Silero VAD
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // llama.cpp Android JNI wrapper — use a maintained fork or build from source
    // Example: https://github.com/MohamedAG/llama.cpp-android (or build native lib)
    // For now, reference as a local .aar or submodule:
    implementation(fileTree("libs") { include("*.aar") })

    // Room for ContextProfile persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Jetpack Compose (UI)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
```

### 2. Manifest entries

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<application ...>
    <service
        android:name=".service.AudioProcessingForegroundService"
        android:foregroundServiceType="microphone"
        android:exported="false" />
</application>
```

---

## Verifying the Pipeline (Manual Smoke Tests)

### Smoke Test 1: VAD fires on speech

1. Build and install the APK on a physical device.
2. Grant microphone permission when prompted.
3. Tap "Start Listening".
4. Speak any sentence aloud.
5. **Expected**: A debug/log entry (or a visible transcript in the dev overlay)
   shows a `TranscriptSegment` was produced.

### Smoke Test 2: Question detector fires on a question

1. With the session active, speak: _"Can you tell me about your experience with
   distributed systems?"_
2. **Expected**: Within 3 seconds, a suggestion card appears on screen.

### Smoke Test 3: Non-question is silently ignored

1. With the session active, speak: _"The quarterly results were better than expected.
   The team delivered ahead of schedule. Stakeholders are satisfied."_ (3 statements)
2. **Expected**: No new suggestion card appears after any of the three statements.

### Smoke Test 4: Mode affects suggestion tone

1. Start a session in **Interview Mode**.
2. Speak: _"Tell me about a time you solved a difficult technical problem."_
3. Note the suggestion (it should be in first-person, drawing on your profile role).
4. Stop the session. Start a new session in **Meeting Mode**.
5. Ask the same question.
6. **Expected**: The suggestion in Meeting Mode is collaborative/action-oriented,
   visibly different in tone from the Interview Mode suggestion.

### Smoke Test 5: Freshness indicator transitions

1. Start a session. Trigger a suggestion by asking a question.
2. Note the suggestion shows "fresh" styling.
3. Wait 65 seconds without speaking.
4. **Expected**: The suggestion card visually transitions to "stale" styling without
   any user interaction.

### Smoke Test 6: Floating overlay persists over other apps

1. Start a session and tap "Float".
2. Open another app (e.g., the device browser).
3. Ask a question aloud.
4. **Expected**: A suggestion appears in the floating window while the browser is
   in the foreground.

---

## First-Launch Model Download

On first launch, the app downloads the Phi-3-mini Q4_K_M model (~2.2 GB).
A progress screen is displayed. The download uses the system's default network;
no Wi-Fi check is enforced in v1 (users are warned of the size).

To test with a pre-downloaded model during development:
1. Push the GGUF file to the device:
   ```bash
   adb push phi-3-mini-4k-instruct-q4_k_m.gguf \
       /data/data/com.yourpackage.meetmind/files/models/phi3-mini.gguf
   ```
2. The app checks for the file at `getFilesDir()/models/phi3-mini.gguf` on start.
   If present, the download step is skipped.

---

## Tuning Notes

- **VAD sensitivity**: Adjust `WebRTC VAD aggressiveness` (0–3) in `VadPipeline` if
  too many false positives in noisy environments. Start at 2.
- **Question classifier**: Add domain-specific phrases to the auxiliary inversion
  list if your use case involves domain jargon (e.g., "Could you elaborate on...").
- **LLM temperature**: Default 0.7. Lower (0.3–0.5) for more deterministic,
  concise suggestions. Higher (0.8–1.0) for more varied but less reliable outputs.
- **Token cap**: `max_new_tokens` defaults to 40 (2 concise sentences). Increase to
  60 for 3-sentence suggestions if latency allows on your specific device.
- **GPU backend**: Build with `-DGGML_OPENCL=ON` and launch with `-ngl 99 --pure`
  to use the Adreno 750 OpenCL backend for fast prefill. Use Q4_0 model format
  (not Q4_K_M) when OpenCL is enabled — Q4_0 is the only format with optimised
  OpenCL kernels as of llama.cpp 2024 releases. **Do not use Vulkan** (`-DGGML_VULKAN`)
  — it is 14× slower than CPU on Adreno.
- **Freshness threshold**: The 60-second staleness threshold is a constant in
  `FreshnessState.kt`. Change it without a spec amendment.
- **CPU core pinning**: Pin llama.cpp threads to big cores only:
  `--cpu-mask 0x3f` (for the 6 performance cores on Snapdragon 8 Gen 3). This
  prevents scheduling on the efficiency cores during generation.

---

## Known Limitations (v1)

- Speaker diarisation is not implemented — the classifier may occasionally trigger on
  the user's own questions (best-effort suppression only).
- Rhetorical questions trigger suggestions; no filtering implemented.
- The LLM takes ~1–2s to produce the first token after a question is detected.
  On lower-end devices (Snapdragon 600-class), total latency may exceed 3s.
- The model download requires a network connection on first launch; fully offline
  first-use requires pre-loading the model via ADB (see above).
