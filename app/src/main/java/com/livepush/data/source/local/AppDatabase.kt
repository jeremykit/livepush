package com.livepush.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StreamHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamHistoryDao(): StreamHistoryDao
}
