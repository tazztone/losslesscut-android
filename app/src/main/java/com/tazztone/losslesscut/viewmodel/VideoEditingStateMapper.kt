package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import java.util.UUID

public object VideoEditingStateMapper {

    public fun mapToState(
        currentClips: List<MediaClip>,
        selectedClipIndex: Int,
        currentKeyframes: List<Long>,
        selectedSegmentId: UUID?,
        canUndo: Boolean,
        canRedo: Boolean,
        isSnapshotInProgress: Boolean,
        detectionPreviewRanges: List<LongRange>,
        playbackSpeed: Float,
        isPitchCorrectionEnabled: Boolean,
        currentState: VideoEditingUiState
    ): VideoEditingUiState {
        val clip = currentClips.getOrNull(selectedClipIndex)
        if (clip == null) {
            return if (currentState is VideoEditingUiState.Success) {
                VideoEditingUiState.Initial
            } else {
                currentState
            }
        }

        return VideoEditingUiState.Success(
            clips = currentClips,
            selectedClipIndex = selectedClipIndex,
            keyframes = currentKeyframes,
            segments = clip.segments,
            selectedSegmentId = selectedSegmentId,
            canUndo = canUndo,
            canRedo = canRedo,
            videoFps = clip.fps,
            isAudioOnly = clip.isAudioOnly,
            hasAudioTrack = clip.audioMime != null,
            isSnapshotInProgress = isSnapshotInProgress,
            detectionPreviewRanges = detectionPreviewRanges,
            availableTracks = clip.availableTracks,
            playbackSpeed = playbackSpeed,
            isPitchCorrectionEnabled = isPitchCorrectionEnabled
        )
    }
}
