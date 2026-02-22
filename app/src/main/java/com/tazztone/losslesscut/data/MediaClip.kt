package com.tazztone.losslesscut.data

import android.net.Uri
import java.util.UUID

enum class SegmentAction { KEEP, DISCARD }

data class TrimSegment(
    val id: UUID = UUID.randomUUID(),
    val startMs: Long,
    val endMs: Long,
    val action: SegmentAction = SegmentAction.KEEP
)

data class MediaTrack(
    val id: Int,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val language: String? = null,
    val title: String? = null
)

data class MediaClip(
    val id: UUID = UUID.randomUUID(),
    val uri: Uri,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMime: String?,
    val audioMime: String?,
    val sampleRate: Int,
    val channelCount: Int,
    val fps: Float,
    val rotation: Int,
    val isAudioOnly: Boolean,
    val segments: List<TrimSegment> = listOf(TrimSegment(startMs = 0, endMs = durationMs)),
    val availableTracks: List<MediaTrack> = emptyList()
)
