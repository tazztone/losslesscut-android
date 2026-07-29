package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import java.util.UUID

public data class MapStateInput(
    val currentClips: List<MediaClip>,
    val selectedClipIndex: Int,
    val currentKeyframes: List<Long>,
    val selectedSegmentId: UUID?,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isSnapshotInProgress: Boolean,
    val detectionPreviewRanges: List<LongRange>,
    val playbackSpeed: Float,
    val isPitchCorrectionEnabled: Boolean,
    val currentState: VideoEditingUiState
)

public object VideoEditingStateMapper {

    public fun mapToState(input: MapStateInput): VideoEditingUiState {
        val clip = input.currentClips.getOrNull(input.selectedClipIndex)
        if (clip == null) {
            return if (input.currentState is VideoEditingUiState.Success) {
                VideoEditingUiState.Initial
            } else {
                input.currentState
            }
        }

        return VideoEditingUiState.Success(
            clips = input.currentClips,
            selectedClipIndex = input.selectedClipIndex,
            keyframes = input.currentKeyframes,
            segments = clip.segments,
            selectedSegmentId = input.selectedSegmentId,
            canUndo = input.canUndo,
            canRedo = input.canRedo,
            videoFps = clip.fps,
            isAudioOnly = clip.isAudioOnly,
            hasAudioTrack = clip.audioMime != null,
            isSnapshotInProgress = input.isSnapshotInProgress,
            detectionPreviewRanges = input.detectionPreviewRanges,
            availableTracks = clip.availableTracks,
            playbackSpeed = input.playbackSpeed,
            isPitchCorrectionEnabled = input.isPitchCorrectionEnabled
        )
    }
}
