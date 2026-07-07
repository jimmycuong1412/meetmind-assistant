package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.repository.LlmRepository
import kotlinx.coroutines.CancellationException

/**
 * Runs on-device vision analysis of a captured photo and returns the text description.
 *
 * Runs immediately after capture (independent of the recording mode's insight cadence).
 * Serialization with the periodic insight loop is two-layered:
 *  - [LlmRepository.beginInference]/[endInference] make [LlmRepository.isGenerating] cover
 *    this call, so SyncSttLlmUseCase skips its tick while a photo is being analyzed.
 *  - The repository's single-thread dispatcher queues any overlap at the native boundary.
 *
 * @property llmRepository Repository for LLM operations (must be vision-initialized:
 *   the active model config supplied an mmprojPath at initialize time)
 */
class AnalyzePhotoUseCase(
    private val llmRepository: LlmRepository
) {
    companion object {
        // Descriptions are context for the next insight, not a deliverable — keep them short.
        private const val MAX_TOKENS_PHOTO_DESCRIPTION = 256

        // English on purpose: descriptions are intermediate LLM-to-LLM context (like the
        // JSON field names, which also stay English across all 25 locales). The next
        // insight re-renders the information in the session's output language.
        private const val PHOTO_SYSTEM_PROMPT =
            "You are a visual assistant. Describe the image factually and concisely " +
            "for meeting notes. Report visible text, diagrams, charts and key items. " +
            "Plain sentences only — no JSON, no markdown."
    }

    /**
     * @param imagePath Absolute path to the captured photo file
     * @param analysisPrompt Localized instruction text shown to the model as the user prompt
     * @return The generated description, or failure (never throws except for cancellation)
     */
    suspend operator fun invoke(imagePath: String, analysisPrompt: String): Result<String> {
        return try {
            llmRepository.beginInference()
            llmRepository.reloadModel().getOrThrow()

            val builder = StringBuilder()
            llmRepository.generateInsight(
                text = analysisPrompt,
                systemPrompt = PHOTO_SYSTEM_PROMPT,
                maxTokens = MAX_TOKENS_PHOTO_DESCRIPTION,
                imagePath = imagePath
            ).collect { token -> builder.append(token) }

            val description = builder.toString().trim()
            if (description.isBlank()) {
                Result.failure(IllegalStateException("Vision model returned empty description"))
            } else {
                Result.success(description)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            llmRepository.endInference()
        }
    }
}
