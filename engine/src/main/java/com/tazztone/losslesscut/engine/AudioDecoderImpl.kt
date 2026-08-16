package com.tazztone.losslesscut.engine

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import com.tazztone.losslesscut.domain.engine.AudioDecoder
import com.tazztone.losslesscut.domain.di.EngineDispatcher
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDecoderImpl @Inject constructor(
    private val dataSource: MediaDataSource,
    @param:EngineDispatcher private val engineDispatcher: kotlinx.coroutines.CoroutineDispatcher
) : AudioDecoder {

    override suspend fun decode(uri: String, trackIndex: Int?): Flow<AudioDecoder.PcmData> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val trackInfo = setupExtractor(extractor, uri, trackIndex) ?: return@flow
            val format = trackInfo.format
            codec = createCodec(format)

            runDecodingLoop(extractor, codec, format)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Error decoding audio from authority: ${Uri.parse(uri).authority}", e)
        } finally {
            cleanupResources(extractor, codec)
        }
    }.flowOn(engineDispatcher)

    private fun setupExtractor(extractor: MediaExtractor, uri: String, trackIndex: Int?): TrackInfo? {
        dataSource.setExtractorSource(extractor, uri)
        val trackInfo = findAudioTrack(extractor, trackIndex) ?: return null
        extractor.selectTrack(trackInfo.index)
        return trackInfo
    }

    private fun createCodec(format: MediaFormat): MediaCodec {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        }

        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Missing MIME type")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        return codec
    }

    private suspend fun FlowCollector<AudioDecoder.PcmData>.runDecodingLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        format: MediaFormat
    ) {
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        var eosTimeouts = 0

        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            DEFAULT_SAMPLE_RATE
        }
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            DEFAULT_CHANNEL_COUNT
        }

        while (!sawOutputEOS) {
            currentCoroutineContext().ensureActive()
            if (!sawInputEOS) {
                sawInputEOS = processInput(extractor, codec)
            }
            val metadata = DecoderMetadata(durationUs, sampleRate, channelCount)
            val bufferHolder = BufferHolder()
            val output = processOutput(codec, info, metadata, bufferHolder)
            sawOutputEOS = output.sawOutputEOS
            if (sawInputEOS && !sawOutputEOS) {
                eosTimeouts = if (output.hadOutput) 0 else eosTimeouts + 1
                if (eosTimeouts >= MAX_EOS_TIMEOUTS) {
                    Log.w(TAG, "Decoder did not signal EOS after input EOS; force terminating decode loop")
                    break
                }
            }
            kotlinx.coroutines.yield()
        }
    }

    private class BufferHolder(var data: ByteArray = ByteArray(0))

    private data class DecoderMetadata(
        val durationUs: Long,
        val sampleRate: Int,
        val channelCount: Int
    )

    private fun processInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
        var sawInputEOS = false
        if (inIdx >= 0) {
            val buffer = codec.getInputBuffer(inIdx)
            if (buffer == null) {
                Log.w(TAG, "Null input buffer at index $inIdx, skipping")
            } else {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sawInputEOS = true
                } else {
                    codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        return sawInputEOS
    }

    private data class OutputResult(val sawOutputEOS: Boolean, val hadOutput: Boolean)

    private suspend fun FlowCollector<AudioDecoder.PcmData>.processOutput(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        metadata: DecoderMetadata,
        bufferHolder: BufferHolder
    ): OutputResult {
        var outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        var sawOutputEOS = false
        var hadOutput = false
        while (outIdx >= 0) {
            currentCoroutineContext().ensureActive()
            hadOutput = true
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                sawOutputEOS = true
            }

            if (info.size > 0) {
                val frame = OutputFrame(codec, outIdx, info, metadata, sawOutputEOS, bufferHolder)
                emitPcmData(frame)
            }
            codec.releaseOutputBuffer(outIdx, false)
            if (sawOutputEOS) break
            outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_NONE)
        }
        return OutputResult(sawOutputEOS, hadOutput)
    }

    private data class OutputFrame(
        val codec: MediaCodec,
        val outIdx: Int,
        val info: MediaCodec.BufferInfo,
        val metadata: DecoderMetadata,
        val isEOS: Boolean,
        val bufferHolder: BufferHolder
    )

    private suspend fun FlowCollector<AudioDecoder.PcmData>.emitPcmData(frame: OutputFrame) {
        val outBuf = frame.codec.getOutputBuffer(frame.outIdx) ?: return
        val info = frame.info
        val bufferHolder = frame.bufferHolder

        // Reuse buffer if possible to reduce allocations
        if (bufferHolder.data.size < info.size) {
            bufferHolder.data = ByteArray(info.size)
        }

        outBuf.position(info.offset)
        outBuf.limit(info.offset + info.size)
        outBuf.get(bufferHolder.data, 0, info.size)

        emit(AudioDecoder.PcmData(
            buffer = bufferHolder.data,
            size = info.size,
            timeUs = info.presentationTimeUs,
            durationUs = frame.metadata.durationUs,
            sampleRate = frame.metadata.sampleRate,
            channelCount = frame.metadata.channelCount,
            isEndOfStream = frame.isEOS
        ))
    }

    private fun cleanupResources(extractor: MediaExtractor, codec: MediaCodec?) {
        try {
            codec?.stop()
        } catch (_: Exception) {
            // Ignore during cleanup
        }
        try {
            codec?.release()
        } catch (_: Exception) {
            // Ignore during cleanup
        }
        try {
            extractor.release()
        } catch (_: Exception) {
            // Ignore during cleanup
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor, targetTrackIndex: Int?): TrackInfo? {
        val audioTracks = (0 until extractor.trackCount)
            .map { TrackInfo(it, extractor.getTrackFormat(it)) }
            .filter { it.format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }

        if (audioTracks.isEmpty()) return null
        if (targetTrackIndex == null) return audioTracks.first()

        return audioTracks.firstOrNull { it.index == targetTrackIndex }
            ?: audioTracks.getOrNull(targetTrackIndex)
            ?: audioTracks.first()
    }

    private data class TrackInfo(val index: Int, val format: MediaFormat)

    companion object {
        private const val TAG = "AudioDecoderImpl"
        private const val TIMEOUT_US = 5000L
        private const val TIMEOUT_NONE = 0L
        private const val MAX_EOS_TIMEOUTS = 50
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_CHANNEL_COUNT = 2
    }
}
