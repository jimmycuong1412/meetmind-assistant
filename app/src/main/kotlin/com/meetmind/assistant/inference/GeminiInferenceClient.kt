// T018/T028: Gemini inference via Firebase AI Logic SDK; T008: implements CloudStreamingProvider
package com.meetmind.assistant.inference

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams suggestion tokens from Google Gemini (gemini-2.5-flash) via Firebase AI Logic SDK.
 *
 * The Firebase project is configured via google-services.json — no API key is passed in code.
 * Constitution v2.0 data minimisation: only question text + system prompt are transmitted.
 */
class GeminiInferenceClient : CloudStreamingProvider {

    override fun streamSuggestion(request: CloudInferenceRequest): Flow<InferenceEvent> = flow {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = generationConfig {
                    maxOutputTokens = request.maxTokens
                },
                systemInstruction = content { text(request.systemPrompt) }
            )

        val fullTextBuilder = StringBuilder()
        model.generateContentStream(content { text(request.questionText) })
            .collect { response ->
                val chunk = response.text ?: return@collect
                fullTextBuilder.append(chunk)
                emit(InferenceEvent.Token(request.requestId, chunk))
            }

        emit(
            InferenceEvent.Complete(
                requestId = request.requestId,
                fullText = fullTextBuilder.toString(),
                provider = CloudProvider.GEMINI
            )
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun validate(): ValidationResult =
        CloudKeyValidationService().validate(CloudProvider.GEMINI, "")
}
