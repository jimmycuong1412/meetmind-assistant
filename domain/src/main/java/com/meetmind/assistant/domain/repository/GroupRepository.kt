package com.meetmind.assistant.domain.repository

import com.meetmind.assistant.domain.model.SessionGroup
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getAllGroups(): Flow<List<SessionGroup>>
    suspend fun createGroup(name: String): Result<SessionGroup>
    suspend fun deleteGroup(groupId: String): Result<Unit>
    suspend fun moveSessionToGroup(sessionId: String, groupId: String?): Result<Unit>
}
