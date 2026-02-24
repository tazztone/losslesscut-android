package com.tazztone.losslesscut.domain.engine

import com.tazztone.losslesscut.domain.model.MediaClip

interface ILosslessEngine {
    suspend fun getKeyframes(videoUri: String): Result<List<Long>>
    suspend fun getMediaMetadata(uri: String): Result<MediaMetadata>
    suspend fun getFrameAt(uri: String, positionMs: Long): ByteArray?
    suspend fun executeLosslessCut(
        inputUri: String,
        outputUri: String,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null,
        selectedTracks: List<Int>? = null
    ): Result<String>

    suspend fun executeLosslessMerge(
        outputUri: String,
        clips: List<MediaClip>,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null,
        selectedTracks: List<Int>? = null
    ): Result<String>
}

data class MediaMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMime: String?,
    val audioMime: String?,
    val sampleRate: Int,
    val channelCount: Int,
    val fps: Float,
    val rotation: Int,
    val tracks: List<TrackMetadata>
)

data class TrackMetadata(
    val id: Int,
    val mimeType: String,
    val language: String?,
    val title: String?,
    val isVideo: Boolean,
    val isAudio: Boolean
)
