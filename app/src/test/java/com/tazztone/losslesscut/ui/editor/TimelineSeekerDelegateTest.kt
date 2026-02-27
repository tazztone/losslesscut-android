package com.tazztone.losslesscut.ui.editor

import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.ui.PlayerManager
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimelineSeekerDelegateTest {

    private val seeker: CustomVideoSeeker = mockk(relaxed = true)
    private val viewModel: VideoEditingViewModel = mockk(relaxed = true)
    private val playerManager: PlayerManager = mockk(relaxed = true)

    private val seekPositions = mutableListOf<Long>()
    private val draggingStates = mutableListOf<Boolean>()

    private lateinit var delegate: TimelineSeekerDelegate

    @Before
    fun setUp() {
        delegate = TimelineSeekerDelegate(
            seeker = seeker,
            viewModel = viewModel,
            playerManager = playerManager,
            onSeek = { pos -> seekPositions.add(pos) },
            onDraggingChanged = { dragging -> draggingStates.add(dragging) }
        )
        delegate.setup()
    }

    @Test
    fun `setup attaches onSeekStart callback that triggers dragging`() {
        val callback = slot<(() -> Unit)>()
        verify { seeker.onSeekStart = capture(callback) }
        callback.captured.invoke()
        assertEquals(listOf(true), draggingStates)
    }

    @Test
    fun `setup attaches onSeekEnd callback that stops dragging`() {
        val callback = slot<(() -> Unit)>()
        verify { seeker.onSeekEnd = capture(callback) }
        callback.captured.invoke()
        assertEquals(listOf(false), draggingStates)
    }

    @Test
    fun `onSeekListener seeks player and triggers onSeek callback`() {
        val callback = slot<((Long) -> Unit)>()
        verify { seeker.onSeekListener = capture(callback) }
        callback.captured.invoke(5000L)
        verify { playerManager.seekTo(5000L) }
        assertEquals(listOf(5000L), seekPositions)
    }

    @Test
    fun `onSegmentSelected notifies viewModel`() {
        val callback = slot<((UUID?) -> Unit)>()
        verify { seeker.onSegmentSelected = capture(callback) }
        val id = UUID.randomUUID()
        callback.captured.invoke(id)
        verify { viewModel.selectSegment(id) }
    }

    @Test
    fun `onSegmentBoundsChanged updates model, seeks, and starts dragging`() {
        val callback = slot<((UUID, Long, Long, Long) -> Unit)>()
        verify { seeker.onSegmentBoundsChanged = capture(callback) }
        val id = UUID.randomUUID()
        callback.captured.invoke(id, 1000L, 5000L, 2000L)
        verify { viewModel.updateSegmentBounds(id, 1000L, 5000L) }
        verify { playerManager.seekTo(2000L) }
        assertEquals(listOf(true), draggingStates)
        assertEquals(listOf(2000L), seekPositions)
    }

    @Test
    fun `setVideoDuration propagates to seeker`() {
        delegate.setVideoDuration(30_000L)
        verify { seeker.setVideoDuration(30_000L) }
    }

    @Test
    fun `resetView propagates to seeker`() {
        delegate.resetView()
        verify { seeker.resetView() }
    }
}
