package com.meetmind.assistant.domain.usecase.ask

import com.meetmind.assistant.domain.model.AskMessage
import com.meetmind.assistant.domain.model.AskRole
import com.meetmind.assistant.domain.model.LlmSamplerConfig
import com.meetmind.assistant.domain.model.SessionWithDetails
import com.meetmind.assistant.domain.model.ThermalThrottle
import com.meetmind.assistant.domain.model.TranscriptionSegment
import com.meetmind.assistant.domain.model.TranscriptionSession
import com.meetmind.assistant.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskMeetingsUseCaseTest {

    private class FakeLlmRepository(
        private val tokens: List<String>,
        private val throwOnGenerate: Boolean = false
    ) : LlmRepository {
        var prompt: String? = null
        var systemPrompt: String? = null
        var rawPrompt: Boolean? = null
        var generateCalls = 0
        val callOrder = mutableListOf<String>()

        override suspend fun initialize(
            modelPath: String, systemPrompt: String?, loadImmediately: Boolean, mmprojPath: String?
        ) = Result.success(Unit)

        override suspend fun reloadModel(): Result<Unit> {
            callOrder += "reload"
            return Result.success(Unit)
        }

        override fun isMemoryConstrained() = false

        override fun generateInsight(
            text: String, systemPrompt: String?, maxTokens: Int, imagePath: String?, rawPrompt: Boolean
        ): Flow<String> {
            prompt = text
            this.systemPrompt = systemPrompt
            this.rawPrompt = rawPrompt
            generateCalls++
            return flow {
                callOrder += "generate"
                if (throwOnGenerate) throw RuntimeException("native OOM")
                tokens.forEach { emit(it) }
            }
        }

        override suspend fun updateSystemPrompt(systemPrompt: String) = Result.success(Unit)
        override fun useConservativeThreads() {}
        override suspend fun checkAndCacheMemoryConstraint() {}
        override suspend fun recordConstrainedInference(inputChars: Int) {}
        override fun isLargeContext(inputChars: Int) = false
        override val isGenerating: Boolean get() = false
        override fun beginInference() { callOrder += "begin" }
        override fun endInference() { callOrder += "end" }
        override suspend fun cleanup() {}
        override suspend fun updateSamplerConfig(config: LlmSamplerConfig) = Result.success(Unit)
        override val thermalThrottleFlow: Flow<ThermalThrottle> = emptyFlow()
    }

    private val sprintReview = SessionWithDetails(
        session = TranscriptionSession(id = "a", name = "Sprint review", createdAt = 0L, lastModifiedAt = 0L),
        segments = listOf(
            TranscriptionSegment("s1", "a", "Lunch is at noon.", 0L, isComplete = true, speaker = "Alex"),
            TranscriptionSegment("s2", "a", "Dora takes the browser blocker fix.", 872_000L, isComplete = true, speaker = "Dora")
        ),
        insights = emptyList()
    )

    @Test
    fun `streams a cited answer and maps citations to their passages`() = runBlocking {
        val repo = FakeLlmRepository(listOf("Dora owns ", "the fix [2]."))
        val useCase = AskMeetingsUseCase(repo)

        val events = useCase(listOf(sprintReview), "Who owns the browser blocker fix?").toList()

        val reading = events.first() as AskMeetingsUseCase.Progress.Reading
        assertEquals(1, reading.meetingCount)
        val done = events.last() as AskMeetingsUseCase.Progress.Done
        assertEquals("Dora owns the fix [1].", done.answer.text)
        assertFalse(done.answer.notFound)
        val citation = done.answer.citations.single()
        assertEquals(1, citation.number)
        assertEquals("s2", citation.segmentId)
        assertEquals("Dora", citation.speaker)
        assertEquals(872_000L, citation.offsetMs)
        assertEquals("Sprint review", citation.sessionName)
        assertTrue(events.any { it is AskMeetingsUseCase.Progress.Writing && it.partial == "Dora owns " })
        assertEquals(listOf("begin", "reload", "generate", "end"), repo.callOrder)
        assertEquals(true, repo.rawPrompt)
        assertEquals(AskMeetingsUseCase.SYSTEM_PROMPT, repo.systemPrompt)
    }

    @Test
    fun `prompt carries excerpts, recent history without markers, and the question`() = runBlocking {
        val repo = FakeLlmRepository(listOf("ok"))
        val history = listOf(
            AskMessage("m1", "c", AskRole.USER, "Who owns the fix?", 1L),
            AskMessage("m2", "c", AskRole.ASSISTANT, "Dora owns it [1].", 2L)
        )

        AskMeetingsUseCase(repo)(listOf(sprintReview), "When is it due?", history).toList()

        val prompt = repo.prompt!!
        assertTrue(prompt.startsWith("Meeting excerpts:\n[1] \"Sprint review\""))
        assertTrue(prompt.contains("Q: Who owns the fix?\nA: Dora owns it."))
        assertTrue(prompt.endsWith("Question: When is it due?"))
    }

    @Test
    fun `reports not found when the model says so`() = runBlocking {
        val repo = FakeLlmRepository(listOf("NOT_FOUND The release date is never mentioned."))

        val done = AskMeetingsUseCase(repo)(listOf(sprintReview), "When is the release?").toList().last()
            as AskMeetingsUseCase.Progress.Done

        assertTrue(done.answer.notFound)
        assertEquals("The release date is never mentioned.", done.answer.text)
    }

    @Test
    fun `meetings with no text answer not found without calling the model`() = runBlocking {
        val repo = FakeLlmRepository(listOf("unused"))
        val empty = sprintReview.copy(segments = emptyList())

        val done = AskMeetingsUseCase(repo)(listOf(empty), "Anything?").toList().last() as AskMeetingsUseCase.Progress.Done

        assertTrue(done.answer.notFound)
        assertEquals(0, repo.generateCalls)
        assertNull(repo.prompt)
    }

    @Test
    fun `ends inference even when generation fails`() {
        val repo = FakeLlmRepository(emptyList(), throwOnGenerate = true)

        val result = runCatching {
            runBlocking { AskMeetingsUseCase(repo)(listOf(sprintReview), "fix?").toList() }
        }

        assertTrue(result.isFailure)
        assertEquals("end", repo.callOrder.last())
    }
}
