// T009 (spec 008): Test double for CloudInferenceEngine used in ConversationAnalyzer tests
package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.inference.CloudStreamingProvider
import com.meetmind.assistant.inference.ValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Configurable fake [CloudStreamingProvider] for contract tests.
 *
 * Captures the last received request and tracks call counts so tests can assert
 * data minimisation (C1.6: lastReceivedText.length ≤ 600) and non-inference
 * of NoSignal paths (C1.1: callCount == 0).
 *
 * @param stubTokens      Tokens to emit per call. Defaults to a single stub token.
 * @param throwOnStream   If non-null, throws this exception instead of emitting tokens.
 */
class FakeInferenceEngine(
    private val stubTokens: List<String> = listOf("stub suggestion"),
    private val throwOnStream: Exception? = null
) : CloudStreamingProvider {

    /** Number of times [streamSuggestion] has been called. Thread-safe. */
    val callCount: AtomicInteger = AtomicInteger(0)

    /** The last [CloudInferenceRequest] passed to [streamSuggestion]. */
    var lastRequest: CloudInferenceRequest? = null
        private set

    /** Convenience accessor for the last question text passed in. */
    val lastReceivedText: String get() = lastRequest?.questionText ?: ""

    override suspend fun validate(): ValidationResult = ValidationResult.SUCCESS

    override fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent> {
        callCount.incrementAndGet()
        lastRequest = request
        return flow {
            if (throwOnStream != null) throw throwOnStream
            val requestId = request.requestId
            stubTokens.forEach { token -> emit(InferenceEvent.Token(requestId, token)) }
            emit(InferenceEvent.Complete(requestId, stubTokens.joinToString(""), CloudProvider.GEMINI))
        }
    }
}
