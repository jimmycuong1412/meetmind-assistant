// T025 (spec 008): ViewModel for the analysis settings screen
// spec 009 — T021: migrated to @HiltViewModel @Inject constructor; Factory deleted
package com.meetmind.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetmind.assistant.data.AnalysisSettingsRepository
import com.meetmind.assistant.data.model.AnalysisSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes [AnalysisSettings] as a [StateFlow] and provides write operations for the UI.
 *
 * Connected to [AnalysisSettingsRepository] for DataStore persistence.
 */
@HiltViewModel
class AnalysisSettingsViewModel @Inject constructor(
    private val repository: AnalysisSettingsRepository
) : ViewModel() {

    /** Current analysis settings, observed from DataStore. */
    val settings: StateFlow<AnalysisSettings> = repository.observe()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AnalysisSettings()
        )

    /** Enable or disable the continuous analysis cadence. */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    /**
     * Set the cadence interval in seconds.
     * Clamped to [AnalysisSettings.MIN_INTERVAL_S]..[AnalysisSettings.MAX_INTERVAL_S] by repository.
     */
    fun setIntervalS(intervalS: Int) {
        viewModelScope.launch { repository.setIntervalS(intervalS) }
    }

    /**
     * Set the transcript window size in seconds.
     * Clamped to [AnalysisSettings.MIN_WINDOW_S]..[AnalysisSettings.MAX_WINDOW_S] by repository.
     */
    fun setWindowS(windowS: Int) {
        viewModelScope.launch { repository.setWindowS(windowS) }
    }
}
