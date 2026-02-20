package com.tazztone.losslesscut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
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
                while (extractor.sampleTime >= 0) {
                    val sampleTime = extractor.sampleTime
                    if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        keyframes.add(sampleTime / 1_000_000.0)
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

    @android.annotation.SuppressLint("WrongConstant")
    suspend fun executeLosslessCut(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            extractor.setDataSource(context, inputUri, null)
            
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException("Failed to open PFD for $outputUri"))

            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mMuxer = muxer
            
            // Validate start/end times & get duration from track format
            var durationUs = -1L
            val trackMap = mutableMapOf<Int, Int>()
            val isVideoTrackMap = mutableMapOf<Int, Boolean>()
            var bufferSize = -1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/")
                
                if (isVideo && !keepVideo) continue
                if (isAudio && !keepAudio) continue
                
                if (isVideo || isAudio) {
                    if (isVideo && format.containsKey(MediaFormat.KEY_DURATION)) {
                        try {
                            durationUs = format.getLong(MediaFormat.KEY_DURATION)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not get duration from video track format")
                        }
                    }
                    
                    trackMap[i] = mMuxer.addTrack(format)
                    isVideoTrackMap[i] = isVideo
                    
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                         val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                         if (size > bufferSize) bufferSize = size
                    }
                }
            }
            
            if (trackMap.isEmpty()) return@withContext Result.failure(IOException("No video/audio tracks found"))
            if (bufferSize < 0) bufferSize = 1024 * 1024 // Default 1MB buffer

            val startUs = startMs * 1000
            val endUs = if (endMs > 0) endMs * 1000 else if (durationUs > 0) durationUs else Long.MAX_VALUE

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, inputUri)
            val originalRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val finalRotation = rotationOverride ?: originalRotation
            mMuxer.setOrientationHint(finalRotation)
            retriever.release()

            mMuxer.start()
            isMuxerStarted = true
            
            // Use allocateDirect to reduce memory copy overhead between Java heap and Native layer
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Select ALL tracks first
            for ((extractorTrack, _) in trackMap) {
                extractor.selectTrack(extractorTrack)
            }

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var effectiveStartUs = -1L
            var lastVideoSampleTimeUs = -1L
            var lastAudioSampleTimeUs = -1L

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

                if (effectiveStartUs == -1L) {
                    effectiveStartUs = sampleTime
                }

                bufferInfo.presentationTimeUs = sampleTime - effectiveStartUs
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.flags = extractor.sampleFlags
                if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
                
                // EOS Tracking
                if (isVideoTrackMap[currentTrack] == true) {
                    lastVideoSampleTimeUs = Math.max(lastVideoSampleTimeUs, bufferInfo.presentationTimeUs)
                } else {
                    lastAudioSampleTimeUs = Math.max(lastAudioSampleTimeUs, bufferInfo.presentationTimeUs)
                }

                mMuxer.writeSampleData(muxerTrack, buffer, bufferInfo)

                if (!extractor.advance()) break
            }
            
            Log.d(TAG, "Extraction finished. Last Video Us: $lastVideoSampleTimeUs, Last Audio Us: $lastAudioSampleTimeUs")
            
            StorageUtils.finalizeVideo(context, outputUri)
            Result.success(outputUri)

        } catch (e: Exception) {
            Log.e(TAG, "Error executing lossless cut", e)
            Result.failure(e)
        } finally {
            try {
                if (isMuxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (e: Exception) {
                 Log.e(TAG, "Error releasing muxer", e)
            }
            pfd?.close()
            extractor.release()
        }
    }
}
