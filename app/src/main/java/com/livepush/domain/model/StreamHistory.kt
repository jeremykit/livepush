package com.livepush.domain.model

data class StreamHistory(
    val id: Long = 0,
    val url: String,
    val protocol: StreamProtocol,
    val lastUsed: Long = System.currentTimeMillis(),
    val usageCount: Int = 1
)
