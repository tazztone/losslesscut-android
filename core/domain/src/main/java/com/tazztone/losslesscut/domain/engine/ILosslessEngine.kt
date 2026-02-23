package com.tazztone.losslesscut.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tazztone.losslesscut.domain.model.MediaClip

interface ILosslessEngine {
    suspend fun getKeyframes(context: Context, videoUri: Uri): Result<List<Long>>
    suspend fun getMediaMetadata(context: Context, uri: Uri): Result<MediaMetadata>
    suspend fun getFrameAt(context: Context, uri: Uri, positionMs: Long): Bitmap?
    suspend fun executeLosslessCut(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null,
        selectedTracks: List<Int>? = null
    ): Result<Uri>

    suspend fun executeLosslessMerge(
        context: Context,
        outputUri: Uri,
        clips: List<MediaClip>,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null,
        selectedTracks: List<Int>? = null
    ): Result<Uri>
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
