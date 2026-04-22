// T024–T028 (spec 007): Contract tests for ModelConfigRepository
package com.meetmind.assistant.data

import com.meetmind.assistant.data.model.ModelConfig
import com.meetmind.assistant.helpers.FakeLlamaJni
import com.meetmind.assistant.helpers.testDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for [ModelConfigRepository].
 *
 * Contracts:
 *   C2.1 — setModelPath → observe() returns updated path
 *   C2.2 — isModelReady returns false when file absent; true when file exists + canLoad = true
 *   C2.3 — setNThreads(0) stores 1; setNThreads(99) stores 16
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ModelConfigRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    /** Each test gets an isolated scope + DataStore to prevent state leaks. */
    private fun makeRepo(): Pair<ModelConfigRepository, TestScope> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = testDataStore(scope)
        val repo = ModelConfigRepository(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            dataStore = dataStore
        )
        return Pair(repo, scope)
    }

    // ── C2.1: setModelPath → observe returns updated path ──────────────────────

    @Test
    fun `C2-1 setModelPath persists and observe returns updated path`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            val newPath = "/sdcard/Download/my_model.gguf"
            repo.setModelPath(newPath)
            val config = repo.observe().first()
            assertEquals(newPath, config.modelPath)
        }
    }

    @Test
    fun `C2-1b initial observe returns default path when nothing set`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            val config = repo.observe().first()
            assertEquals(ModelConfig.DEFAULT_PATH, config.modelPath)
        }
    }

    // ── C2.2: isModelReady reflects file existence + canLoad ──────────────────

    @Test
    fun `C2-2a isModelReady returns false when file does not exist`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setModelPath("/nonexistent/path/model.gguf")
            val fake = FakeLlamaJni(canLoadResult = true)
            assertFalse(repo.isModelReady(fake))
        }
    }

    @Test
    fun `C2-2b isModelReady returns false when canLoad returns false`() {
        val (repo, scope) = makeRepo()
        val modelFile = tmpFolder.newFile("model.gguf")
        scope.runTest {
            repo.setModelPath(modelFile.absolutePath)
            val fake = FakeLlamaJni(canLoadResult = false)
            assertFalse(repo.isModelReady(fake))
        }
    }

    @Test
    fun `C2-2c isModelReady returns true when file exists and canLoad is true`() {
        val (repo, scope) = makeRepo()
        val modelFile = tmpFolder.newFile("model.gguf")
        scope.runTest {
            repo.setModelPath(modelFile.absolutePath)
            val fake = FakeLlamaJni(canLoadResult = true)
            assertTrue(repo.isModelReady(fake))
        }
    }

    // ── C2.3: setNThreads clamps to 1..16 ────────────────────────────────────

    @Test
    fun `C2-3a setNThreads(0) stores 1`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setNThreads(0)
            assertEquals(1, repo.observe().first().nThreads)
        }
    }

    @Test
    fun `C2-3b setNThreads(99) stores 16`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setNThreads(99)
            assertEquals(16, repo.observe().first().nThreads)
        }
    }

    @Test
    fun `C2-3c setNThreads(6) stores 6 unchanged`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setNThreads(6)
            assertEquals(6, repo.observe().first().nThreads)
        }
    }

    @Test
    fun `C2-3d setNThreads(1) stores 1 at lower boundary`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setNThreads(1)
            assertEquals(1, repo.observe().first().nThreads)
        }
    }

    @Test
    fun `C2-3e setNThreads(16) stores 16 at upper boundary`() {
        val (repo, scope) = makeRepo()
        scope.runTest {
            repo.setNThreads(16)
            assertEquals(16, repo.observe().first().nThreads)
        }
    }
}
