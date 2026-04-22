// T020 (spec 007): Interface abstracting llama.cpp JNI calls for testability
package com.meetmind.assistant.inference

/**
 * Abstraction over the llama.cpp JNI layer.
 *
 * The production implementation is [LlamaJni] (backed by liballama.so).
 * Tests inject [com.meetmind.assistant.helpers.FakeLlamaJni] to avoid
 * loading native libraries in the JVM test environment.
 */
interface LlamaBackend {

    /**
     * Load a GGUF model file into RAM and return an opaque native handle.
     *
     * @param path        Absolute path to the `.gguf` file.
     * @param nThreads    CPU threads to allocate (clamped to 1..16 by caller).
     * @param nGpuLayers  Transformer layers to offload to GPU (-1 = all).
     * @param contextSize KV cache context window in tokens.
     * @return Handle > 0 on success, or -1 if the file cannot be loaded.
     */
    fun loadModel(path: String, nThreads: Int, nGpuLayers: Int, contextSize: Int): Long

    /**
     * Release all resources associated with [handle].
     * Safe to call multiple times (idempotent) — subsequent calls after the
     * first are no-ops.
     */
    fun unloadModel(handle: Long)

    /**
     * Run inference asynchronously and deliver output via [callback].
     *
     * Called from a background thread inside the engine. [callback] methods
     * will be invoked from a native thread; implementations MUST be thread-safe.
     *
     * Contract:
     *  - [TokenCallback.onToken] is called for each generated token
     *  - [TokenCallback.onComplete] is called exactly once after the last token
     *  - [TokenCallback.onError] is called (instead of onComplete) on failure
     *
     * @param handle      Handle returned by a prior successful [loadModel] call.
     * @param systemPrompt Pre-pended context prompt (≤ 320 chars, enforced by caller).
     * @param userText    The question/input text (≤ 600 chars, enforced by caller).
     * @param callback    Receiver for streamed token output.
     */
    fun inferenceAsync(
        handle: Long,
        systemPrompt: String,
        userText: String,
        callback: TokenCallback
    )

    /**
     * Fast header-only check: does this path point to a loadable GGUF file?
     * Does NOT load the model into RAM.
     *
     * @return true if the file exists and has a valid GGUF magic header.
     */
    fun canLoad(path: String): Boolean
}
