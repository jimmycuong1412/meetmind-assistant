# Graph Report - .  (2026-04-17)

## Corpus Check
- 99 files · ~200,000 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 439 nodes · 497 edges · 48 communities detected
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 37 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Spec 007 Contracts + Graphify Integration|Spec 007 Contracts + Graphify Integration]]
- [[_COMMUNITY_Cloud Inference + Audio Pipeline Concepts|Cloud Inference + Audio Pipeline Concepts]]
- [[_COMMUNITY_WiFi Connectivity Contracts|WiFi Connectivity Contracts]]
- [[_COMMUNITY_Feature 001 Realtime Mic Spec|Feature 001 Realtime Mic Spec]]
- [[_COMMUNITY_Data Minimisation Contracts|Data Minimisation Contracts]]
- [[_COMMUNITY_Audio Capture + VAD Concepts|Audio Capture + VAD Concepts]]
- [[_COMMUNITY_Feature 007 Tech Stack (CLAUDE.md)|Feature 007 Tech Stack (CLAUDE.md)]]
- [[_COMMUNITY_OnDeviceLlamaProvider Tests|OnDeviceLlamaProvider Tests]]
- [[_COMMUNITY_NDK Build + Hardware Setup|NDK Build + Hardware Setup]]
- [[_COMMUNITY_AppContainer + Inference Clients|AppContainer + Inference Clients]]
- [[_COMMUNITY_API Key Store + Nav Graph|API Key Store + Nav Graph]]
- [[_COMMUNITY_CloudInferenceEngine Tests|CloudInferenceEngine Tests]]
- [[_COMMUNITY_ModelConfigRepository Tests|ModelConfigRepository Tests]]
- [[_COMMUNITY_ModelSetupViewModel|ModelSetupViewModel]]
- [[_COMMUNITY_API Key Contracts|API Key Contracts]]
- [[_COMMUNITY_ModelConfigRepository Impl|ModelConfigRepository Impl]]
- [[_COMMUNITY_CloudInferenceEngine Impl|CloudInferenceEngine Impl]]
- [[_COMMUNITY_SessionViewModel|SessionViewModel]]
- [[_COMMUNITY_ModelSetupViewModel Tests|ModelSetupViewModel Tests]]
- [[_COMMUNITY_ModelLoadState Sealed Class|ModelLoadState Sealed Class]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]

## God Nodes (most connected - your core abstractions)
1. `Feature 007: On-Device Gemma 4 E4B Inference Spec` - 22 edges
2. `OnDeviceLlamaProviderTest` - 17 edges
3. `CloudInferenceEngine` - 17 edges
4. `CloudInferenceEngineTest` - 12 edges
5. `ModelConfigRepositoryTest` - 12 edges
6. `Feature 007: On-Device Gemma 4 E4B Inference` - 11 edges
7. `Graph Report Overview` - 11 edges
8. `CloudStreamingProvider Interface` - 9 edges
9. `Native Library Build Instructions` - 9 edges
10. `CloudProvider Enum` - 7 edges

## Surprising Connections (you probably didn't know these)
- `QA Scenario S7.1: Long Question Truncated` --references--> `NetworkAuditTest`  [EXTRACTED]
  docs/test-scenarios.md → app/src/test/kotlin/com/meetmind/assistant/inference/NetworkAuditTest.kt
- `QA Scenario S7.2: No Audio Data in Outbound Requests` --references--> `NetworkAuditTest`  [EXTRACTED]
  docs/test-scenarios.md → app/src/test/kotlin/com/meetmind/assistant/inference/NetworkAuditTest.kt
- `Contract 5.1: No Audio Bytes in Claude Request` --conceptually_related_to--> `FR-009: All Processing On-Device (spec001)`  [INFERRED]
  app/src/test/kotlin/com/meetmind/assistant/inference/NetworkAuditTest.kt → specs/001-realtime-mic-suggestions/spec.md
- `Contract 5.4: No User Identity Fields in Request` --conceptually_related_to--> `FR-009: All Processing On-Device (spec001)`  [INFERRED]
  app/src/test/kotlin/com/meetmind/assistant/inference/NetworkAuditTest.kt → specs/001-realtime-mic-suggestions/spec.md
- `QA Scenario S9.1: TTFT Within 5 Seconds` --references--> `TtftTest`  [EXTRACTED]
  docs/test-scenarios.md → app/src/test/kotlin/com/meetmind/assistant/inference/TtftTest.kt

## Hyperedges (group relationships)
- **Cloud Provider Config and Security** — model_cloudproviderconfig, model_cloudprovider, inference_cloudkeyvalidationservice [INFERRED 0.85]
- **Suggestion Inference Metadata** — model_suggestion, model_inferencemode, model_fallbackreason [EXTRACTED 0.92]
- **Cloud Settings MVVM Flow (Screen to ViewModel to Store/Repo)** — cloudsettingsscreen_CloudSettingsScreen, cloudsettingsviewmodel_CloudSettingsViewModel, apikeystore_ApiKeyStore, cloudproviderconfigrepository_CloudProviderConfigRepository [EXTRACTED 0.95]
- **Session Inference Display Flow (ViewModel to Badge to Screen)** — sessionviewmodel_SessionViewModel, cloudbadge_CloudBadge, sessionscreen_SessionScreen, sessionscreen_SuggestionCard [EXTRACTED 0.95]
- **Test Doubles for Inference Layer** — fakeapikeystore_FakeApiKeyStore, fakecloudkeyvalidationservice_FakeCloudKeyValidationService, fakecloudstreamingprovider_FakeCloudStreamingProvider, fakeconnectivitychecker_FakeConnectivityChecker [INFERRED 0.85]
- **Data Minimisation Contracts Verified by Network Audit + Engine Tests** — contract5_no_audio_bytes, contract5_question_truncated_600, contract5_no_user_identity, inference_networkaudittest, contract3_question_truncation_600 [INFERRED 0.90]
- **QA Manual Scenarios Map to Automated JVM Tests** — docs_testscenarios, qa_appmanualscenariostest, inference_cloudinferenceenginetest, inference_networkaudittest, inference_ttfttest, storage_tinkapikeystore_test, viewmodel_cloudsettingsviewmodeltest [EXTRACTED 1.00]
- **Feature Spec Dependency Chain 001 to 004** — spec001_feature, spec002_feature, spec003_feature, spec004_plan [EXTRACTED 1.00]
- **On-Device Audio Processing Pipeline Flow** — concept_audio_capture_source, concept_vad_pipeline, concept_question_classifier, concept_suggestion_engine [EXTRACTED 0.95]
- **Cloud Inference with On-Device Fallback Pattern** — concept_cloud_inference_engine, concept_suggestion_engine, concept_fallback_reason [EXTRACTED 0.95]
- **Test Infrastructure: Fake Injection into CloudInferenceEngine** — concept_fake_cloud_streaming_provider, concept_fake_clock, concept_fake_connectivity_checker [EXTRACTED 0.90]
- **Feature 007 On-Device Gemma 4 Technology Stack** — claudemd_feature_007, claudemd_llamacpp_jni, claudemd_gemma4_model, claudemd_opencl_adreno, claudemd_modelconfigrepo_tech, claudemd_ondevice_llama_provider_tech, claudemd_model_setup_vm_tech, claudemd_trim_memory_hook [EXTRACTED 1.00]
- **Spec 007 Key Entities** — spec007_entity_model_config, spec007_entity_model_load_state, spec007_entity_llama_backend, spec007_entity_on_device_llama_provider, spec007_entity_model_config_repo [EXTRACTED 1.00]
- **Contract 5 Data Minimisation Contracts** — contracts007_c5_data_minimisation, contracts007_c5_1, contracts007_c5_2, contracts007_c5_3, spec007_fr003, spec007_fr004 [EXTRACTED 1.00]
- **Spec 007 JNI Bridge Cluster** — plan007_llama_jni_object, plan007_token_callback, graph_report_llama_backend, graph_report_llama_jni, graph_report_fake_llama_jni, contracts007_c3_llama_jni [INFERRED 0.85]
- **Native Library Build Pipeline** — build_instructions_doc, cmake_project, cmake_liballama, cmake_jni_libs_dir, quickstart007_build_llamacpp, build_instructions_llamacpp_source, build_instructions_ndk [EXTRACTED 1.00]

## Communities

### Community 0 - "Spec 007 Contracts + Graphify Integration"
Cohesion: 0.05
Nodes (50): Graphify Knowledge Graph Integration, C1.1: Emits â‰¥1 Token Before Complete, C1.2: Complete Carries Full Assembled Text, C1.3: Model Not Loaded Emits Error, C1.4: Empty QuestionText Emits Error, C1.5: Flow is Cancellable, Contract 1: OnDeviceFallback Interface, C2.1: Default modelPath After First Write (+42 more)

### Community 1 - "Cloud Inference + Audio Pipeline Concepts"
Cohesion: 0.07
Nodes (46): ApiKeyStore (Tink + DataStore), AudioProcessingForegroundService, ClaudeInferenceClient (Anthropic SDK), Clock Interface (SystemClock/FakeClock), CloudBadgeController, CloudBadgeState (HIDDEN/CLOUD_ACTIVE/FALLBACK), CloudInferenceEngine, CloudInferenceRequest Entity (+38 more)

### Community 2 - "WiFi Connectivity Contracts"
Cohesion: 0.07
Nodes (34): Contract 2.1: Fresh DataStore Has No Config, Contract 2.5: updateConnectionStatus False Disables Cloud, Contract 2.4: setEnabled True When Not Connected Throws, Contract 2.3: setEnabled True When Connected, Contract 2.2: updateConnectionStatus Connected, Contract 4.2: TTFT Exceeds 5s Budget Triggers TIMEOUT Fallback, Contract 4.3: TTFT Logged to Logcat, Contract 4.1: TTFT Within 5000ms Budget (+26 more)

### Community 3 - "Feature 001 Realtime Mic Spec"
Cohesion: 0.08
Nodes (29): Feature: Real-Time Mic Suggestions, FR-001: Real-Time Mic Capture (no upload), Rationale: Core Loop is Entire Product (P1), Spec 001 Requirements Checklist, SC-001: Suggestion Within 3s (spec001), Session (spec001 entity), Suggestion (spec001 entity), Display Over Other Apps Permission (+21 more)

### Community 4 - "Data Minimisation Contracts"
Cohesion: 0.09
Nodes (25): C5.1: QuestionText Truncated to 600 Chars Before JNI, C5.2: No Audio Bytes Passed to JNI, C5.3: System Prompt â‰¤320 Chars, Contract 5: Data Minimisation (Principle I), CloudBadgeState Extension (No Change), DataStore No Encryption for Model Config, ModelConfig Data Model, Decision 8: System Prompt for Meeting Q&A (+17 more)

### Community 5 - "Audio Capture + VAD Concepts"
Cohesion: 0.15
Nodes (21): AudioCaptureSource, DetectedQuestion Entity, FreshnessState (FRESH/STALE), llama.cpp + Phi-3-mini On-Device LLM, OverlayRenderer, Question Classifier (Rule-Based Heuristics), SuggestionEngine (On-Device LLM), TranscriptSegment (+13 more)

### Community 6 - "Feature 007 Tech Stack (CLAUDE.md)"
Cohesion: 0.11
Nodes (19): Feature 007: On-Device Gemma 4 E4B Inference, Gemma 4 E4B IT Q4_K_M GGUF Model, llama.cpp JNI Bridge, ModelSetupViewModel Technology, ModelConfigRepository DataStore Technology, OnDeviceLlamaProvider Technology, OpenCL Adreno 750 GPU Offload, MeetMind Assistant Development Guidelines (+11 more)

### Community 7 - "OnDeviceLlamaProvider Tests"
Cohesion: 0.11
Nodes (1): OnDeviceLlamaProviderTest

### Community 8 - "NDK Build + Hardware Setup"
Cohesion: 0.15
Nodes (18): Native Library Build Instructions, llama.cpp Source Repository, Android NDK r27+, Snapdragon 8 Gen 3 Adreno 750, Lenovo Legion Y700 Gen 3 Target Device, jniLibs/arm64-v8a Directory, liballama.so (Imported Shared Library), MeetMind CMakeLists.txt (+10 more)

### Community 9 - "AppContainer + Inference Clients"
Cohesion: 0.22
Nodes (16): AppContainer DI, ClaudeInferenceClient, CloudBadgeController, CloudKeyValidationService, CloudStreamingProvider Interface, ValidationResult Enum, MeetMindApplication, CloudBadgeState Enum (+8 more)

### Community 10 - "API Key Store + Nav Graph"
Cohesion: 0.17
Nodes (16): ApiKeyStore (Interface), TinkApiKeyStore, Tink AES-256-GCM + Android Keystore Encryption Pattern, AppNavGraph, Routes (Navigation Constants), CloudBadge Composable, CloudProviderConfigRepository, CloudSettingsScreen (+8 more)

### Community 11 - "CloudInferenceEngine Tests"
Cohesion: 0.15
Nodes (1): CloudInferenceEngineTest

### Community 12 - "ModelConfigRepository Tests"
Cohesion: 0.15
Nodes (1): ModelConfigRepositoryTest

### Community 13 - "ModelSetupViewModel"
Cohesion: 0.22
Nodes (2): Factory, ModelSetupViewModel

### Community 14 - "API Key Contracts"
Cohesion: 0.25
Nodes (8): Contract 1.5: Blank Key Rejected, Contract 1.4: Delete Key Removes Key, Contract 1.3: Key Persists After Store Recreation, Contract 1.7: observeHasKey Emits True After Save, Contract 1.6: Plaintext ByteArray Zeroed After Save, Contract 1.2: Save and Retrieve Claude Key, Contract 1.1: Save and Retrieve Gemini Key, TinkApiKeyStoreTest (Contract Tests)

### Community 15 - "ModelConfigRepository Impl"
Cohesion: 0.25
Nodes (1): ModelConfigRepository

### Community 16 - "CloudInferenceEngine Impl"
Cohesion: 0.29
Nodes (2): CloudInferenceEngine, OnDeviceFallback

### Community 17 - "SessionViewModel"
Cohesion: 0.29
Nodes (2): Factory, SessionViewModel

### Community 18 - "ModelSetupViewModel Tests"
Cohesion: 0.29
Nodes (1): ModelSetupViewModelTest

### Community 19 - "ModelLoadState Sealed Class"
Cohesion: 0.33
Nodes (5): Error, Loading, ModelLoadState, NotLoaded, Ready

### Community 20 - "Community 20"
Cohesion: 0.33
Nodes (1): LlamaBackend

### Community 21 - "Community 21"
Cohesion: 0.33
Nodes (1): LlamaJni

### Community 22 - "Community 22"
Cohesion: 0.33
Nodes (1): OnDeviceLlamaProvider

### Community 23 - "Community 23"
Cohesion: 0.33
Nodes (1): FakeLlamaJni

### Community 24 - "Community 24"
Cohesion: 0.4
Nodes (2): Factory, HomeViewModel

### Community 25 - "Community 25"
Cohesion: 0.5
Nodes (4): AudioProcessingForegroundService, InferenceEvent Broadcast Pattern, FakeConnectivityChecker, GeminiInferenceClient

### Community 26 - "Community 26"
Cohesion: 0.5
Nodes (1): MeetMindApplication

### Community 27 - "Community 27"
Cohesion: 0.5
Nodes (0): 

### Community 28 - "Community 28"
Cohesion: 0.67
Nodes (1): Routes

### Community 29 - "Community 29"
Cohesion: 1.0
Nodes (2): Clock Interface, SystemClock

### Community 30 - "Community 30"
Cohesion: 1.0
Nodes (2): FakeCloudStreamingProvider, FakeStreamResponse

### Community 31 - "Community 31"
Cohesion: 1.0
Nodes (0): 

### Community 32 - "Community 32"
Cohesion: 1.0
Nodes (1): ModelConfig

### Community 33 - "Community 33"
Cohesion: 1.0
Nodes (1): AppContainer

### Community 34 - "Community 34"
Cohesion: 1.0
Nodes (0): 

### Community 35 - "Community 35"
Cohesion: 1.0
Nodes (1): Root Build Gradle

### Community 36 - "Community 36"
Cohesion: 1.0
Nodes (1): Settings Gradle

### Community 37 - "Community 37"
Cohesion: 1.0
Nodes (1): App Build Gradle

### Community 38 - "Community 38"
Cohesion: 1.0
Nodes (1): FakeClock

### Community 39 - "Community 39"
Cohesion: 1.0
Nodes (1): FakeCloudKeyValidationService

### Community 40 - "Community 40"
Cohesion: 1.0
Nodes (0): 

### Community 41 - "Community 41"
Cohesion: 1.0
Nodes (0): 

### Community 42 - "Community 42"
Cohesion: 1.0
Nodes (0): 

### Community 43 - "Community 43"
Cohesion: 1.0
Nodes (1): FR-002: On-Device Transcription (spec001)

### Community 44 - "Community 44"
Cohesion: 1.0
Nodes (1): Utterance (spec001 entity)

### Community 45 - "Community 45"
Cohesion: 1.0
Nodes (1): FR-002: Float/Full-Screen Switch Control

### Community 46 - "Community 46"
Cohesion: 1.0
Nodes (1): Spec 005 Plan: Cloud AI Inference

### Community 47 - "Community 47"
Cohesion: 1.0
Nodes (1): Spec 005 Research: Cloud AI Inference

## Knowledge Gaps
- **132 isolated node(s):** `Root Build Gradle`, `Settings Gradle`, `App Build Gradle`, `MeetMindApplication`, `CloudProviderConfig Data Class` (+127 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 29`** (2 nodes): `Clock Interface`, `SystemClock`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 30`** (2 nodes): `FakeCloudStreamingProvider`, `FakeStreamResponse`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 31`** (2 nodes): `TestProviderSetup.kt`, `enableProvider()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 32`** (2 nodes): `ModelConfig.kt`, `ModelConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 33`** (2 nodes): `AppContainer.kt`, `AppContainer`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 34`** (2 nodes): `HomeScreen.kt`, `HomeScreen()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 35`** (1 nodes): `Root Build Gradle`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 36`** (1 nodes): `Settings Gradle`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 37`** (1 nodes): `App Build Gradle`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 38`** (1 nodes): `FakeClock`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `FakeCloudKeyValidationService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 41`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (1 nodes): `write_chunk01.py`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `FR-002: On-Device Transcription (spec001)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (1 nodes): `Utterance (spec001 entity)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 45`** (1 nodes): `FR-002: Float/Full-Screen Switch Control`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 46`** (1 nodes): `Spec 005 Plan: Cloud AI Inference`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (1 nodes): `Spec 005 Research: Cloud AI Inference`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Feature 007: On-Device Gemma 4 E4B Inference Spec` connect `Data Minimisation Contracts` to `Spec 007 Contracts + Graphify Integration`, `NDK Build + Hardware Setup`, `Feature 007 Tech Stack (CLAUDE.md)`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **Why does `Feature 007: On-Device Gemma 4 E4B Inference` connect `Feature 007 Tech Stack (CLAUDE.md)` to `Spec 007 Contracts + Graphify Integration`, `Data Minimisation Contracts`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **What connects `Root Build Gradle`, `Settings Gradle`, `App Build Gradle` to the rest of the system?**
  _132 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Spec 007 Contracts + Graphify Integration` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Cloud Inference + Audio Pipeline Concepts` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._
- **Should `WiFi Connectivity Contracts` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._
- **Should `Feature 001 Realtime Mic Spec` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._