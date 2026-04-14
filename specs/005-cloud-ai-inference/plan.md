# Implementation Plan: Cloud AI Inference Mode

**Branch**: `feature/005-cloud-ai-inference` | **Date**: 2026-04-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/005-cloud-ai-inference/spec.md`

## Summary

Add an opt-in Cloud AI inference mode that routes detected questions to either the
Google Gemini API (`gemini-2.5-flash`) or Anthropic Claude API
(`claude-haiku-4-5-20251001`) via the user's personal subscription key, streams the
response tokens in real time, and falls back to the on-device Phi-3-mini engine on
failure or timeout. API keys are stored using Tink + Android Keystore encryption.
On-device inference (spec 004) remains the default and always-available fallback.

## Technical Context

**Language/Version**: Kotlin 1.9 (JVM target 17); Java interop for Anthropic Java SDK and Tink
**Primary Dependencies**: Firebase AI Logic SDK (`firebase-ai:17.0.0`), Anthropic Java SDK (`anthropic-java:2.20.0`), OkHttp 4.12.0, Tink Android 1.15.0, DataStore Preferences 1.1.1; all prior deps from spec 004 retained
**Storage**: DataStore Preferences (Tink-encrypted) for CloudProviderConfig + API key ciphertext; Android Keystore for Tink master key; Room DB unchanged
**Testing**: Android instrumented tests (JUnit4 + Espresso) for key storage and Settings UI; unit tests (JUnit5 + MockK) for CloudInferenceEngine routing logic, fallback triggers, timeout handling
**Target Platform**: Android API 23+ for Tink hardware-backed keys; API 21 still declared minimum; cloud config shows warning on API 21-22
**Project Type**: Mobile app (APK-only, sideloaded)
**Performance Goals**: Cloud path first-token ≤3s (Gemini ~400-800ms TTFT; Claude Haiku 4.5 ~600ms TTFT); hard 5s fallback timeout; streaming display so first token visible ≤1s
**Constraints**: INTERNET permission now declared; raw audio never transmitted; API key plaintext never cached beyond a single call; google-services.json excluded from source control; Tink keyset and DataStore excluded from Android cloud backup; maxTokens=200 on all cloud requests; foregroundServiceType updated to microphone|dataSync

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Privacy-First Inference | ✅ PASS | Cloud mode opt-in (isEnabled defaults false). Only question text + role + mode sent (≤430 tokens). Raw audio never leaves device. CloudInferenceEngine enforces data minimisation at contract layer. Persistent cloud badge always shown when active. |
| II. Real-Time Responsiveness | ✅ PASS | Gemini TTFT ~400-800ms; Claude Haiku 4.5 TTFT ~600ms. Streaming display means first token visible ≤1s. Hard 5s fallback timeout ensures session never stalls. |
| III. APK Distribution | ✅ PASS | No Play Services dependency. Firebase AI Logic does not require Play Services for Gemini Developer API backend. Anthropic SDK is pure JVM. APK-only distribution unchanged. |
| IV. Minimal Permissions Footprint | ✅ PASS | INTERNET now declared (permitted by constitution v2.0.0 amendment, Principle IV). FOREGROUND_SERVICE_DATA_SYNC added for API calls from the service. Both documented in spec FR-013 and manifest contract. |
| V. Incremental, Demo-Able Slices | ✅ PASS | US1 (key config) → US2 (toggle + badge) → US3 (cloud suggestion) → US4 (fallback) independently testable. On-device path (spec 004) always runnable. |
| VI. Cloud Inference Mode | ✅ PASS | Gemini + Claude only. User-owned keys in Tink EncryptedDataStore. Data minimisation enforced in CloudInferenceEngine contract. Streaming mandatory. 5s fallback mandatory. Cloud badge mandatory. 401 auto-disable implemented. |

*Post-design re-check: All principles pass. No violations.*

## Project Structure

### Documentation (this feature)

```text
specs/005-cloud-ai-inference/
├── plan.md                          # This file
├── research.md                      # Phase 0 output
├── data-model.md                    # Phase 1 output
├── quickstart.md                    # Phase 1 output
├── contracts/
│   └── cloud-inference-api.md       # Phase 1 output
└── tasks.md                         # Phase 2 output (/speckit.tasks)
```

### Source Code additions (repository root)

```text
app/src/main/kotlin/com/meetmind/assistant/
├── data/
│   └── model/
│       ├── CloudProvider.kt           # NEW enum: GEMINI, CLAUDE
│       ├── CloudProviderConfig.kt     # NEW data class
│       ├── CloudInferenceRequest.kt   # NEW data class
│       ├── InferenceMode.kt           # NEW enum: ON_DEVICE, CLOUD
│       ├── InferenceEvent.kt          # NEW sealed class
│       ├── FallbackReason.kt          # NEW enum
│       ├── CloudBadgeState.kt         # NEW enum
│       └── Suggestion.kt              # EXTENDED: + inferenceMode, cloudRequestId
├── storage/
│   ├── ApiKeyStore.kt                 # NEW: Tink + DataStore encrypted key storage
│   └── CloudProviderConfigRepository.kt  # NEW: reactive config persistence
├── inference/
│   ├── CloudInferenceEngine.kt        # NEW: routes to Gemini/Claude; on-device fallback
│   ├── GeminiInferenceClient.kt       # NEW: Firebase AI Logic streaming wrapper
│   ├── ClaudeInferenceClient.kt       # NEW: Anthropic SDK streaming wrapper
│   ├── CloudKeyValidationService.kt   # NEW: lightweight key validation
│   └── SuggestionEngine.kt            # EXISTING (spec 004): now fallback only
├── ui/
│   ├── screens/
│   │   ├── SessionScreen.kt           # UPDATED: cloud badge, InferenceMode display
│   │   ├── HomeScreen.kt              # UPDATED: cloud toggle
│   │   └── CloudSettingsScreen.kt     # NEW: provider picker, key entry, status
│   └── components/
│       ├── SuggestionCard.kt          # UPDATED: InferenceMode badge
│       └── CloudBadge.kt              # NEW: reusable cloud/fallback badge component
├── service/
│   └── AudioProcessingForegroundService.kt  # UPDATED: dataSync type; CloudInferenceEngine
└── viewmodel/
    └── SessionViewModel.kt            # UPDATED: cloudBadgeState, currentSuggestionTokens

app/src/androidTest/
├── storage/ApiKeyStoreTest.kt         # NEW
└── ui/CloudSettingsScreenTest.kt      # NEW

app/src/test/
├── inference/CloudInferenceEngineTest.kt     # NEW
└── inference/CloudKeyValidationServiceTest.kt  # NEW
```

**Structure Decision**: Single Android project. Cloud inference added as a new
`inference/` package alongside `pipeline/` from spec 004. No new modules.

## Complexity Tracking

> No constitution violations.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| — | — | — |
