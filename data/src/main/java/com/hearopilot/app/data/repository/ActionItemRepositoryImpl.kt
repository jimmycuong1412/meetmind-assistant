package com.hearopilot.app.data.repository

import com.hearopilot.app.data.database.dao.ActionItemDao
import com.hearopilot.app.data.database.mapper.toDomain
import com.hearopilot.app.domain.model.ActionItem
import com.hearopilot.app.domain.repository.ActionItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ActionItemRepositoryImpl @Inject constructor(
    private val dao: ActionItemDao
) : ActionItemRepository {

    override fun getBySession(sessionId: String): Flow<List<ActionItem>> =
        dao.getBySession(sessionId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun toggle(id: String, isDone: Boolean): Result<Unit> = runCatching {
        dao.setDone(id, isDone)
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        dao.deleteById(id)
    }
}
