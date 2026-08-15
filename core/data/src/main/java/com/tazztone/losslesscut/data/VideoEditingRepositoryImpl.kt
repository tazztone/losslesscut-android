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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import com.tazztone.losslesscut.domain.di.IoDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject
import com.tazztone.losslesscut.domain.cache.IAnalysisCache
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class VideoEditingRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: ILosslessEngine,
    private val storageUtils: StorageUtils,
    private val waveformExtractor: AudioWaveformExtractor,
    private val analysisCache: IAnalysisCache,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IVideoEditingRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val sessionIndexMutex = Mutex()
    private val sessionsDir: File by lazy {
        File(context.noBackupFilesDir, "editing_sessions").also { it.mkdirs() }
    }

    override suspend fun createClipFromUri(uri: String): Result<MediaClip> = withContext(ioDispatcher) {
        val uriParsed = Uri.parse(uri)
        engine.getMediaMetadata(uri).map { meta ->
            validateMimeCompatibility(meta.videoMime, meta.audioMime)

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
                    MediaTrack(
                        id = it.id,
                        mimeType = it.mimeType,
                        isVideo = it.isVideo,
                        isAudio = it.isAudio,
                        language = it.language,
                        title = it.title,
                        channelCount = it.channelCount,
                        sampleRate = it.sampleRate
                    )
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

    override suspend fun getFileName(uriString: String): String {
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
            val sessionFile = File(sessionsDir, "session_$sessionId.json")
            val temporaryFile = File(sessionsDir, "session_$sessionId.json.tmp")
            try {
                temporaryFile.writeText(json.encodeToString(clips))
                Files.move(temporaryFile.toPath(), sessionFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile.toPath(), sessionFile.toPath(), REPLACE_EXISTING)
            } finally {
                if (temporaryFile.exists()) temporaryFile.delete()
            }
            updateSessionIndex(
                SessionSummary(
                    uri = clips.first().uri,
                    fileName = clips.first().fileName,
                    clipCount = clips.size,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to save session", e)
        }
    }

    override suspend fun restoreSession(uri: String): List<MediaClip>? = withContext(ioDispatcher) {
        try {
            val sessionId = getSessionId(uri)
            val sessionFile = File(sessionsDir, "session_$sessionId.json")
            if (!sessionFile.exists()) return@withContext null
            
            val jsonText = sessionFile.readText()
            val restoredClips: List<MediaClip> = json.decodeFromString(jsonText)
            
            kotlinx.coroutines.coroutineScope {
                restoredClips.map { clip ->
                    async {
                        val isValid = try {
                            val clipUri = Uri.parse(clip.uri)
                            context.contentResolver.query(clipUri, null, null, null, null)?.use {
                                it.moveToFirst()
                            } ?: false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        }
                        if (isValid) clip else null
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to restore session", e)
            null
        }
    }

    override suspend fun hasSavedSession(uri: String): Boolean = withContext(ioDispatcher) {
        val sessionFile = File(sessionsDir, "session_${getSessionId(uri)}.json")
        sessionFile.exists()
    }

    override suspend fun listSavedSessions(): List<SessionSummary> = withContext(ioDispatcher) {
        sessionIndexMutex.withLock {
            readSessionIndex()
                .filter { File(sessionsDir, "session_${getSessionId(it.uri)}.json").exists() }
                .sortedByDescending { it.updatedAtEpochMs }
        }
    }

    override suspend fun deleteSession(uri: String): Unit = withContext(ioDispatcher) {
        sessionIndexMutex.withLock {
            File(sessionsDir, "session_${getSessionId(uri)}.json").delete()
            writeSessionIndex(readSessionIndex().filterNot { it.uri == uri })
        }
    }

    override suspend fun getWaveform(
        clip: MediaClip,
        onProgress: ((WaveformResult) -> Unit)?
    ): WaveformResult? = withContext(ioDispatcher) {
        val cached = analysisCache.getWaveform(clip)
        if (cached != null) {
            return@withContext cached
        }

        val extracted = extractWaveform(clip.uri, onProgress)
        if (extracted != null) {
            analysisCache.saveWaveform(clip, extracted)
        }
        extracted
    }

    override suspend fun writeSnapshot(bitmap: ByteArray, outputUri: String, format: String, quality: Int): Boolean = withContext(ioDispatcher) {
        try {
            val uriParsed = Uri.parse(outputUri)
            context.contentResolver.openOutputStream(uriParsed)?.use { outputStream ->
                outputStream.write(bitmap)
                true
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VideoEditingRepositoryImpl", "Failed to write snapshot", e)
            false
        }
    }

    private fun getSessionId(uriString: String): String {
        return HashUtils.sha256(uriString)
    }

    private suspend fun updateSessionIndex(summary: SessionSummary) {
        sessionIndexMutex.withLock {
            val updated = buildList {
                add(summary)
                addAll(readSessionIndex().filterNot { it.uri == summary.uri })
            }.take(MAX_RECENT_SESSIONS)
            writeSessionIndex(updated)
        }
    }

    private fun readSessionIndex(): List<SessionSummary> {
        val indexFile = File(sessionsDir, SESSION_INDEX_FILE)
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<SessionSummary>>(indexFile.readText())
        }.getOrElse {
            Log.w("VideoEditingRepositoryImpl", "Failed to read recent session index", it)
            emptyList()
        }
    }

    private fun writeSessionIndex(sessions: List<SessionSummary>) {
        val indexFile = File(sessionsDir, SESSION_INDEX_FILE)
        val temporaryFile = File(sessionsDir, "$SESSION_INDEX_FILE.tmp")
        runCatching {
            temporaryFile.writeText(json.encodeToString(sessions))
            Files.move(temporaryFile.toPath(), indexFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(temporaryFile.toPath(), indexFile.toPath(), REPLACE_EXISTING)
        }.onFailure {
            Log.e("VideoEditingRepositoryImpl", "Failed to write recent session index", it)
        }.also {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun validateMimeCompatibility(videoMime: String?, audioMime: String?) {
        val unsupportedAudio = setOf("audio/mpeg", "audio/flac", "audio/vorbis", "audio/ac3", "audio/eac3", "audio/opus")
        val isAudioUnsupported = audioMime != null && unsupportedAudio.contains(audioMime.lowercase())
        
        require(!isAudioUnsupported) {
            if (videoMime == null) {
                "Unsupported audio format: $audioMime. Only AAC/M4A is supported for lossless remuxing."
            } else {
                "Unsupported audio format inside video: $audioMime. Lossless remuxing requires AAC."
            }
        }

        val unsupportedVideo = setOf("video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9", "video/av01")
        val isVideoUnsupported = videoMime != null && unsupportedVideo.contains(videoMime.lowercase())
        
        require(!isVideoUnsupported) {
            "Unsupported video format: $videoMime. Lossless remuxing requires H.264 or H.265."
        }
    }

    private companion object {
        const val SESSION_INDEX_FILE = "sessions_index.json"
        const val MAX_RECENT_SESSIONS = 5
    }
}
