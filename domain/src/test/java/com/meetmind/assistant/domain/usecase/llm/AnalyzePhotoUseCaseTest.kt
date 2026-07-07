package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.LlmSamplerConfig
import com.meetmind.assistant.domain.model.ThermalThrottle
import com.meetmind.assistant.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzePhotoUseCaseTest {

    private class FakeLlmRepository(
        private val tokens: List<String> = listOf("A ", "whiteboard."),
        private val throwOnGenerate: Boolean = false
    ) : LlmRepository {
        var receivedImagePath: String? = null
        var receivedText: String? = null
        var beginCalled = false
        var endCalled = false
        var reloadCalled = false
        // Records the sequence of LLM calls so tests can assert ordering, not just presence.
        val callOrder = mutableListOf<String>()

        override suspend fun initialize(
            modelPath: String, systemPrompt: String?, loadImmediately: Boolean, mmprojPath: String?
        ) = Result.success(Unit)

        override suspend fun reloadModel(): Result<Unit> {
            reloadCalled = true
            callOrder.add("reload")
            return Result.success(Unit)
        }

        override fun isMemoryConstrained() = false

        override fun generateInsight(
            text: String, systemPrompt: String?, maxTokens: Int, imagePath: String?
        ): Flow<String> {
            receivedText = text
            receivedImagePath = imagePath
            return flow {
                // Recorded inside the cold flow builder so the order reflects when
                // generation actually runs (at collection), not Flow construction.
                callOrder.add("generate")
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
        override fun beginInference() { beginCalled = true; callOrder.add("begin") }
        override fun endInference() { endCalled = true; callOrder.add("end") }
        override suspend fun cleanup() {}
        override suspend fun updateSamplerConfig(config: LlmSamplerConfig) = Result.success(Unit)
        override val thermalThrottleFlow: Flow<ThermalThrottle> = emptyFlow()
    }

    @Test
    fun `returns concatenated tokens and passes image path through`() = runBlocking {
        val repo = FakeLlmRepository()
        val useCase = AnalyzePhotoUseCase(repo)

        val result = useCase("/data/photos/img.jpg", "Describe this image.")

        assertEquals("A whiteboard.", result.getOrThrow())
        assertEquals("/data/photos/img.jpg", repo.receivedImagePath)
        assertEquals("Describe this image.", repo.receivedText)
        assertTrue(repo.reloadCalled)
        assertTrue(repo.beginCalled)
        assertTrue(repo.endCalled)
        // Order matters: isGenerating must cover the model reload, and endInference
        // must release the busy flag only after generation finishes.
        assertEquals(listOf("begin", "reload", "generate", "end"), repo.callOrder)
    }

    @Test
    fun `returns failure and still ends inference when generation throws`() = runBlocking {
        val repo = FakeLlmRepository(throwOnGenerate = true)
        val useCase = AnalyzePhotoUseCase(repo)

        val result = useCase("/data/photos/img.jpg", "Describe this image.")

        assertTrue(result.isFailure)
        assertTrue(repo.endCalled)
        // Even on failure, endInference must be the LAST call (finally block).
        assertEquals("end", repo.callOrder.last())
    }

    @Test
    fun `blank model output is a failure`() = runBlocking {
        val repo = FakeLlmRepository(tokens = listOf("  ", ""))
        val useCase = AnalyzePhotoUseCase(repo)

        val result = useCase("/data/photos/img.jpg", "Describe this image.")

        assertFalse(result.isSuccess)
    }
}
