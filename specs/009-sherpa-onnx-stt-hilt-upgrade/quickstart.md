# Quickstart & Smoke Test Guide — Feature 009

**Branch**: `feature/009-sherpa-onnx-stt-hilt-upgrade`  
**Date**: 2026-04-21

---

## Prerequisites

1. **Device**: Lenovo Legion Y700 Gen 3 (Snapdragon 8 Gen 3, 12 GB, Android 14) OR Honor Magic 6 Pro (same SoC)
2. **ADB connected**: `adb devices` shows the device
3. **Build**: `./gradlew assembleDebug` — must complete with zero errors
4. **STT models**: Downloaded (see Step 1 below)
5. **LLM model**: At least one GGUF variant downloaded or pushed via adb

---

## Step 1: Download STT Model

Open the app → Setup screen (shown automatically on first launch if STT models are missing).

Expected behaviour:
- Progress bar shows download of 4 files (~200 MB total)
- Log filter: `adb logcat -s ModelDownloadService` shows `Downloading encoder.int8.onnx ... 100%`
- After completion, "Start Session" button becomes active

Manual push (optional, for development speed):
```bash
adb shell mkdir -p /sdcard/Android/data/com.meetmind.assistant/files/models/stt/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8
adb push encoder.int8.onnx /sdcard/Android/data/com.meetmind.assistant/files/models/stt/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/
adb push decoder.int8.onnx /sdcard/Android/data/com.meetmind.assistant/files/models/stt/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/
adb push joiner.int8.onnx /sdcard/Android/data/com.meetmind.assistant/files/models/stt/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/
adb push tokens.txt /sdcard/Android/data/com.meetmind.assistant/files/models/stt/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/
```

---

## Step 2: Verify Hilt DI (Build Smoke Test)

```bash
./gradlew assembleDebug 2>&1 | grep -E "(error|Hilt|FAILED|BUILD)"
```

Expected: `BUILD SUCCESSFUL` — no missing binding errors.

```bash
./gradlew testDebugUnitTest
```

Expected: All 44 spec-008 contract tests pass. New spec-009 contract tests also pass.

---

## Step 3: STT Real-Time Transcription Test

1. Open app → Start Session
2. Speak clearly: **"Alice will finish the report by Friday"**
3. Wait ≤ 2s

**Expected in logcat** (`adb logcat -s SherpaOnnxDataSource`):
```
D/SherpaOnnxDataSource: VAD speech detected
D/SherpaOnnxDataSource: decode() complete — "alice will finish the report by friday" (1234ms)
D/AudioProcessingForegroundService: onTranscriptSegment → appended to buffer
```

**Expected in UI**: Transcript segment appears in session screen OR analysis card fires within next cadence tick.

**Latency target**: text visible ≤ 2s after utterance ends.

---

## Step 4: Analysis Cadence + STT Integration Test

1. Start a session
2. Speak: **"We decided to go with the React approach"**
3. Wait ≤ 22s (20s cadence + 2s analysis latency)

**Expected**: `AnalysisEvent.Decision` card appears ("✅ Decision" label).

**Logcat filter**:
```bash
adb logcat -s CadenceController,ConversationAnalyzer,SherpaOnnxDataSource
```

---

## Step 5: Model Variant Selection Test

1. Open Settings → On-Device Model
2. Observe: **Y700 Gen 3 shows "Recommended" badge on Q4_K_M** (12 GB RAM, Android 14)
3. Tap **Q8_0** → Download (if not present) OR select if already downloaded
4. Observe download progress bar (if downloading)
5. After selection: tap Back → Start a new session
6. Speak a question: **"What is the deadline for this project?"**

**Expected**: Inference uses the Q8_0 model (verify via logcat):
```bash
adb logcat -s OnDeviceLlamaProvider
# Expected: "Loading model: .../gemma-4-e4b-it-Q8_0.gguf"
```

**Logcat filter for model switching**:
```bash
adb logcat -s OnDeviceLlamaProvider
```
```
D/OnDeviceLlamaProvider: Unloading model (path change detected)
D/OnDeviceLlamaProvider: Loading: /storage/.../models/gemma-4-e4b-it-Q8_0.gguf
D/OnDeviceLlamaProvider: Model ready (ttLoad=8432ms)
```

---

## Step 6: Download Resume Test

1. Start a model download (Q8_0 if not present)
2. After ~30 seconds (partial download), background the app OR pull the ADB cable briefly
3. Resume the app
4. Observe download resumes from offset (not from 0%)

**Logcat filter**:
```bash
adb logcat -s ModelDownloadManager
# Expected: "Resuming download from byte 52428800 (Range: bytes=52428800-)"
```

---

## Step 7: Honor Magic 6 Pro Verification

Repeat Steps 3–5 on Honor Magic 6 Pro. Expected identical behaviour — same SoC, same model recommendation.

---

## Logcat Tags Reference

| Tag | Component |
|-----|-----------|
| `SherpaOnnxDataSource` | STT pipeline (VAD, decode, timing) |
| `AudioProcessingForegroundService` | ASR segment injection, cloud routing |
| `OnDeviceLlamaProvider` | Model load/unload, inference timing |
| `ModelDownloadManager` | HTTP download progress, resume |
| `ModelDownloadService` | Foreground service lifecycle |
| `CadenceController` | Analysis ticks |
| `ConversationAnalyzer` | Classification, LLM calls |
| `HiltComponent` | Hilt graph initialization (debug only) |
