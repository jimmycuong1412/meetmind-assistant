// T009: Sealed class for streaming inference events emitted by CloudInferenceEngine
package com.meetmind.assistant.data.model

sealed class InferenceEvent {
    /**
     * A single streamed token fragment from the provider.
     * @param requestId UUID of the originating [CloudInferenceRequest]
     * @param text      The token/text fragment
     */
    data class Token(val requestId: String, val text: String) : InferenceEvent()

    /**
     * Stream completed successfully.
     * @param requestId UUID of the originating request
     * @param fullText  Entire assembled suggestion text
     * @param provider  Which cloud provider produced the response
     */
    data class Complete(
        val requestId: String,
        val fullText: String,
        val provider: CloudProvider
    ) : InferenceEvent()

    /**
     * Cloud request failed; on-device fallback was activated.
     * @param requestId UUID of the originating request
     * @param reason    Why the fallback was triggered
     */
    data class FallbackActivated(
        val requestId: String,
        val reason: FallbackReason
    ) : InferenceEvent()

    /**
     * Unrecoverable error during inference (neither cloud nor on-device succeeded).
     * @param requestId UUID of the originating request
     * @param message   Human-readable error description
     */
    data class Error(val requestId: String, val message: String) : InferenceEvent()
}
