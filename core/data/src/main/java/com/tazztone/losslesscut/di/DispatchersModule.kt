package com.tazztone.losslesscut.di

import com.tazztone.losslesscut.domain.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @com.tazztone.losslesscut.domain.di.EngineDispatcher
    fun provideEngineDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "lossless-cut-engine")
        }.asCoroutineDispatcher()
}
