package com.meetmind.assistant.data.model

/**
 * Represents the available quantization variants for the on-device Gemma 4 E4B IT GGUF model.
 * Q4_K_M is the default recommended variant for flagship devices (≥8 GB RAM, Android 14+).
 *
 * spec 009 — T005
 */
enum class GemmaModelVariant(
    val displayName: String,
    val llmFilename: String,
    val sizeHintMb: Int,
    val llmUrl: String
) {
    Q4_K_M(
        displayName  = "Gemma 4 E4B Q4_K_M",
        llmFilename  = "gemma-4-e4b-it-Q4_K_M.gguf",
        sizeHintMb   = 5120,
        llmUrl       = "https://huggingface.co/meetmind/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-Q4_K_M.gguf"
    ),
    Q8_0(
        displayName  = "Gemma 4 E4B Q8_0",
        llmFilename  = "gemma-4-e4b-it-Q8_0.gguf",
        sizeHintMb   = 7168,
        llmUrl       = "https://huggingface.co/meetmind/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-Q8_0.gguf"
    ),
    IQ4_NL(
        displayName  = "Gemma 4 E4B IQ4_NL",
        llmFilename  = "gemma-4-e4b-it-IQ4_NL.gguf",
        sizeHintMb   = 3072,
        llmUrl       = "https://huggingface.co/meetmind/gemma-4-e4b-it-GGUF/resolve/main/gemma-4-e4b-it-IQ4_NL.gguf"
    )
}
