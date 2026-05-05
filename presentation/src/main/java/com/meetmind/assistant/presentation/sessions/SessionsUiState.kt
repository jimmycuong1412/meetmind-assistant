package com.meetmind.assistant.presentation.sessions

import com.meetmind.assistant.domain.model.AppSettings
import com.meetmind.assistant.domain.model.RecordingMode
import com.meetmind.assistant.domain.model.SessionGroup
import com.meetmind.assistant.domain.model.SessionTemplate
import com.meetmind.assistant.domain.model.TranscriptionSession

/** Date filter for the sessions list. */
enum class DateFilter { ALL, TODAY, THIS_WEEK }

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
    val templates: List<SessionTemplate> = emptyList(),
    /** All user-created groups, ordered by creation time. */
    val groups: List<SessionGroup> = emptyList(),
    /** IDs of groups whose session list is currently collapsed. */
    val collapsedGroupIds: Set<String> = emptySet(),
    /** Non-null while the "Move to group" bottom sheet is open for a session. */
    val sessionIdForGroupPicker: String? = null
)
