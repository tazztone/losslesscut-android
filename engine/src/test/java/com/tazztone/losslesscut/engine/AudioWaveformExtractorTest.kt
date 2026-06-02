package com.tazztone.losslesscut.engine

import com.tazztone.losslesscut.domain.engine.AudioDecoder
import com.tazztone.losslesscut.domain.model.WaveformResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioWaveformExtractorTest {

    private val decoder = mockk<AudioDecoder>()
    private val extractor = AudioWaveformExtractorImpl(decoder, Dispatchers.IO)

    @Test
    fun extract_processesPcmDataCorrectly() = runBlocking {
        val uri = "content://mock/audio.wav"
        val pcmData = AudioDecoder.PcmData(
            buffer = ByteArray(1024) { (it % 100).toByte() },
            size = 1024,
            timeUs = 0,
            durationUs = 1000000,
            sampleRate = 44100,
            channelCount = 2,
            isEndOfStream = true
        )

        coEvery { decoder.decode(uri) } returns flowOf(pcmData)

        val result = extractor.extract(uri, onProgress = null)

        assertNotNull(result)
        assertEquals(1000000L, result?.durationUs)
        assertTrue(result!!.rawAmplitudes.isNotEmpty())
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
