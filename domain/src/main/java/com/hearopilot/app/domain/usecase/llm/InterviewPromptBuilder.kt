package com.hearopilot.app.domain.usecase.llm

/**
 * Builds the user-facing prompt sent to the LLM for each Interview Mode inference interval.
 *
 * Interview Mode needs a richer prompt than other modes because:
 *
 * 1. **Question priming** — the model must first classify whether a question was asked
 *    before generating an answer. Prepending a short detection cue improves recall on
 *    small on-device models (Gemma 3 1B) that otherwise miss implicit questions.
 *
 * 2. **Role anchoring** — answer quality is significantly higher when the role is
 *    restated in the user prompt, not just the system prompt, because small models
 *    frequently lose system-prompt context over long conversations.
 *
 * 3. **Context isolation** — unlike meeting modes, the rolling context buffer is *not*
 *    injected as a "Context:" prefix. Interview questions are typically self-contained;
 *    including prior context risks the model referencing earlier questions in its answer,
 *    which confuses the candidate.
 *
 * The prompt keeps its structure short so the model's attention is focused on the
 * `[TRANSCRIPT]` block rather than parsing a long instruction header.
 *
 * ## Caching behaviour
 * The role and mode structure are constant across intervals, making the prefix highly
 * cache-friendly in llama.cpp's KV cache. Only the `[TRANSCRIPT]` block changes each
 * interval, minimising prompt-processing time for subsequent inference calls.
 */
object InterviewPromptBuilder {

    // Maximum characters of transcript included in a single inference call.
    // Interview questions are typically short; 4 000 chars (~960 tokens) is more
    // than enough to capture a multi-part question with its lead-in context.
    // Keeping this below the 12 000-char meeting-mode ceiling speeds up prefill.
    private const val MAX_TRANSCRIPT_CHARS = 4_000

    /**
     * Build the user prompt for an Interview Mode inference call.
     *
     * @param role       The candidate's target role (e.g. "Developer"). Restated here
     *                   for role-anchoring even though it already appears in the system prompt.
     * @param newContent The most recent transcription text from this inference interval.
     * @return           A concise, structured prompt ready for the LLM.
     */
    fun build(role: String, newContent: String): String {
        val transcript = newContent.takeLast(MAX_TRANSCRIPT_CHARS).trim()
        // Two-line structure keeps the [TRANSCRIPT] label close to the text so small
        // models reliably associate the instruction with the content block.
        return "Role: $role\n[TRANSCRIPT]\n$transcript"
    }
}
