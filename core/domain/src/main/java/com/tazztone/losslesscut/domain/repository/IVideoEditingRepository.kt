package com.tazztone.losslesscut.domain.repository

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.WaveformResult

public interface IVideoEditingRepository {
    public suspend fun createClipFromUri(uri: String): Result<MediaClip>
    public suspend fun getKeyframes(uri: String): List<Long>
    public suspend fun extractWaveform(
        uri: String, bucketCount: Int = 1000, onProgress: ((WaveformResult) -> Unit)? = null
    ): WaveformResult?
    public suspend fun getFrameAt(uri: String, positionMs: Long): ByteArray?
    public suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): String?
    public suspend fun createImageOutputUri(fileName: String): String?
    public fun finalizeImage(uri: String)
    public fun finalizeVideo(uri: String)
    public fun finalizeAudio(uri: String)
    public fun getFileName(uriString: String): String
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
    public suspend fun saveSession(clips: List<MediaClip>)
    public suspend fun restoreSession(uri: String): List<MediaClip>?
    public suspend fun hasSavedSession(uri: String): Boolean
    public suspend fun saveWaveformToCache(cacheKey: String, result: WaveformResult)
    public suspend fun loadWaveformFromCache(cacheKey: String): WaveformResult?
    public suspend fun evictOldCacheFiles()
    public suspend fun writeSnapshot(bitmap: ByteArray, outputUri: String, format: String, quality: Int): Boolean
}
