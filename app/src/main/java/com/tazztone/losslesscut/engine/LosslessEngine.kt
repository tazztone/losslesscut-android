package com.tazztone.losslesscut.engine

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
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.di.IoDispatcher
import com.tazztone.losslesscut.viewmodel.MediaClip
import com.tazztone.losslesscut.viewmodel.SegmentAction
import com.tazztone.losslesscut.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
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
                var count = 0
                while (extractor.sampleTime >= 0 && count < 3000) {
                    val sampleTime = extractor.sampleTime
                    if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        keyframes.add(sampleTime / 1000)
                        count++
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
            val hasVideoTrack = isVideoTrackMap.values.any { it }

            val startUs = startMs * 1000
            val endUs = if (endMs > 0) endMs * 1000 else if (durationUs > 0) durationUs else Long.MAX_VALUE

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, inputUri)
                val originalRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val finalRotation = rotationOverride ?: originalRotation
                if (hasVideoTrack) {
                    mMuxer.setOrientationHint(finalRotation)
                }
            } finally {
                retriever.release()
            }

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
            while (currentCoroutineContext().isActive) {
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

        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException(context.getString(R.string.error_failed_open_pfd)))

            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mMuxer = muxer

            val trackMap = mutableMapOf<Int, Int>() // Muxer Track Index -> Type (0 for Video, 1 for Audio)
            var bufferSize = -1
            var audioSampleRate = 44100
            var videoFps = 30f

            // Initialize muxer tracks using the first clip
            val firstExtractor = MediaExtractor()
            try {
                firstExtractor.setDataSource(context, clips[0].uri, null)
                for (i in 0 until firstExtractor.trackCount) {
                    val format = firstExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")

                    if (isVideo && !keepVideo) continue
                    if (isAudio && !keepAudio) continue

                    if (isVideo || isAudio) {
                        val muxIdx = mMuxer.addTrack(format)
                        trackMap[muxIdx] = if (isVideo) 0 else 1
                        
                        if (isVideo && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            videoFps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                        if (isAudio && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }

                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            if (size > bufferSize) bufferSize = size
                        }
                    }
                }
            } finally {
                firstExtractor.release()
            }

            if (trackMap.isEmpty()) return@withContext Result.failure(IOException(context.getString(R.string.error_no_tracks_found)))
            if (bufferSize < 0) bufferSize = 1024 * 1024

            val finalRotation = rotationOverride ?: clips[0].rotation
            if (trackMap.any { it.value == 0 }) {
                mMuxer.setOrientationHint(finalRotation)
            }

                        mMuxer.start()
                        isMuxerStarted = true
            
                        val buffer = ByteBuffer.allocateDirect(bufferSize)
                        val bufferInfo = MediaCodec.BufferInfo()
                        var globalOffsetUs = 0L
                        var actuallyHasVideo = false
            
                        for (clip in clips) {
                            val clipExtractor = MediaExtractor()
                            try {
                                clipExtractor.setDataSource(context, clip.uri, null)
                                
                                val clipTrackMap = mutableMapOf<Int, Int>() // Clip Track Index -> Muxer Track Index
                                for (i in 0 until clipExtractor.trackCount) {
                                    val format = clipExtractor.getTrackFormat(i)
                                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                                    
                                    val isVideo = mime.startsWith("video/")
                                    val isAudio = mime.startsWith("audio/")
                                    
                                    if (isVideo && keepVideo) {
                                        trackMap.filter { it.value == 0 }.keys.firstOrNull()?.let { 
                                            clipTrackMap[i] = it 
                                            actuallyHasVideo = true
                                        }
                                    } else if (isAudio && keepAudio) {
                                        trackMap.filter { it.value == 1 }.keys.firstOrNull()?.let { clipTrackMap[i] = it }
                                    }
                                }
            

                    for (clipTrack in clipTrackMap.keys) {
                        clipExtractor.selectTrack(clipTrack)
                    }

                    val keepSegments = clip.segments.filter { it.action == SegmentAction.KEEP }
                    for (segment in keepSegments) {
                        val startUs = segment.startMs * 1000
                        val endUs = segment.endMs * 1000

                        clipExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                        var segmentStartUs = -1L
                        var lastSampleTimeInSegmentUs = 0L

                        while (currentCoroutineContext().isActive) {
                            val sampleSize = clipExtractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) break

                            val sampleTime = clipExtractor.sampleTime
                            if (sampleTime > endUs) break

                            val currentTrack = clipExtractor.sampleTrackIndex
                            val muxerTrack = clipTrackMap[currentTrack]
                            if (muxerTrack == null) {
                                clipExtractor.advance()
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
                            if ((clipExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                                flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                            }
                            bufferInfo.flags = flags

                            if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
                            
                            lastSampleTimeInSegmentUs = maxOf(lastSampleTimeInSegmentUs, relativeTime)

                            mMuxer.writeSampleData(muxerTrack, buffer, bufferInfo)

                            if (!clipExtractor.advance()) break
                        }
                        val audioFrameDurationUs = (1024.0 * 1_000_000.0 / audioSampleRate).toLong()
                        val videoFrameDurationUs = if (videoFps > 0) (1_000_000.0 / videoFps).toLong() else 33333L
                        val segmentGapUs = maxOf(audioFrameDurationUs, videoFrameDurationUs)
                        
                        globalOffsetUs += lastSampleTimeInSegmentUs + segmentGapUs
                    }
                } finally {
                    clipExtractor.release()
                }
            }

            if (actuallyHasVideo) {
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
        }
    }
}
