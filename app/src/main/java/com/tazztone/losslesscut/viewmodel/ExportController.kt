package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.ExportUseCase
import com.tazztone.losslesscut.domain.usecase.ExtractSnapshotUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles export and snapshot operations.
 */
class ExportController(
    private val exportUseCase: ExportUseCase,
    private val snapshotUseCase: ExtractSnapshotUseCase,
    private val preferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val _isSnapshotInProgress = AtomicBoolean(false)
    val isSnapshotInProgress: Boolean get() = _isSnapshotInProgress.get()

    fun exportSegments(
        clips: List<MediaClip>,
        selectedClipIndex: Int,
        settings: ExportSettings
    ): Flow<ExportUseCase.Result> {
        val params = ExportUseCase.Params(
            clips = clips,
            selectedClipIndex = selectedClipIndex,
            isLossless = settings.isLossless,
            keepAudio = settings.keepAudio,
            keepVideo = settings.keepVideo,
            rotationOverride = settings.rotationOverride,
            mergeSegments = settings.mergeSegments,
            selectedTracks = settings.selectedTracks
        )
        return exportUseCase.execute(params)
    }

    suspend fun extractSnapshot(
        clip: MediaClip,
        positionMs: Long
    ): ExtractSnapshotUseCase.Result? = withContext(ioDispatcher) {
        if (!_isSnapshotInProgress.compareAndSet(false, true)) return@withContext null
        
        try {
            val format = preferences.snapshotFormatFlow.first()
            val quality = preferences.jpgQualityFlow.first()
            snapshotUseCase.execute(clip.uri, positionMs, format, quality)
        } finally {
            _isSnapshotInProgress.set(false)
        }
    }
}
