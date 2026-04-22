// T006 (spec 007): Sealed class tracking the lifecycle of the on-device llama.cpp model handle
package com.meetmind.assistant.data.model

/**
 * Represents the current state of the on-device Gemma 4 E4B model.
 *
 * State transitions:
 *   [NotLoaded] → (user taps Load) → [Loading] → [Ready] or [Error]
 *   [Ready] → (onTrimMemory CRITICAL) → [NotLoaded]
 */
sealed class ModelLoadState {
    /** Model has not been loaded. On-device inference is unavailable. */
    object NotLoaded : ModelLoadState()

    /** Model is currently being loaded from disk into RAM (~5 GB, takes 3–10s). */
    object Loading : ModelLoadState()

    /**
     * Model is loaded and ready for inference.
     * @param handle Opaque native handle returned by [LlamaJni.loadModel]. Always > 0.
     */
    data class Ready(val handle: Long) : ModelLoadState()

    /**
     * Model failed to load or an error occurred during inference setup.
     * @param message Human-readable description of the failure.
     */
    data class Error(val message: String) : ModelLoadState()
}
