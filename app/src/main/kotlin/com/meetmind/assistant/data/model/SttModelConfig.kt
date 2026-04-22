package com.meetmind.assistant.data.model

/**
 * Fixed configuration for the Nemo Parakeet TDT 0.6B v3 Int8 STT model.
 * The STT model is not user-selectable — it is always this model.
 *
 * spec 009 — T043
 */
data class SttModelConfig(
    val baseUrl: String = STT_BASE_URL,
    val files: List<String> = STT_FILES
) {
    companion object {
        const val STT_BASE_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"

        val STT_FILES = listOf(
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            "joiner.int8.onnx",
            "tokens.txt"
        )

        const val MODEL_DIR_NAME = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
    }
}
