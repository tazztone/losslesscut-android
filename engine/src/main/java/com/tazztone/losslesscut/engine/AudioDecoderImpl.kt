package com.tazztone.losslesscut.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.tazztone.losslesscut.domain.engine.AudioDecoder
import com.tazztone.losslesscut.engine.muxing.MediaDataSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDecoderImpl @Inject constructor(
    private val dataSource: MediaDataSource
) : AudioDecoder {

    override suspend fun decode(uri: String): Flow<AudioDecoder.PcmData> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            dataSource.setExtractorSource(extractor, uri)
            val trackInfo = findAudioTrack(extractor) ?: return@flow

            extractor.selectTrack(trackInfo.index)
            val format = trackInfo.format

            // Standardize output to PCM 16-bit if supported (API 24+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            }

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@flow
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            while (!sawOutputEOS && currentCoroutineContext().isActive) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buffer = codec.getInputBuffer(inIdx)
                        val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                while (outIdx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }

                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            val chunk = ByteArray(info.size)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.get(chunk)

                            emit(AudioDecoder.PcmData(
                                buffer = chunk,
                                size = info.size,
                                timeUs = info.presentationTimeUs,
                                durationUs = durationUs,
                                sampleRate = sampleRate,
                                channelCount = channelCount,
                                isEndOfStream = sawOutputEOS
                            ))
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (sawOutputEOS) break
                    outIdx = codec.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // In case of error, flow terminates naturally or throws if needed.
            // For now, logging and finishing is safer than crashing.
        } finally {
            try { codec?.stop() } catch (e: Exception) {}
            try { codec?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): TrackInfo? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return TrackInfo(i, format)
            }
        }
        return null
    }

    private data class TrackInfo(val index: Int, val format: MediaFormat)

    companion object {
        private const val TIMEOUT_US = 5000L
    }
}
