package com.tazztone.losslesscut.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.engine.MediaMetadata
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.engine.muxing.MuxingCutRequest
import com.tazztone.losslesscut.engine.muxing.MuxingMergeRequest
import com.tazztone.losslesscut.engine.muxing.MuxingPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LosslessEngineImpl @Inject constructor(
    @param:ApplicationContext context: Context,
    private val collaborators: EngineCollaborators,
    private val muxingPipeline: MuxingPipeline,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ILosslessEngine {
    
    private val appContext: Context = context
    private val dataSource get() = collaborators.dataSource

    public companion object {
        private const val TAG = "LosslessEngine"
        private const val MS_TO_US = 1000L
        private const val JPEG_QUALITY = 80
    }

    override suspend fun getMediaMetadata(uriString: String): Result<MediaMetadata> = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            retriever.setDataSource(appContext, uri)
            dataSource.setExtractorSource(extractor, uriString)

            val basic = LosslessEngineHelper.readBasicMetadata(retriever, uriString)
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
            Log.e(TAG, "Failed to read metadata for $uriString", e)
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

    override suspend fun getKeyframes(uriString: String): Result<List<Long>> = withContext(ioDispatcher) {
        val extractor = MediaExtractor()
        try {
            dataSource.setExtractorSource(extractor, uriString)
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
            Log.e(TAG, "Failed to extract keyframes from $uriString", e)
            Result.failure(e)
        } finally {
            extractor.release()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getFrameAt(uri: String, positionMs: Long): ByteArray? = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, Uri.parse(uri))
            val bitmap = retriever.getFrameAtTime(positionMs * MS_TO_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                bitmap.recycle()
                stream.toByteArray()
            } else {
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture frame at $positionMs for $uri", e)
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
    ): Result<String> = withContext(ioDispatcher) {
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
    ): Result<String> = withContext(ioDispatcher) {
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
