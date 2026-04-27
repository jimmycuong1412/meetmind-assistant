package com.hearopilot.app.presentation.sessions

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SessionTemplate
import com.hearopilot.app.domain.model.TranscriptionSession

/** Date filter for the sessions list. */
enum class DateFilter { ALL, TODAY, THIS_WEEK }

/**
 * UI state for the sessions list screen.
 *
 * @property allSessions Full unfiltered list of sessions (most recent first)
 * @property filteredSessions Subset after applying [selectedDateFilter] and [selectedModeFilter]
 * @property isLoading Whether data is being loaded
 * @property error Error message if loading failed
 * @property showNewSessionDialog Whether to show the new session dialog
 * @property settings Current app settings (used in new-session dialog to show intervals)
 * @property selectedDateFilter Active date range filter
 * @property selectedModeFilter Active mode filter; null means "All"
 */
data class SessionsUiState(
    val allSessions: List<TranscriptionSession> = emptyList(),
    val filteredSessions: List<TranscriptionSession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showNewSessionDialog: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val totalDataSizeBytes: Long = 0L,
    val selectedDateFilter: DateFilter = DateFilter.ALL,
    val selectedModeFilter: RecordingMode? = null,
    val templates: List<SessionTemplate> = emptyList()
)
