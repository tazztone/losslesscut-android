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
    @ApplicationContext private val context: Context,
    private val storageUtils: StorageUtils,
    private val collaborators: EngineCollaborators,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ILosslessEngine {
    
    private val dataSource get() = collaborators.dataSource
    private val inspector get() = collaborators.inspector
    private val timeMapper get() = collaborators.timeMapper
    private val mergeValidator get() = collaborators.mergeValidator

    private fun getVideoFps(format: MediaFormat): Float {
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

    companion object {
        private const val TAG = "LosslessEngine"
        private const val MAX_KEYFRAME_COUNT = 3000
        private const val MAX_PROBE_SAMPLES = 15000
        private const val DEFAULT_FPS = 30f
        private const val SNAPSHOT_QUALITY = 90
        private const val AUDIO_SAMPLE_RATE_44100 = 44100
    }

    override suspend fun getKeyframes(videoUri: String): Result<List<Long>> = withContext(ioDispatcher) {
        val keyframes = mutableListOf<Long>()
        val extractor = MediaExtractor()

        try {
            dataSource.setExtractorSource(extractor, videoUri)
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
        val retriever = MediaMetadataRetriever()
        try {
            dataSource.setRetrieverSource(retriever, uriString)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLong() ?: 0L
            if (duration <= 0) {
                Log.w(TAG, "Duration is 0 or null for $uriString")
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
                dataSource.setExtractorSource(extractor, uriString)

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
                        fps = getVideoFps(format)
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
            Log.e(TAG, "Failed to get metadata for $uriString", e)
            val msg = "Could not read media file: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Result.failure(Exception(msg, e))
        } finally {
            retriever.release()
        }
    }

    override suspend fun getFrameAt(uriString: String, positionMs: Long): ByteArray? = withContext(ioDispatcher) {
        val retriever = MediaMetadataRetriever()
        try {
            dataSource.setRetrieverSource(retriever, uriString)
            val bitmap = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            bitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_QUALITY, stream)
                stream.toByteArray()
            }
        } catch (e: CancellationException) {
            throw e
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
        val outputUri = Uri.parse(outputUriString)
        if (endMs <= startMs) return@withContext Result.failure(
            IllegalArgumentException("endMs ($endMs) must be > startMs ($startMs)")
        )

        val extractor = MediaExtractor()
        var muxerWriter: MuxerWriter? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            dataSource.setExtractorSource(extractor, inputUriString)
            
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException("Failed to open file descriptor"))

            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerWriter = MuxerWriter(muxer)
            
            val plan = inspector.inspect(extractor, muxerWriter, keepAudio, keepVideo, selectedTracks)
            
            if (plan.trackMap.isEmpty()) {
                val msg = "No tracks found in the media file"
                return@withContext Result.failure(IOException(msg))
            }

            val startUs = startMs * 1000
            val endUs = if (endMs > 0) {
                endMs * 1000
            } else if (plan.durationUs > 0) {
                plan.durationUs
            } else {
                Long.MAX_VALUE
            }

            if (plan.hasVideoTrack && rotationOverride != null) {
                muxerWriter.setOrientationHint(rotationOverride)
            }

            muxerWriter.start()
            
            val buffer = ByteBuffer.allocateDirect(plan.bufferSize)
            val copier = ExtractorSampleCopier(
                extractor,
                muxerWriter,
                timeMapper
            )
            
            copier.copy(plan, startUs, endUs, buffer)
            
            if (plan.hasVideoTrack) {
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
            muxerWriter?.stopAndRelease()
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

        var muxerWriter: MuxerWriter? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
            if (pfd == null) return@withContext Result.failure(IOException("Failed to open file descriptor"))

            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerWriter = MuxerWriter(muxer)

            var audioSampleRate = AUDIO_SAMPLE_RATE_44100
            var videoFps = DEFAULT_FPS
            var expectedVideoMime: String? = null
            var expectedAudioMime: String? = null

            // Initialize muxer tracks using the first clip
            val firstExtractor = MediaExtractor()
            val plan = try {
                dataSource.setExtractorSource(firstExtractor, clips[0].uri)
                val p = inspector.inspect(
                    firstExtractor,
                    muxerWriter,
                    keepAudio,
                    keepVideo,
                    selectedTracks
                )
                
                // Capture format info for gap calculation and validation
                for (i in 0 until firstExtractor.trackCount) {
                    val format = firstExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/")) {
                        expectedVideoMime = mime
                        videoFps = getVideoFps(format)
                    } else if (mime.startsWith("audio/")) {
                        expectedAudioMime = mime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                    }
                }
                p
            } finally {
                firstExtractor.release()
            }

            if (plan.trackMap.isEmpty()) {
                val msg = "No tracks found in the media file"
                return@withContext Result.failure(IOException(msg))
            }

            val finalRotation = rotationOverride ?: clips[0].rotation
            if (plan.hasVideoTrack) {
                muxerWriter.setOrientationHint(finalRotation)
            }

            muxerWriter.start()

            val gapUs = SegmentGapCalculator.calculateGapUs(
                audioSampleRate,
                videoFps
            )
            var maxBufferSize = plan.bufferSize
            var buffer = ByteBuffer.allocateDirect(maxBufferSize)
            var globalOffsetUs = 0L
            var actuallyHasVideo = false

            for (clip in clips) {
                val clipExtractor = MediaExtractor()
                try {
                    dataSource.setExtractorSource(clipExtractor, clip.uri)

                    // Map clip tracks to muxer tracks, validating codecs and checking buffer size
                    val clipTrackMap = mutableMapOf<Int, Int>()
                    val isVideoTrackMap = mutableMapOf<Int, Boolean>()
                    for (i in 0 until clipExtractor.trackCount) {
                        val format = clipExtractor.getTrackFormat(i)
                        
                        // Grow buffer if needed
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            if (size > maxBufferSize) {
                                maxBufferSize = size
                                buffer = ByteBuffer.allocateDirect(maxBufferSize)
                            }
                        }

                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        val isVideo = mime.startsWith("video/")
                        val isAudio = mime.startsWith("audio/")
                        
                        val isSelected = if (selectedTracks != null) {
                            selectedTracks.contains(i)
                        } else {
                            (isVideo && keepVideo) || (isAudio && keepAudio)
                        }
                        if (!isSelected) continue

                        if (isVideo) {
                            mergeValidator.validateCodec(clip.uri, mime, expectedVideoMime, "video")
                            val videoEntry = plan.trackMap.entries.find {
                                plan.isVideoTrackMap[it.key] == true
                            }
                            videoEntry?.value?.let {
                                clipTrackMap[i] = it
                                isVideoTrackMap[i] = true
                                actuallyHasVideo = true
                            }
                        } else if (isAudio) {
                            mergeValidator.validateCodec(clip.uri, mime, expectedAudioMime, "audio")
                            val audioEntry = plan.trackMap.entries.find {
                                plan.isVideoTrackMap[it.key] == false
                            }
                            audioEntry?.value?.let {
                                clipTrackMap[i] = it
                                isVideoTrackMap[i] = false
                            }
                        }
                    }

                    val copier = ExtractorSampleCopier(
                        clipExtractor,
                        muxerWriter,
                        timeMapper
                    )
                    val keepSegments = clip.segments.filter { it.action == SegmentAction.KEEP }
                    
                    for (segment in keepSegments) {
                        val segmentPlan = SelectedTrackPlan(
                            trackMap = clipTrackMap,
                            isVideoTrackMap = isVideoTrackMap,
                            bufferSize = maxBufferSize,
                            durationUs = -1L,
                            hasVideoTrack = isVideoTrackMap.values.any { it }
                        )

                        val lastSampleTimes = copier.copy(
                            plan = segmentPlan,
                            startUs = segment.startMs * 1000,
                            endUs = segment.endMs * 1000,
                            buffer = buffer,
                            globalOffsetUs = globalOffsetUs
                        )
                        
                        val lastSampleTimeInSegmentUs = lastSampleTimes.values.maxOrNull() ?: 0L
                        val effectiveSegmentDurationUs = if (lastSampleTimeInSegmentUs > 0) 
                            lastSampleTimeInSegmentUs else (segment.endMs - segment.startMs) * 1000
                        globalOffsetUs += effectiveSegmentDurationUs + gapUs
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
            muxerWriter?.stopAndRelease()
            pfd?.close()
        }
    }
}

data class EngineCollaborators @Inject constructor(
    val dataSource: MediaDataSource,
    val inspector: TrackInspector,
    val timeMapper: SampleTimeMapper,
    val mergeValidator: MergeValidator
)
