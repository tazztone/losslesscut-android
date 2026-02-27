package com.tazztone.losslesscut.ui.editor

import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.ui.PlayerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@UnstableApi
@ExperimentalCoroutinesApi
class PlaybackProgressTickerTest {

    private val seeker: CustomVideoSeeker = mockk(relaxed = true)
    private val playerManager: PlayerManager = mockk(relaxed = true)
    private val updates = mutableListOf<Pair<Long, Long>>()

    @Before
    fun setUp() {
        updates.clear()
        every { playerManager.isPlaying } returns true
        every { playerManager.currentPosition } returns 1000L
        every { playerManager.duration } returns 60_000L
    }

    @Test
    fun `start polls seeker and calls onUpdate while playing`() = runTest {
        val ticker = PlaybackProgressTicker(this, seeker, playerManager) { current, total ->
            updates.add(current to total)
        }
        ticker.start()
        advanceTimeBy(50L)
        verify(atLeast = 1) { seeker.setSeekPosition(1000L) }
        assert(updates.isNotEmpty())
        ticker.stop()
    }

    @Test
    fun `start does not update when dragging timeline`() = runTest {
        val ticker = PlaybackProgressTicker(this, seeker, playerManager) { current, total ->
            updates.add(current to total)
        }
        ticker.isDraggingTimeline = true
        ticker.start()
        advanceTimeBy(100L)
        verify(exactly = 0) { seeker.setSeekPosition(any()) }
        ticker.stop()
    }

    @Test
    fun `stop cancels polling and forces one final update`() = runTest {
        val ticker = PlaybackProgressTicker(this, seeker, playerManager) { current, total ->
            updates.add(current to total)
        }
        ticker.start()
        advanceTimeBy(16L)
        updates.clear()
        ticker.stop()
        verify(atLeast = 1) { seeker.setSeekPosition(1000L) }
        assertEquals(1, updates.size)
    }

    @Test
    fun `start does not update when playerManager is not playing`() = runTest {
        every { playerManager.isPlaying } returns false
        val ticker = PlaybackProgressTicker(this, seeker, playerManager) { current, total ->
            updates.add(current to total)
        }
        ticker.start()
        advanceTimeBy(100L)
        verify(exactly = 0) { seeker.setSeekPosition(any()) }
        ticker.stop()
    }
}
