package com.tazztone.losslesscut.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.engine.ILosslessEngine
import com.tazztone.losslesscut.domain.engine.MediaMetadata
import com.tazztone.losslesscut.domain.engine.TrackMetadata
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.utils.StorageUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LosslessEngineImpl @Inject constructor(
    private val storageUtils: StorageUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ILosslessEngine {
    
    companion object {
        private const val TAG = "LosslessEngine"
    }


    override suspend fun getKeyframes(context: Context, videoUri: Uri): Result<List<Long>> = withContext(ioDispatcher) {
        val keyframes = mutableListOf<Long>()
        val extractor = MediaExtractor()

        try {
            try {
                extractor.setDataSource(context, videoUri, null)
            } catch (e: Exception) {
                Log.e(TAG, "MediaExtractor (keyframes) failed for $videoUri, trying FileDescriptor", e)
                context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: throw IOException("Could not open FileDescriptor for $videoUri")
            }
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
                var totalSamples = 0
                while (extractor.sampleTime >= 0 && count < 3000 && totalSamples < 15000 && currentCoroutineContext().isActive) {
                    val sampleTime = extractor.sampleTime
                    if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        keyframes.add(sampleTime / 1000)
                        count++
                    }
                    totalSamples++
                    if (!extractor.advance()) break
                }
            }
            Result.success(keyframes)
        } catch (e: Exception) {
            Log.e(TAG, "Error probing keyframes", e)
            Result.failure(e)
        } finally {
            extractor.release()
        }
    }

    override suspend fun getMediaMetadata(context: Context, uri: Uri): Result<MediaMetadata> = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            try {
                retriever.setDataSource(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "MediaMetadataRetriever failed for $uri, trying FileDescriptor", e)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                } ?: throw IOException("Could not open FileDescriptor for $uri")
            }

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLong() ?: 0L
            if (duration <= 0) {
                Log.w(TAG, "Duration is 0 or null for $uri")
            }

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            // Get MIME types
            var videoMime: String? = null
            var audioMime: String? = null
            var sampleRate = 0
            var channelCount = 0
            var fps = 30f

            val extractor = MediaExtractor()
            val tracks = mutableListOf<TrackMetadata>()
            try {
                try {
                    extractor.setDataSource(context, uri, null)
                } catch (e: Exception) {
                    Log.e(TAG, "MediaExtractor failed for $uri, trying FileDescriptor", e)
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    } ?: throw IOException("Could not open FileDescriptor for $uri")
                }

                if (extractor.trackCount == 0) {
                    return@withContext Result.failure(IOException("No tracks found in the media file"))
                }

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")
                    
                    if (isVideo) {
                        videoMime = mime
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            fps = try {
                                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                            } catch (_: Exception) {
                                try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 30f }
                            }
                        }
                    } else if (isAudio) {
                        audioMime = mime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    
                    tracks.add(
                        TrackMetadata(
                            id = i,
                            mimeType = mime,
                            language = format.getString(MediaFormat.KEY_LANGUAGE),
                            title = if (format.containsKey("title")) format.getString("title") else null,
                            isVideo = isVideo,
                            isAudio = isAudio
                        )
                    )
                }
            } finally {
                extractor.release()
            }

            Result.success(
                MediaMetadata(
                    durationMs = duration,
                    width = width,
                    height = height,
                    videoMime = videoMime,
                    audioMime = audioMime,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    fps = fps,
                    rotation = rotation,
                    tracks = tracks
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metadata for $uri", e)
            Result.failure(Exception("Could not read media file: ${e.localizedMessage ?: e.javaClass.simpleName}", e))
        } finally {
            retriever.release()
        }
    }

    override suspend fun getFrameAt(context: Context, uri: Uri, positionMs: Long): android.graphics.Bitmap? = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    override suspend fun executeLosslessCut(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<Uri> = withContext(ioDispatcher) {
        if (endMs <= startMs) return@withContext Result.failure(
            IllegalArgumentException("endMs ($endMs) must be > startMs ($startMs)")
        )

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            try {
                extractor.setDataSource(context, inputUri, null)
            } catch (e: Exception) {
                Log.e(TAG, "MediaExtractor (cut) failed for $inputUri, trying FileDescriptor", e)
                context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd_in ->
                    extractor.setDataSource(pfd_in.fileDescriptor)
                } ?: throw IOException("Could not open FileDescriptor for $inputUri")
            }
            
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException("Failed to open file descriptor"))

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
                
                val isSelected = selectedTracks?.contains(i) ?: true
                if (!isSelected) continue
                
                if (isVideo && !keepVideo && selectedTracks == null) continue
                if (isAudio && !keepAudio && selectedTracks == null) continue
                
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
            
            if (trackMap.isEmpty()) return@withContext Result.failure(IOException("No tracks found in the media file"))
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
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<Uri> = withContext(ioDispatcher) {
        if (clips.isEmpty()) return@withContext Result.failure(IOException("No clips to merge"))

        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException("Failed to open file descriptor"))

            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mMuxer = muxer

            val muxerTrackByType = mutableMapOf<Int, Int>() // Muxer Track Index -> Type (0 for Video, 1 for Audio)
            var bufferSize = -1
            var audioSampleRate = 44100
            var videoFps = 30f
            var expectedVideoMime: String? = null
            var expectedAudioMime: String? = null

            // Initialize muxer tracks using the first clip
            val firstExtractor = MediaExtractor()
            try {
                try {
                    firstExtractor.setDataSource(context, clips[0].uri, null)
                } catch (e: Exception) {
                    Log.e(TAG, "MediaExtractor (merge init) failed for ${clips[0].uri}, trying FileDescriptor", e)
                    context.contentResolver.openFileDescriptor(clips[0].uri, "r")?.use { pfd_in ->
                        firstExtractor.setDataSource(pfd_in.fileDescriptor)
                    } ?: throw IOException("Could not open FileDescriptor for ${clips[0].uri}")
                }
                for (i in 0 until firstExtractor.trackCount) {
                    val format = firstExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")

                    val isSelected = selectedTracks?.contains(i) ?: true
                    if (!isSelected) continue

                    if (isVideo && !keepVideo && selectedTracks == null) continue
                    if (isAudio && !keepAudio && selectedTracks == null) continue

                    if (isVideo || isAudio) {
                        val muxIdx = mMuxer.addTrack(format)
                        muxerTrackByType[muxIdx] = if (isVideo) 0 else 1
                        
                        if (isVideo) {
                            expectedVideoMime = mime
                            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                videoFps = try {
                                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                                } catch (_: Exception) {
                                    try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 30f }
                                }
                            }
                        } else {
                            expectedAudioMime = mime
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
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

            if (muxerTrackByType.isEmpty()) return@withContext Result.failure(IOException("No tracks found in the media file"))
            if (bufferSize < 0) bufferSize = 1024 * 1024

            val finalRotation = rotationOverride ?: clips[0].rotation
            if (muxerTrackByType.any { it.value == 0 }) {
                mMuxer.setOrientationHint(finalRotation)
            }

            mMuxer.start()
            isMuxerStarted = true

            // Pre-calculate gap constants outside the loop
            val audioFrameDurationUs = (1024.0 * 1_000_000.0 / audioSampleRate).toLong()
            val videoFrameDurationUs = if (videoFps > 0) (1_000_000.0 / videoFps).toLong() else 33333L
            val segmentGapUs = maxOf(audioFrameDurationUs, videoFrameDurationUs)

            // Pre-calculate muxer tracks outside the clip loop
            val videoMuxerTrack = muxerTrackByType.entries.firstOrNull { it.value == 0 }?.key
            val audioMuxerTrack = muxerTrackByType.entries.firstOrNull { it.value == 1 }?.key

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var globalOffsetUs = 0L
            var actuallyHasVideo = false

            for (clip in clips) {
                val clipExtractor = MediaExtractor()
                try {
                    try {
                        clipExtractor.setDataSource(context, clip.uri, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaExtractor (merge step) failed for ${clip.uri}, trying FileDescriptor", e)
                        context.contentResolver.openFileDescriptor(clip.uri, "r")?.use { pfd_in ->
                            clipExtractor.setDataSource(pfd_in.fileDescriptor)
                        } ?: throw IOException("Could not open FileDescriptor for ${clip.uri}")
                    }

                    val clipTrackMap = mutableMapOf<Int, Int>() // Clip Track Index -> Muxer Track Index
                    for (i in 0 until clipExtractor.trackCount) {
                        val format = clipExtractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                        val isVideo = mime.startsWith("video/")
                        val isAudio = mime.startsWith("audio/")
                        
                        // respect selectedTracks if provided
                        val isSelected = selectedTracks?.contains(i) ?: true
                        if (!isSelected) continue

                        if (isVideo && keepVideo) {
                            if (mime != expectedVideoMime) {
                                return@withContext Result.failure(IOException("Codec mismatch: expected $expectedVideoMime, got $mime in ${clip.uri}"))
                            }
                            videoMuxerTrack?.let {
                                clipTrackMap[i] = it
                                actuallyHasVideo = true
                            }
                        } else if (isAudio && keepAudio) {
                            if (mime != expectedAudioMime) {
                                return@withContext Result.failure(IOException("Codec mismatch: expected $expectedAudioMime, got $mime in ${clip.uri}"))
                            }
                            audioMuxerTrack?.let { clipTrackMap[i] = it }
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
                        
                        val effectiveSegmentDurationUs = if (lastSampleTimeInSegmentUs > 0) 
                            lastSampleTimeInSegmentUs else (segment.endMs - segment.startMs) * 1000
                        globalOffsetUs += effectiveSegmentDurationUs + segmentGapUs
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
