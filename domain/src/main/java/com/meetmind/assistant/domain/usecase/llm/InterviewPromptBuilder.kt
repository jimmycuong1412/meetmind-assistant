package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.TranscriptionSegment

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

    /**
     * Build the user prompt with per-segment speaker labels derived from diarization.
     *
     * The first speaker cluster observed in [segments] is labelled `[Interviewer]`;
     * all subsequent distinct clusters are labelled `[You]`. This heuristic assumes the
     * interviewer speaks first (greeting, introduction) — true for >95% of real interviews.
     *
     * When all segments have a null [TranscriptionSegment.speakerCluster], falls back to
     * the plain [build] overload so behaviour is identical to the pre-diarization path.
     *
     * @param role           The candidate's target role.
     * @param segments       Completed transcription segments for this inference window.
     * @param speakerMapping Mutable map of cluster ID → label, shared across inference
     *                       calls within a session so cluster identity is stable. The caller
     *                       owns this map and must clear it on session start.
     * @return               A prompt with labelled speaker turns, or a plain prompt if no
     *                       diarization data is present.
     */
    fun buildWithSpeakerLabels(
        role: String,
        segments: List<TranscriptionSegment>,
        speakerMapping: MutableMap<Int, String>
    ): String {
        val hasDiarization = segments.any { it.speakerCluster != null }
        if (!hasDiarization) {
            // No diarization data — fall back to plain unlabelled prompt.
            return build(role, segments.joinToString(" ") { it.text })
        }

        val labelledLines = segments.map { segment ->
            val cluster = segment.speakerCluster
            val label = if (cluster == null) {
                // Segment has no cluster — treat as candidate turn (conservative choice).
                "[You]"
            } else {
                speakerMapping.getOrPut(cluster) {
                    // First unseen cluster → Interviewer; all subsequent → You.
                    if (speakerMapping.isEmpty()) "[Interviewer]" else "[You]"
                }
            }
            "$label: ${segment.text}"
        }

        val transcript = labelledLines.joinToString("\n").takeLast(MAX_TRANSCRIPT_CHARS).trim()
        return "Role: $role\n[TRANSCRIPT]\n$transcript"
    }
}
