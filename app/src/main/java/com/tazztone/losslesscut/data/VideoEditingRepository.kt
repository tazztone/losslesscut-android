package com.tazztone.losslesscut.data

import android.content.Context
import android.net.Uri
import com.tazztone.losslesscut.engine.AudioWaveformExtractor
import com.tazztone.losslesscut.engine.LosslessEngineInterface
import com.tazztone.losslesscut.utils.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.tazztone.losslesscut.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoEditingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: LosslessEngineInterface,
    private val storageUtils: StorageUtils,
    private val waveformExtractor: AudioWaveformExtractor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun createClipFromUri(uri: Uri): Result<MediaClip> = withContext(ioDispatcher) {
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

    suspend fun getKeyframes(uri: Uri): List<Long> = withContext(ioDispatcher) {
        engine.getKeyframes(context, uri).getOrElse { emptyList() }
    }

    suspend fun extractWaveform(uri: Uri, onProgress: ((FloatArray) -> Unit)? = null): FloatArray? {
        return waveformExtractor.extract(context, uri, onProgress = onProgress)
    }

    suspend fun getFrameAt(uri: Uri, positionMs: Long) = withContext(ioDispatcher) {
        engine.getFrameAt(context, uri, positionMs)
    }

    suspend fun createMediaOutputUri(fileName: String, isAudio: Boolean): Uri? {
        return storageUtils.createMediaOutputUri(fileName, isAudio)
    }

    suspend fun createImageOutputUri(fileName: String): Uri? {
        return storageUtils.createImageOutputUri(fileName)
    }

    fun finalizeImage(uri: Uri) {
        storageUtils.finalizeImage(uri)
    }

    fun finalizeVideo(uri: Uri) {
        storageUtils.finalizeVideo(uri)
    }

    fun finalizeAudio(uri: Uri) {
        storageUtils.finalizeAudio(uri)
    }

    fun getFileName(uri: Uri): String {
        return storageUtils.getFileName(uri)
    }

    suspend fun executeLosslessCut(
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ) = engine.executeLosslessCut(context, inputUri, outputUri, startMs, endMs, keepAudio, keepVideo, rotationOverride, selectedTracks)

    suspend fun executeLosslessMerge(
        outputUri: Uri,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ) = engine.executeLosslessMerge(context, outputUri, clips, keepAudio, keepVideo, rotationOverride, selectedTracks)
}
