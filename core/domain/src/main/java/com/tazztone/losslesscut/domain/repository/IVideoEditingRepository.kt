package com.tazztone.losslesscut.domain.repository

import com.tazztone.losslesscut.domain.model.MediaClip

interface IVideoEditingRepository {
    suspend fun createClipFromUri(uri: String): Result<MediaClip>
    suspend fun getKeyframes(uri: String): List<Long>
    suspend fun extractWaveform(
        uri: String, bucketCount: Int = 1000, onProgress: ((FloatArray) -> Unit)? = null
    ): FloatArray?
    suspend fun getFrameAt(uri: String, positionMs: Long): ByteArray?
    suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): String?
    suspend fun createImageOutputUri(fileName: String): String?
    fun finalizeImage(uri: String)
    fun finalizeVideo(uri: String)
    fun finalizeAudio(uri: String)
    fun getFileName(uriString: String): String
    suspend fun executeLosslessCut(
        inputUri: String,
        outputUri: String,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String>
    suspend fun executeLosslessMerge(
        outputUri: String,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String>
    suspend fun saveSession(clips: List<MediaClip>)
    suspend fun restoreSession(uri: String): List<MediaClip>?
    suspend fun hasSavedSession(uri: String): Boolean
    suspend fun saveWaveformToCache(cacheKey: String, waveform: FloatArray)
    suspend fun loadWaveformFromCache(cacheKey: String): FloatArray?
    suspend fun evictOldCacheFiles()
    suspend fun writeSnapshot(bitmap: ByteArray, outputUri: String, format: String, quality: Int): Boolean
}
