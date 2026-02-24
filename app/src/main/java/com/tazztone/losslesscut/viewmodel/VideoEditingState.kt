package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.MediaTrack
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.model.UiText
import java.util.UUID

sealed class VideoEditingUiState {
    object Initial : VideoEditingUiState()
    data class Loading(val progress: Int = 0, val message: UiText? = null) : VideoEditingUiState()
    data class Success(
        val clips: List<MediaClip>,
        val selectedClipIndex: Int = 0,
        val keyframes: List<Long>,
        val segments: List<TrimSegment>,
        val selectedSegmentId: UUID? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val videoFps: Float = 30f,
        val isAudioOnly: Boolean = false,
        val hasAudioTrack: Boolean = true,
        val isSnapshotInProgress: Boolean = false,
        val silencePreviewRanges: List<LongRange> = emptyList(),
        val availableTracks: List<MediaTrack> = emptyList(),
        val playbackSpeed: Float = 1.0f,
        val isPitchCorrectionEnabled: Boolean = false
    ) : VideoEditingUiState()
    data class Error(val error: UiText) : VideoEditingUiState()
}

sealed class VideoEditingEvent {
    data class ShowToast(val message: UiText) : VideoEditingEvent()
    data class ExportComplete(val success: Boolean, val count: Int = 0) : VideoEditingEvent()
    object SessionRestored : VideoEditingEvent()
    object DismissHints : VideoEditingEvent()
}
