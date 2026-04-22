// T008 (spec 007): JNI declarations for llama.cpp native bridge
package com.meetmind.assistant.inference

import android.util.Log

/**
 * Singleton JNI bridge to the pre-built `liballama.so`.
 *
 * All `external fun` declarations map to native functions implemented in
 * the llama.cpp library. The library is built from source per:
 *   app/src/main/jniLibs/arm64-v8a/BUILD_INSTRUCTIONS.md
 *
 * **Unit tests**: This object must NOT be used directly in Robolectric tests
 * because `System.loadLibrary` will fail on the JVM. Inject [FakeLlamaJni]
 * via the [LlamaBackend] interface instead.
 */
object LlamaJni : LlamaBackend {

    private const val TAG = "LlamaJni"

    init {
        try {
            System.loadLibrary("llama")
            Log.i(TAG, "liballama.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            // In unit test environments (Robolectric / JVM) the .so is not present.
            // This is expected — tests inject FakeLlamaJni via LlamaBackend.
            Log.w(TAG, "liballama.so not found — on-device inference unavailable: ${e.message}")
        }
    }

    /**
     * Load a GGUF model file and return a native context handle.
     * Returns -1 on failure (file not found, unsupported format, OOM).
     */
    external override fun loadModel(
        path: String,
        nThreads: Int,
        nGpuLayers: Int,
        contextSize: Int
    ): Long

    /**
     * Free all native resources for the given [handle].
     * Idempotent — safe to call multiple times.
     */
    external override fun unloadModel(handle: Long)

    /**
     * Run inference on [userText] using the loaded model at [handle].
     * Tokens are delivered via [callback] from a native thread.
     */
    external override fun inferenceAsync(
        handle: Long,
        systemPrompt: String,
        userText: String,
        callback: TokenCallback
    )

    /**
     * Check if [path] is a valid GGUF file without loading it into RAM.
     * Returns true iff file exists and GGUF magic bytes are present.
     */
    external override fun canLoad(path: String): Boolean
}
