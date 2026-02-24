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
import com.tazztone.losslesscut.domain.model.SegmentAction
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
    @ApplicationContext private val context: Context,
    private val storageUtils: StorageUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ILosslessEngine {
    
    enum class TrackType { VIDEO, AUDIO }

    companion object {
        private const val TAG = "LosslessEngine"
        private const val MAX_KEYFRAME_COUNT = 3000
        private const val MAX_PROBE_SAMPLES = 15000
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB
        private const val DEFAULT_FPS = 30f
        private const val AUDIO_SAMPLE_RATE_44100 = 44100
        private const val AUDIO_FRAME_SIZE = 1024.0
        private const val MICROSECONDS_IN_SECOND = 1_000_000.0
        private const val VIDEO_FRAME_DURATION_DEFAULT_US = 33333L
        private const val SNAPSHOT_QUALITY = 90
    }

    private fun setDataSourceSafely(extractor: MediaExtractor, context: Context, uri: Uri) {
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            Log.e(TAG, "MediaExtractor failed for $uri, trying FileDescriptor", e)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw IOException("Could not open FileDescriptor for $uri")
        }
    }

    private fun setDataSourceSafely(retriever: MediaMetadataRetriever, context: Context, uri: Uri) {
        try {
            retriever.setDataSource(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever failed for $uri, trying FileDescriptor", e)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: throw IOException("Could not open FileDescriptor for $uri")
        }
    }

    override suspend fun getKeyframes(videoUri: String): Result<List<Long>> = withContext(ioDispatcher) {
        val uri = Uri.parse(videoUri)
        val keyframes = mutableListOf<Long>()
        val extractor = MediaExtractor()

        try {
            setDataSourceSafely(extractor, context, uri)
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
                while (extractor.sampleTime >= 0 && count < MAX_KEYFRAME_COUNT && totalSamples < MAX_PROBE_SAMPLES && currentCoroutineContext().isActive) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error probing keyframes", e)
            Result.failure(e)
        } finally {
            extractor.release()
        }
    }

    override suspend fun getMediaMetadata(uriString: String): Result<MediaMetadata> = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        try {
            setDataSourceSafely(retriever, context, uri)

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
            var fps = DEFAULT_FPS

            val extractor = MediaExtractor()
            val tracks = mutableListOf<TrackMetadata>()
            try {
                setDataSourceSafely(extractor, context, uri)

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
                                try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { DEFAULT_FPS }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metadata for $uri", e)
            Result.failure(Exception("Could not read media file: ${e.localizedMessage ?: e.javaClass.simpleName}", e))
        } finally {
            retriever.release()
        }
    }

    override suspend fun getFrameAt(uriString: String, positionMs: Long): ByteArray? = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        try {
            setDataSourceSafely(retriever, context, uri)
            val bitmap = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            bitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_QUALITY, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    override suspend fun executeLosslessCut(
        inputUriString: String,
        outputUriString: String,
        startMs: Long,
        endMs: Long,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String> = withContext(ioDispatcher) {
        val inputUri = Uri.parse(inputUriString)
        val outputUri = Uri.parse(outputUriString)
        if (endMs <= startMs) return@withContext Result.failure(
            IllegalArgumentException("endMs ($endMs) must be > startMs ($startMs)")
        )

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: ParcelFileDescriptor? = null

        try {
            setDataSourceSafely(extractor, context, inputUri)
            
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
                
                val isSelected = if (selectedTracks != null) {
                    selectedTracks.contains(i)
                } else {
                    (isVideo && keepVideo) || (isAudio && keepAudio)
                }
                if (!isSelected) continue
                
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
            if (bufferSize < 0) bufferSize = DEFAULT_BUFFER_SIZE
            val hasVideoTrack = isVideoTrackMap.values.any { it }

            val startUs = startMs * 1000
            val endUs = if (endMs > 0) endMs * 1000 else if (durationUs > 0) durationUs else Long.MAX_VALUE

            if (hasVideoTrack && rotationOverride != null) {
                mMuxer.setOrientationHint(rotationOverride)
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
            Result.success(outputUriString)

        } catch (e: CancellationException) {
            throw e
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
        outputUriString: String,
        clips: List<MediaClip>,
        keepAudio: Boolean,
        keepVideo: Boolean,
        rotationOverride: Int?,
        selectedTracks: List<Int>?
    ): Result<String> = withContext(ioDispatcher) {
        val outputUri = Uri.parse(outputUriString)
        if (clips.isEmpty()) return@withContext Result.failure(IOException("No clips to merge"))

        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException("Failed to open file descriptor"))

            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mMuxer = muxer

            val muxerTrackByType = mutableMapOf<Int, TrackType>() // Muxer Track Index -> Type (VIDEO/AUDIO)
            var bufferSize = -1
            var audioSampleRate = AUDIO_SAMPLE_RATE_44100
            var videoFps = DEFAULT_FPS
            var expectedVideoMime: String? = null
            var expectedAudioMime: String? = null

            // Initialize muxer tracks using the first clip
            val firstExtractor = MediaExtractor()
            try {
                setDataSourceSafely(firstExtractor, context, Uri.parse(clips[0].uri))
                for (i in 0 until firstExtractor.trackCount) {
                    val format = firstExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")

                    val isSelected = if (selectedTracks != null) {
                        selectedTracks.contains(i)
                    } else {
                        (isVideo && keepVideo) || (isAudio && keepAudio)
                    }
                    if (!isSelected) continue

                    if (isVideo || isAudio) {
                        val muxIdx = mMuxer.addTrack(format)
                        muxerTrackByType[muxIdx] = if (isVideo) TrackType.VIDEO else TrackType.AUDIO
                        
                        if (isVideo) {
                            expectedVideoMime = mime
                            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                videoFps = try {
                                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                                } catch (_: Exception) {
                                    try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { DEFAULT_FPS }
                                }
                            }
                        } else {
                            expectedAudioMime = mime
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                        }
                    }
                }
            } finally {
                firstExtractor.release()
            }

            // Find the maximum buffer size across all clips
            for (clip in clips) {
                val clipExtractor = MediaExtractor()
                try {
                    setDataSourceSafely(clipExtractor, context, Uri.parse(clip.uri))
                    for (i in 0 until clipExtractor.trackCount) {
                        val format = clipExtractor.getTrackFormat(i)
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            if (size > bufferSize) bufferSize = size
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not probe buffer size for ${clip.uri}", e)
                } finally {
                    clipExtractor.release()
                }
            }

            if (muxerTrackByType.isEmpty()) return@withContext Result.failure(IOException("No tracks found in the media file"))
            if (bufferSize < 0) bufferSize = DEFAULT_BUFFER_SIZE

            val finalRotation = rotationOverride ?: clips[0].rotation
            if (muxerTrackByType.any { it.value == TrackType.VIDEO }) {
                mMuxer.setOrientationHint(finalRotation)
            }

            mMuxer.start()
            isMuxerStarted = true

            // Pre-calculate gap constants outside the loop
            val audioFrameDurationUs = (AUDIO_FRAME_SIZE * MICROSECONDS_IN_SECOND / audioSampleRate).toLong()
            val videoFrameDurationUs = if (videoFps > 0) (MICROSECONDS_IN_SECOND / videoFps).toLong() else VIDEO_FRAME_DURATION_DEFAULT_US
            val segmentGapUs = maxOf(audioFrameDurationUs, videoFrameDurationUs)

            // Pre-calculate muxer tracks outside the clip loop
            val videoMuxerTrack = muxerTrackByType.entries.firstOrNull { it.value == TrackType.VIDEO }?.key
            val audioMuxerTrack = muxerTrackByType.entries.firstOrNull { it.value == TrackType.AUDIO }?.key

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var globalOffsetUs = 0L
            var actuallyHasVideo = false

            for (clip in clips) {
                val clipExtractor = MediaExtractor()
                try {
                    setDataSourceSafely(clipExtractor, context, Uri.parse(clip.uri))

                    val clipTrackMap = mutableMapOf<Int, Int>() // Clip Track Index -> Muxer Track Index
                    for (i in 0 until clipExtractor.trackCount) {
                        val format = clipExtractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                        val isVideo = mime.startsWith("video/")
                        val isAudio = mime.startsWith("audio/")
                        
                        // respect selectedTracks if provided
                        val isSelected = if (selectedTracks != null) {
                            selectedTracks.contains(i)
                        } else {
                            (isVideo && keepVideo) || (isAudio && keepAudio)
                        }
                        if (!isSelected) continue

                        if (isVideo) {
                            if (mime != expectedVideoMime) {
                                return@withContext Result.failure(IOException("Codec mismatch: expected $expectedVideoMime, got $mime in ${clip.uri}"))
                            }
                            videoMuxerTrack?.let {
                                clipTrackMap[i] = it
                                actuallyHasVideo = true
                            }
                        } else if (isAudio) {
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
            Result.success(outputUriString)

        } catch (e: CancellationException) {
            throw e
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
