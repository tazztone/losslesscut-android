package com.tazztone.losslesscut.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.SegmentAction
import com.tazztone.losslesscut.TrimSegment
import java.util.UUID

class CustomVideoSeeker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var seekPositionMs = 0L
    private var videoDurationMs = 0L

    var onSeekListener: ((Long) -> Unit)? = null
    var onSegmentSelected: ((UUID?) -> Unit)? = null
    var onSegmentBoundsChanged: ((UUID, Long, Long) -> Unit)? = null
    var onSegmentBoundsDragEnd: (() -> Unit)? = null

    private var keyframes = listOf<Long>() // milliseconds
    var isLosslessMode = true
    private var lastSnappedKeyframe: Long? = null

    private var segments = listOf<TrimSegment>()
    private var selectedSegmentId: UUID? = null

    // Zoom and Pan
    private var zoomFactor = 1f
    private val minZoom = 1f
    private val maxZoom = 20f
    private var scrollOffsetX = 0f

    // Paints
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 5f }
    private val playheadTrianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.FILL }
    private val playheadPath = android.graphics.Path()

    private val keyframePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW; strokeWidth = 2f }
    private val keepColors = arrayOf(
        Color.parseColor("#6688FF88"), // Pastel Green
        Color.parseColor("#66FF8888"), // Pastel Red
        Color.parseColor("#668888FF"), // Pastel Blue
        Color.parseColor("#66FFFF88"), // Pastel Yellow
        Color.parseColor("#6688FFFF"), // Pastel Cyan
        Color.parseColor("#66FF88FF")  // Pastel Magenta
    )
    private val keepSegmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
    private val zoomHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#80FFFFFF")
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }

    private var showZoomHint = true

    private val segmentRect = RectF()

    init {
        keepSegmentPaint.color = keepColors[0]
        contentDescription = context.getString(R.string.video_timeline_description)
    }

    // Hit testing
    enum class TouchTarget { NONE, HANDLE_LEFT, HANDLE_RIGHT, PLAYHEAD }
    private var currentTouchTarget = TouchTarget.NONE
    private var activeSegmentId: UUID? = null

    // Auto-Pan
    private var isAutoPanning = false
    private var lastTouchX = 0f
    private val autoPanRunnable = object : Runnable {
        override fun run() {
            if (!isAutoPanning || currentTouchTarget == TouchTarget.NONE || activeSegmentId == null) return
            
            val segment = segments.find { it.id == activeSegmentId } ?: return
            
            val panSpeed = 30f / zoomFactor
            if (currentTouchTarget == TouchTarget.HANDLE_RIGHT) {
                scrollOffsetX = (scrollOffsetX + panSpeed).coerceIn(0f, maxScrollOffset())
            } else if (currentTouchTarget == TouchTarget.HANDLE_LEFT) {
                scrollOffsetX = (scrollOffsetX - panSpeed).coerceIn(0f, maxScrollOffset())
            }
            
            val contentX = lastTouchX + scrollOffsetX
            var touchTimeMs = xToTime(contentX)
            
            if (isLosslessMode && keyframes.isNotEmpty()) {
                val snapTimeMs = keyframes.minByOrNull { kotlin.math.abs(it - touchTimeMs) }
                if (snapTimeMs != null && timeToX(kotlin.math.abs(snapTimeMs - touchTimeMs)) < 30f) {
                    touchTimeMs = snapTimeMs
                    if (lastSnappedKeyframe != snapTimeMs) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastSnappedKeyframe = snapTimeMs
                    }
                } else {
                    lastSnappedKeyframe = null
                }
            }

            if (currentTouchTarget == TouchTarget.HANDLE_LEFT) {
                val newStart = touchTimeMs.coerceAtMost(segment.endMs - 100)
                onSegmentBoundsChanged?.invoke(segment.id, newStart, segment.endMs)
                seekPositionMs = newStart
            } else {
                val newEnd = touchTimeMs.coerceAtLeast(segment.startMs + 100)
                onSegmentBoundsChanged?.invoke(segment.id, segment.startMs, newEnd)
                seekPositionMs = newEnd
            }
            invalidate()
            
            if ((currentTouchTarget == TouchTarget.HANDLE_RIGHT && scrollOffsetX < maxScrollOffset()) ||
                (currentTouchTarget == TouchTarget.HANDLE_LEFT && scrollOffsetX > 0f)) {
                postDelayed(this, 16)
            } else {
                isAutoPanning = false
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAutoPanning = false
        removeCallbacks(autoPanRunnable)
    }
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val prevZoom = zoomFactor
            val newFactor = zoomFactor * detector.scaleFactor
            if (kotlin.math.abs(newFactor - zoomFactor) > 0.01f) {
                showZoomHint = false
            }
            zoomFactor = newFactor.coerceIn(1f, 20f)
            
            // Adjust scroll offset to zoom around the focal point
            val focusX = detector.focusX
            val contentFocusX = (scrollOffsetX + focusX) / prevZoom
            scrollOffsetX = (contentFocusX * zoomFactor - focusX).coerceIn(0f, maxScrollOffset())
            
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (currentTouchTarget == TouchTarget.NONE) {
                scrollOffsetX = (scrollOffsetX + distanceX).coerceIn(0f, maxScrollOffset())
                invalidate()
                return true
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val contentX = e.x + scrollOffsetX
            val timeMs = xToTime(contentX)
            
            // Find if a segment was tapped
            val tappedSegment = segments.find { it.action == SegmentAction.KEEP && timeMs in it.startMs..it.endMs }
            onSegmentSelected?.invoke(tappedSegment?.id)
            
            seekPositionMs = timeMs
            onSeekListener?.invoke(seekPositionMs)

            invalidate()
            return true
        }
    })

    private fun maxScrollOffset(): Float {
        val logicalWidth = width * zoomFactor
        return (logicalWidth - width).coerceAtLeast(0f)
    }

    private fun timeToX(timeMs: Long): Float {
        if (videoDurationMs == 0L) return 0f
        return (timeMs.toFloat() / videoDurationMs) * (width * zoomFactor)
    }

    private fun xToTime(x: Float): Long {
        val logicalWidth = width * zoomFactor
        if (logicalWidth == 0f) return 0L
        return ((x / logicalWidth) * videoDurationMs).toLong().coerceIn(0L, videoDurationMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (videoDurationMs <= 0L) return

        canvas.save()
        canvas.translate(-scrollOffsetX, 0f)

        // Draw Segments
        var keepSegmentIndex = 0
        for (segment in segments) {
            if (segment.action == SegmentAction.DISCARD) continue
            
            val startX = timeToX(segment.startMs)
            val endX = timeToX(segment.endMs)
            segmentRect.set(startX, 0f, endX, height.toFloat())
            
            val color = keepColors[keepSegmentIndex % keepColors.size]
            keepSegmentPaint.color = color
            canvas.drawRect(segmentRect, keepSegmentPaint)
            keepSegmentIndex++

            // Draw handles for ALL keep segments
            canvas.drawLine(startX, 0f, startX, height.toFloat(), handlePaint)
            canvas.drawCircle(startX, height.toFloat() - 25f, 25f, handlePaint)

            canvas.drawLine(endX, 0f, endX, height.toFloat(), handlePaint)
            canvas.drawCircle(endX, height.toFloat() - 25f, 25f, handlePaint)

            // Draw highlight border if selected
            if (segment.id == selectedSegmentId) {
                canvas.drawRect(segmentRect, selectedBorderPaint)
            }
        }

        // Draw keyframes
        for (kfMs in keyframes) {
            val kfX = timeToX(kfMs)
            if (kfX >= scrollOffsetX && kfX <= scrollOffsetX + width) {
                canvas.drawLine(kfX, 0f, kfX, height.toFloat() / 2, keyframePaint)
            }
        }

        // Draw Playhead
        val seekX = timeToX(seekPositionMs)
        canvas.drawLine(seekX, 0f, seekX, height.toFloat(), playheadPaint)

        playheadPath.reset()
        playheadPath.moveTo(seekX - 20f, 0f)
        playheadPath.lineTo(seekX + 20f, 0f)
        playheadPath.lineTo(seekX, 30f)
        playheadPath.close()
        canvas.drawPath(playheadPath, playheadTrianglePaint)

        canvas.restore()

        if (showZoomHint) {
            canvas.drawText(context.getString(R.string.hint_pinch_to_zoom), width / 2f, height / 2f, zoomHintPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress) return true

        gestureDetector.onTouchEvent(event)

        lastTouchX = event.x
        val contentX = event.x + scrollOffsetX
        val touchTimeMs = xToTime(contentX)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentTouchTarget = TouchTarget.NONE
                val logicalWidth = width * zoomFactor
                val hitTestThresholdMs = if (logicalWidth > 0) ((60f / logicalWidth) * videoDurationMs).toLong() else 0L

                // Only allow dragging handles if touched near the bottom where the circle is
                val isTouchingBottom = event.y > height - 80f

                showZoomHint = false // hide hint on any interaction

                if (isTouchingBottom) {
                    val keepSegments = segments.filter { it.action != SegmentAction.DISCARD }
                    for (segment in keepSegments) {
                        if (kotlin.math.abs(segment.startMs - touchTimeMs) < hitTestThresholdMs) {
                            currentTouchTarget = TouchTarget.HANDLE_LEFT
                            activeSegmentId = segment.id
                            if (segment.id != selectedSegmentId) {
                                onSegmentSelected?.invoke(segment.id)
                            }
                            return true
                        } else if (kotlin.math.abs(segment.endMs - touchTimeMs) < hitTestThresholdMs) {
                            currentTouchTarget = TouchTarget.HANDLE_RIGHT
                            activeSegmentId = segment.id
                            if (segment.id != selectedSegmentId) {
                                onSegmentSelected?.invoke(segment.id)
                            }
                            return true
                        }
                    }
                }
                
                if (kotlin.math.abs(seekPositionMs - touchTimeMs) < hitTestThresholdMs) {
                    currentTouchTarget = TouchTarget.PLAYHEAD
                    return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTouchTarget == TouchTarget.HANDLE_LEFT || currentTouchTarget == TouchTarget.HANDLE_RIGHT) {
                    activeSegmentId?.let { id ->
                        val segment = segments.find { it.id == id } ?: return@let
                        var newTimeMs = touchTimeMs

                        if (isLosslessMode && keyframes.isNotEmpty()) {
                            val snapTimeMs = keyframes.minByOrNull { kotlin.math.abs(it - newTimeMs) }
                            if (snapTimeMs != null && timeToX(kotlin.math.abs(snapTimeMs - newTimeMs)) < 30f) {
                                newTimeMs = snapTimeMs
                                if (lastSnappedKeyframe != snapTimeMs) {
                                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    lastSnappedKeyframe = snapTimeMs
                                }
                            } else {
                                lastSnappedKeyframe = null
                            }
                        }

                        val keepSegments = segments.filter { it.action != SegmentAction.DISCARD }.sortedBy { it.startMs }
                        val currentIndex = keepSegments.indexOfFirst { it.id == segment.id }
                        val prevSegment = if (currentIndex > 0) keepSegments[currentIndex - 1] else null
                        val nextSegment = if (currentIndex >= 0 && currentIndex < keepSegments.size - 1) keepSegments[currentIndex + 1] else null

                        if (currentTouchTarget == TouchTarget.HANDLE_LEFT) {
                            val minAllowed = prevSegment?.endMs ?: 0L
                            val newStart = newTimeMs.coerceIn(minAllowed, segment.endMs - 100)
                            onSegmentBoundsChanged?.invoke(id, newStart, segment.endMs)
                            seekPositionMs = newStart
                        } else {
                            val maxAllowed = nextSegment?.startMs ?: videoDurationMs
                            val newEnd = newTimeMs.coerceIn(segment.startMs + 100, maxAllowed)
                            onSegmentBoundsChanged?.invoke(id, segment.startMs, newEnd)
                            seekPositionMs = newEnd
                        }
                    }

                    // Auto-pan check
                    val edgeThreshold = 100f
                    if (event.x > width - edgeThreshold && currentTouchTarget == TouchTarget.HANDLE_RIGHT) {
                        if (!isAutoPanning) {
                            isAutoPanning = true
                            post(autoPanRunnable)
                        }
                    } else if (event.x < edgeThreshold && currentTouchTarget == TouchTarget.HANDLE_LEFT) {
                        if (!isAutoPanning) {
                            isAutoPanning = true
                            post(autoPanRunnable)
                        }
                    } else {
                        isAutoPanning = false
                        removeCallbacks(autoPanRunnable)
                    }

                } else if (currentTouchTarget == TouchTarget.PLAYHEAD) {
                    seekPositionMs = touchTimeMs
                    onSeekListener?.invoke(seekPositionMs)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isAutoPanning = false
                removeCallbacks(autoPanRunnable)

                if (currentTouchTarget == TouchTarget.HANDLE_LEFT || currentTouchTarget == TouchTarget.HANDLE_RIGHT) {
                    onSegmentBoundsDragEnd?.invoke()
                }
                
                if (currentTouchTarget != TouchTarget.NONE) {
                    onSeekListener?.invoke(seekPositionMs)
                }
                currentTouchTarget = TouchTarget.NONE
                activeSegmentId = null
            }
        }
        return true
    }

    fun setVideoDuration(durationMs: Long) {
        if (durationMs > 0) {
            videoDurationMs = durationMs
            invalidate()
        }
    }

    fun setKeyframes(framesMs: List<Long>) {
        this.keyframes = framesMs.sorted()
        invalidate()
    }

    fun setSegments(segs: List<TrimSegment>, selectedId: UUID?) {
        this.segments = segs
        this.selectedSegmentId = selectedId
        invalidate()
    }

    fun setSeekPosition(positionMs: Long) {
        this.seekPositionMs = positionMs
        
        val playheadX = timeToX(positionMs)
        if (playheadX < scrollOffsetX || playheadX > scrollOffsetX + width) {
            scrollOffsetX = (playheadX - width / 2).coerceIn(0f, maxScrollOffset())
        }
        
        invalidate()
    }
}
