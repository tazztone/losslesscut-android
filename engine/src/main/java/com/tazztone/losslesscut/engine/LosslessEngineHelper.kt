package com.tazztone.losslesscut.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.tazztone.losslesscut.domain.engine.TrackMetadata
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.engine.muxing.ExtractorSampleCopier
import com.tazztone.losslesscut.engine.muxing.MuxerWriter
import com.tazztone.losslesscut.engine.muxing.SelectedTrackPlan
import java.nio.ByteBuffer

internal object LosslessEngineHelper {
    private const val TAG = "LosslessEngine"
    private const val MS_TO_US = 1000L
    private const val DEFAULT_FPS = 30f

    fun readBasicMetadata(retriever: MediaMetadataRetriever, uri: String): BasicMeta {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        if (duration <= 0) Log.w(TAG, "Duration is 0 or null for $uri")
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        return BasicMeta(duration, width, height, rotation)
    }

    fun readTrackMetadata(extractor: MediaExtractor): TrackData {
        var videoMime: String? = null
        var audioMime: String? = null
        var sampleRate = 0
        var channelCount = 0
        var fps = DEFAULT_FPS
        val tracks = mutableListOf<TrackMetadata>()

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            val isVideo = mime.startsWith("video/")
            val isAudio = mime.startsWith("audio/")
            if (isVideo) {
                videoMime = mime
                fps = LosslessEngineImpl.getVideoFps(format)
            } else if (isAudio) {
                audioMime = mime
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            tracks.add(TrackMetadata(
                i, mime, format.getString(MediaFormat.KEY_LANGUAGE),
                if (format.containsKey("title")) format.getString("title") else null, isVideo, isAudio
            ))
        }
        return TrackData(videoMime, audioMime, sampleRate, channelCount, fps, tracks)
    }

    fun findMuxerTrack(initialPlan: MergeInitialPlan, isVideo: Boolean): Int? {
        return initialPlan.plan.trackMap.entries.find { 
            initialPlan.plan.isVideoTrackMap[it.key] == isVideo 
        }?.value
    }

    suspend fun copyClipSegments(params: CopySegmentsParams): Long {
        var currentOffsetUs = params.initialOffsetUs
        val trackInfo = params.trackInfo
        val keepSegments = params.clip.segments.filter { it.action == SegmentAction.KEEP }
        
        for (segment in keepSegments) {
            val segmentPlan = SelectedTrackPlan(
                trackInfo.trackMap, trackInfo.isVideoTrackMap, 
                params.maxBufferSize, -1L, trackInfo.isVideoTrackMap.values.any { it }
            )
            val lastSampleTimes = params.copier.copy(
                segmentPlan, segment.startMs * MS_TO_US, segment.endMs * MS_TO_US, 
                params.buffer, currentOffsetUs
            )
            val lastSampleTimeInSegmentUs = lastSampleTimes.values.maxOrNull() ?: 0L
            val segmentDurationUs = if (lastSampleTimeInSegmentUs > 0) {
                lastSampleTimeInSegmentUs 
            } else {
                (segment.endMs - segment.startMs) * MS_TO_US
            }
            currentOffsetUs += segmentDurationUs + params.gapUs
        }
        return currentOffsetUs
    }

    data class BasicMeta(val duration: Long, val width: Int, val height: Int, val rotation: Int)
    data class TrackData(
        val videoMime: String?, val audioMime: String?, val sampleRate: Int, 
        val channelCount: Int, val fps: Float, val tracks: List<TrackMetadata>
    )
    data class MergeInitialPlan(
        val plan: SelectedTrackPlan, val audioSampleRate: Int, val videoFps: Float, 
        val expectedVideoMime: String?, val expectedAudioMime: String?
    )
    data class ClipTrackInfo(val trackMap: Map<Int, Int>, val isVideoTrackMap: Map<Int, Boolean>, val maxInputSize: Int)
    data class MergeParams(
        val clips: List<MediaClip>, val muxerWriter: MuxerWriter, val initialPlan: MergeInitialPlan, 
        val keepAudio: Boolean, val keepVideo: Boolean, val selectedTracks: List<Int>?
    )
    data class CopySegmentsParams(
        val clip: MediaClip,
        val copier: ExtractorSampleCopier,
        val trackInfo: ClipTrackInfo,
        val buffer: ByteBuffer,
        val initialOffsetUs: Long,
        val gapUs: Long,
        val maxBufferSize: Int
    )
}
