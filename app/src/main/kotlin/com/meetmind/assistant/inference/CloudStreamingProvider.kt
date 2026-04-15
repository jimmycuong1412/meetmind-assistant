// T007: Interface abstracting per-provider cloud streaming — enables test injection
package com.meetmind.assistant.inference

import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.InferenceEvent
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over Gemini and Claude streaming clients.
 *
 * Production implementations: [GeminiInferenceClient], [ClaudeInferenceClient].
 * Test implementation: FakeCloudStreamingProvider (in src/test/).
 *
 * Extracting this interface allows [CloudInferenceEngine] to be tested without
 * initialising the Firebase AI Logic SDK or Anthropic Java SDK.
 */
interface CloudStreamingProvider {
    fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent>
    suspend fun validate(): ValidationResult
}
