// T007 (spec 007): DataStore-backed config for the on-device llama.cpp model
package com.meetmind.assistant.data.model

/**
 * Runtime configuration for the on-device Gemma 4 E4B inference engine.
 *
 * Persisted to DataStore Preferences with key prefix `on_device_`.
 * Defaults are tuned for Snapdragon 8 Gen 3 + Adreno 750 (Y700 Gen 3, 16 GB RAM).
 *
 * @param modelPath     Absolute path to the GGUF file on-device.
 *                      Default: `/sdcard/Download/gemma-4-E4B-it-Q4_K_M.gguf`
 *                      (pre-pushed via `adb push` per quickstart.md).
 * @param nThreads      Number of CPU threads for inference. Clamped to 1..16.
 *                      Default 6 = 1×Cortex-X4 prime + 5×Cortex-A720 big cores.
 * @param nGpuLayers    Number of transformer layers to offload to Adreno 750 via OpenCL.
 *                      -1 = offload all layers (recommended).
 * @param contextSize   KV cache context window in tokens. Default 8192 = full context supported
 *                      by Gemma 4 E4B IT. ~1.6 GB KV-cache overhead is within budget on
 *                      Y700 Gen 3 (16 GB RAM, ~5 GB used by model weights).
 */
data class ModelConfig(
    val modelPath: String = DEFAULT_PATH,
    val nThreads: Int = DEFAULT_THREADS,
    val nGpuLayers: Int = DEFAULT_GPU_LAYERS,
    val contextSize: Int = DEFAULT_CONTEXT_SIZE
) {
    companion object {
        const val DEFAULT_PATH = "/sdcard/Download/gemma-4-E4B-it-Q4_K_M.gguf"
        const val DEFAULT_THREADS = 6
        const val DEFAULT_GPU_LAYERS = -1  // offload all layers to Adreno 750
        const val DEFAULT_CONTEXT_SIZE = 8192  // full Gemma 4 E4B context; ~1.6 GB KV-cache on Y700 Gen 3
    }
}
