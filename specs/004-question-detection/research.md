# Research: Question Detection and Timely Suggestions

**Feature**: 004-question-detection
**Date**: 2026-04-14
**Branch**: `feature/004-question-detection`

## 1. On-Device ASR (Speech-to-Text)

**Decision**: Vosk Android SDK with `vosk-model-small-en-us` (~40 MB)

**Rationale**:
- Streaming partial results emitted continuously at ~200–400ms latency per chunk —
  fits the 3s total pipeline budget
- Official Kotlin/Java API; no JNI plumbing needed
- ~10–15% CPU on a single core during active transcription (Snapdragon 700)
- APK-safe: 40 MB model bundled as an asset + ~4 MB native `.so`

**Alternatives considered**:
- **Whisper.cpp via JNI**: Better accuracy (WER ~5–8%) but batch-oriented; tiny.en
  model (~75 MB) takes 1.5–3s per segment on Snapdragon 700 — consumes the entire
  latency budget before the LLM starts. Eliminated.
- **Mozilla DeepSpeech / Coqui**: Effectively unmaintained since 2022; Android support
  is community-only. Eliminated.

**Risks & mitigations**:
- Vosk accuracy degrades in noisy environments — pair with noise suppression (VAD
  section below).
- Partial results are provisional — question detector only acts on confirmed
  (sentence-final) results.
- Large Vosk models (1.8 GB) are too large to bundle — use the small model;
  offer an optional large-model download on first launch in a future version.

---

## 2. Voice Activity Detection (VAD)

**Decision**: Two-stage VAD — WebRTC VAD as a gate + Silero VAD (TFLite, ~1.8 MB)
for sentence-boundary detection

**Rationale**:
- **WebRTC VAD** (~50 KB native lib): energy/spectral heuristic, <1ms/frame, used
  to stop forwarding audio to Vosk during silence — keeps CPU near zero when nobody
  is speaking.
- **Silero VAD TFLite**: semantic/prosodic boundary detection, ~1–2ms per 30ms chunk,
  operates at 16 kHz mono (same as Vosk). Reliably detects when a sentence ends vs.
  just a breath pause.

**Alternatives considered**:
- **WebRTC VAD alone**: Misclassifies filler words and breathing as speech endpoints.
  Sentence boundaries unreliable. Retained only as the cheap gate, not the boundary
  detector.
- **Custom energy-based VAD**: Fast but fragile; requires per-device threshold tuning.
  Eliminated.

**Risks & mitigations**:
- Silero VAD is stateful — must pass the hidden-state tensor across consecutive calls.
- TFLite GPU delegate optional speedup deferred; CPU inference is sufficient.

---

## 3. Question Detection / Intent Classification

**Decision**: Rule-based heuristics on confirmed transcript text

**Rationale**:
- <1ms, zero model size, covers ~85–90% of English conversational questions.
- Rules: interrogative words at sentence start (what/where/when/why/who/how/which),
  auxiliary inversion patterns (is/are/was/were/do/does/did/can/could/will/would/
  should/have/has/had followed by pronoun or noun), terminal `?` from ASR punctuation.
- Minimum word-count threshold (≥4 words) filters single-word transcription noise.

**Alternatives considered**:
- **Small TFLite classifier (distilBERT-based)**: ~96% accuracy but +50–80 MB model,
  50–150ms latency, high memory overhead. Accuracy gain doesn't justify cost within
  the 3s budget. Eliminated.
- **LLM-based classification (prompt the on-device LLM)**: Doubles LLM calls, adds
  1–2s latency per utterance. Never use the LLM as the gate. Eliminated.

**Risks & mitigations**:
- Vosk small model sometimes omits `?` and lowercases first word — normalise transcript
  before applying rules.
- Rhetorical questions ("Isn't that interesting?") will trigger suggestions; filtering
  rhetorical intent is out of scope for v1 (documented in spec assumptions).

---

## 4. On-Device LLM for Suggestion Generation

> **Updated 2026-04-14**: Research expanded to cover Snapdragon 8 Gen 3 hardware
> (Lenovo Legion Y700 Gen 3, Honor Magic 6 Pro) which is ~2× faster than the
> Snapdragon 700-series baseline originally assumed.

### 4a. Target Hardware Capability — Snapdragon 8 Gen 3

| Subsystem | Snapdragon 700-series (original baseline) | Snapdragon 8 Gen 3 (updated target) |
|-----------|------------------------------------------|--------------------------------------|
| CPU architecture | Cortex-A78/A55 cluster | 1× Cortex-X4 @ 3.19 GHz + 5× Cortex-A720 + 2× A520; SVE2 + i8mm |
| GPU | Adreno 6xx | Adreno 750 (Vulkan 1.3, OpenCL 3.0) |
| System AI TOPS (CPU+GPU+NPU) | ~10 TOPS | **45 TOPS** |
| AnTuTu overall (approx.) | ~450k | ~1.5M (~2–3× faster) |
| LLM decode (7B CPU, est.) | ~6–8 t/s | **~10–15 t/s** |
| RAM (typical) | 6–8 GB | **12–16 GB** |

The 8 Gen 3 is Qualcomm's first chipset officially marketed for on-device 7B+ models.
The Y700 Gen 3 and Magic 6 Pro are significantly more capable than the original
planning baseline.

### 4b. Model Evaluation for Snapdragon 8 Gen 3

**80-token generation within 3s requires ≥27 t/s decode** — achievable only for
sub-4B models on CPU, or sub-8B on NPU (Hexagon). The table below covers candidates:

| Model | GGUF Q4_K_M size | RAM needed | CPU t/s (est.) | 80-tok time | 3s viable? |
|-------|-----------------|------------|----------------|-------------|-----------|
| Phi-3-mini 3.8B Q4_K_M | 2.2 GB | ~3.5 GB | 10–15 | 5–8s | Marginal |
| Phi-3-mini 3.8B Q4_0 (OpenCL) | 2.2 GB | ~3.5 GB | prefill 85 t/s, decode 10–15 t/s | ~3–4s with short prompt | **Yes at 40 tokens** |
| Llama-3.2-3B Q4_K_M | ~2.0 GB | ~3 GB | 12–18 | 4–7s | Borderline |
| Llama-3-8B Q4_K_M | 4.7 GB | ~6.5 GB | 7–10 | 8–11s | No |
| Gemma-2-9B Q4_K_M | 5.4 GB | ~7.5 GB | 6–9 | 9–13s | No |
| Phi-3-medium 14B Q4_K_M | 8.6 GB | ~11 GB | 3–5 | 16–27s | No |

**Critical finding**: Only sub-4B models on the CPU path are viable for a 3s budget.
All 7B+ models require either the NPU path or relaxing the latency target.

**Latency lever**: Reducing generation to 40 tokens (2 crisp sentences) at 15 t/s
gives ~2.7s total with short-prompt prefill — this is the key tuning knob.

### 4c. GPU Acceleration — Adreno 750 (OpenCL backend)

Qualcomm contributed a dedicated OpenCL backend to llama.cpp (merged in PR #10693).
**Do not use the Vulkan backend** — it is 14× slower than CPU on Adreno devices.

Real benchmarks on Snapdragon 8 Gen 3 (Galaxy S24 Ultra), Qwen2 7B Q4_0:
- GPU OpenCL decode: **6.29 ± 0.14 t/s** (worse than CPU for decode)
- CPU (6 threads) decode: **9.88 ± 0.93 t/s**
- GPU OpenCL prefill: **84.74 ± 2.06 t/s** (excellent — use this)

**Practical strategy**: Use `-ngl 99` (offload all layers to OpenCL GPU) for fast
prefill of the context prompt, then let CPU handle decode. This hybrid approach
minimises first-token latency. Build with `-DGGML_OPENCL=ON`; use Q4_0 format and
`--pure` flag for the OpenCL-optimised kernel path.

### 4d. Decision — Updated

**Decision**: llama.cpp via JNI with **Phi-3-mini-4k-instruct (3.8B, Q4_0 for
OpenCL, fallback Q4_K_M for CPU-only)**, targeting Snapdragon 8 Gen 3.

**Key changes from original Snapdragon 700 plan**:
- Generate **40–50 tokens max** (2 concise sentences) rather than 80 — brings
  latency from ~5–8s to **~2.7–3.5s** on target hardware
- Use **OpenCL backend** (`-ngl 99`, Q4_0) for 85 t/s prefill on Adreno 750
- Phi-3-mini remains the correct size class — no justification to move to 7B+
  on the CPU path given latency constraints
- Stream tokens to UI as they generate — perceived first-token latency ~0.8s
- 12–16 GB RAM on Y700 Gen 3 / Magic 6 Pro provides ample headroom (model uses
  ~3.5 GB, leaving 8–12 GB for OS, audio, VAD, Vosk)

**Distribution strategy** (unchanged):
- The ~2.2 GB model is NOT bundled in the APK.
- Downloaded on first launch to app-internal storage (`getFilesDir()`), with a
  progress screen. Model persists across app updates.
- Model is loaded once at service start and kept in memory.

**Alternatives considered (updated)**:
- **Llama-3.2-3B Q4_K_M (~2.0 GB)**: Slightly smaller, faster (~12–18 t/s), but
  weaker instruction-following than Phi-3-mini for meeting/interview framing.
  Viable as a fallback if Phi-3-mini latency is unacceptable on a specific device.
- **QNN/Genie (Hexagon NPU) path**: Would give ~10 t/s for Llama-3.2-3B on 8 Gen 3
  NPU, still ~8s for 80 tokens. Not faster enough to justify the complex QNN SDK
  integration + device-specific `.bin` compilation. Deferred to future spec.
- **ExecuTorch + QNN delegate**: Best long-term path for NPU, powers Meta apps at
  scale, fully APK-distributable. Migration cost from GGUF/llama.cpp is high for v1.
  Revisit after v1 ships.
- **MLC LLM (TVM + OpenCL)**: ~10 t/s for small models; suffered thermal throttling
  in sustained benchmarks (GPU hit 78°C → 231 MHz). Eliminated for real-time use.
- **MediaPipe LLM Inference**: Requires Google Play Services — eliminated.
- **Vulkan backend**: 14× slower than CPU on Adreno — eliminated.

**Risks & mitigations**:
- OpenCL backend is functional but still maturing (Q4_0 only optimised). Monitor
  llama.cpp releases; upgrade when Q4_K_M OpenCL kernels are optimised.
- Thermal throttling under sustained inference: the Foreground Service should
  implement a cool-down heuristic (skip generation if device reports high thermal
  state via `ThermalManager`).
- First-token latency (prompt prefill via OpenCL): ~0.5–1s for a 50-token prompt
  at 85 t/s prefill. Keep total input ≤100 tokens.
- Memory pressure: model + KV cache ~3.5 GB. On 12 GB devices this is comfortable.
  Handle `onTrimMemory()` callbacks to gracefully degrade if needed.

---

## 5. Android Service Architecture

**Decision**: Foreground Service (`FOREGROUND_SERVICE_TYPE_MICROPHONE`) + overlay
window (`TYPE_APPLICATION_OVERLAY`)

**Rationale**:
- Foreground Service with `startForeground()` is the only Android-sanctioned way to
  keep audio capture alive in the background without being killed by Doze/App Standby
  (Android 8+).
- `FOREGROUND_SERVICE_TYPE_MICROPHONE` is required on Android 14+ and best practice
  on Android 10+.
- The floating overlay uses `WindowManager.addView()` with `TYPE_APPLICATION_OVERLAY`,
  requiring `SYSTEM_ALERT_WINDOW` permission.

**Recommended component architecture**:

```
MainActivity
  └── AudioProcessingForegroundService
        ├── AudioRecord thread (16kHz, 16-bit mono PCM)
        ├── VAD pipeline (WebRTC gate → Silero boundary)
        ├── Vosk streaming recogniser
        ├── Question classifier (heuristics)
        ├── llama.cpp inference (dedicated HandlerThread)
        └── OverlayWindowManager (WindowManager overlay)
```

**Required manifest permissions**:
```
RECORD_AUDIO
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MICROPHONE
SYSTEM_ALERT_WINDOW
```

**Risks & mitigations**:
- On Android 14+, `FOREGROUND_SERVICE_TYPE_MICROPHONE` must appear in both the
  manifest `<service>` tag AND be passed to `startForeground()` — missing either
  causes `SecurityException`.
- `SYSTEM_ALERT_WINDOW` cannot use standard `requestPermissions()` — send user to
  `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`, check `Settings.canDrawOverlays()`
  on return.
- Aggressive OEM battery optimizers (Xiaomi, Huawei, OnePlus) may kill foreground
  services — prompt user to add app to battery optimization whitelist via
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Remove the overlay view in `onDestroy()` to prevent `WindowManager` leak.

---

## Pipeline Latency Budget

### Original baseline (Snapdragon 700-series)

| Stage | Component | Latency |
|-------|-----------|---------|
| VAD sentence boundary | Silero VAD | ~0ms marginal |
| ASR final result | Vosk streaming | ~300ms |
| Question classification | Rule-based heuristics | <1ms |
| LLM prefill + 80-token generation | llama.cpp Phi-3-mini Q4_K_M CPU | ~5–8s |
| **Total** | | **~5–8s** ⚠️ exceeds 3s SLA |

### Updated target (Snapdragon 8 Gen 3 — Y700 Gen 3 / Magic 6 Pro)

| Stage | Component | Latency |
|-------|-----------|---------|
| VAD sentence boundary | Silero VAD | ~0ms marginal |
| ASR final result | Vosk streaming | ~300ms |
| Question classification | Rule-based heuristics | <1ms |
| LLM prefill (~50 tokens) | llama.cpp OpenCL Adreno 750 | ~0.6s (85 t/s prefill) |
| LLM decode (~40 tokens, streamed) | llama.cpp CPU (10–15 t/s) | ~2.7–4s |
| **Total (first visible token)** | | **~1s perceived** |
| **Total (full 2-sentence suggestion)** | | **~3–4s** |

**Key mitigations to hit 3s**:
1. Cap generation at **40 tokens** (2 concise sentences) — brings decode to ~2.7s at
   15 t/s
2. Use **OpenCL backend** (`-ngl 99`, Q4_0) for fast prefill; stream tokens to UI
3. Keep system prompt + question context ≤100 tokens total
4. Pre-load model at service start (not per-request)

**Streaming display**: Show tokens as they generate rather than waiting for completion.
Perceived latency becomes ~0.8–1s to first word on screen.

---

## NEEDS CLARIFICATION — Resolved

All technical unknowns resolved by research. No open questions remain.
