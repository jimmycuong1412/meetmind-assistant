// T015: Fake CloudStreamingProvider for test injection
package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.inference.CloudStreamingProvider
import com.meetmind.assistant.inference.ValidationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Configurable fake for [CloudStreamingProvider].
 *
 * @param tokens             Ordered list of text tokens to emit.
 * @param firstTokenDelayMs  Delay before first token — used to simulate TTFT.
 * @param validateResult     Result returned by [validate].
 * @param throwOnStream      If non-null, thrown when [streamSuggestion] is invoked.
 * @param provider           CloudProvider label on the Complete event.
 */
class FakeCloudStreamingProvider(
    private val tokens: List<String> = listOf("Hello", " world"),
    private val firstTokenDelayMs: Long = 0L,
    private val validateResult: ValidationResult = ValidationResult.SUCCESS,
    private val throwOnStream: Exception? = null,
    private val provider: CloudProvider = CloudProvider.CLAUDE
) : CloudStreamingProvider {

    /** Captures the last request received — useful for data-minimisation assertions. */
    var lastRequest: CloudInferenceRequest? = null
        private set

    override fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent> = flow {
        lastRequest = request
        throwOnStream?.let { throw it }

        if (firstTokenDelayMs > 0) delay(firstTokenDelayMs)

        for (token in tokens) {
            emit(InferenceEvent.Token(request.requestId, token))
        }
        emit(
            InferenceEvent.Complete(
                requestId = request.requestId,
                fullText = tokens.joinToString(""),
                provider = provider
            )
        )
    }

    override suspend fun validate(): ValidationResult = validateResult
}
