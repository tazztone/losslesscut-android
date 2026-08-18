package com.tazztone.losslesscut.domain.repository

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SessionRestoreResult
import com.tazztone.losslesscut.domain.model.SessionSummary
import com.tazztone.losslesscut.domain.model.WaveformResult

public interface IVideoEditingRepository {
    public suspend fun createClipFromUri(uri: String): Result<MediaClip>
    public suspend fun getKeyframes(uri: String): List<Long>
    public suspend fun extractWaveform(
        uri: String,
        trackId: Int? = null,
        onProgress: ((WaveformResult) -> Unit)? = null
    ): WaveformResult?
    public suspend fun getFrameAt(
        uri: String,
        positionMs: Long,
        format: String = "JPEG",
        quality: Int = 80
    ): ByteArray?
    public suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): String?
    public suspend fun createImageOutputUri(fileName: String): String?
    public suspend fun deleteOutput(uri: String): Boolean
    public fun finalizeImage(uri: String)
    public fun finalizeVideo(uri: String)
    public fun finalizeAudio(uri: String)
    public suspend fun getFileName(uriString: String): String
    public suspend fun executeLosslessCut(
        inputUri: String,
        outputUri: String,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String>
    public suspend fun executeLosslessMerge(
        outputUri: String,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String>
    public suspend fun saveSession(sessionId: String, clips: List<MediaClip>): Result<Unit>
    public suspend fun restoreSession(sessionId: String): SessionRestoreResult?
    public suspend fun hasSavedSession(sessionId: String): Boolean
    public suspend fun listSavedSessions(): List<SessionSummary>
    public suspend fun deleteSession(sessionId: String)
    public suspend fun getWaveform(
        clip: MediaClip,
        trackId: Int? = null,
        onProgress: ((WaveformResult) -> Unit)? = null
    ): WaveformResult?
    public suspend fun writeSnapshot(bitmap: ByteArray, outputUri: String): Boolean
}
