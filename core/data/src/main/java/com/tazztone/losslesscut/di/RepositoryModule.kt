package com.tazztone.losslesscut.di

import com.tazztone.losslesscut.data.VideoEditingRepository
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
    abstract fun bindVideoEditingRepository(impl: VideoEditingRepository): IVideoEditingRepository
}
