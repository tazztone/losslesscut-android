package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

public class VideoEditingStateMapperTest {

    @Test
    public fun `mapToState returns Initial when clip is null and currentState is Success`() {
        val result = VideoEditingStateMapper.mapToState(
            MapStateInput(
                currentClips = emptyList(),
                selectedClipIndex = 0,
                currentKeyframes = emptyList(),
                selectedSegmentId = null,
                canUndo = false,
                canRedo = false,
                isSnapshotInProgress = false,
                detectionPreviewRanges = emptyList(),
                selectedAudioTrackIndex = 0,
                playbackSpeed = 1.0f,
                isPitchCorrectionEnabled = false,
                currentState = VideoEditingUiState.Success(emptyList(), keyframes = emptyList(), segments = emptyList())
            )
        )

        assertEquals(VideoEditingUiState.Initial, result)
    }

    @Test
    public fun `mapToState returns currentState when clip is null and currentState is not Success`() {
        val currentState = VideoEditingUiState.Loading(50)
        val result = VideoEditingStateMapper.mapToState(
            MapStateInput(
                currentClips = emptyList(),
                selectedClipIndex = 0,
                currentKeyframes = emptyList(),
                selectedSegmentId = null,
                canUndo = false,
                canRedo = false,
                isSnapshotInProgress = false,
                detectionPreviewRanges = emptyList(),
                selectedAudioTrackIndex = 0,
                playbackSpeed = 1.0f,
                isPitchCorrectionEnabled = false,
                currentState = currentState
            )
        )

        assertEquals(currentState, result)
    }

    @Test
    public fun `mapToState returns Success with correct values when clip is valid`() {
        val clip = MediaClip(
            id = UUID.randomUUID(),
            uri = "uri",
            fileName = "file.mp4",
            durationMs = 1000,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 60f,
            rotation = 0,
            isAudioOnly = false
        )

        val clips = listOf(clip)
        val selectedSegmentId = UUID.randomUUID()
        val keyframes = listOf(10L, 20L)
        val previewRanges = listOf(0L..100L)

        val result = VideoEditingStateMapper.mapToState(
            MapStateInput(
                currentClips = clips,
                selectedClipIndex = 0,
                currentKeyframes = keyframes,
                selectedSegmentId = selectedSegmentId,
                canUndo = true,
                canRedo = false,
                isSnapshotInProgress = true,
                detectionPreviewRanges = previewRanges,
                selectedAudioTrackIndex = 1,
                playbackSpeed = 1.5f,
                isPitchCorrectionEnabled = true,
                currentState = VideoEditingUiState.Initial
            )
        )

        assertTrue(result is VideoEditingUiState.Success)
        val successState = result as VideoEditingUiState.Success

        assertEquals(clips, successState.clips)
        assertEquals(0, successState.selectedClipIndex)
        assertEquals(keyframes, successState.keyframes)
        assertEquals(clip.segments, successState.segments)
        assertEquals(selectedSegmentId, successState.selectedSegmentId)
        assertEquals(true, successState.canUndo)
        assertEquals(false, successState.canRedo)
        assertEquals(60f, successState.videoFps)
        assertEquals(false, successState.isAudioOnly)
        assertEquals(true, successState.hasAudioTrack)
        assertEquals(true, successState.isSnapshotInProgress)
        assertEquals(previewRanges, successState.detectionPreviewRanges)
        assertEquals(clip.availableTracks, successState.availableTracks)
        assertEquals(1, successState.selectedAudioTrackIndex)
        assertEquals(1.5f, successState.playbackSpeed)
        assertEquals(true, successState.isPitchCorrectionEnabled)
    }

    @Test
    public fun `mapToState preserves export loading while unrelated state emits`() {
        val clip = MediaClip(
            uri = "uri",
            fileName = "file.mp4",
            durationMs = 1000,
            width = 1920,
            height = 1080,
            videoMime = "video/mp4",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 0,
            isAudioOnly = false
        )
        val loading = VideoEditingUiState.Loading(50)

        val result = VideoEditingStateMapper.mapToState(
            MapStateInput(
                currentClips = listOf(clip),
                selectedClipIndex = 0,
                currentKeyframes = emptyList(),
                selectedSegmentId = null,
                canUndo = false,
                canRedo = false,
                isSnapshotInProgress = false,
                detectionPreviewRanges = emptyList(),
                selectedAudioTrackIndex = 0,
                playbackSpeed = 1f,
                isPitchCorrectionEnabled = false,
                currentState = loading,
                isExporting = true
            )
        )

        assertEquals(loading, result)
    }
}
