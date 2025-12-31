package com.livepush.di

import android.content.Context
import androidx.room.Room
import com.livepush.data.source.local.AppDatabase
import com.livepush.data.source.local.StreamHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "livepush.db"
        ).build()
    }

    @Provides
    fun provideStreamHistoryDao(database: AppDatabase): StreamHistoryDao {
        return database.streamHistoryDao()
    }
}
