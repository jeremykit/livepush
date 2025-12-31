package com.livepush.data.repository

import com.livepush.data.source.local.StreamHistoryDao
import com.livepush.data.source.local.StreamHistoryEntity
import com.livepush.data.source.local.toDomain
import com.livepush.domain.model.StreamHistory
import com.livepush.domain.model.StreamProtocol
import com.livepush.domain.repository.StreamHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamHistoryRepositoryImpl @Inject constructor(
    private val dao: StreamHistoryDao
) : StreamHistoryRepository {

    override fun getAllHistory(): Flow<List<StreamHistory>> {
        return dao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecentHistory(limit: Int): Flow<List<StreamHistory>> {
        return dao.getRecentHistory(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addOrUpdateHistory(url: String, protocol: StreamProtocol) {
        val existing = dao.getByUrl(url)
        if (existing != null) {
            dao.update(
                existing.copy(
                    lastUsed = System.currentTimeMillis(),
                    usageCount = existing.usageCount + 1,
                    protocol = protocol.name
                )
            )
        } else {
            dao.insert(
                StreamHistoryEntity(
                    url = url,
                    protocol = protocol.name,
                    lastUsed = System.currentTimeMillis(),
                    usageCount = 1
                )
            )
        }
    }

    override suspend fun deleteHistory(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteAllHistory() {
        dao.deleteAll()
    }
}
