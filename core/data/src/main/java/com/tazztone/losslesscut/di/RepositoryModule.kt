package com.tazztone.losslesscut.di

import com.tazztone.losslesscut.data.VideoEditingRepositoryImpl
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
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
    abstract fun bindVideoEditingRepository(impl: VideoEditingRepositoryImpl): IVideoEditingRepository

    @Binds
    @Singleton
    abstract fun bindMediaFinalizer(impl: MediaFinalizerImpl): IMediaFinalizer
}
