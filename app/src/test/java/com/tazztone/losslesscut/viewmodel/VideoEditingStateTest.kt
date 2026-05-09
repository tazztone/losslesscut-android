package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VideoEditingStateTest {

    @Test
    fun `Success state isPlaylistVisible returns true when clips size greater than 1`() {
        val clip1 = MediaClip(
            id = UUID.randomUUID(),
            uri = "uri1",
            fileName = "file1",
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
        val clip2 = MediaClip(
            id = UUID.randomUUID(),
            uri = "uri2",
            fileName = "file2",
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

        val stateWithZeroClips = VideoEditingUiState.Success(
            clips = emptyList(),
            keyframes = emptyList(),
            segments = emptyList()
        )
        assertFalse(stateWithZeroClips.isPlaylistVisible)

        val stateWithOneClip = VideoEditingUiState.Success(
            clips = listOf(clip1),
            keyframes = emptyList(),
            segments = emptyList()
        )
        assertFalse(stateWithOneClip.isPlaylistVisible)

        val stateWithTwoClips = VideoEditingUiState.Success(
            clips = listOf(clip1, clip2),
            keyframes = emptyList(),
            segments = emptyList()
        )
        assertTrue(stateWithTwoClips.isPlaylistVisible)
    }

    @Test
    fun `Success state default values are set correctly`() {
        val state = VideoEditingUiState.Success(
            clips = emptyList(),
            keyframes = emptyList(),
            segments = emptyList()
        )

        assertEquals(0, state.selectedClipIndex)
        assertNull(state.selectedSegmentId)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertEquals(30f, state.videoFps)
        assertFalse(state.isAudioOnly)
        assertTrue(state.hasAudioTrack)
        assertFalse(state.isSnapshotInProgress)
        assertTrue(state.detectionPreviewRanges.isEmpty())
        assertTrue(state.availableTracks.isEmpty())
        assertEquals(1.0f, state.playbackSpeed)
        assertFalse(state.isPitchCorrectionEnabled)
    }

    @Test
    fun `Loading state default and explicit values`() {
        val defaultLoading = VideoEditingUiState.Loading()
        assertEquals(0, defaultLoading.progress)
        assertNull(defaultLoading.message)

        val message = UiText.DynamicString("Loading...")
        val explicitLoading = VideoEditingUiState.Loading(progress = 50, message = message)
        assertEquals(50, explicitLoading.progress)
        assertEquals(message, explicitLoading.message)
    }

    @Test
    fun `Error state holds correct error`() {
        val errorMsg = UiText.DynamicString("An error occurred")
        val state = VideoEditingUiState.Error(errorMsg)
        assertEquals(errorMsg, state.error)
    }

    @Test
    fun `VideoEditingEvent ShowToast holds correct message`() {
        val message = UiText.DynamicString("Toast message")
        val event = VideoEditingEvent.ShowToast(message)
        assertEquals(message, event.message)
    }

    @Test
    fun `VideoEditingEvent ExportComplete default and explicit values`() {
        val defaultEvent = VideoEditingEvent.ExportComplete(success = true)
        assertTrue(defaultEvent.success)
        assertEquals(0, defaultEvent.count)

        val explicitEvent = VideoEditingEvent.ExportComplete(success = false, count = 5)
        assertFalse(explicitEvent.success)
        assertEquals(5, explicitEvent.count)
    }
}
