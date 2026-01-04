package com.livepush.domain.repository

import com.livepush.domain.model.StreamConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getStreamConfig(): Flow<StreamConfig>
    suspend fun updateStreamConfig(config: StreamConfig)
    suspend fun getLastStreamUrl(): String?
    suspend fun setLastStreamUrl(url: String)

    // Network settings
    suspend fun getMaxReconnectAttempts(): Int
    suspend fun setMaxReconnectAttempts(attempts: Int)
    suspend fun getConnectionTimeout(): Int
    suspend fun setConnectionTimeout(timeout: Int)
}
