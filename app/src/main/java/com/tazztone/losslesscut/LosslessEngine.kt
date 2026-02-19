package com.tazztone.losslesscut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
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
                while (true) {
                    val flags = extractor.sampleFlags
                    // SAMPLE_FLAG_SYNC indicates a keyframe (I-frame)
                    if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        val timeUs = extractor.sampleTime
                        keyframes.add(timeUs / 1_000_000.0) // Convert microseconds to seconds
                    }
                    if (!extractor.advance()) break
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
            
            // Validate start/end times
            val durationUs = try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, inputUri)
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                dur * 1000
            } catch (e: Exception) {
                -1L
            }
            
            val startUs = startMs * 1000
            val endUs = if (endMs > 0) endMs * 1000 else if (durationUs > 0) durationUs else Long.MAX_VALUE

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableMapOf<Int, Int>()
            var bufferSize = -1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                // Select tracks to keep (Video and Audio)
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                    
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                         val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                         if (size > bufferSize) bufferSize = size
                    }
                }
            }
            
            if (trackMap.isEmpty()) return@withContext false
            if (bufferSize < 0) bufferSize = 1024 * 1024 // Default 1MB buffer

            muxer.start()
            
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            for ((extractorTrack, muxerTrack) in trackMap) {
                extractor.selectTrack(extractorTrack)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    
                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endUs) break
                    
                    if (sampleTime >= startUs) { // Should include packets slightly before start for B-frames if needed, but SEEK_TO_PREVIOUS_SYNC usually lands on IDR
                        bufferInfo.presentationTimeUs = sampleTime - startUs
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.flags = extractor.sampleFlags
                        
                        // Fix for negative muxer capability
                        if (bufferInfo.presentationTimeUs < 0) {
                            bufferInfo.presentationTimeUs = 0
                        }

                        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    }
                    
                    if (!extractor.advance()) break
                }
                extractor.unselectTrack(extractorTrack)
            }
            
            success = true

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
