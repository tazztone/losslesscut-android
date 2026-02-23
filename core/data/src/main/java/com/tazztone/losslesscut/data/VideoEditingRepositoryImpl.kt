package com.tazztone.losslesscut.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.tazztone.losslesscut.domain.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.model.*
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import com.tazztone.losslesscut.utils.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.tazztone.losslesscut.domain.di.IoDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoEditingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ILosslessEngine,
    private val storageUtils: StorageUtils,
    private val waveformExtractor: AudioWaveformExtractor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IVideoEditingRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun createClipFromUri(uri: Uri): Result<MediaClip> = withContext(ioDispatcher) {
        engine.getMediaMetadata(context, uri).map { meta ->
            MediaClip(
                uri = uri,
                fileName = storageUtils.getFileName(uri),
                durationMs = meta.durationMs,
                width = meta.width,
                height = meta.height,
                videoMime = meta.videoMime,
                audioMime = meta.audioMime,
                sampleRate = meta.sampleRate,
                channelCount = meta.channelCount,
                fps = meta.fps,
                rotation = meta.rotation,
                isAudioOnly = meta.videoMime == null,
                segments = listOf(TrimSegment(startMs = 0, endMs = meta.durationMs)),
                availableTracks = meta.tracks.map { 
                    MediaTrack(it.id, it.mimeType, it.isVideo, it.isAudio, it.language, it.title)
                }
            )
        }
    }

    override suspend fun getKeyframes(uri: Uri): List<Long> = withContext(ioDispatcher) {
        engine.getKeyframes(context, uri).getOrElse { emptyList() }
    }

    override suspend fun extractWaveform(uri: Uri, onProgress: ((FloatArray) -> Unit)?): FloatArray? {
        return waveformExtractor.extract(context, uri, onProgress = onProgress)
    }

    override suspend fun getFrameAt(uri: Uri, positionMs: Long) = withContext(ioDispatcher) {
        engine.getFrameAt(context, uri, positionMs)
    }

    override suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): Uri? {
        return storageUtils.createMediaOutputUri(fileName, isAudio)
    }

    override suspend fun createImageOutputUri(fileName: String): Uri? {
        return storageUtils.createImageOutputUri(fileName)
    }

    override fun finalizeImage(uri: Uri) {
        storageUtils.finalizeImage(uri)
    }

    override fun finalizeVideo(uri: Uri) {
        storageUtils.finalizeVideo(uri)
    }

    override fun finalizeAudio(uri: Uri) {
        storageUtils.finalizeAudio(uri)
    }

    override fun getFileName(uri: Uri): String {
        return storageUtils.getFileName(uri)
    }

    override suspend fun executeLosslessCut(
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ) = engine.executeLosslessCut(context, inputUri, outputUri, startMs, endMs, keepAudio, keepVideo, rotationOverride, selectedTracks)

    override suspend fun executeLosslessMerge(
        outputUri: Uri,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ) = engine.executeLosslessMerge(context, outputUri, clips, keepAudio, keepVideo, rotationOverride, selectedTracks)

    // --- Session & Cache Management ---

    override suspend fun saveSession(clips: List<MediaClip>) = withContext(ioDispatcher) {
        if (clips.isEmpty()) return@withContext
        try {
            val sessionId = getSessionId(clips.first().uri)
            val sessionFile = File(context.cacheDir, "session_$sessionId.json")
            val jsonText = json.encodeToString(clips)
            sessionFile.writeText(jsonText)
        } catch (e: Exception) {
            Log.e("VideoEditingRepository", "Failed to save session", e)
        }
    }

    override suspend fun restoreSession(uri: Uri): List<MediaClip>? = withContext(ioDispatcher) {
        try {
            val sessionId = getSessionId(uri)
            val sessionFile = File(context.cacheDir, "session_$sessionId.json")
            if (!sessionFile.exists()) return@withContext null
            
            val jsonText = sessionFile.readText()
            val restoredClips: List<MediaClip> = json.decodeFromString(jsonText)
            
            // Filter valid clips
            restoredClips.filter { clip ->
                try {
                    context.contentResolver.query(clip.uri, null, null, null, null)?.use { 
                        it.moveToFirst() 
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingRepository", "Failed to restore session", e)
            null
        }
    }

    override suspend fun hasSavedSession(uri: Uri): Boolean = withContext(ioDispatcher) {
        val sessionFile = File(context.cacheDir, "session_${getSessionId(uri)}.json")
        sessionFile.exists()
    }

    override suspend fun saveWaveformToCache(cacheKey: String, waveform: FloatArray) {
        withContext(ioDispatcher) {
            try {
                val cacheFile = File(context.cacheDir, cacheKey)
                DataOutputStream(FileOutputStream(cacheFile)).use { out ->
                    out.writeInt(waveform.size)
                    waveform.forEach { out.writeFloat(it) }
                }
            } catch (e: Exception) {
                Log.e("VideoEditingRepository", "Failed to save waveform to cache", e)
            }
        }
    }

    override suspend fun loadWaveformFromCache(cacheKey: String): FloatArray? = withContext(ioDispatcher) {
        val cacheFile = File(context.cacheDir, cacheKey)
        if (!cacheFile.exists()) return@withContext null
        try {
            DataInputStream(FileInputStream(cacheFile)).use { input ->
                val size = input.readInt()
                FloatArray(size) { input.readFloat() }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingRepository", "Failed to load waveform from cache", e)
            null
        }
    }

    override suspend fun evictOldCacheFiles() {
        withContext(ioDispatcher) {
            try {
                val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
                context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith("waveform_") || it.name.startsWith("session_") }
                    ?.filter { it.lastModified() < sevenDaysAgo }
                    ?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("VideoEditingRepository", "Failed to evict old cache files", e)
            }
        }
    }

    // --- Snapshot Management ---

    override suspend fun writeSnapshot(bitmap: Bitmap, outputUri: Uri, format: String, quality: Int): Boolean = withContext(ioDispatcher) {
        try {
            val compressFormat = if (format == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                bitmap.compress(compressFormat, quality, outputStream)
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("VideoEditingRepository", "Failed to write snapshot", e)
            false
        }
    }

    private fun getSessionId(uri: Uri): String {
        // Optimized: simple hash code is enough for local cache key
        return uri.toString().hashCode().toString()
    }
}
