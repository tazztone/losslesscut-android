package com.tazztone.losslesscut.engine.muxing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tazztone.losslesscut.domain.engine.IMediaFinalizer
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.engine.LosslessEngineHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

public data class MuxingCutRequest(
    public val inputUri: String,
    public val outputUri: String,
    public val startMs: Long,
    public val endMs: Long,
    public val keepAudio: Boolean = true,
    public val keepVideo: Boolean = true,
    public val rotationOverride: Int? = null,
    public val selectedTracks: List<Int>? = null
)

public data class MuxingMergeRequest(
    public val outputUri: String,
    public val clips: List<MediaClip>,
    public val keepAudio: Boolean = true,
    public val keepVideo: Boolean = true,
    public val rotationOverride: Int? = null,
    public val selectedTracks: List<Int>? = null
)

public data class PipelineTrackInfo(
    public val audioRate: Int,
    public val videoFps: Float,
    public val vMime: String?,
    public val aMime: String?,
    public val videoFormat: MediaFormat? = null,
    public val audioFormat: MediaFormat? = null
)

@Singleton
public class MuxingPipeline @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaDataSource,
    private val inspector: TrackInspector,
    private val timeMapper: SampleTimeMapper,
    private val mergeValidator: MergeValidator,
    private val mediaFinalizer: IMediaFinalizer
) {
    public companion object {
        private const val TAG = "MuxingPipeline"
        private const val MS_TO_US = 1000L
        private const val DEFAULT_FPS = 30f
        private const val AUDIO_SAMPLE_RATE_44100 = 44100

        public fun getVideoFps(format: MediaFormat): Float {
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

    @Suppress("LongMethod", "TooGenericExceptionCaught", "ReturnCount")
    public suspend fun executeCut(request: MuxingCutRequest): Result<String> {
        if (request.endMs <= request.startMs) {
            return Result.failure(IllegalArgumentException("endMs <= startMs"))
        }

        val outUriParsed = Uri.parse(request.outputUri)
        val extractor = MediaExtractor()
        var muxerWriter: MuxerWriter? = null
        var pfd: ParcelFileDescriptor? = null
        var success = false

        val cutResult: Result<String> = try {
            dataSource.setExtractorSource(extractor, request.inputUri)
            pfd = context.contentResolver.openFileDescriptor(outUriParsed, "rw")
                ?: return Result.failure(IOException("Failed to open PFD"))
            muxerWriter = MuxerWriter(MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))

            val plan = inspector.inspect(
                extractor, muxerWriter, request.keepAudio, request.keepVideo, request.selectedTracks
            )
            if (plan.trackMap.isEmpty()) {
                Result.failure(IOException("No tracks found"))
            } else {
                val endUs = if (request.endMs > 0) {
                    request.endMs * MS_TO_US
                } else if (plan.durationUs > 0) {
                    plan.durationUs
                } else {
                    Long.MAX_VALUE
                }

                if (plan.hasVideoTrack && request.rotationOverride != null) {
                    muxerWriter.setOrientationHint(request.rotationOverride)
                }

                muxerWriter.start()
                val copier = ExtractorSampleCopier(extractor, muxerWriter, timeMapper)
                copier.copy(plan, request.startMs * MS_TO_US, endUs, ByteBuffer.allocateDirect(plan.bufferSize))

                if (!muxerWriter.stopAndReleaseSafely()) {
                    muxerWriter = null
                    throw IOException("Failed to finalize muxer")
                }
                muxerWriter = null
                pfd.close()
                pfd = null

                if (plan.hasVideoTrack) {
                    mediaFinalizer.finalizeVideo(request.outputUri)
                } else {
                    mediaFinalizer.finalizeAudio(request.outputUri)
                }
                success = true
                Result.success(request.outputUri)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            muxerWriter?.stopAndRelease()
            pfd?.close()
            extractor.release()
            if (!success) {
                try {
                    context.contentResolver.delete(outUriParsed, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete corrupted output from authority: ${outUriParsed.authority}", e)
                }
            }
        }
        return cutResult
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    public suspend fun executeMerge(request: MuxingMergeRequest): Result<String> {
        if (request.clips.isEmpty()) return Result.failure(IOException("No clips"))

        val outUriParsed = Uri.parse(request.outputUri)
        var muxerWriter: MuxerWriter? = null
        var pfd: ParcelFileDescriptor? = null
        var success = false

        val mergeResult: Result<String> = try {
            pfd = context.contentResolver.openFileDescriptor(outUriParsed, "rw")
                ?: return Result.failure(IOException("Failed to open PFD"))
            muxerWriter = MuxerWriter(MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))

            val init = initializeMuxerForMerge(
                request.clips[0], muxerWriter, request.keepAudio, request.keepVideo, request.selectedTracks
            )
            if (init.plan.trackMap.isEmpty()) {
                Result.failure(IOException("No tracks found"))
            } else {
                if (init.plan.hasVideoTrack) {
                    muxerWriter.setOrientationHint(request.rotationOverride ?: request.clips[0].rotation)
                }

                muxerWriter.start()
                val mParams = LosslessEngineHelper.MergeParams(
                    request.clips, muxerWriter, init, request.keepAudio, request.keepVideo, request.selectedTracks
                )
                processClipsForMerge(mParams)

                if (!muxerWriter.stopAndReleaseSafely()) {
                    muxerWriter = null
                    throw IOException("Failed to finalize muxer")
                }
                muxerWriter = null
                pfd.close()
                pfd = null

                if (init.plan.hasVideoTrack) {
                    mediaFinalizer.finalizeVideo(request.outputUri)
                } else {
                    mediaFinalizer.finalizeAudio(request.outputUri)
                }
                success = true
                Result.success(request.outputUri)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            muxerWriter?.stopAndRelease()
            pfd?.close()
            if (!success) {
                try {
                    context.contentResolver.delete(outUriParsed, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete corrupted output from authority: ${outUriParsed.authority}", e)
                }
            }
        }
        return mergeResult
    }

    private fun initializeMuxerForMerge(
        firstClip: MediaClip,
        mux: MuxerWriter,
        keepA: Boolean,
        keepV: Boolean,
        sel: List<Int>?
    ): LosslessEngineHelper.MergeInitialPlan {
        val ex = MediaExtractor()
        return try {
            dataSource.setExtractorSource(ex, firstClip.uri)
            val plan = inspector.inspect(ex, mux, keepA, keepV, sel)
            val trackInfo = readTracksForInitialPlan(ex)
            LosslessEngineHelper.MergeInitialPlan(
                plan,
                trackInfo.audioRate,
                trackInfo.videoFps,
                trackInfo.vMime,
                trackInfo.aMime,
                trackInfo.videoFormat,
                trackInfo.audioFormat,
                readSelectedTrackLayout(ex, keepA, keepV, sel)
            )
        } finally {
            ex.release()
        }
    }

    private fun readTracksForInitialPlan(ex: MediaExtractor): PipelineTrackInfo {
        var audioRate = AUDIO_SAMPLE_RATE_44100
        var videoFps = DEFAULT_FPS
        var vMime: String? = null
        var aMime: String? = null
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until ex.trackCount) {
            val format = ex.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && vMime == null) {
                vMime = mime
                videoFps = getVideoFps(format)
                videoFormat = format
            } else if (mime.startsWith("audio/") && aMime == null) {
                aMime = mime
                audioFormat = format
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    audioRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            }
        }
        return PipelineTrackInfo(audioRate, videoFps, vMime, aMime, videoFormat, audioFormat)
    }

    private fun readSelectedTrackLayout(
        ex: MediaExtractor,
        keepAudio: Boolean,
        keepVideo: Boolean,
        selectedTracks: List<Int>?
    ): List<String> {
        val selected = selectedTracks?.toSet()
        return (0 until ex.trackCount).mapNotNull { index ->
            val format = ex.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
            val isVideo = mime.startsWith("video/")
            val isAudio = mime.startsWith("audio/")
            val keepType = when {
                isVideo -> keepVideo
                isAudio -> keepAudio
                else -> false
            }
            if (!keepType) {
                return@mapNotNull null
            }
            if (selected != null && index !in selected) return@mapNotNull null

            val language = runCatching { format.getString(MediaFormat.KEY_LANGUAGE) }.getOrNull().orEmpty()
            val title = runCatching { format.getString("title") }.getOrNull().orEmpty()
            val width = runCatching { format.getInteger(MediaFormat.KEY_WIDTH) }.getOrNull() ?: 0
            val height = runCatching { format.getInteger(MediaFormat.KEY_HEIGHT) }.getOrNull() ?: 0
            val sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull() ?: 0
            val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull() ?: 0
            "$index|$mime|$language|$title|$width|$height|$sampleRate|$channels"
        }
    }

    private suspend fun processClipsForMerge(params: LosslessEngineHelper.MergeParams) {
        val init = params.initialPlan
        val hasVideo = init.plan.isVideoTrackMap.values.any { it }
        val hasAudio = init.plan.isVideoTrackMap.values.any { !it }
        val gapUs = SegmentGapCalculator.calculateGapUs(
            init.audioSampleRate,
            init.videoFps,
            hasAudio = hasAudio,
            hasVideo = hasVideo
        )
        var maxBuf = init.plan.bufferSize
        var buffer = ByteBuffer.allocateDirect(maxBuf)
        var offUs = 0L

        for (clip in params.clips) {
            currentCoroutineContext().ensureActive()
            val ex = MediaExtractor()
            try {
                dataSource.setExtractorSource(ex, clip.uri)
                val layout = readSelectedTrackLayout(
                    ex, params.keepAudio, params.keepVideo, params.selectedTracks
                )
                if (layout != init.expectedTrackLayout) {
                    throw IOException("Selected track layout differs between clips")
                }
                val cPlan = inspector.inspectClipForMerge(ex, init, params.keepAudio, params.keepVideo, params.selectedTracks)
                val trackInfo = readTracksForInitialPlan(ex)
                mergeValidator.validateTrack(
                    clip.uri,
                    trackInfo.videoFormat,
                    init.expectedVideoFormat,
                    "video",
                    params.keepVideo
                )
                mergeValidator.validateTrack(
                    clip.uri,
                    trackInfo.audioFormat,
                    init.expectedAudioFormat,
                    "audio",
                    params.keepAudio
                )
                if (cPlan.bufferSize > maxBuf) {
                    maxBuf = cPlan.bufferSize
                    buffer = ByteBuffer.allocateDirect(maxBuf)
                }
                val copier = ExtractorSampleCopier(ex, params.muxerWriter, timeMapper)
                val clipTrackInfo = LosslessEngineHelper.ClipTrackInfo(
                    cPlan.trackMap, cPlan.isVideoTrackMap, cPlan.bufferSize
                )
                val copyParams = LosslessEngineHelper.CopySegmentsParams(
                    clip, copier, clipTrackInfo, buffer, offUs, gapUs, cPlan.bufferSize
                )
                offUs = LosslessEngineHelper.copyClipSegments(copyParams)
            } finally {
                ex.release()
            }
        }
    }
}
