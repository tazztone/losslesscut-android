package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.MediaTrack
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.model.UiText
import java.util.UUID

public sealed class VideoEditingUiState {
    public object Initial : VideoEditingUiState()
    public data class Loading(val progress: Int = 0, val message: UiText? = null) : VideoEditingUiState()
    public data class Success(
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
        val detectionPreviewRanges: List<LongRange> = emptyList(),
        val availableTracks: List<MediaTrack> = emptyList(),
        val playbackSpeed: Float = 1.0f,
        val isPitchCorrectionEnabled: Boolean = false
    ) : VideoEditingUiState() {
        public val isPlaylistVisible: Boolean get() = clips.size > 1
    }

    public data class Error(val error: UiText) : VideoEditingUiState()
}

public sealed class VideoEditingEvent {
    public data class ShowToast(val message: UiText) : VideoEditingEvent()
    public data class ExportComplete(val success: Boolean, val count: Int = 0) : VideoEditingEvent()
    public object SessionRestored : VideoEditingEvent()
    public object DismissHints : VideoEditingEvent()
}
