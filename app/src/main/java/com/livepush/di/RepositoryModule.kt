package com.livepush.di

import com.livepush.data.repository.SettingsRepositoryImpl
import com.livepush.data.repository.StreamHistoryRepositoryImpl
import com.livepush.domain.repository.SettingsRepository
import com.livepush.domain.repository.StreamHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStreamHistoryRepository(
        impl: StreamHistoryRepositoryImpl
    ): StreamHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}
