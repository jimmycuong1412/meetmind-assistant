# meetmind-assistant Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-22

## Active Technologies
- Kotlin 1.9 (JVM target 17); Java interop for native JNI bindings + Vosk Android SDK 0.3.47, TFLite 2.14 (Silero VAD), llama.cpp (JNI, Phi-3-mini Q4_K_M), Room 2.6, Jetpack Compose BOM 2024.02 (feature/004-question-detection)
- Room DB (ContextProfile only); session/suggestion data in-memory only (feature/004-question-detection)
- Kotlin 1.9 (JVM target 17); Java interop for Anthropic Java SDK and Tink + Firebase AI Logic SDK (`firebase-ai:17.0.0`), Anthropic Java SDK (`anthropic-java:2.20.0`), OkHttp 4.12.0, Tink Android 1.15.0, DataStore Preferences 1.1.1; all prior deps from spec 004 retained (feature/005-cloud-ai-inference)
- DataStore Preferences (Tink-encrypted) for CloudProviderConfig + API key ciphertext; Android Keystore for Tink master key; Room DB unchanged (feature/005-cloud-ai-inference)
- Kotlin 2.0.21 (JVM target 17) (feature/005-cloud-ai-inference)
- In-memory DataStore (test artifact from `datastore-preferences`); no file I/O in tests (feature/005-cloud-ai-inference)
- llama.cpp JNI (pre-built `liballama.so` + `libggml*.so`, arm64-v8a, OpenCL + Adreno kernels); Gemma 4 E4B IT Q4_K_M (~5 GB GGUF, `nGpuLayers = -1`, `nThreads = 6`, `contextSize = 8192`); `ModelConfigRepository` (DataStore Preferences); `OnDeviceLlamaProvider` (callbackFlow streaming, data minimisation 600/320 chars) (feature/007-on-device-gemma4)
- Kotlin 2.0.21 (JVM target 17) — same as existing codebase (feature/008-continuous-conversation-analysis)
- Sherpa-ONNX JNI (`libsherpa-onnx-jni.so` + `libonnxruntime.so`, arm64-v8a); Nemo Parakeet TDT 0.6B v3 Int8 ASR; Silero VAD (`silero_vad.onnx` asset); `SherpaOnnxDataSource` (16 kHz, mutex-serialized decode, 2 threads); Hilt 2.51 + `hilt-navigation-compose:1.1.0` replacing `AppContainer` manual DI; `GemmaModelVariant` enum (Q4_K_M/Q8_0/IQ4_NL); `ModelDownloadManager` (HTTP resume via Range header); `DeviceTierDetector` (RAM ≥ 8 GB + SDK ≥ 33 → Q4_K_M); `ModelDownloadService` (foreground DATA_SYNC) (feature/009-sherpa-onnx-stt-hilt-upgrade)
- DataStore Preferences only (`analysis_enabled`, `analysis_interval_s`, `analysis_window_s`). Room DB is unchanged. (feature/009-sherpa-onnx-stt-hilt-upgrade)

- [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION] + [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION] (feature/004-question-detection)

## Project Structure

```text
backend/
frontend/
tests/
```

## Commands

cd src; pytest; ruff check .

## Code Style

[e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]: Follow standard conventions

## STT Model — Development Quick Push

Skip the in-app download during development by pushing pre-downloaded files directly:

```bash
STT_DIR=/sdcard/Android/data/com.meetmind.assistant/files/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8
adb shell mkdir -p $STT_DIR
adb push encoder.int8.onnx  $STT_DIR/
adb push decoder.int8.onnx  $STT_DIR/
adb push joiner.int8.onnx   $STT_DIR/
adb push tokens.txt         $STT_DIR/
```

Files from: https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/

## Logcat Quick Reference (spec 009)

```bash
adb logcat -s SherpaOnnxDataSource,AudioProcessingService,ModelDownloadManager,ModelDownloadService,DeviceTierDetector,OnDeviceLlamaProvider
```

## Recent Changes
- feature/009-sherpa-onnx-stt-hilt-upgrade: Added Kotlin 2.0.21 (JVM target 17)
- feature/009-sherpa-onnx-stt-hilt-upgrade: Full implementation — Sherpa-ONNX Nemo Parakeet TDT 0.6B v3 ASR + Silero VAD; Hilt 2.51 full DI migration (replaces AppContainer); GemmaModelVariant enum (Q4_K_M/Q8_0/IQ4_NL) with ModelDownloadManager + DeviceTierDetector; ModelDownloadService (foreground DATA_SYNC); SttRepository interface + SttRepositoryImpl + SttModule; ModelSetupScreen multi-variant UI with STT download section; SESSION route guard
- feature/008-continuous-conversation-analysis: Added Kotlin 2.0.21 (JVM target 17) — same as existing codebase


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- After modifying code files in this session, run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"` to keep the graph current
