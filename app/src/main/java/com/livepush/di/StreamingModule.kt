package com.livepush.di

import com.livepush.domain.usecase.StreamManager
import com.livepush.streaming.RtmpStreamManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {

    @Binds
    @Singleton
    abstract fun bindStreamManager(
        rtmpStreamManager: RtmpStreamManager
    ): StreamManager
}
