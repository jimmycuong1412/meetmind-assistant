# meetmind-assistant Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-14

## Active Technologies
- Kotlin 1.9 (JVM target 17); Java interop for native JNI bindings + Vosk Android SDK 0.3.47, TFLite 2.14 (Silero VAD), llama.cpp (JNI, Phi-3-mini Q4_K_M), Room 2.6, Jetpack Compose BOM 2024.02 (feature/004-question-detection)
- Room DB (ContextProfile only); session/suggestion data in-memory only (feature/004-question-detection)
- Kotlin 1.9 (JVM target 17); Java interop for Anthropic Java SDK and Tink + Firebase AI Logic SDK (`firebase-ai:17.0.0`), Anthropic Java SDK (`anthropic-java:2.20.0`), OkHttp 4.12.0, Tink Android 1.15.0, DataStore Preferences 1.1.1; all prior deps from spec 004 retained (feature/005-cloud-ai-inference)
- DataStore Preferences (Tink-encrypted) for CloudProviderConfig + API key ciphertext; Android Keystore for Tink master key; Room DB unchanged (feature/005-cloud-ai-inference)

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

## Recent Changes
- feature/005-cloud-ai-inference: Added Kotlin 1.9 (JVM target 17); Java interop for Anthropic Java SDK and Tink + Firebase AI Logic SDK (`firebase-ai:17.0.0`), Anthropic Java SDK (`anthropic-java:2.20.0`), OkHttp 4.12.0, Tink Android 1.15.0, DataStore Preferences 1.1.1; all prior deps from spec 004 retained
- feature/004-question-detection: Added Kotlin 1.9 (JVM target 17); Java interop for native JNI bindings + Vosk Android SDK 0.3.47, TFLite 2.14 (Silero VAD), llama.cpp (JNI, Phi-3-mini Q4_K_M), Room 2.6, Jetpack Compose BOM 2024.02

- feature/004-question-detection: Added [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION] + [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
