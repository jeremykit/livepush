package com.livepush.domain.repository

import com.livepush.domain.model.ReconnectionConfig
import com.livepush.domain.model.StreamConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getStreamConfig(): Flow<StreamConfig>
    suspend fun updateStreamConfig(config: StreamConfig)
    suspend fun getLastStreamUrl(): String?
    suspend fun setLastStreamUrl(url: String)
<<<<<<< HEAD
    fun getReconnectionConfig(): Flow<ReconnectionConfig>
    suspend fun updateReconnectionConfig(config: ReconnectionConfig)
    fun getStreamConfirmationEnabled(): Flow<Boolean>
    suspend fun setStreamConfirmationEnabled(enabled: Boolean)
=======
    fun getStreamConfirmationEnabled(): Flow<Boolean>
    suspend fun setStreamConfirmationEnabled(enabled: Boolean)
    fun getReconnectionConfig(): Flow<ReconnectionConfig>
    suspend fun updateReconnectionConfig(config: ReconnectionConfig)
>>>>>>> c4059d0c7434ae92f1973b9b2be54d064fd3f4f0

    // Network settings
    suspend fun getMaxReconnectAttempts(): Int
    suspend fun setMaxReconnectAttempts(attempts: Int)
    suspend fun getConnectionTimeout(): Int
    suspend fun setConnectionTimeout(timeout: Int)
<<<<<<< HEAD
}
=======
}
>>>>>>> c4059d0c7434ae92f1973b9b2be54d064fd3f4f0
