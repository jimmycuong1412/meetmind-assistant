// T019/T029: Claude inference via Anthropic Java SDK 2.24.0
// T011/T012: Implements CloudStreamingProvider; accepts optional baseUrl for MockWebServer in tests
package com.meetmind.assistant.inference

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams suggestion tokens from Anthropic Claude (claude-haiku-4-5-20251001).
 *
 * The Anthropic Java SDK's createStreaming() returns StreamResponse<RawMessageStreamEvent>.
 * StreamResponse.stream() returns java.util.stream.Stream<T> (a Java Stream).
 * We convert it to an Iterator to safely call suspend `emit()` from within the flow block.
 *
 * Constitution v2.0 data minimisation: only question text + system prompt transmitted.
 *
 * @param apiKey  Decrypted API key from [ApiKeyStore]; caller is responsible for zeroing.
 * @param baseUrl Optional endpoint override — used in [NetworkAuditTest] to point at MockWebServer.
 */
class ClaudeInferenceClient(
    private val apiKey: String,
    private val baseUrl: String? = null
) : CloudStreamingProvider {

    companion object {
        // Defence-in-depth: mirrors CloudInferenceEngine limits (constitution v2.0)
        private const val MAX_QUESTION_CHARS = 600
        private const val MAX_SYSTEM_CHARS = 320
    }

    fun streamSuggestion(
        request: CloudInferenceRequest,
        apiKey: String = this.apiKey,
        baseUrl: String? = this.baseUrl
    ): Flow<InferenceEvent> = flow {
        val builder = AnthropicOkHttpClient.builder().apiKey(apiKey)
        if (baseUrl != null) builder.baseUrl(baseUrl)
        val client = builder.build()

        val params = MessageCreateParams.builder()
            .model(Model.CLAUDE_HAIKU_4_5_20251001)
            .maxTokens(request.maxTokens.toLong())
            .system(request.systemPrompt.take(MAX_SYSTEM_CHARS))
            .addUserMessage(request.questionText.take(MAX_QUESTION_CHARS))
            .build()

        val fullTextBuilder = StringBuilder()

        // createStreaming() returns StreamResponse<RawMessageStreamEvent> (AutoCloseable)
        // Convert java.util.stream.Stream to Iterator so we can call suspend emit()
        client.messages().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            while (iterator.hasNext()) {
                val event = iterator.next()
                // contentBlockDelta() → Optional<RawContentBlockDeltaEvent>
                // .delta() → RawContentBlockDelta (non-optional, direct field)
                // .text()  → Optional<TextDelta>
                // .text()  → String (on TextDelta)
                if (event.isContentBlockDelta()) {
                    val deltaEvent = event.asContentBlockDelta()
                    val deltaOpt = deltaEvent.delta().text()
                    if (deltaOpt.isPresent) {
                        val text = deltaOpt.get().text()
                        fullTextBuilder.append(text)
                        emit(InferenceEvent.Token(request.requestId, text))
                    }
                }
            }
        }

        emit(
            InferenceEvent.Complete(
                requestId = request.requestId,
                fullText = fullTextBuilder.toString(),
                provider = CloudProvider.CLAUDE
            )
        )
    }.flowOn(Dispatchers.IO)

    /** Implements [CloudStreamingProvider.streamSuggestion] — delegates to the main fun. */
    override fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent> =
        streamSuggestion(request, apiKey, baseUrl)

    override suspend fun validate(): ValidationResult =
        CloudKeyValidationService().validate(CloudProvider.CLAUDE, apiKey)
}
