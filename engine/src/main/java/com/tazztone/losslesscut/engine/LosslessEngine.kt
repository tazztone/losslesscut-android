package com.tazztone.losslesscut.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.engine.MediaMetadata
import com.tazztone.losslesscut.domain.engine.TrackMetadata
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.engine.muxing.ExtractorSampleCopier
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import com.tazztone.losslesscut.engine.muxing.MergeValidator
import com.tazztone.losslesscut.engine.muxing.MuxerWriter
import com.tazztone.losslesscut.engine.muxing.SampleTimeMapper
import com.tazztone.losslesscut.engine.muxing.SegmentGapCalculator
import com.tazztone.losslesscut.engine.muxing.SelectedTrackPlan
import com.tazztone.losslesscut.engine.muxing.TrackInspector
import com.tazztone.losslesscut.utils.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LosslessEngineImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageUtils: StorageUtils,
    private val collaborators: EngineCollaborators,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ILosslessEngine {
    
    private val dataSource get() = collaborators.dataSource
    private val inspector get() = collaborators.inspector
    private val timeMapper get() = collaborators.timeMapper
    private val mergeValidator get() = collaborators.mergeValidator

    companion object {
        private const val TAG = "LosslessEngine"
        private const val MAX_KEYFRAME_COUNT = 3000
        private const val MAX_PROBE_SAMPLES = 15000
        private const val DEFAULT_FPS = 30f
        private const val SNAPSHOT_QUALITY = 90
        private const val AUDIO_SAMPLE_RATE_44100 = 44100
        private const val MS_TO_US = 1000L

        fun getVideoFps(format: MediaFormat): Float {
            return if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                try {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                } catch (_: Exception) {
                    try {
                        format.getFloat(MediaFormat.KEY_FRAME_RATE)
                    } catch (_: Exception) {
                        DEFAULT_FPS
                    }
                }
            } else DEFAULT_FPS
        }
    }

    override suspend fun getKeyframes(videoUri: String): Result<List<Long>> = withContext(ioDispatcher) {
        val keyframes = mutableListOf<Long>()
        val extractor = MediaExtractor()
        try {
            dataSource.setExtractorSource(extractor, videoUri)
            val vIdx = findVideoTrack(extractor)
            if (vIdx >= 0) {
                extractor.selectTrack(vIdx)
                var count = 0; var totalSamples = 0
                while (extractor.sampleTime >= 0 && count < MAX_KEYFRAME_COUNT && 
                    totalSamples < MAX_PROBE_SAMPLES && currentCoroutineContext().isActive) {
                    if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        keyframes.add(extractor.sampleTime / MS_TO_US); count++
                    }
                    totalSamples++
                    if (!extractor.advance()) break
                }
            }
            Result.success(keyframes)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Error probing keyframes", e); Result.failure(e)
        } finally { extractor.release() }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) return i
        }
        return -1
    }

    override suspend fun getMediaMetadata(uri: String): Result<MediaMetadata> = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            dataSource.setRetrieverSource(retriever, uri)
            val basicMeta = LosslessEngineHelper.readBasicMetadata(retriever, uri)
            val extractor = MediaExtractor()
            try {
                dataSource.setExtractorSource(extractor, uri)
                if (extractor.trackCount == 0) return@withContext Result.failure(IOException("No tracks"))
                val trackData = LosslessEngineHelper.readTrackMetadata(extractor)
                Result.success(MediaMetadata(
                    basicMeta.duration, basicMeta.width, basicMeta.height, trackData.videoMime,
                    trackData.audioMime, trackData.sampleRate, trackData.channelCount, 
                    trackData.fps, basicMeta.rotation, trackData.tracks
                ))
            } finally { extractor.release() }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.e(TAG, "Failed to get metadata for $uri", e); Result.failure(e)
        } finally { retriever.release() }
    }

    override suspend fun getFrameAt(uri: String, positionMs: Long): ByteArray? = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            dataSource.setRetrieverSource(retriever, uri)
            val bitmap = retriever.getFrameAtTime(positionMs * MS_TO_US, MediaMetadataRetriever.OPTION_CLOSEST)
            bitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_QUALITY, stream); stream.toByteArray()
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null } finally {
            retriever.release()
        }
    }

    override suspend fun executeLosslessCut(
        inputUri: String, outputUri: String, startMs: Long, endMs: Long,
        keepAudio: Boolean, keepVideo: Boolean, rotationOverride: Int?, selectedTracks: List<Int>?
    ): Result<String> = withContext(ioDispatcher) {
        val outUriParsed = Uri.parse(outputUri)
        if (endMs <= startMs) return@withContext Result.failure(IllegalArgumentException("endMs <= startMs"))
        val extractor = MediaExtractor()
        var muxerWriter: MuxerWriter? = null; var pfd: ParcelFileDescriptor? = null
        try {
            dataSource.setExtractorSource(extractor, inputUri)
            pfd = context.contentResolver.openFileDescriptor(outUriParsed, "rw") ?: 
                return@withContext Result.failure(IOException("Failed to open PFD"))
            muxerWriter = MuxerWriter(MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
            val plan = inspector.inspect(extractor, muxerWriter, keepAudio, keepVideo, selectedTracks)
            if (plan.trackMap.isEmpty()) return@withContext Result.failure(IOException("No tracks found"))
            val endUs = if (endMs > 0) {
                endMs * MS_TO_US
            } else if (plan.durationUs > 0) {
                plan.durationUs
            } else {
                Long.MAX_VALUE
            }
            if (plan.hasVideoTrack && rotationOverride != null) muxerWriter.setOrientationHint(rotationOverride)
            muxerWriter.start()
            val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)
            copier.copy(plan, startMs * MS_TO_US, endUs, ByteBuffer.allocateDirect(plan.bufferSize))
            if (plan.hasVideoTrack) {
                storageUtils.finalizeVideo(outUriParsed)
            } else {
                storageUtils.finalizeAudio(outUriParsed)
            }
            Result.success(outputUri)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) } finally {
            muxerWriter?.stopAndRelease(); pfd?.close(); extractor.release()
        }
    }

    override suspend fun executeLosslessMerge(
        outputUri: String, clips: List<MediaClip>, keepAudio: Boolean, keepVideo: Boolean,
        rotationOverride: Int?, selectedTracks: List<Int>?
    ): Result<String> = withContext(ioDispatcher) {
        val outUriParsed = Uri.parse(outputUri)
        if (clips.isEmpty()) return@withContext Result.failure(IOException("No clips"))
        var muxerWriter: MuxerWriter? = null; var pfd: ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(outUriParsed, "rw") ?: 
                return@withContext Result.failure(IOException("Failed to open PFD"))
            muxerWriter = MuxerWriter(MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
            val init = initializeMuxerForMerge(clips[0], muxerWriter, keepAudio, keepVideo, selectedTracks)
            if (init.plan.trackMap.isEmpty()) return@withContext Result.failure(IOException("No tracks found"))
            if (init.plan.hasVideoTrack) muxerWriter.setOrientationHint(rotationOverride ?: clips[0].rotation)
            muxerWriter.start()
            val mParams = LosslessEngineHelper.MergeParams(
                clips, muxerWriter, init, keepAudio, keepVideo, selectedTracks
            )
            processClipsForMerge(mParams)
            if (init.plan.hasVideoTrack) {
                storageUtils.finalizeVideo(outUriParsed)
            } else {
                storageUtils.finalizeAudio(outUriParsed)
            }
            Result.success(outputUri)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) } finally {
            muxerWriter?.stopAndRelease(); pfd?.close()
        }
    }

    private fun initializeMuxerForMerge(
        firstClip: MediaClip, mux: MuxerWriter, keepA: Boolean, keepV: Boolean, sel: List<Int>?
    ): LosslessEngineHelper.MergeInitialPlan {
        val ex = MediaExtractor()
        return try {
            dataSource.setExtractorSource(ex, firstClip.uri)
            val plan = inspector.inspect(ex, mux, keepA, keepV, sel)
            val trackInfo = readTracksForInitialPlan(ex)
            LosslessEngineHelper.MergeInitialPlan(
                plan, trackInfo.audioRate, trackInfo.videoFps, trackInfo.vMime, trackInfo.aMime
            )
        } finally { ex.release() }
    }

    private fun readTracksForInitialPlan(ex: MediaExtractor): InitialTrackInfo {
        var audioRate = AUDIO_SAMPLE_RATE_44100; var videoFps = DEFAULT_FPS
        var vMime: String? = null; var aMime: String? = null
        for (i in 0 until ex.trackCount) {
            val format = ex.getTrackFormat(i); val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) { 
                vMime = mime; videoFps = getVideoFps(format) 
            } else if (mime.startsWith("audio/")) { 
                aMime = mime
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    audioRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            }
        }
        return InitialTrackInfo(audioRate, videoFps, vMime, aMime)
    }

    private suspend fun processClipsForMerge(params: LosslessEngineHelper.MergeParams) {
        val init = params.initialPlan
        val gapUs = SegmentGapCalculator.calculateGapUs(init.audioSampleRate, init.videoFps)
        var maxBuf = init.plan.bufferSize; var buffer = ByteBuffer.allocateDirect(maxBuf); var offUs = 0L
        for (clip in params.clips) {
            val ex = MediaExtractor()
            try {
                dataSource.setExtractorSource(ex, clip.uri)
                val trackInfo = mapTracksForMerge(ex, params)
                if (trackInfo.maxInputSize > maxBuf) {
                    maxBuf = trackInfo.maxInputSize; buffer = ByteBuffer.allocateDirect(maxBuf)
                }
                val cp = ExtractorSampleCopier(ex, params.muxerWriter, timeMapper)
                val cpParams = LosslessEngineHelper.CopySegmentsParams(
                    clip, cp, trackInfo, buffer, offUs, gapUs, maxBuf
                )
                offUs = LosslessEngineHelper.copyClipSegments(cpParams)
            } finally { ex.release() }
        }
    }

    private fun mapTracksForMerge(
        ex: MediaExtractor, params: LosslessEngineHelper.MergeParams
    ): LosslessEngineHelper.ClipTrackInfo {
        val tMap = mutableMapOf<Int, Int>(); val isVMap = mutableMapOf<Int, Boolean>(); var maxSize = 0
        for (i in 0 until ex.trackCount) {
            val format = ex.getTrackFormat(i)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxSize = maxOf(maxSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            val isV = mime.startsWith("video/"); val isA = mime.startsWith("audio/")
            val isS = if (params.selectedTracks != null) {
                params.selectedTracks.contains(i)
            } else {
                (isV && params.keepVideo) || (isA && params.keepAudio)
            }
            if (!isS) continue
            val muxIdx = LosslessEngineHelper.findMuxerTrack(params.initialPlan, isV)
            if (muxIdx != null) {
                tMap[i] = muxIdx; isVMap[i] = isV
                val expMime = if (isV) params.initialPlan.expectedVideoMime else params.initialPlan.expectedAudioMime
                mergeValidator.validateCodec("clip", mime, expMime, if (isV) "video" else "audio")
            }
        }
        return LosslessEngineHelper.ClipTrackInfo(tMap, isVMap, maxSize)
    }

    private data class InitialTrackInfo(val audioRate: Int, val videoFps: Float, val vMime: String?, val aMime: String?)
}
