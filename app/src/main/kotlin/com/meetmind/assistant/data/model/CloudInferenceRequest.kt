// T011: Payload passed to CloudInferenceEngine for a single suggestion request
package com.meetmind.assistant.data.model

import java.util.UUID

/**
 * Encapsulates everything needed to call a cloud AI provider for one suggestion.
 *
 * Constitution v2.0 constraint: only the detected question text (≤150 tokens) and
 * a short system prompt (≤80 tokens) are transmitted — raw audio NEVER leaves device.
 */
data class CloudInferenceRequest(
    /** Unique ID used to correlate [InferenceEvent]s back to this request */
    val requestId: String = UUID.randomUUID().toString(),

    /**
     * The detected question text to answer.
     * Must be ≤150 tokens per constitution v2.0.
     */
    val questionText: String,

    /**
     * System / persona prompt injected before the question.
     * Must be ≤80 tokens per constitution v2.0.
     * Typically derived from the active [ContextProfile].
     */
    val systemPrompt: String,

    /** Which provider to use for this request */
    val provider: CloudProvider,

    /** Active session mode — affects framing in the system prompt */
    val sessionMode: SessionMode,

    /**
     * Maximum tokens the provider should generate.
     * Default 200 tokens; cloud providers have more headroom than on-device (40 tokens).
     */
    val maxTokens: Int = 200,

    /** Epoch ms when this request was created — used for 5 s fallback SLA */
    val createdAt: Long = System.currentTimeMillis()
)
