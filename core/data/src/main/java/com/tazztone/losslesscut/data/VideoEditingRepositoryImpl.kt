package com.tazztone.losslesscut.data

import android.content.Context
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
@Suppress("TooManyFunctions")
class VideoEditingRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: ILosslessEngine,
    private val storageUtils: StorageUtils,
    private val waveformExtractor: AudioWaveformExtractor,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IVideoEditingRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun createClipFromUri(uri: String): Result<MediaClip> = withContext(ioDispatcher) {
        val uriParsed = Uri.parse(uri)
        engine.getMediaMetadata(uri).map { meta ->
            MediaClip(
                uri = uri,
                fileName = storageUtils.getFileName(uriParsed),
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

    override suspend fun getKeyframes(uri: String): List<Long> = withContext(ioDispatcher) {
        engine.getKeyframes(uri).getOrElse { emptyList() }
    }

    override suspend fun extractWaveform(
        uri: String,
        onProgress: ((WaveformResult) -> Unit)?
    ): WaveformResult? {
        return waveformExtractor.extract(uri, onProgress = onProgress)
    }

    override suspend fun getFrameAt(uri: String, positionMs: Long) = withContext(ioDispatcher) {
        engine.getFrameAt(uri, positionMs)
    }

    override suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): String? {
        return storageUtils.createMediaOutputUri(fileName, isAudio)?.toString()
    }

    override suspend fun createImageOutputUri(fileName: String): String? {
        return storageUtils.createImageOutputUri(fileName)?.toString()
    }

    override fun finalizeImage(uri: String) {
        storageUtils.finalizeImage(Uri.parse(uri))
    }

    override fun finalizeVideo(uri: String) {
        storageUtils.finalizeVideo(Uri.parse(uri))
    }

    override fun finalizeAudio(uri: String) {
        storageUtils.finalizeAudio(Uri.parse(uri))
    }

    override fun getFileName(uriString: String): String {
        return storageUtils.getFileName(Uri.parse(uriString))
    }

    override suspend fun executeLosslessCut(
        inputUri: String,
        outputUri: String,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ) = engine.executeLosslessCut(inputUri, outputUri, startMs, endMs, keepAudio, keepVideo, rotationOverride, selectedTracks)

    override suspend fun executeLosslessMerge(
        outputUri: String,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ) = engine.executeLosslessMerge(outputUri, clips, keepAudio, keepVideo, rotationOverride, selectedTracks)

    // --- Session & Cache Management ---

    override suspend fun saveSession(clips: List<MediaClip>) = withContext(ioDispatcher) {
        if (clips.isEmpty()) return@withContext
        try {
            val sessionId = getSessionId(clips.first().uri.toString())
            val sessionFile = File(context.cacheDir, "session_$sessionId.json")
            val jsonText = json.encodeToString(clips)
            sessionFile.writeText(jsonText)
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to save session", e)
        }
    }

    override suspend fun restoreSession(uri: String): List<MediaClip>? = withContext(ioDispatcher) {
        try {
            val sessionId = getSessionId(uri)
            val sessionFile = File(context.cacheDir, "session_$sessionId.json")
            if (!sessionFile.exists()) return@withContext null
            
            val jsonText = sessionFile.readText()
            val restoredClips: List<MediaClip> = json.decodeFromString(jsonText)
            
            restoredClips.filter { clip ->
                try {
                    val clipUri = Uri.parse(clip.uri)
                    context.contentResolver.query(clipUri, null, null, null, null)?.use { 
                        it.moveToFirst() 
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to restore session", e)
            null
        }
    }

    override suspend fun hasSavedSession(uri: String): Boolean = withContext(ioDispatcher) {
        val sessionFile = File(context.cacheDir, "session_${getSessionId(uri)}.json")
        sessionFile.exists()
    }

    override suspend fun saveWaveformToCache(cacheKey: String, result: WaveformResult) {
        withContext(ioDispatcher) {
            try {
                val cacheFile = File(context.cacheDir, cacheKey)
                DataOutputStream(FileOutputStream(cacheFile)).use { out ->
                    out.writeInt(2) // Version 2: 100Hz resolution
                    out.writeLong(result.durationUs)
                    out.writeFloat(result.maxAmplitude)
                    out.writeInt(result.rawAmplitudes.size)
                    result.rawAmplitudes.forEach { out.writeFloat(it) }
                }
            } catch (e: Exception) {
                Log.e("VideoEditingRepositoryImpl", "Failed to save waveform to cache", e)
            }
        }
    }

    override suspend fun loadWaveformFromCache(cacheKey: String): WaveformResult? = withContext(ioDispatcher) {
        val cacheFile = File(context.cacheDir, cacheKey)
        if (!cacheFile.exists()) return@withContext null
        try {
            DataInputStream(FileInputStream(cacheFile)).use { input ->
                val version = input.readInt()
                if (version == 2) {
                    val durationUs = input.readLong()
                    val maxAmplitude = input.readFloat()
                    val size = input.readInt()
                    val amplitudes = FloatArray(size)
                    for (i in 0 until size) {
                        amplitudes[i] = input.readFloat()
                    }
                    WaveformResult(amplitudes, maxAmplitude, durationUs)
                } else {
                    null // Version mismatch, ignore old cache
                }
            }
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to load waveform from cache", e)
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
                Log.e("VideoEditingRepositoryImpl", "Failed to evict old cache files", e)
            }
        }
    }

    override suspend fun writeSnapshot(bitmap: ByteArray, outputUri: String, format: String, quality: Int): Boolean = withContext(ioDispatcher) {
        try {
            val uriParsed = Uri.parse(outputUri)
            context.contentResolver.openOutputStream(uriParsed)?.use { outputStream ->
                outputStream.write(bitmap)
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to write snapshot", e)
            false
        }
    }

    private fun getSessionId(uriString: String): String {
        return uriString.hashCode().toString()
    }
}
