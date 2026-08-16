package com.tazztone.losslesscut.engine

import com.tazztone.losslesscut.domain.engine.AudioDecoder
import com.tazztone.losslesscut.domain.model.WaveformResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudioWaveformExtractorTest {

    private val decoder = mockk<AudioDecoder>()
    private val extractor = AudioWaveformExtractorImpl(decoder, Dispatchers.IO)

    @After
    fun tearDown() {
        unmockkAll()
    }

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

        coEvery { decoder.decode(uri, any()) } returns flowOf(pcmData)

        val result = extractor.extract(uri, trackIndex = 1, onProgress = null)

        assertNotNull(result)
        assertEquals(1000000L, result?.durationUs)
        assertTrue(result!!.rawAmplitudes.isNotEmpty())
    }

    @Test
    fun extract_handlesExceptionCorrectly() = runBlocking {
        val uri = "content://mock/audio.wav"
        val exception = RuntimeException("Test exception")
        coEvery { decoder.decode(uri, any()) } throws exception

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        val result = extractor.extract(uri, onProgress = null)

        assertNull(result)
        verify { Log.e("AudioWaveformExtractor", "Error extracting waveform", exception) }
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
