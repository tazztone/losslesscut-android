package com.tazztone.losslesscut.customviews

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeekerAccessibilityHelperTest {

    private lateinit var seeker: CustomVideoSeeker
    private lateinit var accessibilityHelper: SeekerAccessibilityHelper

    @Before
    fun setup() {
        seeker = CustomVideoSeeker(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        // Set dimensions for threshold calculation
        seeker.layout(0, 0, 1000, 200)
        seeker.videoDurationMs = 10000L
        seeker.zoomFactor = 1f
        seeker.scrollOffsetX = 0f

        accessibilityHelper = SeekerAccessibilityHelper(seeker)
    }

    @Test
    fun `getVirtualViewAt returns playhead when touched near seekPosition`() {
        seeker.seekPositionMs = 5000L

        // At exactly the playhead position
        val x = seeker.timeToX(5000L)
        // Calling getVirtualViewAt using reflections as it's protected in ExploreByTouchHelper
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVirtualViewAt", Float::class.java, Float::class.java)
        method.isAccessible = true
        val id = method.invoke(accessibilityHelper, x, 100f) as Int

        assertEquals(0, id) // VIRTUAL_ID_PLAYHEAD
    }

    @Test
    fun `getVirtualViewAt returns handle start when touched near start boundary of KEEP segment`() {
        seeker.seekPositionMs = 0L
        val segment = TrimSegment(UUID.randomUUID(), 2000L, 8000L, SegmentAction.KEEP)
        seeker.segments = listOf(segment)

        // At exactly the start boundary position
        val x = seeker.timeToX(2000L)
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVirtualViewAt", Float::class.java, Float::class.java)
        method.isAccessible = true
        val id = method.invoke(accessibilityHelper, x, 100f) as Int

        assertEquals(100, id) // VIRTUAL_ID_HANDLE_START
    }

    @Test
    fun `getVirtualViewAt returns handle end when touched near end boundary of KEEP segment`() {
        seeker.seekPositionMs = 0L
        val segment = TrimSegment(UUID.randomUUID(), 2000L, 8000L, SegmentAction.KEEP)
        seeker.segments = listOf(segment)

        // At exactly the end boundary position
        val x = seeker.timeToX(8000L)
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVirtualViewAt", Float::class.java, Float::class.java)
        method.isAccessible = true
        val id = method.invoke(accessibilityHelper, x, 100f) as Int

        assertEquals(101, id) // VIRTUAL_ID_HANDLE_START + 1
    }

    @Test
    fun `getVirtualViewAt returns INVALID_ID when touched far from elements`() {
        seeker.seekPositionMs = 5000L
        val segment = TrimSegment(UUID.randomUUID(), 2000L, 8000L, SegmentAction.KEEP)
        seeker.segments = listOf(segment)

        // Touched at 0ms, which is 2000ms away from any element
        val x = seeker.timeToX(0L)
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVirtualViewAt", Float::class.java, Float::class.java)
        method.isAccessible = true
        val id = method.invoke(accessibilityHelper, x, 100f) as Int

        assertEquals(ExploreByTouchHelper.INVALID_ID, id)
    }


    @Test
    fun `getVisibleVirtualViews returns empty when playhead is hidden`() {
        seeker.playheadVisible = false
        seeker.segmentsVisible = false

        val list = mutableListOf<Int>()
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVisibleVirtualViews", MutableList::class.java)
        method.isAccessible = true
        method.invoke(accessibilityHelper, list)

        assertTrue(list.isEmpty())
    }

    @Test
    fun `getVisibleVirtualViews returns playhead only when segments are hidden`() {
        seeker.playheadVisible = true
        seeker.segmentsVisible = false

        val list = mutableListOf<Int>()
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVisibleVirtualViews", MutableList::class.java)
        method.isAccessible = true
        method.invoke(accessibilityHelper, list)

        assertEquals(1, list.size)
        assertEquals(0, list[0]) // VIRTUAL_ID_PLAYHEAD
    }

    @Test
    fun `getVisibleVirtualViews returns playhead and handle IDs for KEEP segments`() {
        seeker.playheadVisible = true
        seeker.segmentsVisible = true

        val keep1 = TrimSegment(UUID.randomUUID(), 0L, 2000L, SegmentAction.KEEP)
        val discard1 = TrimSegment(UUID.randomUUID(), 2000L, 4000L, SegmentAction.DISCARD)
        val keep2 = TrimSegment(UUID.randomUUID(), 4000L, 6000L, SegmentAction.KEEP)

        seeker.segments = listOf(keep1, discard1, keep2)

        val list = mutableListOf<Int>()
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("getVisibleVirtualViews", MutableList::class.java)
        method.isAccessible = true
        method.invoke(accessibilityHelper, list)

        assertEquals(5, list.size)
        assertEquals(0, list[0]) // VIRTUAL_ID_PLAYHEAD
        // Segment 1 handles
        assertEquals(100, list[1]) // VIRTUAL_ID_HANDLE_START
        assertEquals(101, list[2])
        // Segment 2 handles (discard is skipped, so index 1)
        assertEquals(102, list[3]) // VIRTUAL_ID_HANDLE_START + (1 * 2)
        assertEquals(103, list[4])
    }


    @Test
    fun `onPopulateNodeForVirtualView for playhead populates correctly`() {
        seeker.seekPositionMs = 5000L

        val nodeInfo = AccessibilityNodeInfoCompat.obtain()

        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("onPopulateNodeForVirtualView", Int::class.java, AccessibilityNodeInfoCompat::class.java)
        method.isAccessible = true
        method.invoke(accessibilityHelper, 0, nodeInfo)

        // Use R.string.accessibility_playhead_pos_format for content description check
        // "Playhead at %1$s" -> "Playhead at 00:05" for 5000L
        assertTrue(nodeInfo.contentDescription.toString().contains("Playhead at"))
        assertTrue(nodeInfo.isFocusable)

        // Assert actions
        val actions = nodeInfo.actionList.map { it.id }
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD))
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD))

        // Clean up
        nodeInfo.recycle()
    }

    @Test
    fun `onPopulateNodeForVirtualView for handles populates correctly`() {
        val segment = TrimSegment(UUID.randomUUID(), 2000L, 8000L, SegmentAction.KEEP)
        seeker.segments = listOf(segment)

        // Test Start Handle
        var nodeInfo = AccessibilityNodeInfoCompat.obtain()
        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("onPopulateNodeForVirtualView", Int::class.java, AccessibilityNodeInfoCompat::class.java)
        method.isAccessible = true
        method.invoke(accessibilityHelper, 100, nodeInfo)

        // accessibility_segment_handle_format: "Segment %1$d %2$s handle at %3$s"
        val startDesc = nodeInfo.contentDescription.toString()
        assertTrue(startDesc.contains("Segment 1 start handle at"))
        assertTrue(nodeInfo.isFocusable)

        var actions = nodeInfo.actionList.map { it.id }
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD))
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD))

        // Clean up
        nodeInfo.recycle()

        // Test End Handle
        nodeInfo = AccessibilityNodeInfoCompat.obtain()
        method.invoke(accessibilityHelper, 101, nodeInfo)

        val endDesc = nodeInfo.contentDescription.toString()
        assertTrue(endDesc.contains("Segment 1 end handle at"))
        assertTrue(nodeInfo.isFocusable)

        actions = nodeInfo.actionList.map { it.id }
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD))
        assertTrue(actions.contains(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD))

        // Clean up
        nodeInfo.recycle()
    }


    @Test
    fun `onPerformActionForVirtualView scrolls playhead forward`() {
        seeker.seekPositionMs = 5000L
        var seekCalled = false
        var seekPos = 0L
        seeker.onSeekListener = {
            seekCalled = true
            seekPos = it
        }

        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("onPerformActionForVirtualView", Int::class.java, Int::class.java, Bundle::class.java)
        method.isAccessible = true
        val result = method.invoke(accessibilityHelper, 0, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, null) as Boolean

        assertTrue(result)
        assertEquals(6000L, seeker.seekPositionMs)
        assertTrue(seekCalled)
        assertEquals(6000L, seekPos)
    }

    @Test
    fun `onPerformActionForVirtualView scrolls playhead backward`() {
        seeker.seekPositionMs = 5000L

        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("onPerformActionForVirtualView", Int::class.java, Int::class.java, Bundle::class.java)
        method.isAccessible = true
        val result = method.invoke(accessibilityHelper, 0, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, null) as Boolean

        assertTrue(result)
        assertEquals(4000L, seeker.seekPositionMs)
    }

    @Test
    fun `onPerformActionForVirtualView scrolls handle start forward`() {
        val segmentId = UUID.randomUUID()
        val segment = TrimSegment(segmentId, 2000L, 8000L, SegmentAction.KEEP)
        seeker.segments = listOf(segment)

        var boundsChangedCalled = false
        var dragEndCalled = false
        seeker.onSegmentBoundsChanged = { id, start, end, touch ->
            boundsChangedCalled = true
            assertEquals(segmentId, id)
            assertEquals(3000L, start)
            assertEquals(8000L, end)
        }
        seeker.onSegmentBoundsDragEnd = {
            dragEndCalled = true
        }

        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("onPerformActionForVirtualView", Int::class.java, Int::class.java, Bundle::class.java)
        method.isAccessible = true
        val result = method.invoke(accessibilityHelper, 100, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, null) as Boolean

        assertTrue(result)
        assertTrue(boundsChangedCalled)
        assertTrue(dragEndCalled)
    }

    @Test
    fun `onPerformActionForVirtualView scrolls handle end backward`() {
        val segmentId = UUID.randomUUID()
        val segment = TrimSegment(segmentId, 2000L, 8000L, SegmentAction.KEEP)
        seeker.segments = listOf(segment)

        var boundsChangedCalled = false
        var dragEndCalled = false
        seeker.onSegmentBoundsChanged = { id, start, end, touch ->
            boundsChangedCalled = true
            assertEquals(segmentId, id)
            assertEquals(2000L, start)
            assertEquals(7000L, end)
        }
        seeker.onSegmentBoundsDragEnd = {
            dragEndCalled = true
        }

        val method = ExploreByTouchHelper::class.java.getDeclaredMethod("onPerformActionForVirtualView", Int::class.java, Int::class.java, Bundle::class.java)
        method.isAccessible = true
        val result = method.invoke(accessibilityHelper, 101, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, null) as Boolean

        assertTrue(result)
        assertTrue(boundsChangedCalled)
        assertTrue(dragEndCalled)
    }
}
