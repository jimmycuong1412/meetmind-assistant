// T012: Suggestion data class — extended with inferenceMode and cloudRequestId for cloud AI feature
package com.meetmind.assistant.data.model

import java.util.UUID

/**
 * A suggestion produced in response to a detected question.
 *
 * Supports both on-device (llama.cpp / Phi-3-mini) and cloud inference paths.
 * [inferenceMode] and [cloudRequestId] are required per spec 005.
 */
data class Suggestion(
    /** Unique identifier for this suggestion */
    val id: String = UUID.randomUUID().toString(),

    /** The question text that triggered this suggestion */
    val questionText: String,

    /** The generated suggestion text (may be built incrementally via streaming) */
    val text: String,

    /** Whether this suggestion was produced on-device or via cloud */
    val inferenceMode: InferenceMode = InferenceMode.ON_DEVICE,

    /**
     * UUID of the [CloudInferenceRequest] that produced this suggestion.
     * Non-null only when [inferenceMode] is [InferenceMode.CLOUD].
     */
    val cloudRequestId: String? = null,

    /** Which cloud provider produced this suggestion, if [inferenceMode] is CLOUD */
    val cloudProvider: CloudProvider? = null,

    /** If cloud fell back to on-device, the reason is recorded here */
    val fallbackReason: FallbackReason? = null,

    /** Epoch ms when the suggestion was created */
    val createdAt: Long = System.currentTimeMillis(),

    /** True while tokens are still streaming in */
    val isStreaming: Boolean = false
)
