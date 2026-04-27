package com.hearopilot.app.domain.model

/**
 * Identifies which LLM GGUF quantization variant to download and use.
 *
 * The variant is persisted in [AppSettings] so the user can switch at any time;
 * re-downloading the model is required after switching.
 */
enum class LlmModelVariant {
    /**
     * 8-bit quantization — higher accuracy, ~1 GB on disk.
     * Suited to flagship devices with ≥ 10 GB RAM and a modern CPU (Cortex-A78 / Oryon or newer).
     */
    Q8_0,

    /**
     * 4-bit non-linear quantization — ~35 % smaller (~650 MB), lower CPU load and battery use.
     * Recommended for mid-range and older devices (e.g. Snapdragon 865 / Cortex-A77 era).
     */
    IQ4_NL,

    /**
     * Qwen 3.5 0.8B Q8_0 — alternative model architecture, ~870 MB on disk.
     * Beta: experimental, available only via manual download in Settings.
     * Not recommended automatically by [com.hearopilot.app.data.device.DeviceTierDetector].
     */
    QWEN3_5_Q8_0,

    /**
     * Gemma 3 4B Q4_K_M — ~2.5 GB, significantly better reasoning and analysis than 1B.
     * Ideal for flagship devices with ≥ 12 GB RAM and Snapdragon 8 Gen 2/3.
     */
    GEMMA3_4B_Q4,

    /**
     * Qwen 3 4B Q4_K_M — ~2.5 GB, excellent multilingual summarization and reasoning.
     * 32K context, strong instruction-following. Best for meeting analysis on high-end devices.
     */
    QWEN3_4B_Q4,

    /**
     * Phi-4-mini Q4_K_M — ~2.5 GB, Microsoft's efficiency-focused 4B model.
     * Exceptional instruction-following with low inference latency for real-time insights.
     */
    PHI4_MINI_Q4
}
