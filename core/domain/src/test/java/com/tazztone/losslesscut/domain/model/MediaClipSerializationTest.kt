package com.tazztone.losslesscut.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

public class MediaClipSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    public fun testTrimSegmentSerialization(): Unit {
        val id = UUID.randomUUID()
        val segment = TrimSegment(id, 100, 500, SegmentAction.DISCARD)
        val serialized = json.encodeToString(segment)
        
        assertTrue(serialized.contains(id.toString()))
        assertTrue(serialized.contains("\"startMs\":100"))
        assertTrue(serialized.contains("\"endMs\":500"))
        assertTrue(serialized.contains("\"action\":\"DISCARD\""))

        val deserialized = json.decodeFromString<TrimSegment>(serialized)
        assertEquals(segment, deserialized)
    }

    @Test
    public fun testMediaClipSerialization(): Unit {
        val clip = MediaClip(
            uri = "test_uri",
            fileName = "test.mp4",
            durationMs = 1000,
            width = 1920,
            height = 1080,
            videoMime = "video/avc",
            audioMime = "audio/mp4",
            sampleRate = 44100,
            channelCount = 2,
            fps = 30f,
            rotation = 90,
            isAudioOnly = false,
            segments = listOf(
                TrimSegment(UUID.randomUUID(), 0, 500, SegmentAction.KEEP),
                TrimSegment(UUID.randomUUID(), 500, 1000, SegmentAction.DISCARD)
            )
        )

        val serialized = json.encodeToString(clip)
        val deserialized = json.decodeFromString<MediaClip>(serialized)
        
        assertEquals(clip.id, deserialized.id)
        assertEquals(clip.uri, deserialized.uri)
        assertEquals(clip.segments.size, deserialized.segments.size)
        assertEquals(clip.segments[0].action, deserialized.segments[0].action)
        assertEquals(clip, deserialized)
    }

    @Test
    public fun testMediaClipSerialization_WithDefaults(): Unit {
        // Minimal data for MediaClip
        val jsonStr = """
            {
                "uri": "uri",
                "fileName": "file",
                "durationMs": 1000,
                "width": 0,
                "height": 0,
                "videoMime": null,
                "audioMime": null,
                "sampleRate": 0,
                "channelCount": 0,
                "fps": 0.0,
                "rotation": 0,
                "isAudioOnly": false
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<MediaClip>(jsonStr)
        
        assertEquals("uri", deserialized.uri)
        // Check default segments list
        assertEquals(1, deserialized.segments.size)
        assertEquals(0L, deserialized.segments[0].startMs)
        assertEquals(1000L, deserialized.segments[0].endMs)
    }
}
