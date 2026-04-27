package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.usecase.transcription.UpdateInsightContentUseCase
import kotlinx.coroutines.flow.first

/**
 * Shared use case for regenerating a single LLM insight in-place.
 *
 * Encapsulates the full regeneration pipeline so it can be invoked identically
 * from [com.hearopilot.app.presentation.main.MainViewModel] and
 * [com.hearopilot.app.presentation.sessiondetails.SessionDetailsViewModel]
 * without duplicating any logic.
 *
 * ## Pipeline
 * 1. Validate the LLM model path (fail-fast with [RegenerateResult.ModelNotDownloaded]).
 * 2. Initialize the LLM engine (non-blocking warmup via `loadImmediately = false`).
 * 3. Build the source text:
 *    - If the insight has [LlmInsight.sourceSegmentIds], use only those segments.
 *    - Otherwise fall back to the full transcript (insight is a whole-session summary).
 * 4. Run [GenerateFinalInsightUseCase] with the caller-supplied session context.
 * 5. Persist updated title / content / tasks via [UpdateInsightContentUseCase].
 * 6. Release the LLM engine.
 *
 * The caller is responsible for managing foreground-service lifecycle and UI loading
 * state (e.g. `isInitializingLlm`, `regeneratingInsightId`) around the `invoke` call.
 *
 * @property generateFinalInsightUseCase LLM inference use case
 * @property updateInsightContentUseCase Persistence use case for insight fields
 * @property initializeLlmUseCase LLM initialization use case
 * @property settingsRepository Source of the persisted LLM model path
 * @property llmRepository Direct LLM handle for post-generation cleanup
 */
class RegenerateInsightUseCase(
    private val generateFinalInsightUseCase: GenerateFinalInsightUseCase,
    private val updateInsightContentUseCase: UpdateInsightContentUseCase,
    private val initializeLlmUseCase: InitializeLlmUseCase,
    private val settingsRepository: SettingsRepository,
    private val llmRepository: LlmRepository
) {
    /**
     * Outcome of a regeneration attempt.
     *
     * Callers map each variant to their own UI state rather than receiving raw exceptions,
     * which keeps the ViewModel error-handling paths explicit and testable.
     */
    sealed class RegenerateResult {
        /** Insight updated successfully; [insight] contains the fresh content. */
        data class Success(val insight: LlmInsight) : RegenerateResult()

        /** LLM model path is blank — user needs to download the model first. */
        object ModelNotDownloaded : RegenerateResult()

        /**
         * LLM initialization failed (corrupt model file, OOM, native crash, etc.).
         * [cause] is the underlying exception.
         */
        data class InitError(val cause: Throwable) : RegenerateResult()

        /**
         * Source text is empty — no segments are associated with this insight and the
         * session transcript is blank. Nothing useful can be generated.
         */
        object EmptySource : RegenerateResult()

        /** LLM returned a blank response — treated as a no-op (insight unchanged). */
        object EmptyResponse : RegenerateResult()

        /** Unexpected error during inference or persistence. */
        data class Error(val cause: Throwable) : RegenerateResult()
    }

    /**
     * Run the regeneration pipeline.
     *
     * @param insight          The existing insight to regenerate (provides [LlmInsight.id]
     *                         and [LlmInsight.sourceSegmentIds]).
     * @param sessionId        Session the insight belongs to (forwarded to [GenerateFinalInsightUseCase]).
     * @param mode             Recording mode that originally produced the insight (drives prompt).
     * @param outputLanguage   BCP-47 output language code, or `null` for no override.
     * @param topic            Session topic / interview role injected into the prompt.
     * @param allSegments      All completed segments for the session, in timestamp order.
     *                         The use case filters this list using [LlmInsight.sourceSegmentIds].
     */
    suspend operator fun invoke(
        insight: LlmInsight,
        sessionId: String,
        mode: RecordingMode,
        outputLanguage: String?,
        topic: String?,
        allSegments: List<TranscriptionSegment>
    ): RegenerateResult {
        // ── 1. Model path check ──────────────────────────────────────────────
        val settings = settingsRepository.getSettings().first()
        if (settings.llmModelPath.isBlank()) {
            return RegenerateResult.ModelNotDownloaded
        }

        // ── 2. LLM initialization ────────────────────────────────────────────
        initializeLlmUseCase(settings.llmModelPath, loadImmediately = false)
            .onFailure { e -> return RegenerateResult.InitError(e) }

        // ── 3. Build source text ─────────────────────────────────────────────
        val completedSegments = allSegments
            .filter { it.isComplete }
            .sortedBy { it.timestamp }

        val sourceText = if (insight.sourceSegmentIds.isEmpty()) {
            // No specific segments — use the full session transcript.
            completedSegments.joinToString(" ") { it.text }
        } else {
            val idSet = insight.sourceSegmentIds.toSet()
            completedSegments.filter { it.id in idSet }.joinToString(" ") { it.text }
        }

        if (sourceText.isBlank()) {
            return RegenerateResult.EmptySource
        }

        return try {
            // ── 4. Run inference ─────────────────────────────────────────────
            val insightResult = generateFinalInsightUseCase(
                pendingText = sourceText,
                sessionId = sessionId,
                mode = mode,
                outputLanguage = outputLanguage,
                topic = topic
            )

            val newInsight = insightResult.getOrElse { e ->
                return RegenerateResult.Error(e)
            } ?: return RegenerateResult.EmptyResponse

            // ── 5. Persist ───────────────────────────────────────────────────
            updateInsightContentUseCase.updateTitle(insight.id, newInsight.title)
            updateInsightContentUseCase.updateContent(insight.id, newInsight.content)
            updateInsightContentUseCase.updateTasks(insight.id, newInsight.tasks)

            RegenerateResult.Success(newInsight)
        } catch (e: Exception) {
            RegenerateResult.Error(e)
        } finally {
            // ── 6. Cleanup ───────────────────────────────────────────────────
            try { llmRepository.cleanup() } catch (_: Exception) {}
        }
    }
}
