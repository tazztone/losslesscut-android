package com.tazztone.losslesscut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

object LosslessEngine {

    const val TAG = "LosslessEngine"

    suspend fun probeKeyframes(context: Context, videoUri: Uri): List<Double> = withContext(Dispatchers.IO) {
        val keyframes = mutableListOf<Double>()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(context, videoUri, null)
            val trackCount = extractor.trackCount
            var videoTrackIndex = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    break
                }
            }

            if (videoTrackIndex >= 0) {
                extractor.selectTrack(videoTrackIndex)
                var timeUs = 0L
                while (timeUs >= 0) {
                    extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
                    val nextKeyframe = extractor.sampleTime
                    if (nextKeyframe < 0 || (keyframes.isNotEmpty() && nextKeyframe <= timeUs)) break
                    
                    keyframes.add(nextKeyframe / 1_000_000.0)
                    timeUs = nextKeyframe + 1 // Advance past this keyframe
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error probing keyframes", e)
        } finally {
            extractor.release()
        }

        keyframes
    }

    suspend fun executeLosslessCut(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var success = false

        try {
            extractor.setDataSource(context, inputUri, null)
            
            val mMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mMuxer
            
            // Validate start/end times & get duration from track format
            var durationUs = -1L
            val trackMap = mutableMapOf<Int, Int>()
            var bufferSize = -1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    
                    trackMap[i] = mMuxer.addTrack(format)
                    
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                         val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                         if (size > bufferSize) bufferSize = size
                    }
                }
            }
            
            if (trackMap.isEmpty()) return@withContext false
            if (bufferSize < 0) bufferSize = 1024 * 1024 // Default 1MB buffer

            val startUs = startMs * 1000
            val endUs = if (endMs > 0) endMs * 1000 else if (durationUs > 0) durationUs else Long.MAX_VALUE

            mMuxer.start()
            
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Select ALL tracks first
            for ((extractorTrack, _) in trackMap) {
                extractor.selectTrack(extractorTrack)
            }

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            // Single loop — let MediaExtractor decide which track is next
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break

                val currentTrack = extractor.sampleTrackIndex
                val muxerTrack = trackMap[currentTrack]
                if (muxerTrack == null) {
                    extractor.advance()
                    continue
                }

                if (sampleTime >= startUs) {
                    bufferInfo.presentationTimeUs = sampleTime - startUs
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.flags = extractor.sampleFlags
                    if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
                    mMuxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                }

                if (!extractor.advance()) break
            }
            
            success = true
            
            // Scan file so it appears in gallery
            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)

        } catch (e: Exception) {
            Log.e(TAG, "Error executing lossless cut", e)
             // Clean up partial file on failure
            if (outputFile.exists()) outputFile.delete()
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                 Log.e(TAG, "Error releasing muxer", e)
            }
            extractor.release()
        }

        success
    }
}
