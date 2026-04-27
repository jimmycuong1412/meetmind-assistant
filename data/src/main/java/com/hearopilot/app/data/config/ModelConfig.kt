package com.hearopilot.app.data.config

import com.hearopilot.app.domain.model.LlmModelVariant

/**
 * Encapsulates all model download configuration: URLs, filenames, and related file lists.
 *
 * Separating configuration from [com.hearopilot.app.data.datasource.ModelDownloadManager]
 * allows model references to be changed (e.g. for different build variants or A/B tests)
 * without touching download infrastructure.
 */
data class ModelConfig(
    val llmUrl: String,
    val llmFilename: String,
    val sttBaseUrl: String,
    val sttFiles: List<String>,
    val sttModelType: Int = 40 // Default to English Parakeet
)

// STT config is shared across all LLM variants.
private const val STT_BASE_URL =
    "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"
private val STT_FILES = listOf(
    "encoder.int8.onnx",
    "decoder.int8.onnx",
    "joiner.int8.onnx",
    "tokens.txt"
)

// Vietnamese STT config (Type 26)
private const val STT_VI_BASE_URL =
    "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20/resolve/main"
private val STT_VI_FILES = listOf(
    "encoder-epoch-12-avg-8.int8.onnx",
    "decoder-epoch-12-avg-8.onnx",
    "joiner-epoch-12-avg-8.int8.onnx",
    "tokens.txt"
)

/**
 * Q8_0 model configuration — higher accuracy, ~1 GB.
 * Recommended for flagship devices (≥ 10 GB RAM, Cortex-A78 / Oryon CPU or newer).
 *
 * LLM  : Gemma 3 1B Q8_0 (ggml-org, HuggingFace)
 * STT  : Sherpa-ONNX Nemo Parakeet TDT 0.6B Int8 (csukuangfj, HuggingFace)
 */
object DefaultModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q8_0.gguf?download=true"
    private const val LLM_FILENAME = "gemma-3-1b-it-Q8_0.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40
    )
}

/**
 * Vietnamese STT configuration — specialized model for Vietnamese speech.
 * Uses Zipformer architecture (Type 26).
 */
object VietnameseSttConfig {
    val INSTANCE = ModelConfig(
        llmUrl = DefaultModelConfig.INSTANCE.llmUrl,
        llmFilename = DefaultModelConfig.INSTANCE.llmFilename,
        sttBaseUrl = STT_VI_BASE_URL,
        sttFiles = STT_VI_FILES,
        sttModelType = 26
    )
}

/**
 * IQ4_NL model configuration — ~35 % smaller (~650 MB), lower CPU load and battery use.
 * Recommended for mid-range and older devices (Cortex-A77 / Snapdragon 865 era and below).
 *
 * LLM  : Gemma 3 1B IQ4_NL (unsloth, HuggingFace)
 * STT  : same as [DefaultModelConfig]
 */
object LowEndModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-IQ4_NL.gguf?download=true"
    private const val LLM_FILENAME = "gemma-3-1b-it-IQ4_NL.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40
    )
}

/**
 * Qwen 3.5 0.8B Q8_0 configuration — alternative model, ~870 MB.
 * Beta: available only via manual download in Settings; not auto-recommended by DeviceTierDetector.
 *
 * LLM  : Qwen 3.5 0.8B Q8_0 (unsloth, HuggingFace)
 * STT  : same as [DefaultModelConfig]
 */
object Qwen35ModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf?download=true"
    private const val LLM_FILENAME = "Qwen3.5-0.8B-Q8_0.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40
    )
}

/**
 * Gemma 3 4B Q4_K_M — ~2.5 GB, best accuracy/size balance for flagship devices.
 * Recommended for Snapdragon 8 Gen 2/3 with ≥ 12 GB RAM.
 */
object Gemma3_4B_Q4Config {
    private const val LLM_URL =
        "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf?download=true"
    private const val LLM_FILENAME = "gemma-3-4b-it-Q4_K_M.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40
    )
}

/**
 * Qwen 3 4B Q4_K_M — ~2.5 GB, strong multilingual summarization and 32K context.
 * Excellent for meeting transcription analysis on high-end devices.
 */
object Qwen3_4B_Q4Config {
    private const val LLM_URL =
        "https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true"
    private const val LLM_FILENAME = "Qwen3-4B-Q4_K_M.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40
    )
}

/**
 * Phi-4-mini Q4_K_M — ~2.5 GB, Microsoft's efficiency-optimized 4B model.
 * Low inference latency with high instruction-following quality.
 */
object Phi4MiniQ4Config {
    private const val LLM_URL =
        "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf?download=true"
    private const val LLM_FILENAME = "Phi-4-mini-instruct-Q4_K_M.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40
    )
}

/** Returns the [ModelConfig] that corresponds to [variant]. */
fun modelConfigForVariant(variant: LlmModelVariant): ModelConfig = when (variant) {
    LlmModelVariant.Q8_0         -> DefaultModelConfig.INSTANCE
    LlmModelVariant.IQ4_NL       -> LowEndModelConfig.INSTANCE
    LlmModelVariant.QWEN3_5_Q8_0 -> Qwen35ModelConfig.INSTANCE
    LlmModelVariant.GEMMA3_4B_Q4 -> Gemma3_4B_Q4Config.INSTANCE
    LlmModelVariant.QWEN3_4B_Q4  -> Qwen3_4B_Q4Config.INSTANCE
    LlmModelVariant.PHI4_MINI_Q4 -> Phi4MiniQ4Config.INSTANCE
}

/** Returns the [ModelConfig] for a specific language. */
fun modelConfigForLanguage(languageCode: String): ModelConfig = when {
    languageCode.startsWith("vi") -> VietnameseSttConfig.INSTANCE
    else -> DefaultModelConfig.INSTANCE
}
