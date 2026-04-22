// T009 (spec 007): Test double for LlamaBackend — no native libraries required
package com.meetmind.assistant.helpers

import com.meetmind.assistant.inference.LlamaBackend
import com.meetmind.assistant.inference.TokenCallback

/**
 * In-memory fake implementation of [LlamaBackend] for use in JVM/Robolectric tests.
 *
 * No native libraries are loaded. All behaviour is configurable via constructor params
 * to cover the contract scenarios in [OnDeviceLlamaProviderTest].
 *
 * @param tokensToEmit      Sequence of token strings to emit via [TokenCallback.onToken].
 *                          Defaults to a single "Hello!" token.
 * @param loadShouldFail    If true, [loadModel] returns -1 (simulates file-not-found / OOM).
 * @param inferShouldError  If true, [inferenceAsync] calls [TokenCallback.onError] instead
 *                          of emitting tokens.
 * @param canLoadResult     Return value for [canLoad]. Defaults to true for any non-empty path.
 */
class FakeLlamaJni(
    private val tokensToEmit: List<String> = listOf("Hello!"),
    private val loadShouldFail: Boolean = false,
    private val inferShouldError: Boolean = false,
    private val canLoadResult: Boolean = true
) : LlamaBackend {

    /** Last systemPrompt passed to inferenceAsync — inspectable in tests. */
    var lastSystemPrompt: String = ""
        private set

    /** Last userText passed to inferenceAsync — inspectable in tests. */
    var lastUserText: String = ""
        private set

    /** Number of times unloadModel was called — verifies idempotency (C3.3). */
    var unloadCallCount: Int = 0
        private set

    private val loadedHandles = mutableSetOf<Long>()
    private var nextHandle = 1L

    override fun loadModel(path: String, nThreads: Int, nGpuLayers: Int, contextSize: Int): Long {
        if (loadShouldFail || path.startsWith("/nonexistent")) return -1L
        val handle = nextHandle++
        loadedHandles.add(handle)
        return handle
    }

    override fun unloadModel(handle: Long) {
        unloadCallCount++
        loadedHandles.remove(handle) // idempotent — remove is safe even if not present
    }

    override fun inferenceAsync(
        handle: Long,
        systemPrompt: String,
        userText: String,
        callback: TokenCallback
    ) {
        lastSystemPrompt = systemPrompt
        lastUserText = userText

        if (inferShouldError) {
            callback.onError("FakeLlamaJni: simulated inference error")
            return
        }

        val fullText = StringBuilder()
        for (token in tokensToEmit) {
            fullText.append(token)
            callback.onToken(token)
        }
        callback.onComplete(fullText.toString())
    }

    override fun canLoad(path: String): Boolean {
        if (path.startsWith("/nonexistent")) return false
        return canLoadResult
    }
}
