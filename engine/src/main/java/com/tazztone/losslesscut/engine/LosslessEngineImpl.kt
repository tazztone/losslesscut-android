package com.tazztone.losslesscut.engine

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.tazztone.losslesscut.domain.di.EngineDispatcher
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.engine.MediaMetadata
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.engine.muxing.MuxingCutRequest
import com.tazztone.losslesscut.engine.muxing.MuxingMergeRequest
import com.tazztone.losslesscut.engine.muxing.MuxingPipeline
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LosslessEngineImpl @Inject constructor(
    private val dataSource: MediaDataSource,
    private val muxingPipeline: MuxingPipeline,
    @param:EngineDispatcher private val engineDispatcher: CoroutineDispatcher
) : ILosslessEngine {
    public companion object {
        private const val TAG = "LosslessEngine"
        private const val MS_TO_US = 1000L
        private const val MAX_QUALITY = 100
    }

    override suspend fun getMediaMetadata(uri: String): Result<MediaMetadata> = withContext(engineDispatcher) {
        val authority = Uri.parse(uri).authority
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            dataSource.setRetrieverSource(retriever, uri)
            dataSource.setExtractorSource(extractor, uri)

            val basic = LosslessEngineHelper.readBasicMetadata(retriever, uri)
            val trackData = LosslessEngineHelper.readTrackMetadata(extractor)

            Result.success(MediaMetadata(
                durationMs = basic.duration,
                width = basic.width,
                height = basic.height,
                rotation = basic.rotation,
                videoMime = trackData.videoMime,
                audioMime = trackData.audioMime,
                sampleRate = trackData.sampleRate,
                channelCount = trackData.channelCount,
                fps = trackData.fps,
                tracks = trackData.tracks
            ))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read metadata from authority: $authority", e)
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to release retriever", e)
            }
            extractor.release()
        }
    }

    override suspend fun getKeyframes(videoUri: String): Result<List<Long>> = withContext(engineDispatcher) {
        val extractor = MediaExtractor()
        try {
            dataSource.setExtractorSource(extractor, videoUri)
            val trackData = LosslessEngineHelper.readTrackMetadata(extractor)
            val videoTrackIndex = trackData.tracks.firstOrNull { it.isVideo }?.id
                ?: return@withContext Result.failure(IllegalStateException("No video track found"))

            extractor.selectTrack(videoTrackIndex)
            val keyframes = mutableListOf<Long>()

            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break

                val flags = extractor.sampleFlags
                if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    keyframes.add(sampleTime / MS_TO_US)
                }
                if (!extractor.advance()) break
            }

            Result.success(keyframes)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract keyframes from authority: ${Uri.parse(videoUri).authority}", e)
            Result.failure(e)
        } finally {
            extractor.release()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getFrameAt(
        uri: String,
        positionMs: Long,
        format: String,
        quality: Int
    ): ByteArray? = withContext(engineDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            dataSource.setRetrieverSource(retriever, uri)
            val bitmap = retriever.getFrameAtTime(positionMs * MS_TO_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                val compressFormat = if (format.equals("PNG", ignoreCase = true)) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                val compressed = bitmap.compress(compressFormat, quality.coerceIn(0, MAX_QUALITY), stream)
                bitmap.recycle()
                if (compressed) stream.toByteArray() else null
            } else {
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture frame at $positionMs from authority: ${Uri.parse(uri).authority}", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to release retriever", e)
            }
        }
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
    ): Result<String> = withContext(engineDispatcher) {
        val request = MuxingCutRequest(
            inputUri = inputUri,
            outputUri = outputUri,
            startMs = startMs,
            endMs = endMs,
            keepAudio = keepAudio,
            keepVideo = keepVideo,
            rotationOverride = rotationOverride,
            selectedTracks = selectedTracks
        )
        muxingPipeline.executeCut(request)
    }

    override suspend fun executeLosslessMerge(
        outputUri: String,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String> = withContext(engineDispatcher) {
        val request = MuxingMergeRequest(
            outputUri = outputUri,
            clips = clips,
            keepAudio = keepAudio,
            keepVideo = keepVideo,
            rotationOverride = rotationOverride,
            selectedTracks = selectedTracks
        )
        muxingPipeline.executeMerge(request)
    }
}
