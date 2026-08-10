package com.tazztone.losslesscut.domain.testutil

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.TrimSegment
import java.util.UUID

public object MediaClipTestFixture {
    public fun createDummyClip(
        uri: String = "content://media/external/video/media/1",
        fileName: String = "test.mp4",
        durationMs: Long = 1000L,
        segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = 1000L))
    ): MediaClip = MediaClip(
        id = UUID.randomUUID(),
        uri = uri,
        fileName = fileName,
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        videoMime = "video/mp4",
        audioMime = "audio/mp4",
        sampleRate = 44100,
        channelCount = 2,
        fps = 30f,
        rotation = 0,
        isAudioOnly = false,
        segments = segments
    )
}
