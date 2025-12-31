package com.livepush.data.source.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamHistoryDao {

    @Query("SELECT * FROM stream_history ORDER BY lastUsed DESC")
    fun getAllHistory(): Flow<List<StreamHistoryEntity>>

    @Query("SELECT * FROM stream_history ORDER BY lastUsed DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 10): Flow<List<StreamHistoryEntity>>

    @Query("SELECT * FROM stream_history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): StreamHistoryEntity?

    @Query("SELECT * FROM stream_history WHERE id = :id")
    suspend fun getById(id: Long): StreamHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: StreamHistoryEntity): Long

    @Update
    suspend fun update(history: StreamHistoryEntity)

    @Delete
    suspend fun delete(history: StreamHistoryEntity)

    @Query("DELETE FROM stream_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM stream_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM stream_history")
    suspend fun getCount(): Int
}
