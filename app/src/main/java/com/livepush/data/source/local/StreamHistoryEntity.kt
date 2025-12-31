package com.livepush.data.source.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.livepush.domain.model.StreamProtocol

@Entity(tableName = "stream_history")
data class StreamHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val protocol: String,
    val lastUsed: Long = System.currentTimeMillis(),
    val usageCount: Int = 1
)

fun StreamHistoryEntity.toDomain() = com.livepush.domain.model.StreamHistory(
    id = id,
    url = url,
    protocol = StreamProtocol.valueOf(protocol),
    lastUsed = lastUsed,
    usageCount = usageCount
)

fun com.livepush.domain.model.StreamHistory.toEntity() = StreamHistoryEntity(
    id = id,
    url = url,
    protocol = protocol.name,
    lastUsed = lastUsed,
    usageCount = usageCount
)
