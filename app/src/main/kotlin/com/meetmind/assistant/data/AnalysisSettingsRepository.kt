// T024 (spec 008): DataStore-backed repository for AnalysisSettings
package com.meetmind.assistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.meetmind.assistant.data.model.AnalysisSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads and writes [AnalysisSettings] to DataStore Preferences.
 *
 * Key prefix: `analysis_` (spec 008 FR-012).
 * Clamping is enforced on write so stored values are always within valid range.
 *
 * @param dataStore Injectable DataStore; tests pass an in-memory instance via [testDataStore].
 */
class AnalysisSettingsRepository(
    private val dataStore: DataStore<Preferences>
) {

    /** Observes the current [AnalysisSettings], emitting on every change. */
    fun observe(): Flow<AnalysisSettings> = dataStore.data.map { prefs ->
        AnalysisSettings(
            analysisEnabled = prefs[KEY_ENABLED] ?: AnalysisSettings.DEFAULT_ENABLED,
            analysisIntervalS = prefs[KEY_INTERVAL_S] ?: AnalysisSettings.DEFAULT_INTERVAL_S,
            analysisWindowS = prefs[KEY_WINDOW_S] ?: AnalysisSettings.DEFAULT_WINDOW_S
        )
    }

    /** Persists the enabled flag. */
    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    /** Persists the cadence interval, clamped to [AnalysisSettings.MIN_INTERVAL_S]..[AnalysisSettings.MAX_INTERVAL_S]. */
    suspend fun setIntervalS(intervalS: Int) {
        val clamped = intervalS.coerceIn(AnalysisSettings.MIN_INTERVAL_S, AnalysisSettings.MAX_INTERVAL_S)
        dataStore.edit { it[KEY_INTERVAL_S] = clamped }
    }

    /** Persists the window size, clamped to [AnalysisSettings.MIN_WINDOW_S]..[AnalysisSettings.MAX_WINDOW_S]. */
    suspend fun setWindowS(windowS: Int) {
        val clamped = windowS.coerceIn(AnalysisSettings.MIN_WINDOW_S, AnalysisSettings.MAX_WINDOW_S)
        dataStore.edit { it[KEY_WINDOW_S] = clamped }
    }

    companion object {
        private val KEY_ENABLED    = booleanPreferencesKey("analysis_enabled")
        private val KEY_INTERVAL_S = intPreferencesKey("analysis_interval_s")
        private val KEY_WINDOW_S   = intPreferencesKey("analysis_window_s")
    }
}
