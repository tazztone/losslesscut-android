package com.tazztone.losslesscut.customviews

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.viewmodel.ClipController

/**
 * Extracted accessibility helper for CustomVideoSeeker to reduce class size.
 */
internal class SeekerAccessibilityHelper(private val seeker: CustomVideoSeeker) : ExploreByTouchHelper(seeker) {
    
    companion object {
        private const val VIRTUAL_ID_PLAYHEAD = 0
        private const val VIRTUAL_ID_HANDLE_START = 100
        private const val STEP_MS = 1000L
        private const val TOUCH_THRESHOLD_DP = 60f
        private const val PLAYHEAD_HIT_WIDTH = 20
        private const val HANDLE_HIT_WIDTH = 25
        private const val HANDLE_Y_HIT_OFFSET = 60
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val touchTimeMs = seeker.xToTime(x + seeker.scrollOffsetX)
        val thresholdMs = calculateThresholdMs()

        return when {
            kotlin.math.abs(seeker.seekPositionMs - touchTimeMs) < thresholdMs -> VIRTUAL_ID_PLAYHEAD
            else -> findNearbyHandle(touchTimeMs, thresholdMs)
        }
    }

    private fun calculateThresholdMs(): Long {
        val logicalWidth = seeker.width * seeker.zoomFactor
        return if (logicalWidth > 0) {
            ((TOUCH_THRESHOLD_DP / logicalWidth) * seeker.videoDurationMs).toLong()
        } else {
            0L
        }
    }

    private fun findNearbyHandle(touchTimeMs: Long, thresholdMs: Long): Int {
        val keepSegments = seeker.segments.filter { it.action == SegmentAction.KEEP }
        return keepSegments.withIndex().firstNotNullOfOrNull { (index, segment) ->
            when {
                kotlin.math.abs(segment.startMs - touchTimeMs) < thresholdMs -> 
                    VIRTUAL_ID_HANDLE_START + (index * 2)
                kotlin.math.abs(segment.endMs - touchTimeMs) < thresholdMs -> 
                    VIRTUAL_ID_HANDLE_START + (index * 2) + 1
                else -> null
            }
        } ?: INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        if (!seeker.playheadVisible) return
        virtualViewIds.add(VIRTUAL_ID_PLAYHEAD)
        if (seeker.segmentsVisible) {
            val keepSegments = seeker.segments.filter { it.action == SegmentAction.KEEP }
            for (i in keepSegments.indices) {
                virtualViewIds.add(VIRTUAL_ID_HANDLE_START + (i * 2))
                virtualViewIds.add(VIRTUAL_ID_HANDLE_START + (i * 2) + 1)
            }
        }
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        if (virtualViewId == VIRTUAL_ID_PLAYHEAD) {
            node.contentDescription = seeker.context.getString(
                R.string.accessibility_playhead_pos_format,
                seeker.formatTimeShort(seeker.seekPositionMs)
            )
            val x = seeker.timeToX(seeker.seekPositionMs) - seeker.scrollOffsetX
            val rect = Rect((x - PLAYHEAD_HIT_WIDTH).toInt(), 0, (x + PLAYHEAD_HIT_WIDTH).toInt(), seeker.height)
            @Suppress("DEPRECATION")
            node.setBoundsInParent(rect)
            node.isFocusable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
        } else {
            val index = (virtualViewId - VIRTUAL_ID_HANDLE_START) / 2
            val isStart = (virtualViewId - VIRTUAL_ID_HANDLE_START) % 2 == 0
            val keepSegments = seeker.segments.filter { it.action == SegmentAction.KEEP }
            if (index < keepSegments.size) {
                val segment = keepSegments[index]
                val timeMs = if (isStart) segment.startMs else segment.endMs
                val type = seeker.context.getString(
                    if (isStart) R.string.accessibility_handle_type_start else R.string.accessibility_handle_type_end
                )
                node.contentDescription = seeker.context.getString(
                    R.string.accessibility_segment_handle_format,
                    index + 1,
                    type,
                    seeker.formatTimeShort(timeMs)
                )
                
                val x = seeker.timeToX(timeMs) - seeker.scrollOffsetX
                val rect = Rect(
                    (x - HANDLE_HIT_WIDTH).toInt(), 
                    seeker.height - HANDLE_Y_HIT_OFFSET, 
                    (x + HANDLE_HIT_WIDTH).toInt(), 
                    seeker.height
                )
                @Suppress("DEPRECATION")
                node.setBoundsInParent(rect)
                node.isFocusable = true
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            }
        }
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD && 
            action != AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) return false
            
        val direction = if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) 1 else -1
        return if (virtualViewId == VIRTUAL_ID_PLAYHEAD) {
            performPlayheadScroll(direction)
        } else {
            performHandleScroll(virtualViewId, direction)
        }
    }

    private fun performPlayheadScroll(direction: Int): Boolean {
        val newPos = (seeker.seekPositionMs + direction * STEP_MS).coerceIn(0, seeker.videoDurationMs)
        seeker.setSeekPosition(newPos)
        seeker.onSeekListener?.invoke(newPos)
        return true
    }

    private fun performHandleScroll(virtualViewId: Int, direction: Int): Boolean {
        val index = (virtualViewId - VIRTUAL_ID_HANDLE_START) / 2
        val isStart = (virtualViewId - VIRTUAL_ID_HANDLE_START) % 2 == 0
        val keepSegments = seeker.segments.filter { it.action == SegmentAction.KEEP }
        if (index >= keepSegments.size) return false
        
        val segment = keepSegments[index]
        if (isStart) {
            val newStart = (segment.startMs + direction * STEP_MS)
                .coerceIn(0, segment.endMs - ClipController.MIN_SEGMENT_DURATION_MS)
            seeker.onSegmentBoundsChanged?.invoke(segment.id, newStart, segment.endMs, newStart)
            seeker.onSegmentBoundsDragEnd?.invoke()
        } else {
            val newEnd = (segment.endMs + direction * STEP_MS)
                .coerceIn(segment.startMs + ClipController.MIN_SEGMENT_DURATION_MS, seeker.videoDurationMs)
            seeker.onSegmentBoundsChanged?.invoke(segment.id, segment.startMs, newEnd, newEnd)
            seeker.onSegmentBoundsDragEnd?.invoke()
        }
        seeker.invalidate()
        invalidateVirtualView(virtualViewId)
        return true
    }
}
