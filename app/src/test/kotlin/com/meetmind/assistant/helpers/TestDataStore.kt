// T014: In-memory DataStore factory for isolated tests
package com.meetmind.assistant.helpers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import java.io.File
import java.util.UUID

/**
 * Creates a [DataStore<Preferences>] backed by a unique temp file for each test.
 * Each call returns a completely isolated store — no state leaks between tests.
 *
 * Accepts the [TestScope] so the DataStore coroutines run on the test scheduler,
 * ensuring [kotlinx.coroutines.test.runTest] can control completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testDataStore(scope: CoroutineScope = TestScope()): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = {
            File(
                System.getProperty("java.io.tmpdir"),
                "test_prefs_${UUID.randomUUID()}.preferences_pb"
            )
        }
    )
