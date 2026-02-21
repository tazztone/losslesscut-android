package com.tazztone.losslesscut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import com.tazztone.losslesscut.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

interface LosslessEngineInterface {
    suspend fun probeKeyframes(context: Context, videoUri: Uri): List<Long>
    suspend fun executeLosslessCut(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null
    ): Result<Uri>

    suspend fun executeLosslessMerge(
        context: Context,
        outputUri: Uri,
        clips: List<MediaClip>,
        keepAudio: Boolean = true,
        keepVideo: Boolean = true,
        rotationOverride: Int? = null
    ): Result<Uri>
}

@OptIn(UnstableApi::class)
@Singleton
class LosslessEngineImpl @Inject constructor(
    private val storageUtils: StorageUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LosslessEngineInterface {
    
    companion object {
        private const val TAG = "LosslessEngine"
    }

    override suspend fun probeKeyframes(context: Context, videoUri: Uri): List<Long> = withContext(ioDispatcher) {
        val keyframes = mutableListOf<Long>()
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
                        keyframes.add(sampleTime / 1000)
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

    override suspend fun executeLosslessCut(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?
    ): Result<Uri> = withContext(ioDispatcher) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            extractor.setDataSource(context, inputUri, null)
            
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException(context.getString(R.string.error_failed_open_pfd)))

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
            
            if (trackMap.isEmpty()) return@withContext Result.failure(IOException(context.getString(R.string.error_no_tracks_found)))
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
            var hasVideoTrack = false

            for ((_, isVideo) in isVideoTrackMap) {
                if (isVideo) {
                    hasVideoTrack = true
                    break
                }
            }

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
                
                var flags = 0
                if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                bufferInfo.flags = flags

                if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
                
                // EOS Tracking
                if (isVideoTrackMap[currentTrack] == true) {
                    lastVideoSampleTimeUs = maxOf(lastVideoSampleTimeUs, bufferInfo.presentationTimeUs)
                } else {
                    lastAudioSampleTimeUs = maxOf(lastAudioSampleTimeUs, bufferInfo.presentationTimeUs)
                }

                mMuxer.writeSampleData(muxerTrack, buffer, bufferInfo)

                if (!extractor.advance()) break
            }
            
            Log.d(TAG, "Extraction finished. Last Video Us: $lastVideoSampleTimeUs, Last Audio Us: $lastAudioSampleTimeUs")
            
            if (hasVideoTrack) {
                storageUtils.finalizeVideo(outputUri)
            } else {
                storageUtils.finalizeAudio(outputUri)
            }
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

    override suspend fun executeLosslessMerge(
        context: Context,
        outputUri: Uri,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?
    ): Result<Uri> = withContext(ioDispatcher) {
        if (clips.isEmpty()) return@withContext Result.failure(IOException("No clips to merge"))

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            // Initialize muxer using the first clip
            extractor.setDataSource(context, clips[0].uri, null)

            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException(context.getString(R.string.error_failed_open_pfd)))

            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mMuxer = muxer

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
                    trackMap[i] = mMuxer.addTrack(format)
                    isVideoTrackMap[i] = isVideo

                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (size > bufferSize) bufferSize = size
                    }
                }
            }

            if (trackMap.isEmpty()) return@withContext Result.failure(IOException(context.getString(R.string.error_no_tracks_found)))
            if (bufferSize < 0) bufferSize = 1024 * 1024

            val finalRotation = rotationOverride ?: clips[0].rotation
            mMuxer.setOrientationHint(finalRotation)

            mMuxer.start()
            isMuxerStarted = true

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var globalOffsetUs = 0L

            for (clip in clips) {
                // For subsequent clips, we need to reset the extractor
                if (clip != clips[0]) {
                    extractor.setDataSource(context, clip.uri, null)
                }
                
                // Note: We assume track indices in subsequent clips map similarly to the first clip 
                // because of our validation in ViewModel. But to be safe, we should re-map tracks.
                val clipTrackMap = mutableMapOf<Int, Int>() // Clip Track Index -> Muxer Track Index
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")
                    
                    if (isVideo && keepVideo) {
                        // Find the muxer track that matches video
                        trackMap.forEach { (firstIdx, muxIdx) ->
                             if (isVideoTrackMap[firstIdx] == true) clipTrackMap[i] = muxIdx
                        }
                    } else if (isAudio && keepAudio) {
                        // Find the muxer track that matches audio
                        trackMap.forEach { (firstIdx, muxIdx) ->
                             if (isVideoTrackMap[firstIdx] == false) clipTrackMap[i] = muxIdx
                        }
                    }
                }

                for ((clipTrack, _) in clipTrackMap) {
                    extractor.selectTrack(clipTrack)
                }

                val keepSegments = clip.segments.filter { it.action == SegmentAction.KEEP }
                for (segment in keepSegments) {
                    val startUs = segment.startMs * 1000
                    val endUs = segment.endMs * 1000

                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    var segmentStartUs = -1L
                    var lastSampleTimeInSegmentUs = 0L

                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val sampleTime = extractor.sampleTime
                        if (sampleTime > endUs) break

                        val currentTrack = extractor.sampleTrackIndex
                        val muxerTrack = clipTrackMap[currentTrack]
                        if (muxerTrack == null) {
                            extractor.advance()
                            continue
                        }

                        if (segmentStartUs == -1L) {
                            segmentStartUs = sampleTime
                        }

                        val relativeTime = sampleTime - segmentStartUs
                        bufferInfo.presentationTimeUs = globalOffsetUs + relativeTime
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize

                        var flags = 0
                        if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        bufferInfo.flags = flags

                        if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
                        
                        lastSampleTimeInSegmentUs = maxOf(lastSampleTimeInSegmentUs, relativeTime)

                        mMuxer.writeSampleData(muxerTrack, buffer, bufferInfo)

                        if (!extractor.advance()) break
                    }
                    globalOffsetUs += lastSampleTimeInSegmentUs + 1000
                }
                
                // Deselect tracks before moving to next clip
                for (i in 0 until extractor.trackCount) {
                    extractor.unselectTrack(i)
                }
            }

            var hasVideoTrack = false
            for (i in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    hasVideoTrack = true
                    break
                }
            }

            if (hasVideoTrack) {
                storageUtils.finalizeVideo(outputUri)
            } else {
                storageUtils.finalizeAudio(outputUri)
            }
            Result.success(outputUri)

        } catch (e: Exception) {
            Log.e(TAG, "Error executing lossless merge", e)
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
