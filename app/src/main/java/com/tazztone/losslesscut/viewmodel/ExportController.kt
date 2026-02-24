package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.ExportUseCase
import com.tazztone.losslesscut.domain.usecase.ExtractSnapshotUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles export and snapshot operations.
 */
class ExportController(
    private val exportUseCase: ExportUseCase,
    private val snapshotUseCase: ExtractSnapshotUseCase,
    private val preferences: AppPreferences
) {
    private val _isSnapshotInProgress = MutableStateFlow(false)
    val isSnapshotInProgress: StateFlow<Boolean> = _isSnapshotInProgress.asStateFlow()

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
    ): ExtractSnapshotUseCase.Result? {
        if (!_isSnapshotInProgress.compareAndSet(false, true)) return null
        
        return try {
            val format = preferences.snapshotFormatFlow.first()
            val quality = preferences.jpgQualityFlow.first()
            snapshotUseCase.execute(clip.uri, positionMs, format, quality)
        } finally {
            _isSnapshotInProgress.value = false
        }
    }
}
