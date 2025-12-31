package com.livepush.domain.repository

import com.livepush.domain.model.StreamHistory
import com.livepush.domain.model.StreamProtocol
import kotlinx.coroutines.flow.Flow

interface StreamHistoryRepository {
    fun getAllHistory(): Flow<List<StreamHistory>>
    fun getRecentHistory(limit: Int = 10): Flow<List<StreamHistory>>
    suspend fun addOrUpdateHistory(url: String, protocol: StreamProtocol)
    suspend fun deleteHistory(id: Long)
    suspend fun deleteAllHistory()
}
