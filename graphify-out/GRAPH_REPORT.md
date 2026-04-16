# Graph Report - .  (2026-04-16)

## Corpus Check
- Corpus is ~40,263 words - fits in a single context window. You may not need a graph.

## Summary
- 376 nodes · 370 edges · 58 communities detected
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 15 edges (avg confidence: 0.86)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Audio Pipeline Contracts|Audio Pipeline Contracts]]
- [[_COMMUNITY_Cloud Inference Services|Cloud Inference Services]]
- [[_COMMUNITY_Tech Stack & Feature Branches|Tech Stack & Feature Branches]]
- [[_COMMUNITY_Cloud Provider Data Models|Cloud Provider Data Models]]
- [[_COMMUNITY_API Key Store Interface|API Key Store Interface]]
- [[_COMMUNITY_SDK Research & Decisions|SDK Research & Decisions]]
- [[_COMMUNITY_Cloud Settings ViewModel|Cloud Settings ViewModel]]
- [[_COMMUNITY_Inference Engine Tests|Inference Engine Tests]]
- [[_COMMUNITY_Network Audit Tests|Network Audit Tests]]
- [[_COMMUNITY_Feature Specs 001-003|Feature Specs 001-003]]
- [[_COMMUNITY_Config Repository|Config Repository]]
- [[_COMMUNITY_Tink Key Store Tests|Tink Key Store Tests]]
- [[_COMMUNITY_Audio Foreground Service|Audio Foreground Service]]
- [[_COMMUNITY_Config Repository Tests|Config Repository Tests]]
- [[_COMMUNITY_Settings ViewModel Tests|Settings ViewModel Tests]]
- [[_COMMUNITY_Fake API Key Store|Fake API Key Store]]
- [[_COMMUNITY_TTFT Tests|TTFT Tests]]
- [[_COMMUNITY_Cloud Inference Engine|Cloud Inference Engine]]
- [[_COMMUNITY_Key Validation Service|Key Validation Service]]
- [[_COMMUNITY_Session ViewModel|Session ViewModel]]
- [[_COMMUNITY_Inference Event Sealed Class|Inference Event Sealed Class]]
- [[_COMMUNITY_CloudProviderConfig Model|CloudProviderConfig Model]]
- [[_COMMUNITY_Clock Interface|Clock Interface]]
- [[_COMMUNITY_Home ViewModel|Home ViewModel]]
- [[_COMMUNITY_Fake Clock|Fake Clock]]
- [[_COMMUNITY_Claude Inference Client|Claude Inference Client]]
- [[_COMMUNITY_Cloud Badge Controller|Cloud Badge Controller]]
- [[_COMMUNITY_Cloud Streaming Provider Interface|Cloud Streaming Provider Interface]]
- [[_COMMUNITY_Gemini Inference Client|Gemini Inference Client]]
- [[_COMMUNITY_Session Screen UI|Session Screen UI]]
- [[_COMMUNITY_Fake Streaming Provider|Fake Streaming Provider]]
- [[_COMMUNITY_Fake Stream Response|Fake Stream Response]]
- [[_COMMUNITY_Cloud Badge UI Spec|Cloud Badge UI Spec]]
- [[_COMMUNITY_Application Entry Point|Application Entry Point]]
- [[_COMMUNITY_Main Activity|Main Activity]]
- [[_COMMUNITY_Cloud Badge Component|Cloud Badge Component]]
- [[_COMMUNITY_Navigation Graph|Navigation Graph]]
- [[_COMMUNITY_Cloud Settings Screen|Cloud Settings Screen]]
- [[_COMMUNITY_Fake Key Validation|Fake Key Validation]]
- [[_COMMUNITY_On-Device Latency Budget|On-Device Latency Budget]]
- [[_COMMUNITY_CloudBadgeState Model|CloudBadgeState Model]]
- [[_COMMUNITY_CloudInferenceRequest Model|CloudInferenceRequest Model]]
- [[_COMMUNITY_CloudProvider Enum|CloudProvider Enum]]
- [[_COMMUNITY_FallbackReason Enum|FallbackReason Enum]]
- [[_COMMUNITY_InferenceMode Enum|InferenceMode Enum]]
- [[_COMMUNITY_SessionMode Enum|SessionMode Enum]]
- [[_COMMUNITY_Suggestion Model|Suggestion Model]]
- [[_COMMUNITY_App Dependency Container|App Dependency Container]]
- [[_COMMUNITY_Home Screen UI|Home Screen UI]]
- [[_COMMUNITY_App Theme|App Theme]]
- [[_COMMUNITY_Fake Connectivity Checker|Fake Connectivity Checker]]
- [[_COMMUNITY_Test DataStore|Test DataStore]]
- [[_COMMUNITY_On-Device Privacy Principle|On-Device Privacy Principle]]
- [[_COMMUNITY_Root Build Config|Root Build Config]]
- [[_COMMUNITY_Settings Gradle|Settings Gradle]]
- [[_COMMUNITY_App Build Config|App Build Config]]
- [[_COMMUNITY_Phi-3 Model Download|Phi-3 Model Download]]
- [[_COMMUNITY_Spec 005 FR001|Spec 005 FR001]]

## God Nodes (most connected - your core abstractions)
1. `CloudInferenceEngine` - 18 edges
2. `CloudInferenceEngineTest` - 11 edges
3. `NetworkAuditTest` - 10 edges
4. `TinkApiKeyStoreTest` - 9 edges
5. `AudioProcessingForegroundService` - 8 edges
6. `CloudProviderConfigRepositoryTest` - 8 edges
7. `CloudSettingsViewModelTest` - 8 edges
8. `TinkApiKeyStore` - 7 edges
9. `FakeApiKeyStore` - 7 edges
10. `TtftTest` - 7 edges

## Surprising Connections (you probably didn't know these)
- `Suggestion (001)` --semantically_similar_to--> `Suggestion (Data Model)`  [INFERRED] [semantically similar]
  specs/001-realtime-mic-suggestions/spec.md → specs/004-question-detection/data-model.md
- `ContextProfile (Data Model)` --references--> `Room DB 2.6`  [EXTRACTED]
  specs/004-question-detection/data-model.md → CLAUDE.md
- `Plan 004: Technical Context` --references--> `Vosk Android SDK 0.3.47`  [EXTRACTED]
  specs/004-question-detection/plan.md → CLAUDE.md
- `ASR Decision: Vosk Android SDK` --references--> `Vosk Android SDK 0.3.47`  [EXTRACTED]
  specs/004-question-detection/research.md → CLAUDE.md
- `Plan 004: Technical Context` --references--> `TFLite 2.14 (Silero VAD)`  [EXTRACTED]
  specs/004-question-detection/plan.md → CLAUDE.md

## Hyperedges (group relationships)
- **On-Device Audio Processing Pipeline Flow** — contract_audio_capture_source, contract_vad_pipeline, contract_streaming_recogniser, contract_question_classifier, contract_suggestion_engine, contract_overlay_renderer [EXTRACTED 1.00]
- **Suggestion Generation Requires Mode + Profile + Detected Question** — datamodel_session_mode, datamodel_context_profile, datamodel_detected_question, datamodel_suggestion, datamodel_llm_prompt_schema [EXTRACTED 1.00]
- **Feature Dependency Chain: 001 â†’ 002 â†’ 003 â†’ 004** — spec001_feature, spec002_feature, spec003_feature, spec004_feature [EXTRACTED 1.00]
- **Cloud Inference + Timeout Fallback + On-Device Fallback Pipeline** — 005_cloud_inference_engine, 005_fr007, 005_fallback_reason_enum, 005_inference_event_sealed, 005_foreground_service_network [EXTRACTED 0.90]
- **Test Doubles Injected into CloudInferenceEngine for JVM Testing** — 005_cloud_inference_engine, 006_fakecloudstreamingprovider, 006_fakeclock, 006_fakeconnectivitychecker, 006_cloud_inference_engine_test [EXTRACTED 0.92]
- **API Key Security Chain (Tink + Keystore + DataStore)** — 005_api_key_store, 005_tink_android_1_15_0, 005_android_keystore, 005_datastore_preferences_1_1_1, 005_cloudproviderconfig [EXTRACTED 0.93]

## Communities

### Community 0 - "Audio Pipeline Contracts"
Cohesion: 0.08
Nodes (33): Room DB 2.6, AudioCaptureSource Contract, OverlayRenderer Contract, QuestionClassifier Contract, StreamingRecogniser Contract, SuggestionEngine Contract, VadPipeline Contract, AudioChunk (Data Model) (+25 more)

### Community 1 - "Cloud Inference Services"
Cohesion: 0.1
Nodes (32): ApiKeyStore Interface, AudioProcessingForegroundService (updated), ClaudeInferenceClient, CloudInferenceEngine, CloudKeyValidationService, CloudProviderConfigRepository Interface, CloudSettingsScreen (Compose UI), CloudSettingsViewModel (+24 more)

### Community 2 - "Tech Stack & Feature Branches"
Cohesion: 0.1
Nodes (23): Anthropic Java SDK (anthropic-java:2.20.0), Jetpack Compose BOM 2024.02, DataStore Preferences 1.1.1, Feature 004: Question Detection, Feature 005: Cloud AI Inference, Firebase AI Logic SDK (firebase-ai:17.0.0), Kotlin 1.9 (JVM target 17), Kotlin 2.0.21 (JVM target 17) (+15 more)

### Community 3 - "Cloud Provider Data Models"
Cohesion: 0.14
Nodes (17): Android Keystore (hardware-backed), Cloud AI Inference Mode (Feature 005), CloudInferenceRequest, CloudProvider Enum (GEMINI, CLAUDE), CloudProviderConfig, ConnectionStatus Enum, DataStore Preferences 1.1.1, FR-002: Encrypted hardware-backed API key storage (+9 more)

### Community 4 - "API Key Store Interface"
Cohesion: 0.13
Nodes (2): ApiKeyStore, TinkApiKeyStore

### Community 5 - "SDK Research & Decisions"
Cohesion: 0.15
Nodes (15): Anthropic Java SDK (anthropic-java:2.20.0), Claude Haiku Model (claude-haiku-4-5-20251001), Claude Prompt (Anthropic SDK), Firebase AI Logic SDK (firebase-ai:17.0.0), FR-005: Data minimisation (question text â‰¤150 tokens only), Gemini Model (gemini-2.5-flash), Gemini Prompt (Firebase AI Logic), LLM Prompt Schema (Cloud Path) (+7 more)

### Community 6 - "Cloud Settings ViewModel"
Cohesion: 0.15
Nodes (7): CloudSettingsViewModel, Factory, Failure, Idle, Success, Validating, ValidationState

### Community 7 - "Inference Engine Tests"
Cohesion: 0.17
Nodes (1): CloudInferenceEngineTest

### Community 8 - "Network Audit Tests"
Cohesion: 0.18
Nodes (1): NetworkAuditTest

### Community 9 - "Feature Specs 001-003"
Cohesion: 0.22
Nodes (11): Feature: Real-Time Mic Suggestions, Rationale: Core loop is entire product (P1), Spec 001 Requirements Checklist, Feature: Floating and Full-Screen Views, Display Over Other Apps Permission, Rationale: Floating mode is primary use-case differentiator, Spec 002 Requirements Checklist, Feature: Session Modes and Context Profiles (+3 more)

### Community 10 - "Config Repository"
Cohesion: 0.2
Nodes (1): CloudProviderConfigRepository

### Community 11 - "Tink Key Store Tests"
Cohesion: 0.2
Nodes (1): TinkApiKeyStoreTest

### Community 12 - "Audio Foreground Service"
Cohesion: 0.22
Nodes (1): AudioProcessingForegroundService

### Community 13 - "Config Repository Tests"
Cohesion: 0.22
Nodes (1): CloudProviderConfigRepositoryTest

### Community 14 - "Settings ViewModel Tests"
Cohesion: 0.22
Nodes (1): CloudSettingsViewModelTest

### Community 15 - "Fake API Key Store"
Cohesion: 0.25
Nodes (1): FakeApiKeyStore

### Community 16 - "TTFT Tests"
Cohesion: 0.25
Nodes (1): TtftTest

### Community 17 - "Cloud Inference Engine"
Cohesion: 0.29
Nodes (2): CloudInferenceEngine, OnDeviceFallback

### Community 18 - "Key Validation Service"
Cohesion: 0.29
Nodes (2): CloudKeyValidationService, ValidationResult

### Community 19 - "Session ViewModel"
Cohesion: 0.29
Nodes (2): Factory, SessionViewModel

### Community 20 - "Inference Event Sealed Class"
Cohesion: 0.33
Nodes (5): Complete, Error, FallbackActivated, InferenceEvent, Token

### Community 21 - "CloudProviderConfig Model"
Cohesion: 0.4
Nodes (1): CloudProviderConfig

### Community 22 - "Clock Interface"
Cohesion: 0.4
Nodes (2): Clock, SystemClock

### Community 23 - "Home ViewModel"
Cohesion: 0.4
Nodes (2): Factory, HomeViewModel

### Community 24 - "Fake Clock"
Cohesion: 0.4
Nodes (1): FakeClock

### Community 25 - "Claude Inference Client"
Cohesion: 0.5
Nodes (1): ClaudeInferenceClient

### Community 26 - "Cloud Badge Controller"
Cohesion: 0.5
Nodes (1): CloudBadgeController

### Community 27 - "Cloud Streaming Provider Interface"
Cohesion: 0.5
Nodes (1): CloudStreamingProvider

### Community 28 - "Gemini Inference Client"
Cohesion: 0.5
Nodes (1): GeminiInferenceClient

### Community 29 - "Session Screen UI"
Cohesion: 0.5
Nodes (0): 

### Community 30 - "Fake Streaming Provider"
Cohesion: 0.5
Nodes (1): FakeCloudStreamingProvider

### Community 31 - "Fake Stream Response"
Cohesion: 0.5
Nodes (1): FakeStreamResponse

### Community 32 - "Cloud Badge UI Spec"
Cohesion: 0.5
Nodes (4): CloudBadge Compose Component, CloudBadgeController, CloudBadgeState (HIDDEN, CLOUD_ACTIVE, FALLBACK), SessionViewModel (updated with cloudBadgeState)

### Community 33 - "Application Entry Point"
Cohesion: 0.67
Nodes (1): MeetMindApplication

### Community 34 - "Main Activity"
Cohesion: 0.67
Nodes (1): MainActivity

### Community 35 - "Cloud Badge Component"
Cohesion: 0.67
Nodes (0): 

### Community 36 - "Navigation Graph"
Cohesion: 0.67
Nodes (1): Routes

### Community 37 - "Cloud Settings Screen"
Cohesion: 0.67
Nodes (0): 

### Community 38 - "Fake Key Validation"
Cohesion: 0.67
Nodes (1): FakeCloudKeyValidationService

### Community 39 - "On-Device Latency Budget"
Cohesion: 0.67
Nodes (3): Tuning Notes (VAD/LLM/GPU), Pipeline Latency Budget (Snapdragon 8 Gen 3), Snapdragon 8 Gen 3 Hardware Capability

### Community 40 - "CloudBadgeState Model"
Cohesion: 1.0
Nodes (1): CloudBadgeState

### Community 41 - "CloudInferenceRequest Model"
Cohesion: 1.0
Nodes (1): CloudInferenceRequest

### Community 42 - "CloudProvider Enum"
Cohesion: 1.0
Nodes (1): CloudProvider

### Community 43 - "FallbackReason Enum"
Cohesion: 1.0
Nodes (1): FallbackReason

### Community 44 - "InferenceMode Enum"
Cohesion: 1.0
Nodes (1): InferenceMode

### Community 45 - "SessionMode Enum"
Cohesion: 1.0
Nodes (1): SessionMode

### Community 46 - "Suggestion Model"
Cohesion: 1.0
Nodes (1): Suggestion

### Community 47 - "App Dependency Container"
Cohesion: 1.0
Nodes (1): AppContainer

### Community 48 - "Home Screen UI"
Cohesion: 1.0
Nodes (0): 

### Community 49 - "App Theme"
Cohesion: 1.0
Nodes (0): 

### Community 50 - "Fake Connectivity Checker"
Cohesion: 1.0
Nodes (1): FakeConnectivityChecker

### Community 51 - "Test DataStore"
Cohesion: 1.0
Nodes (0): 

### Community 52 - "On-Device Privacy Principle"
Cohesion: 1.0
Nodes (2): Plan 004: Constitution Check, On-Device Only Principle (spec001)

### Community 53 - "Root Build Config"
Cohesion: 1.0
Nodes (0): 

### Community 54 - "Settings Gradle"
Cohesion: 1.0
Nodes (0): 

### Community 55 - "App Build Config"
Cohesion: 1.0
Nodes (0): 

### Community 56 - "Phi-3 Model Download"
Cohesion: 1.0
Nodes (1): Phi-3-mini Model First-Launch Download

### Community 57 - "Spec 005 FR001"
Cohesion: 1.0
Nodes (1): FR-001: Single cloud provider at a time

## Knowledge Gaps
- **72 isolated node(s):** `CloudBadgeState`, `CloudInferenceRequest`, `CloudProvider`, `FallbackReason`, `InferenceEvent` (+67 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `CloudBadgeState Model`** (2 nodes): `CloudBadgeState.kt`, `CloudBadgeState`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `CloudInferenceRequest Model`** (2 nodes): `CloudInferenceRequest.kt`, `CloudInferenceRequest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `CloudProvider Enum`** (2 nodes): `CloudProvider.kt`, `CloudProvider`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `FallbackReason Enum`** (2 nodes): `FallbackReason.kt`, `FallbackReason`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `InferenceMode Enum`** (2 nodes): `InferenceMode.kt`, `InferenceMode`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `SessionMode Enum`** (2 nodes): `SessionMode.kt`, `SessionMode`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Suggestion Model`** (2 nodes): `Suggestion.kt`, `Suggestion`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `App Dependency Container`** (2 nodes): `AppContainer.kt`, `AppContainer`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Home Screen UI`** (2 nodes): `HomeScreen.kt`, `HomeScreen()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `App Theme`** (2 nodes): `Theme.kt`, `MeetMindTheme()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Fake Connectivity Checker`** (2 nodes): `FakeConnectivityChecker.kt`, `FakeConnectivityChecker`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test DataStore`** (2 nodes): `TestDataStore.kt`, `testDataStore()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `On-Device Privacy Principle`** (2 nodes): `Plan 004: Constitution Check`, `On-Device Only Principle (spec001)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Root Build Config`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Settings Gradle`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `App Build Config`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Phi-3 Model Download`** (1 nodes): `Phi-3-mini Model First-Launch Download`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Spec 005 FR001`** (1 nodes): `FR-001: Single cloud provider at a time`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CloudInferenceEngine` connect `Cloud Inference Services` to `Cloud Provider Data Models`, `SDK Research & Decisions`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **Why does `Feature 004: Question Detection` connect `Tech Stack & Feature Branches` to `Audio Pipeline Contracts`?**
  _High betweenness centrality (0.010) - this node is a cross-community bridge._
- **What connects `CloudBadgeState`, `CloudInferenceRequest`, `CloudProvider` to the rest of the system?**
  _72 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Audio Pipeline Contracts` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `Cloud Inference Services` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Tech Stack & Feature Branches` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Cloud Provider Data Models` be split into smaller, more focused modules?**
  _Cohesion score 0.14 - nodes in this community are weakly interconnected._