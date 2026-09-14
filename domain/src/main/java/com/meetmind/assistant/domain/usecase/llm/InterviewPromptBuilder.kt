package com.meetmind.assistant.domain.usecase.llm

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

    // Cap on the injected candidate profile (~180 tokens). Generous for the intended
    // shorthand (stacks, scale, a few war stories) while leaving the transcript and the
    // output budget room inside Gemma 3 1B's 4096-token window.
    private const val MAX_PROFILE_CHARS = 750

    /**
     * Build the user prompt for an Interview Mode inference call.
     *
     * @param role       The candidate's target role (e.g. "Developer"). Restated here
     *                   for role-anchoring even though it already appears in the system prompt.
     * @param newContent The most recent transcription text from this inference interval.
     * @return           A concise, structured prompt ready for the LLM.
     */
    fun build(role: String, newContent: String): String = build(role, newContent, null)

    /**
     * Build the user prompt, optionally grounded in the candidate's own experience.
     *
     * @param role       The candidate's target role (e.g. "Senior DevOps Engineer").
     * @param newContent The most recent transcription text from this inference interval.
     * @param candidateProfile Optional background — clouds, scale, tooling, war stories.
     *   When present, the model reaches for the candidate's real incidents instead of
     *   textbook generalities, which is the difference between an answer that survives a
     *   follow-up and one that does not.
     *
     * ## Ordering matters for cache reuse
     * Role and profile are constant for the whole session, so they are emitted **before**
     * the transcript. That keeps the prompt prefix byte-identical across intervals and
     * lets llama.cpp reuse its KV cache, so only the changing `[TRANSCRIPT]` block needs
     * prefill. Putting the profile after the transcript would invalidate the cache on
     * every call and add seconds of latency to a mode whose whole point is speed.
     */
    fun build(role: String, newContent: String, candidateProfile: String?): String {
        val transcript = newContent.takeLast(MAX_TRANSCRIPT_CHARS).trim()

        return buildString {
            append("Role: ").append(role)
            candidateProfile?.trim()?.takeIf { it.isNotEmpty() }?.let { profile ->
                append("\n[CANDIDATE PROFILE]\n")
                // Bounded so a long profile can never crowd out the transcript in the
                // 4096-token context window.
                append(profile.take(MAX_PROFILE_CHARS))
            }
            // Keep the [TRANSCRIPT] label adjacent to the text so small models reliably
            // associate the instruction with the content block.
            append("\n[TRANSCRIPT]\n").append(transcript)
        }
    }
}
