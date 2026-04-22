package com.meetmind.assistant.data

import com.meetmind.assistant.data.model.GemmaModelVariant
import com.meetmind.assistant.helpers.testDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [ModelConfigRepository] GemmaModelVariant persistence.
 *
 * C3.1 — setModelVariant persists and observeVariant reflects the change
 * C3.2 — Default variant is Q4_K_M on fresh DataStore
 *
 * spec 009 — T026
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ModelConfigRepositoryVariantTest {

    private fun makeRepo(): ModelConfigRepository {
        val scope = TestScope(StandardTestDispatcher())
        // Use the DataStore constructor to inject an in-memory store
        return ModelConfigRepository(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            dataStore = testDataStore(scope)
        )
    }

    /**
     * C3.1 — After [ModelConfigRepository.setModelVariant] with Q8_0,
     * [ModelConfigRepository.observeVariant] emits Q8_0 on the next collect.
     */
    @Test
    fun `C3_1 setModelVariant persists variant`() = runTest {
        val scope = TestScope(testScheduler)
        val repo = ModelConfigRepository(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            dataStore = testDataStore(scope)
        )
        repo.setModelVariant(GemmaModelVariant.Q8_0)
        assertEquals(GemmaModelVariant.Q8_0, repo.observeVariant().first())
    }

    /**
     * C3.2 — A freshly constructed [ModelConfigRepository] with empty DataStore
     * returns [GemmaModelVariant.Q4_K_M] from [ModelConfigRepository.observeVariant].
     */
    @Test
    fun `C3_2 default variant is Q4_K_M on fresh DataStore`() = runTest {
        val scope = TestScope(testScheduler)
        val repo = ModelConfigRepository(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            dataStore = testDataStore(scope)
        )
        assertEquals(GemmaModelVariant.Q4_K_M, repo.observeVariant().first())
    }

    /**
     * Verify all three variants can be persisted and retrieved correctly.
     */
    @Test
    fun `setModelVariant round-trips for all three variants`() = runTest {
        GemmaModelVariant.entries.forEach { variant ->
            val scope = TestScope(testScheduler)
            val repo = ModelConfigRepository(
                context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                dataStore = testDataStore(scope)
            )
            repo.setModelVariant(variant)
            assertEquals(variant, repo.observeVariant().first())
        }
    }
}
