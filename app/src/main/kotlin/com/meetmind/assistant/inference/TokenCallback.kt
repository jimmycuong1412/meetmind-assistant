// T005 (spec 007): Callback interface bridging llama.cpp native callbacks → Kotlin Flow
package com.meetmind.assistant.inference

/**
 * Receives token-by-token output from the llama.cpp JNI layer.
 *
 * Implementations MUST be thread-safe — [onToken] and [onComplete] are called
 * from a native background thread managed by llama.cpp.
 *
 * The contract:
 *  - [onToken] is called 0..N times before [onComplete] or [onError]
 *  - [onComplete] is called exactly once when inference finishes normally
 *  - [onError] is called at most once if inference fails; [onComplete] is NOT called
 */
interface TokenCallback {
    /** Called for each generated token fragment from the model. */
    fun onToken(token: String)

    /**
     * Called once when the model has finished generating.
     * @param fullText The complete assembled response (all tokens joined).
     */
    fun onComplete(fullText: String)

    /**
     * Called if the native inference encounters an unrecoverable error.
     * [onComplete] will NOT be called after this.
     * @param message Human-readable error from the native layer.
     */
    fun onError(message: String)
}
