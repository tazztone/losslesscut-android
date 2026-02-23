package com.tazztone.losslesscut.engine

import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds
    @Singleton
    abstract fun bindLosslessEngine(impl: LosslessEngineImpl): ILosslessEngine

    @Binds
    @Singleton
    abstract fun bindAudioWaveformExtractor(impl: AudioWaveformExtractorImpl): AudioWaveformExtractor
}
