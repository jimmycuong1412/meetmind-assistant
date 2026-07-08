package com.meetmind.assistant.data.repository

import com.meetmind.assistant.data.database.dao.SessionGroupDao
import com.meetmind.assistant.data.database.entity.SessionGroupEntity
import com.meetmind.assistant.domain.model.SessionGroup
import com.meetmind.assistant.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class GroupRepositoryImpl(
    private val dao: SessionGroupDao
) : GroupRepository {

    override fun getAllGroups(): Flow<List<SessionGroup>> =
        dao.getAllGroups().map { list -> list.map { it.toDomain() } }

    override suspend fun createGroup(name: String): Result<SessionGroup> = runCatching {
        val group = SessionGroup(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis()
        )
        dao.insert(group.toEntity())
        group
    }

    override suspend fun deleteGroup(groupId: String): Result<Unit> = runCatching {
        dao.clearGroupFromSessions(groupId)
        dao.deleteGroup(groupId)
    }

    override suspend fun moveSessionToGroup(sessionId: String, groupId: String?): Result<Unit> =
        runCatching { dao.setSessionGroup(sessionId, groupId) }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun SessionGroupEntity.toDomain() = SessionGroup(id = id, name = name, createdAt = createdAt)
    private fun SessionGroup.toEntity() = SessionGroupEntity(id = id, name = name, createdAt = createdAt)
}
