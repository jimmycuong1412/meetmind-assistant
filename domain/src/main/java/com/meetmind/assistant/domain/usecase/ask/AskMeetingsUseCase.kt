package com.meetmind.assistant.domain.usecase.ask

import com.meetmind.assistant.domain.model.AskCitation
import com.meetmind.assistant.domain.model.AskMessage
import com.meetmind.assistant.domain.model.AskRole
import com.meetmind.assistant.domain.model.SessionWithDetails
import com.meetmind.assistant.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Answers a question from the selected meetings with the on-device model, citing
 * the excerpts each claim comes from.
 *
 * Each call is a single stateless inference (the repository clears the KV cache per
 * call): the chosen excerpts, the last [HISTORY_TURNS] exchanges and the question go
 * into one prompt. The caller initialises the LLM (model path) beforehand and keeps
 * other inference from running concurrently — llama.cpp is not thread-safe.
 */
class AskMeetingsUseCase(
    private val llmRepository: LlmRepository
) {

    sealed interface Progress {
        /** Excerpts chosen; inference is about to start. */
        data class Reading(val excerptCount: Int, val meetingCount: Int, val truncated: Boolean) : Progress

        /** Answer text so far, with citation markers hidden. */
        data class Writing(val partial: String) : Progress

        data class Done(val answer: Answer) : Progress
    }

    data class Answer(
        val text: String,
        val citations: List<AskCitation>,
        val notFound: Boolean
    )

    /**
     * @param sessions Meetings to read, in the order the user picked them.
     * @param history Earlier messages of this chat, oldest first.
     * @param meetingLabel Localized name for a meeting the user never named, by its index.
     */
    operator fun invoke(
        sessions: List<SessionWithDetails>,
        question: String,
        history: List<AskMessage> = emptyList(),
        meetingLabel: (sessionIndex: Int) -> String = { "Meeting ${it + 1}" }
    ): Flow<Progress> = flow {
        // A follow-up such as "who owns it?" carries no keywords of its own, so the
        // previous question also steers retrieval.
        val previousQuestion = history.lastOrNull { it.role == AskRole.USER }?.text.orEmpty()
        val context = AskContextBuilder.build(sessions, "$question $previousQuestion")
        emit(Progress.Reading(context.passages.size, sessions.size, context.truncated))

        if (context.passages.isEmpty()) {
            emit(Progress.Done(Answer(text = "", citations = emptyList(), notFound = true)))
            return@flow
        }

        val prompt = buildUserPrompt(
            excerpts = AskContextBuilder.formatExcerpts(context, meetingLabel),
            history = history,
            question = question
        )

        llmRepository.beginInference()
        try {
            if (llmRepository.isLargeContext(prompt.length)) llmRepository.useConservativeThreads()
            llmRepository.reloadModel().getOrThrow()
            llmRepository.checkAndCacheMemoryConstraint()

            val raw = StringBuilder()
            llmRepository.generateInsight(
                text = prompt,
                systemPrompt = SYSTEM_PROMPT,
                maxTokens = MAX_ANSWER_TOKENS,
                rawPrompt = true
            ).collect { token ->
                raw.append(token)
                emit(Progress.Writing(AskAnswerParser.streamingPreview(raw.toString())))
            }
            llmRepository.recordConstrainedInference(prompt.length)

            val parsed = AskAnswerParser.parse(raw.toString(), context.passages.size)
            val citations = parsed.citedExcerpts.mapIndexed { i, excerpt ->
                val passage = context.passages[excerpt]
                AskCitation(
                    number = i + 1,
                    sessionId = passage.sessionId,
                    sessionName = passage.sessionName,
                    segmentId = passage.segmentId,
                    offsetMs = passage.offsetMs,
                    speaker = passage.speaker,
                    quote = passage.text
                )
            }
            emit(Progress.Done(Answer(parsed.text, citations, parsed.notFound)))
        } finally {
            llmRepository.endInference()
        }
    }

    internal fun buildUserPrompt(excerpts: String, history: List<AskMessage>, question: String): String =
        buildString {
            append("Meeting excerpts:\n")
            append(excerpts)
            val earlier = history.takeLast(HISTORY_TURNS * 2)
            if (earlier.isNotEmpty()) {
                append("\n\nEarlier in this chat:\n")
                earlier.forEach { message ->
                    // Old answers cite a different excerpt numbering; drop their markers.
                    val text = message.text.replace(Regex("\\s*\\[\\d+]"), "").take(HISTORY_CHARS).trim()
                    append(if (message.role == AskRole.USER) "Q: " else "A: ")
                    append(text)
                    append('\n')
                }
            }
            append("\n\nQuestion: ")
            append(question.trim())
        }

    companion object {
        private const val MAX_ANSWER_TOKENS = 384
        private const val HISTORY_TURNS = 2
        private const val HISTORY_CHARS = 400

        /**
         * Kept in English on purpose: small models follow English instructions most
         * reliably, and rule 4 makes the answer follow the question's language.
         */
        const val SYSTEM_PROMPT = """You are MeetMind, an assistant that answers questions about the user's own meeting notes.
Rules:
1. Use ONLY the meeting excerpts in the message. Never use outside knowledge or guess.
2. After every fact, cite the excerpt numbers it comes from in square brackets, like [2] or [1][3].
3. If the excerpts do not contain the answer, start your reply with NOT_FOUND, then say in one sentence what the excerpts do cover.
4. Answer in the same language as the question.
5. Be brief: at most 6 sentences or bullet points. No preamble and no list of sources at the end."""
    }
}
