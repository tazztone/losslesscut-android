package com.tazztone.losslesscut.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.tazztone.losslesscut.domain.model.MediaClip

interface IVideoEditingRepository {
    suspend fun createClipFromUri(uri: Uri): Result<MediaClip>
    suspend fun getKeyframes(uri: Uri): List<Long>
    suspend fun extractWaveform(uri: Uri, onProgress: ((FloatArray) -> Unit)? = null): FloatArray?
    suspend fun getFrameAt(uri: Uri, positionMs: Long): Bitmap?
    suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): Uri?
    suspend fun createImageOutputUri(fileName: String): Uri?
    fun finalizeImage(uri: Uri)
    fun finalizeVideo(uri: Uri)
    fun finalizeAudio(uri: Uri)
    fun getFileName(uri: Uri): String
    suspend fun executeLosslessCut(
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<Uri>
    suspend fun executeLosslessMerge(
        outputUri: Uri,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<Uri>
    suspend fun saveSession(clips: List<MediaClip>)
    suspend fun restoreSession(uri: Uri): List<MediaClip>?
    suspend fun hasSavedSession(uri: Uri): Boolean
    suspend fun saveWaveformToCache(cacheKey: String, waveform: FloatArray)
    suspend fun loadWaveformFromCache(cacheKey: String): FloatArray?
    suspend fun evictOldCacheFiles()
    suspend fun writeSnapshot(bitmap: Bitmap, outputUri: Uri, format: String, quality: Int): Boolean
}
