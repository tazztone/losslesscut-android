package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualStrategy
import com.tazztone.losslesscut.domain.usecase.IVisualSegmentDetector
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class VisualSegmentDetectorImpl @Inject constructor(
    private val dataSource: MediaDataSource
) : IVisualSegmentDetector {

    private class DetectionContext(
        val extractor: MediaExtractor,
        val codec: MediaCodec,
        val sampleIntervalMs: Long,
        val analyses: MutableList<FrameAnalysis> = mutableListOf()
    ) {
        var previousHash: Long? = null
        var previousSmallY: ByteArray? = null

        var lastProcessedUs: Long = -1L
        val sampleIntervalUs: Long = sampleIntervalMs * US_PER_MS
        val info: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

        var sawInputEOS = false
        var sawOutputEOS = false
    }

    override suspend fun analyze(
        uri: String,
        sampleIntervalMs: Long,
        onProgress: (Int, Int) -> Unit
    ): List<FrameAnalysis> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var context: DetectionContext? = null

        try {
            dataSource.setExtractorSource(extractor, uri)
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex == -1) {
                println("No video track found")
                return emptyList()
            }

            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()

            codec = MediaCodec.createDecoderByType(mime)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec.configure(format, null, null, 0 /* flags */)
            codec.start()

            context = DetectionContext(extractor, codec, sampleIntervalMs)
            val estimatedTotal = (durationUs / context.sampleIntervalUs).toInt()
            
            detectLoop(context, estimatedTotal, onProgress)

        } catch (e: MediaCodec.CodecException) {
            Log.e(TAG, "MediaCodec error during visual detection", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state during visual detection", e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Unexpected error during visual detection", e)
        } finally {
            cleanup(codec, extractor)
        }

        return context?.analyses ?: emptyList()
    }

    private fun cleanup(codec: MediaCodec?, extractor: MediaExtractor) {
        try { 
            codec?.stop() 
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state when stopping codec during cleanup", e)
        }
        try { 
            codec?.release() 
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state when releasing codec during cleanup", e)
        }
        try { 
            extractor.release() 
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state when releasing extractor during cleanup", e)
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                extractor.selectTrack(i)
                return i
            }
        }
        return -1
    }

    private suspend fun detectLoop(ctx: DetectionContext, estimatedTotal: Int, onProgress: (Int, Int) -> Unit) {
        var processedCount = 0
        while (!ctx.sawOutputEOS) {
            
            if (!ctx.sawInputEOS) ctx.sawInputEOS = feedInput(ctx)

            val outputBufferIndex = ctx.codec.dequeueOutputBuffer(ctx.info, TIMEOUT_US)
            if (outputBufferIndex >= 0) {
                val wasProcessed = processOutputBufferResult(ctx, outputBufferIndex)
                if (wasProcessed) {
                    processedCount++
                    onProgress(processedCount, estimatedTotal)
                }
            }
        }
    }

    private fun processOutputBufferResult(ctx: DetectionContext, index: Int): Boolean {
        var didProcess = false
        if (ctx.info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            ctx.sawOutputEOS = true
        }

        if (ctx.info.size > 0) {
            val currentUs = ctx.info.presentationTimeUs
            if (shouldProcess(ctx.lastProcessedUs, currentUs, ctx.sampleIntervalUs)) {
                processOutputBuffer(ctx, index)
                ctx.lastProcessedUs = currentUs
                didProcess = true
            }
        }
        ctx.codec.releaseOutputBuffer(index, false)
        return didProcess
    }

    private fun feedInput(ctx: DetectionContext): Boolean {
        val inputBufferIndex = ctx.codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferIndex >= 0) {
            val inputBuffer = ctx.codec.getInputBuffer(inputBufferIndex) ?: return false
            val sampleSize = ctx.extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) {
                ctx.codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return true
            }
            ctx.codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, ctx.extractor.sampleTime, 0)
            ctx.extractor.advance()
        }
        return false
    }

    private fun shouldProcess(lastProcessedUs: Long, currentUs: Long, intervalUs: Long): Boolean {
        return lastProcessedUs == -1L || (currentUs - lastProcessedUs >= intervalUs)
    }

    private fun processOutputBuffer(ctx: DetectionContext, index: Int) {
        val outputBuffer = ctx.codec.getOutputBuffer(index)
        val outputFormat = ctx.codec.getOutputFormat(index)

        if (outputBuffer != null) {
            Log.d(TAG, "Algorithm processing started")
            val meanLuma = VisualAlgorithms.calculateMeanLuma(outputBuffer, outputFormat, ctx.info)
            val blurVariance = VisualAlgorithms.calculateBlurVariance(outputBuffer, outputFormat, ctx.info)
            val currentHash = VisualAlgorithms.calculatePHash(outputBuffer, outputFormat, ctx.info)
            val resultSmall = VisualAlgorithms.downscaleY(outputBuffer, outputFormat, ctx.info, DOWNSCALE_SIZE, DOWNSCALE_SIZE)
            val currentSmallY = resultSmall.data
            
            val sceneDistance = if (ctx.previousHash != null) {
                java.lang.Long.bitCount(currentHash xor ctx.previousHash!!)
            } else null
            
            val freezeDiff = if (ctx.previousSmallY != null) {
                VisualAlgorithms.calculateSAD(currentSmallY, ctx.previousSmallY!!)
            } else null
            
            ctx.analyses.add(FrameAnalysis(
                timeMs = ctx.info.presentationTimeUs / US_PER_MS,
                meanLuma = meanLuma,
                blurVariance = blurVariance,
                sceneDistance = sceneDistance,
                freezeDiff = freezeDiff
            ))

            ctx.previousHash = currentHash
            ctx.previousSmallY = currentSmallY
        }
    }

    companion object {
        private const val TAG = "VisualSegmentDetector"
        private const val TIMEOUT_US = 10000L
        private const val US_PER_MS = 1000L
        private const val DOWNSCALE_SIZE = 32
    }
}
