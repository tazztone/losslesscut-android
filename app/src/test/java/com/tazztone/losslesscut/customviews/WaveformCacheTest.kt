package com.tazztone.losslesscut.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WaveformCacheTest {

    private lateinit var context: Context
    private lateinit var seeker: CustomVideoSeeker
    private lateinit var renderer: SeekerRenderer
    private lateinit var waveformCache: SeekerRenderer.WaveformCache

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
        seeker = spyk(CustomVideoSeeker(context))
        renderer = spyk(SeekerRenderer(seeker))
        waveformCache = renderer.waveformCache
    }

    @Test
    fun testInitialization() {
        assertNotNull(waveformCache)
    }

    @Test
    fun testDrawReturnsEarlyWhenNoWaveformData() {
        val canvas = mockk<Canvas>(relaxed = true)
        every { seeker.waveformData } returns null

        waveformCache.draw(canvas)

        verify(exactly = 0) { canvas.drawBitmap(any(), any<Float>(), any<Float>(), any<Paint>()) }
    }

    @Test
    fun testDrawReturnsEarlyWhenDrawStartGreaterThanDrawEnd() {
        val canvas = mockk<Canvas>(relaxed = true)
        every { seeker.waveformData } returns FloatArray(10) { it.toFloat() }
        every { seeker.zoomFactor } returns 1f
        every { seeker.height } returns 100
        every { seeker.width } returns 1000

        every { seeker.timeToX(0) } returns 0f
        every { seeker.videoDurationMs } returns 1000L
        every { seeker.timeToX(1000L) } returns 100f // timelineEnd
        every { seeker.scrollOffsetX } returns 200f // visibleStart > timelineEnd

        waveformCache.draw(canvas)

        verify(exactly = 0) { canvas.drawBitmap(any(), any<Float>(), any<Float>(), any<Paint>()) }
    }

    @Test
    fun testEvictsCacheWhenShouldInvalidate() {
        val canvas = mockk<Canvas>(relaxed = true)
        val data1 = FloatArray(10) { it.toFloat() }
        val data2 = FloatArray(10) { it.toFloat() }

        // Setup base state
        every { seeker.waveformData } returns data1
        every { seeker.zoomFactor } returns 1f
        every { seeker.height } returns 100
        every { seeker.width } returns 1000
        every { seeker.timeToX(0) } returns 0f
        every { seeker.videoDurationMs } returns 1000L
        every { seeker.timeToX(1000L) } returns 1000f
        every { seeker.scrollOffsetX } returns 0f

        // Initial draw populates cache
        waveformCache.draw(canvas)

        // Change data to invalidate cache
        every { seeker.waveformData } returns data2

        waveformCache.draw(canvas)

        // Ensure no crash occurred and code logic proceeded.
    }

    @Test
    fun testGeneratesAndDrawsTiles() {
        val canvas = mockk<Canvas>(relaxed = true)
        val data = FloatArray(100) { it.toFloat() }

        every { seeker.waveformData } returns data
        every { seeker.zoomFactor } returns 1f
        every { seeker.height } returns 100
        every { seeker.width } returns 1000
        every { seeker.timeToX(0) } returns 0f
        every { seeker.videoDurationMs } returns 10000L
        // CACHE_TILE_SIZE is 2048
        every { seeker.timeToX(10000L) } returns 4000f
        every { seeker.scrollOffsetX } returns 0f

        waveformCache.draw(canvas)

        // 4000 total width, visible width 1000.
        // drawStart = 0, drawEnd = 1000
        // endTile = 1000 / 2048 = 0.
        // Should draw exactly one tile (index 0).
        verify(exactly = 1) { canvas.drawBitmap(any(), 0f, 0f, any<Paint>()) }
    }

    @Test
    fun testShouldInvalidateLogic() {
        val shouldInvalidateMethod = waveformCache.javaClass.getDeclaredMethod(
            "shouldInvalidate",
            FloatArray::class.java,
            Float::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java
        )
        shouldInvalidateMethod.isAccessible = true

        val canvas = mockk<Canvas>(relaxed = true)
        val data = FloatArray(10) { it.toFloat() }

        every { seeker.waveformData } returns data
        every { seeker.zoomFactor } returns 1f
        every { seeker.height } returns 100
        every { seeker.width } returns 1000
        every { seeker.timeToX(0) } returns 0f
        every { seeker.videoDurationMs } returns 100L
        every { seeker.timeToX(100L) } returns 1000f
        every { seeker.scrollOffsetX } returns 0f

        // Run first draw to set the variables inside shouldInvalidate
        // (lastData, lastZoom, lastHeight, lastWidth, lastColor)
        waveformCache.draw(canvas)

        // Test same data, shouldn't invalidate
        val colorField = waveformCache.javaClass.getDeclaredField("lastColor")
        colorField.isAccessible = true
        val lastColor = colorField.get(waveformCache) as Int

        var result = shouldInvalidateMethod.invoke(waveformCache, data, 1f, 100, 1000, lastColor) as Boolean
        assertFalse("Should not invalidate with same data", result)

        // Test zoom changed
        result = shouldInvalidateMethod.invoke(waveformCache, data, 2f, 100, 1000, lastColor) as Boolean
        assertTrue("Should invalidate when zoom changes", result)

        // Test height changed
        result = shouldInvalidateMethod.invoke(waveformCache, data, 1f, 200, 1000, lastColor) as Boolean
        assertTrue("Should invalidate when height changes", result)

        // Test width changed
        result = shouldInvalidateMethod.invoke(waveformCache, data, 1f, 100, 2000, lastColor) as Boolean
        assertTrue("Should invalidate when width changes", result)

        // Test color changed
        result = shouldInvalidateMethod.invoke(waveformCache, data, 1f, 100, 1000, lastColor + 1) as Boolean
        assertTrue("Should invalidate when color changes", result)

        // Test data changed
        val data2 = FloatArray(10) { it.toFloat() }
        result = shouldInvalidateMethod.invoke(waveformCache, data2, 1f, 100, 1000, lastColor) as Boolean
        assertTrue("Should invalidate when data reference changes", result)
    }

    @Test
    fun testGenerateTileEdgeCases() {
        val generateTileMethod = waveformCache.javaClass.getDeclaredMethod(
            "generateTile",
            Int::class.java,
            FloatArray::class.java,
            Int::class.java,
            Int::class.java
        )
        generateTileMethod.isAccessible = true
        val data = FloatArray(10) { it.toFloat() }

        every { seeker.height } returns 100

        // Test availableWidth <= 0 (timelineEnd <= timelineStart)
        var bitmap = generateTileMethod.invoke(waveformCache, 0, data, 1000, 500) as Bitmap
        assertNotNull(bitmap)

        // Test loopStart > loopEnd
        // index = 0, CACHE_TILE_SIZE = 2048 => tileStartPx = 0, tileEndPx = 2048
        // timelineStart = 3000, timelineEnd = 4000 => loopStart = 3000, loopEnd = 2047
        bitmap = generateTileMethod.invoke(waveformCache, 0, data, 3000, 4000) as Bitmap
        assertNotNull(bitmap)
    }
}
