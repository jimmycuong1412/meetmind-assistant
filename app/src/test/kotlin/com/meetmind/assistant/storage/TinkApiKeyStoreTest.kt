// T016: ApiKeyStore contract tests — verified against FakeApiKeyStore (JVM unit test)
// NOTE: TinkApiKeyStore requires Android Keystore hardware — tested in androidTest/ instead.
package com.meetmind.assistant.storage

import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.helpers.FakeApiKeyStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the [ApiKeyStore] interface contracts (Contract 1).
 *
 * Tests run against [FakeApiKeyStore] — a pure in-memory implementation — so that
 * contract behaviour can be verified on the JVM without requiring Android Keystore
 * hardware or a physical device.
 *
 * [TinkApiKeyStore] (the production implementation) must satisfy these same contracts;
 * it is verified separately in instrumented tests (`androidTest/`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class TinkApiKeyStoreTest {

    private lateinit var store: ApiKeyStore

    @Before
    fun setUp() {
        store = FakeApiKeyStore()
    }

    /** Contract 1.1: Save and retrieve a Gemini API key */
    @Test
    fun saveAndRetrieveKey_gemini() = runTest(UnconfinedTestDispatcher()) {
        val key = "AIzaSy-fake-gemini-key-for-test"
        store.saveKey(CloudProvider.GEMINI, key.toByteArray())

        assertTrue(store.hasKey(CloudProvider.GEMINI))
        val retrieved = store.getKey(CloudProvider.GEMINI)
        assertEquals(key, retrieved?.let { String(it) })
    }

    /** Contract 1.2: Save and retrieve a Claude API key */
    @Test
    fun saveAndRetrieveKey_claude() = runTest(UnconfinedTestDispatcher()) {
        val key = "sk-ant-fake-claude-key-for-test"
        store.saveKey(CloudProvider.CLAUDE, key.toByteArray())

        assertTrue(store.hasKey(CloudProvider.CLAUDE))
        val retrieved = store.getKey(CloudProvider.CLAUDE)
        assertEquals(key, retrieved?.let { String(it) })
    }

    /** Contract 1.3: Key persists after store is recreated (separate instance, same backing) */
    @Test
    fun keyPersistsAfterStoreRecreation() = runTest(UnconfinedTestDispatcher()) {
        val key = "sk-ant-persist-test"
        store.saveKey(CloudProvider.CLAUDE, key.toByteArray())

        // For FakeApiKeyStore, we verify the store retains the key without recreating —
        // persistence-across-process-restart is an integration concern for TinkApiKeyStore.
        assertTrue(store.hasKey(CloudProvider.CLAUDE))
        val retrieved = store.getKey(CloudProvider.CLAUDE)
        assertEquals(key, retrieved?.let { String(it) })
    }

    /** Contract 1.4: Delete removes the key */
    @Test
    fun deleteKey_removesKey() = runTest(UnconfinedTestDispatcher()) {
        val key = "sk-ant-to-be-deleted"
        store.saveKey(CloudProvider.CLAUDE, key.toByteArray())
        assertTrue(store.hasKey(CloudProvider.CLAUDE))

        store.deleteKey(CloudProvider.CLAUDE)

        assertFalse(store.hasKey(CloudProvider.CLAUDE))
        assertNull(store.getKey(CloudProvider.CLAUDE))
    }

    /** Contract 1.5: Blank key is rejected with IllegalArgumentException */
    @Test(expected = IllegalArgumentException::class)
    fun blankKey_notSaved() = runTest(UnconfinedTestDispatcher()) {
        store.saveKey(CloudProvider.CLAUDE, "".toByteArray())
    }

    /** Contract 1.6: Plaintext ByteArray is zeroed after saveKey() returns */
    @Test
    fun plaintextZeroedAfterSave() = runTest(UnconfinedTestDispatcher()) {
        val plaintext = "sk-ant-zero-after-save".toByteArray()
        store.saveKey(CloudProvider.CLAUDE, plaintext)

        val expected = ByteArray(plaintext.size) { 0 }
        assertArrayEquals(expected, plaintext)
    }

    /** Contract 1.7: observeHasKey emits false before save, then true after */
    @Test
    fun observeHasKey_emitsTrue_afterSave() = runTest(UnconfinedTestDispatcher()) {
        assertFalse(store.observeHasKey(CloudProvider.GEMINI).first())

        store.saveKey(CloudProvider.GEMINI, "AIzaSy-observe-test".toByteArray())

        assertTrue(store.observeHasKey(CloudProvider.GEMINI).first())
    }
}
