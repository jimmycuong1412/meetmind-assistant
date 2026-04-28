package com.meetmind.assistant.domain.repository

import com.meetmind.assistant.domain.model.ActionItem
import kotlinx.coroutines.flow.Flow

interface ActionItemRepository {
    fun getBySession(sessionId: String): Flow<List<ActionItem>>
    suspend fun toggle(id: String, isDone: Boolean): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
