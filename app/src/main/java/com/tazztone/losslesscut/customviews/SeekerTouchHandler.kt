package com.tazztone.losslesscut.customviews

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.viewmodel.ClipController
import java.util.UUID

/**
 * Consolidated touch and gesture handling for CustomVideoSeeker.
 */
internal class SeekerTouchHandler(private val seeker: CustomVideoSeeker) {

    companion object {
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 20f
        private const val ZOOM_CHANGE_THRESHOLD = 0.01f
        private const val PAN_SPEED_BASE = 30f
        private const val SNAP_THRESHOLD_DP = 30f
        private const val EDGE_PAN_THRESHOLD = 100f
        private const val AUTO_PAN_DELAY_MS = 16L
        private const val HANDLE_HIT_THRESHOLD_DP = 60f
        private const val HANDLE_TOUCH_BOTTOM_OFFSET = 80f
    }

    var activeSegmentId: UUID? = null
    var isAutoPanning = false
    var lastTouchX = 0f
    var lastSnappedKeyframe: Long? = null

    val scaleGestureDetector = ScaleGestureDetector(
        seeker.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevZoom = seeker.zoomFactor
            val newFactor = seeker.zoomFactor * detector.scaleFactor
            if (kotlin.math.abs(newFactor - seeker.zoomFactor) > ZOOM_CHANGE_THRESHOLD) {
                seeker.dismissHints()
            }
            seeker.zoomFactor = newFactor.coerceIn(MIN_ZOOM, MAX_ZOOM)
            
            val focusX = detector.focusX
            val contentFocusX = (seeker.scrollOffsetX + focusX) / prevZoom
            seeker.scrollOffsetX = (contentFocusX * seeker.zoomFactor - focusX).coerceIn(0f, seeker.maxScrollOffset())
            
            seeker.invalidate()
            return true
        }
    })

    val gestureDetector = GestureDetector(seeker.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.NONE) {
                seeker.scrollOffsetX = (seeker.scrollOffsetX + distanceX).coerceIn(0f, seeker.maxScrollOffset())
                seeker.invalidate()
                return true
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val contentX = e.x + seeker.scrollOffsetX
            val timeMs = seeker.xToTime(contentX)
            
            if (!seeker.isRemuxMode) {
                val tappedSegment = seeker.segments.find { 
                    it.action == SegmentAction.KEEP && timeMs in it.startMs..it.endMs 
                }
                if (tappedSegment != null) {
                    seeker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                seeker.onSegmentSelected?.invoke(tappedSegment?.id)
            }
            
            seeker.seekPositionMs = timeMs
            seeker.onSeekListener?.invoke(seeker.seekPositionMs)
            seeker.invalidate()
            return true
        }
    })

    val autoPanRunnable = object : Runnable {
        override fun run() {
            val shouldAutoPan = isAutoPanning && 
                    seeker.currentTouchTarget != CustomVideoSeeker.TouchTarget.NONE && 
                    activeSegmentId != null
            if (!shouldAutoPan) return
            
            val segment = seeker.segments.find { it.id == activeSegmentId } ?: return
            
            val panSpeed = PAN_SPEED_BASE / seeker.zoomFactor
            if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_RIGHT) {
                seeker.scrollOffsetX = (seeker.scrollOffsetX + panSpeed).coerceIn(0f, seeker.maxScrollOffset())
            } else if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT) {
                seeker.scrollOffsetX = (seeker.scrollOffsetX - panSpeed).coerceIn(0f, seeker.maxScrollOffset())
            }
            
            val contentX = lastTouchX + seeker.scrollOffsetX
            var touchTimeMs = seeker.xToTime(contentX)
            
            if (seeker.isLosslessMode && seeker.keyframes.isNotEmpty()) {
                val snapTimeMs = seeker.keyframes.minByOrNull { kotlin.math.abs(it - touchTimeMs) }
                val snapThresholdWidth = seeker.durationToWidth(kotlin.math.abs((snapTimeMs ?: 0) - touchTimeMs))
                if (snapTimeMs != null && snapThresholdWidth < SNAP_THRESHOLD_DP) {
                    touchTimeMs = snapTimeMs
                    if (lastSnappedKeyframe != snapTimeMs) {
                        seeker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastSnappedKeyframe = snapTimeMs
                    }
                } else {
                    lastSnappedKeyframe = null
                }
            }

            if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT) {
                val newStart = touchTimeMs.coerceAtMost(
                    segment.endMs - ClipController.MIN_SEGMENT_DURATION_MS
                )
                seeker.onSegmentBoundsChanged?.invoke(segment.id, newStart, segment.endMs, newStart)
                seeker.seekPositionMs = newStart
            } else {
                val newEnd = touchTimeMs.coerceAtLeast(
                    segment.startMs + ClipController.MIN_SEGMENT_DURATION_MS
                )
                seeker.onSegmentBoundsChanged?.invoke(segment.id, segment.startMs, newEnd, newEnd)
                seeker.seekPositionMs = newEnd
            }
            seeker.invalidate()
            
            val canPostNextAutoPan = 
                (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_RIGHT && 
                    seeker.scrollOffsetX < seeker.maxScrollOffset()) ||
                (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT && 
                    seeker.scrollOffsetX > 0f)
            
            if (canPostNextAutoPan) {
                seeker.postDelayed(this, AUTO_PAN_DELAY_MS)
            } else {
                isAutoPanning = false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress) return true
        gestureDetector.onTouchEvent(event)

        lastTouchX = event.x
        val contentX = event.x + seeker.scrollOffsetX
        val touchTimeMs = seeker.xToTime(contentX)

        when (event.actionMasked) {
             MotionEvent.ACTION_DOWN -> handleActionDown(event, touchTimeMs)
             MotionEvent.ACTION_MOVE -> handleActionMove(event, touchTimeMs)
             MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleActionUp()
        }
        return true
    }

    private fun handleActionDown(event: MotionEvent, touchTimeMs: Long) {
        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.NONE
        val logicalWidth = seeker.width * seeker.zoomFactor
        val hitThresholdMs = if (logicalWidth > 0) {
            ((HANDLE_HIT_THRESHOLD_DP / logicalWidth) * seeker.videoDurationMs).toLong()
        } else {
            0L
        }
        seeker.dismissHints()

        if (handleSegmentHit(event, touchTimeMs, hitThresholdMs)) return
        handlePlayheadHit(touchTimeMs, hitThresholdMs)
    }

    private fun handleSegmentHit(event: MotionEvent, touchTimeMs: Long, hitThresholdMs: Long): Boolean {
        val isTouchingBottom = event.y > seeker.height - HANDLE_TOUCH_BOTTOM_OFFSET
        val canHitSegments = seeker.segmentsVisible && isTouchingBottom && !seeker.isRemuxMode
        if (!canHitSegments) return false

        val (hitHandle, hitId) = findHandleHit(touchTimeMs, hitThresholdMs)
        val hasHit = hitHandle != CustomVideoSeeker.TouchTarget.NONE
        if (hasHit) {
            seeker.currentTouchTarget = hitHandle
            activeSegmentId = hitId
            seeker.onSeekStart?.invoke()
            seeker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            hitId?.let { id ->
                if (id != seeker.selectedSegmentId) seeker.onSegmentSelected?.invoke(id)
            }
        }
        return hasHit
    }

    private fun handlePlayheadHit(touchTimeMs: Long, hitThresholdMs: Long) {
        val playheadDist = kotlin.math.abs(seeker.seekPositionMs - touchTimeMs)
        if (seeker.playheadVisible && playheadDist < hitThresholdMs) {
            seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.PLAYHEAD
            seeker.onSeekStart?.invoke()
            seeker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun findHandleHit(
        touchTimeMs: Long,
        hitThresholdMs: Long
    ): Pair<CustomVideoSeeker.TouchTarget, UUID?> {
        val keepSegments = seeker.segments.filter { it.action != SegmentAction.DISCARD }
        var bestDist = hitThresholdMs + 1
        var bestHandle = CustomVideoSeeker.TouchTarget.NONE
        var bestId: UUID? = null

        for (seg in keepSegments) {
            val leftDist = kotlin.math.abs(seg.startMs - touchTimeMs)
            val isLeftCandidate = leftDist <= hitThresholdMs
            val isBetterLeft = leftDist < bestDist || (leftDist == bestDist && touchTimeMs > seg.startMs)
            if (isLeftCandidate && isBetterLeft) {
                bestDist = leftDist
                bestHandle = CustomVideoSeeker.TouchTarget.HANDLE_LEFT
                bestId = seg.id
            }
            val rightDist = kotlin.math.abs(seg.endMs - touchTimeMs)
            val isRightCandidate = rightDist <= hitThresholdMs
            val isBetterRight = rightDist < bestDist || (rightDist == bestDist && touchTimeMs < seg.endMs)
            if (isRightCandidate && isBetterRight) {
                bestDist = rightDist
                bestHandle = CustomVideoSeeker.TouchTarget.HANDLE_RIGHT
                bestId = seg.id
            }
        }
        return bestHandle to bestId
    }

    private fun handleActionMove(event: MotionEvent, touchTimeMs: Long) {
        if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT || 
            seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_RIGHT) {
            
            activeSegmentId?.let { id ->
                performSegmentDrag(id, touchTimeMs)
            }
            checkAutoPanTrigger(event)
        } else if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.PLAYHEAD) {
            seeker.seekPositionMs = touchTimeMs
            seeker.onSeekListener?.invoke(seeker.seekPositionMs)
            seeker.invalidate()
        }
    }

    private fun performSegmentDrag(id: UUID, touchTimeMs: Long) {
        val segment = seeker.segments.find { it.id == id } ?: return
        val newTimeMs = applySnap(touchTimeMs)
        
        val allSegments = seeker.segments.filter { it.action != SegmentAction.DISCARD }
        val keepSegments = allSegments.sortedBy { it.startMs }
        val idx = keepSegments.indexOfFirst { it.id == id }
        
        val isLeft = seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT
        if (isLeft) {
            val prev = if (idx > 0) keepSegments[idx - 1] else null
            performEdgeDrag(true, id, segment, newTimeMs, prev)
        } else {
            val next = if (idx >= 0 && idx < keepSegments.size - 1) keepSegments[idx + 1] else null
            performEdgeDrag(false, id, segment, newTimeMs, next)
        }
    }

    private fun performEdgeDrag(
        isLeft: Boolean,
        id: UUID,
        segment: TrimSegment,
        touchTime: Long,
        neighbor: TrimSegment?
    ) {
        val min = if (isLeft) {
            neighbor?.endMs ?: 0L
        } else {
            segment.startMs + ClipController.MIN_SEGMENT_DURATION_MS
        }
        val max = if (isLeft) {
            segment.endMs - ClipController.MIN_SEGMENT_DURATION_MS
        } else {
            neighbor?.startMs ?: seeker.videoDurationMs
        }
        
        var finalTime = touchTime
        if (seeker.isLosslessMode && seeker.keyframes.isNotEmpty()) {
            val isOutOfSegment = if (isLeft) finalTime >= segment.endMs else finalTime <= segment.startMs
            if (isOutOfSegment) {
                finalTime = if (isLeft) {
                    seeker.keyframes.filter { it < segment.endMs }.lastOrNull() ?: 0L
                } else {
                    seeker.keyframes.filter { it > segment.startMs }.firstOrNull() ?: seeker.videoDurationMs
                }
            }
        }
        val clampedTime = finalTime.coerceIn(min, max)
        if (isLeft) {
            seeker.onSegmentBoundsChanged?.invoke(id, clampedTime, segment.endMs, clampedTime)
        } else {
            seeker.onSegmentBoundsChanged?.invoke(id, segment.startMs, clampedTime, clampedTime)
        }
        seeker.seekPositionMs = clampedTime
    }

    private fun checkAutoPanTrigger(event: MotionEvent) {
        val isRightEdge = event.x > seeker.width - EDGE_PAN_THRESHOLD &&
                seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_RIGHT
        val isLeftEdge = event.x < EDGE_PAN_THRESHOLD &&
                seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT

        if (isRightEdge || isLeftEdge) {
            if (!isAutoPanning) {
                isAutoPanning = true
                seeker.post(autoPanRunnable)
            }
        } else {
            isAutoPanning = false
            seeker.removeCallbacks(autoPanRunnable)
        }
    }

    private fun applySnap(touchTimeMs: Long): Long {
        if (!seeker.isLosslessMode || seeker.keyframes.isEmpty()) return touchTimeMs
        
        val snapTimeMs = seeker.keyframes.minByOrNull { kotlin.math.abs(it - touchTimeMs) }
        val diff = (snapTimeMs ?: 0) - touchTimeMs
        val isCloseEnough = snapTimeMs != null && 
                seeker.durationToWidth(kotlin.math.abs(diff)) < SNAP_THRESHOLD_DP
        
        return if (isCloseEnough) {
            if (lastSnappedKeyframe != snapTimeMs) {
                seeker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastSnappedKeyframe = snapTimeMs
            }
            snapTimeMs!!
        } else {
            lastSnappedKeyframe = null
            touchTimeMs
        }
    }

    private fun handleActionUp() {
        isAutoPanning = false
        seeker.removeCallbacks(autoPanRunnable)
        if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_LEFT || 
            seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.HANDLE_RIGHT) {
            seeker.onSeekEnd?.invoke()
            seeker.onSegmentBoundsDragEnd?.invoke()
        }
        if (seeker.currentTouchTarget != CustomVideoSeeker.TouchTarget.NONE) {
            if (seeker.currentTouchTarget == CustomVideoSeeker.TouchTarget.PLAYHEAD) seeker.onSeekEnd?.invoke()
            seeker.onSeekListener?.invoke(seeker.seekPositionMs)
        }
        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.NONE
        activeSegmentId = null
    }
}
