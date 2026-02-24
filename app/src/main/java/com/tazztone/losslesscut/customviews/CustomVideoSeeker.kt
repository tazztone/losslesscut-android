package com.tazztone.losslesscut.customviews

import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.viewmodel.ClipController
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
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
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.UUID
import androidx.customview.widget.ExploreByTouchHelper
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.view.accessibility.AccessibilityEvent
import android.graphics.Rect
import androidx.core.view.ViewCompat

class CustomVideoSeeker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var seekPositionMs = 0L
    private var videoDurationMs = 0L

    var onSeekListener: ((Long) -> Unit)? = null
    var onSeekStart: (() -> Unit)? = null
    var onSeekEnd: (() -> Unit)? = null
    var onSegmentSelected: ((UUID?) -> Unit)? = null
    var onSegmentBoundsChanged: ((UUID, Long, Long, Long) -> Unit)? = null
    var onSegmentBoundsDragEnd: (() -> Unit)? = null

    private var keyframes = listOf<Long>() // milliseconds
    var isLosslessMode = true
    var isRemuxMode = false
    private var lastSnappedKeyframe: Long? = null

    private var segments = listOf<TrimSegment>()
    private var selectedSegmentId: UUID? = null

    private var waveformData: FloatArray? = null
    private val waveformPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, Color.red(colorOnSurfaceVariant), Color.green(colorOnSurfaceVariant), Color.blue(colorOnSurfaceVariant))
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
    }

    var silencePreviewRanges: List<LongRange> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val silencePreviewPaint by lazy {
        Paint().apply {
            color = 0xBB000000.toInt() // Dark semi-transparent black
            style = Paint.Style.FILL
        }
    }

    // Colors
    private val colorOnSurface: Int by lazy { resolveColor(R.attr.colorOnSurface, Color.WHITE) }
    private val colorOnSurfaceVariant: Int by lazy { resolveColor(R.attr.colorOnSurfaceVariant, Color.LTGRAY) }
    private val colorPrimary: Int by lazy { resolveColor(android.R.attr.colorPrimary, Color.BLUE) }
    private val colorAccent: Int by lazy { resolveColor(R.attr.colorAccent, Color.CYAN) }
    private val colorSegmentKeep: Int by lazy { resolveColor(R.attr.colorSegmentKeep, Color.GREEN) }
    private val colorSegmentDiscard: Int by lazy { resolveColor(R.attr.colorSegmentDiscard, Color.RED) }

    // Zoom and Pan
    private var zoomFactor = 1f
    private val minZoom = 1f
    private val maxZoom = 20f
    private var scrollOffsetX = 0f
    private val timelinePadding = 50f

    // Paints
    private val playheadPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = Color.WHITE
            strokeWidth = 5f 
        }
    }
    private val playheadTrianglePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = Color.RED 
            style = Paint.Style.FILL 
        }
    }
    private val playheadPath = android.graphics.Path()

    private val keyframePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = Color.YELLOW 
            strokeWidth = 2f 
        }
    }
    private val keepColors = arrayOf(
        Color.parseColor("#6688FF88"), // Pastel Green
        Color.parseColor("#66FF8888"), // Pastel Red
        Color.parseColor("#668888FF"), // Pastel Blue
        Color.parseColor("#66FFFF88"), // Pastel Yellow
        Color.parseColor("#6688FFFF"), // Pastel Cyan
        Color.parseColor("#66FF88FF")  // Pastel Magenta
    )
    private val keepSegmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBorderPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
    }
    private val handlePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val zoomHintPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = Color.WHITE
            textSize = 64f
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
    }
    private val zoomHintBgPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99000000")
            style = Paint.Style.FILL
        }
    }
    private val timeLabelPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
    }
    private val fingerPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
    }
    private val arrowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }
    private val arrowPath = android.graphics.Path()
    private val timeStringBuilder = StringBuilder()

    private var pinchAnimValue = 0f
    private var pinchAnimator: ValueAnimator? = null
    private val accessibilityHelper = SeekerAccessibilityHelper(this)

    private var showZoomHint = true
    private var showHandleHint = true
    private val dismissRunnable = Runnable { dismissHints() }

    private val segmentRect = RectF()

    companion object {
        private const val HINT_DISMISS_DELAY_MS = 5000L
    }

    init {
        keepSegmentPaint.color = colorSegmentKeep
        contentDescription = context.getString(R.string.video_timeline_description)
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        startPinchAnimation()
    }

    private fun resolveColor(attr: Int, default: Int): Int {
        val typedValue = android.util.TypedValue()
        val theme = context.theme
        return if (theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            default
        }
    }

    private fun startPinchAnimation() {
        pinchAnimator?.cancel()
        pinchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pinchAnimValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPinchAnimation() {
        pinchAnimator?.cancel()
        pinchAnimator = null
        pinchAnimValue = 0f
        invalidate()
    }

    fun dismissHints() {
        if (!showZoomHint && !showHandleHint && pinchAnimator == null) return
        showZoomHint = false
        showHandleHint = false
        stopPinchAnimation()
        removeCallbacks(dismissRunnable)
        invalidate()
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
                if (snapTimeMs != null && durationToWidth(kotlin.math.abs(snapTimeMs - touchTimeMs)) < 30f) {
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
                val newStart = touchTimeMs.coerceAtMost(segment.endMs - ClipController.MIN_SEGMENT_DURATION_MS)
                onSegmentBoundsChanged?.invoke(segment.id, newStart, segment.endMs, newStart)
                seekPositionMs = newStart
            } else {
                val newEnd = touchTimeMs.coerceAtLeast(segment.startMs + ClipController.MIN_SEGMENT_DURATION_MS)
                onSegmentBoundsChanged?.invoke(segment.id, segment.startMs, newEnd, newEnd)
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (showZoomHint || showHandleHint) {
            postDelayed(dismissRunnable, HINT_DISMISS_DELAY_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAutoPanning = false
        removeCallbacks(autoPanRunnable)
        removeCallbacks(dismissRunnable)
    }
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val prevZoom = zoomFactor
            val newFactor = zoomFactor * detector.scaleFactor
            if (kotlin.math.abs(newFactor - zoomFactor) > 0.01f) {
                dismissHints()
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
            
            if (!isRemuxMode) {
                // Find if a segment was tapped
                val tappedSegment = segments.find { it.action == SegmentAction.KEEP && timeMs in it.startMs..it.endMs }
                if (tappedSegment != null) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onSegmentSelected?.invoke(tappedSegment?.id)
            }
            
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
        if (videoDurationMs == 0L || width == 0) return timelinePadding
        val logicalWidthRaw = width * zoomFactor
        val availableWidth = logicalWidthRaw - 2 * timelinePadding
        return timelinePadding + (timeMs.toFloat() / videoDurationMs) * availableWidth
    }

    private fun xToTime(x: Float): Long {
        if (width == 0) return 0L
        val logicalWidthRaw = width * zoomFactor
        val availableWidth = logicalWidthRaw - 2 * timelinePadding
        if (availableWidth <= 0f) return 0L
        return (((x - timelinePadding) / availableWidth) * videoDurationMs).toLong().coerceIn(0L, videoDurationMs)
    }

    private fun durationToWidth(durationMs: Long): Float {
        if (videoDurationMs == 0L || width == 0) return 0f
        val logicalWidthRaw = width * zoomFactor
        val availableWidth = logicalWidthRaw - 2 * timelinePadding
        return (durationMs.toFloat() / videoDurationMs) * availableWidth
    }

    private fun formatTimeShort(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        
        timeStringBuilder.setLength(0)
        if (hours > 0) {
            timeStringBuilder.append(hours).append(':')
            if (minutes < 10) timeStringBuilder.append('0')
            timeStringBuilder.append(minutes).append(':')
        } else {
            if (minutes < 10) timeStringBuilder.append('0')
            timeStringBuilder.append(minutes).append(':')
        }
        if (seconds < 10) timeStringBuilder.append('0')
        timeStringBuilder.append(seconds)
        return timeStringBuilder.toString()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (videoDurationMs <= 0L) return

        canvas.save()
        canvas.translate(-scrollOffsetX, 0f)

        // Draw time labels background grid if needed, or just tick marks
        if (videoDurationMs > 0) {
            // Calculate visible time range
            val startTime = xToTime(scrollOffsetX)
            val endTime = xToTime(scrollOffsetX + width)
            val visibleDuration = endTime - startTime

            val stepMs = when {
                visibleDuration < 3000 -> 500L     // < 3s visible -> every 0.5s
                visibleDuration < 10000 -> 1000L   // < 10s visible -> every 1s
                visibleDuration < 60000 -> 5000L   // < 1m visible -> every 5s
                visibleDuration < 180000 -> 15000L // < 3m visible -> every 15s
                else -> 60000L                     // > 3m visible -> every 1m
            }

            // Snap start to nearest step
            var currentTime = (startTime / stepMs) * stepMs
            if (currentTime < startTime) currentTime += stepMs

            while (currentTime <= endTime) {
                val x = timeToX(currentTime)
                canvas.drawLine(x, height - 30f, x, height.toFloat(), keyframePaint)
                canvas.drawText(formatTimeShort(currentTime), x, height - 40f, timeLabelPaint)
                currentTime += stepMs
            }
        }

        // Draw waveform
        drawWaveform(canvas)
        drawSilencePreviews(canvas)
        drawSegments(canvas)

        // Draw keyframes
        for (kfMs in keyframes) {
            val kfX = timeToX(kfMs)
            if (kfX >= scrollOffsetX && kfX <= scrollOffsetX + width) {
                canvas.drawLine(kfX, 0f, kfX, height.toFloat() / 4, keyframePaint)
            }
        }

        // Draw Playhead
        val seekX = timeToX(seekPositionMs)
        canvas.drawLine(seekX, 0f, seekX, height.toFloat(), playheadPaint)

        // Draw Playhead Handle (Rounded Rectangle)
        val handleWidth = 40f
        val handleHeight = 50f
        val handleRect = RectF(seekX - handleWidth / 2, 0f, seekX + handleWidth / 2, handleHeight)
        canvas.drawRoundRect(handleRect, 10f, 10f, playheadTrianglePaint)

        // Draw a small arrow pointing down inside the handle
        playheadPath.reset()
        playheadPath.moveTo(seekX - 10f, 10f)
        playheadPath.lineTo(seekX + 10f, 10f)
        playheadPath.lineTo(seekX, 25f)
        playheadPath.close()
        // We can reuse playheadPaint for white arrow inside red handle
        playheadPaint.style = Paint.Style.FILL
        canvas.drawPath(playheadPath, playheadPaint)
        playheadPaint.style = Paint.Style.STROKE // restore

        canvas.restore()

        if (showZoomHint) {
            val text = context.getString(R.string.hint_pinch_to_zoom)
            val textWidth = zoomHintPaint.measureText(text)
            val textHeight = zoomHintPaint.textSize
            val py = height / 2f
            val px = width / 2f
            
            // Draw a rounded rect background
            val padding = 40f
            val bgRect = RectF(px - textWidth / 2 - padding, py - textHeight - padding / 2, px + textWidth / 2 + padding, py + padding / 2)
            canvas.drawRoundRect(bgRect, 20f, 20f, zoomHintBgPaint)
            
            canvas.drawText(text, px, py, zoomHintPaint)

            // Draw animated "fingers"
            val fingerSpacing = 60f + (pinchAnimValue * 100f)
            val fingerRadius = 15f * (1f - pinchAnimValue * 0.5f)
            fingerPaint.alpha = ((1f - pinchAnimValue) * 255).toInt()
            
            // Left finger
            canvas.drawCircle(px - fingerSpacing, py - textHeight * 1.5f, fingerRadius, fingerPaint)
            // Right finger
            canvas.drawCircle(px + fingerSpacing, py - textHeight * 1.5f, fingerRadius, fingerPaint)
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    private inner class SeekerAccessibilityHelper(view: View) : ExploreByTouchHelper(view) {
        private val VIRTUAL_ID_PLAYHEAD = 0
        private val VIRTUAL_ID_HANDLE_START = 100

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val contentX = x + scrollOffsetX
            val touchTimeMs = xToTime(contentX)
            val logicalWidth = width * zoomFactor
            val thresholdMs = if (logicalWidth > 0) ((60f / logicalWidth) * videoDurationMs).toLong() else 0L

            // Playhead check
            if (kotlin.math.abs(seekPositionMs - touchTimeMs) < thresholdMs) {
                return VIRTUAL_ID_PLAYHEAD
            }

            // Handles check
            val keepSegments = segments.filter { it.action == SegmentAction.KEEP }
            for ((index, segment) in keepSegments.withIndex()) {
                if (kotlin.math.abs(segment.startMs - touchTimeMs) < thresholdMs) return VIRTUAL_ID_HANDLE_START + (index * 2)
                if (kotlin.math.abs(segment.endMs - touchTimeMs) < thresholdMs) return VIRTUAL_ID_HANDLE_START + (index * 2) + 1
            }

            return INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.add(VIRTUAL_ID_PLAYHEAD)
            val keepSegments = segments.filter { it.action == SegmentAction.KEEP }
            for (i in keepSegments.indices) {
                virtualViewIds.add(VIRTUAL_ID_HANDLE_START + (i * 2))
                virtualViewIds.add(VIRTUAL_ID_HANDLE_START + (i * 2) + 1)
            }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            if (virtualViewId == VIRTUAL_ID_PLAYHEAD) {
                node.contentDescription = context.getString(R.string.accessibility_playhead_pos_format, formatTimeShort(seekPositionMs))
                val x = timeToX(seekPositionMs) - scrollOffsetX
                val rect = Rect((x - 20).toInt(), 0, (x + 20).toInt(), height)
                @Suppress("DEPRECATION")
                node.setBoundsInParent(rect)
                node.isFocusable = true
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            } else {
                val index = (virtualViewId - VIRTUAL_ID_HANDLE_START) / 2
                val isStart = (virtualViewId - VIRTUAL_ID_HANDLE_START) % 2 == 0
                val keepSegments = segments.filter { it.action == SegmentAction.KEEP }
                if (index < keepSegments.size) {
                    val segment = keepSegments[index]
                    val timeMs = if (isStart) segment.startMs else segment.endMs
                    val type = context.getString(if (isStart) R.string.accessibility_handle_type_start else R.string.accessibility_handle_type_end)
                    node.contentDescription = context.getString(R.string.accessibility_segment_handle_format, index + 1, type, formatTimeShort(timeMs))
                    
                    val x = timeToX(timeMs) - scrollOffsetX
                    val rect = Rect((x - 25).toInt(), height - 60, (x + 25).toInt(), height)
                    @Suppress("DEPRECATION")
                    node.setBoundsInParent(rect)
                    node.isFocusable = true
                    node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                    node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
                }
            }
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val stepMs = 1000L // 1 second step for accessibility
            if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
                val direction = if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) 1 else -1
                if (virtualViewId == VIRTUAL_ID_PLAYHEAD) {
                    seekPositionMs = (seekPositionMs + direction * stepMs).coerceIn(0, videoDurationMs)
                    onSeekListener?.invoke(seekPositionMs)
                    invalidate()
                    invalidateVirtualView(VIRTUAL_ID_PLAYHEAD)
                    return true
                } else {
                    val index = (virtualViewId - VIRTUAL_ID_HANDLE_START) / 2
                    val isStart = (virtualViewId - VIRTUAL_ID_HANDLE_START) % 2 == 0
                    val keepSegments = segments.filter { it.action == SegmentAction.KEEP }
                    if (index < keepSegments.size) {
                        val segment = keepSegments[index]
                        if (isStart) {
                             val newStart = (segment.startMs + direction * stepMs)
                                 .coerceIn(0, segment.endMs - ClipController.MIN_SEGMENT_DURATION_MS)
                            onSegmentBoundsChanged?.invoke(segment.id, newStart, segment.endMs, newStart)
                            onSegmentBoundsDragEnd?.invoke()
                        } else {
                            val newEnd = (segment.endMs + direction * stepMs)
                                .coerceIn(segment.startMs + ClipController.MIN_SEGMENT_DURATION_MS, videoDurationMs)
                            onSegmentBoundsChanged?.invoke(segment.id, segment.startMs, newEnd, newEnd)
                            onSegmentBoundsDragEnd?.invoke()
                        }
                        invalidate()
                        invalidateVirtualView(virtualViewId)
                        return true
                    }
                }
            }
            return false
        }
    }

    private fun drawHandleArrow(canvas: Canvas, cx: Float, cy: Float, isLeft: Boolean) {
        val arrowSize = 30f
        val offset = 60f + (pinchAnimValue * 30f)
        val alpha = ((1f - pinchAnimValue) * 255).toInt()
        arrowPaint.alpha = alpha
        
        val direction = if (isLeft) 1 else -1
        val tipX = cx + offset * direction
        
        arrowPath.reset()
        arrowPath.moveTo(tipX, cy - arrowSize)
        arrowPath.lineTo(tipX + arrowSize * direction, cy)
        arrowPath.lineTo(tipX, cy + arrowSize)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
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

                dismissHints()

                if (isTouchingBottom && !isRemuxMode) {
                    val keepSegments = segments.filter { it.action != SegmentAction.DISCARD }
                    var bestDist = hitTestThresholdMs + 1
                    var bestHandle: TouchTarget = TouchTarget.NONE
                    var bestSegmentId: UUID? = null

                    for (segment in keepSegments) {
                        // Check Left Handle
                        val leftDist = kotlin.math.abs(segment.startMs - touchTimeMs)
                        if (leftDist <= hitTestThresholdMs) {
                            val isBetter = leftDist < bestDist || 
                                (leftDist == bestDist && touchTimeMs > segment.startMs) // Tie-breaker: touched right of handle
                            
                            if (isBetter) {
                                bestDist = leftDist
                                bestHandle = TouchTarget.HANDLE_LEFT
                                bestSegmentId = segment.id
                            }
                        }

                        // Check Right Handle
                        val rightDist = kotlin.math.abs(segment.endMs - touchTimeMs)
                        if (rightDist <= hitTestThresholdMs) {
                            val isBetter = rightDist < bestDist || 
                                (rightDist == bestDist && touchTimeMs < segment.endMs) // Tie-breaker: touched left of handle

                            if (isBetter) {
                                bestDist = rightDist
                                bestHandle = TouchTarget.HANDLE_RIGHT
                                bestSegmentId = segment.id
                            }
                        }
                    }

                    if (bestHandle != TouchTarget.NONE) {
                        currentTouchTarget = bestHandle
                        activeSegmentId = bestSegmentId
                        onSeekStart?.invoke()
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        activeSegmentId?.let { id ->
                            if (id != selectedSegmentId) {
                                onSegmentSelected?.invoke(id)
                            }
                        }
                        return true
                    }
                }
                
                if (kotlin.math.abs(seekPositionMs - touchTimeMs) < hitTestThresholdMs) {
                    currentTouchTarget = TouchTarget.PLAYHEAD
                    onSeekStart?.invoke()
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
                            if (snapTimeMs != null && durationToWidth(kotlin.math.abs(snapTimeMs - newTimeMs)) < 30f) {
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
                            
                            // Keyframe awareness: prevent snapping to the same keyframe as the end point
                            if (isLosslessMode && keyframes.isNotEmpty()) {
                                if (newTimeMs >= segment.endMs) {
                                    newTimeMs = keyframes.filter { it < segment.endMs }.lastOrNull() ?: 0L
                                }
                            }

                            val maxAllowed = segment.endMs - ClipController.MIN_SEGMENT_DURATION_MS
                            val newStart = newTimeMs.coerceIn(minAllowed, maxAllowed)
                            
                            onSegmentBoundsChanged?.invoke(id, newStart, segment.endMs, newStart)
                            seekPositionMs = newStart
                        } else {
                            val minAllowed = segment.startMs + ClipController.MIN_SEGMENT_DURATION_MS
                            val maxAllowed = nextSegment?.startMs ?: videoDurationMs
                            
                            // Keyframe awareness: prevent snapping to the same keyframe as the start point
                            if (isLosslessMode && keyframes.isNotEmpty()) {
                                if (newTimeMs <= segment.startMs) {
                                    newTimeMs = keyframes.filter { it > segment.startMs }.firstOrNull() ?: videoDurationMs
                                }
                            }

                            val newEnd = newTimeMs.coerceIn(minAllowed, maxAllowed)

                            onSegmentBoundsChanged?.invoke(id, segment.startMs, newEnd, newEnd)
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
                    onSeekEnd?.invoke()
                    onSegmentBoundsDragEnd?.invoke()
                }
                
                if (currentTouchTarget != TouchTarget.NONE) {
                    if (currentTouchTarget == TouchTarget.PLAYHEAD) {
                        onSeekEnd?.invoke()
                    }
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
        accessibilityHelper.invalidateRoot()
        invalidate()
    }

    fun setSeekPosition(positionMs: Long) {
        this.seekPositionMs = positionMs
        
        val playheadX = timeToX(positionMs)
        if (playheadX < scrollOffsetX || playheadX > scrollOffsetX + width) {
            scrollOffsetX = (playheadX - width / 2).coerceIn(0f, maxScrollOffset())
        }
        
        accessibilityHelper.invalidateVirtualView(0) // VIRTUAL_ID_PLAYHEAD
        invalidate()
    }

    fun setWaveformData(data: FloatArray?) {
        this.waveformData = data
        invalidate()
    }

    fun resetView() {
        zoomFactor = 1f
        scrollOffsetX = 0f
        invalidate()
    }

    private fun drawWaveform(canvas: Canvas) {
        waveformData?.let { data ->
            val midY = height / 2f
            val maxAvailableHeight = midY * 0.95f
            val timelineStart = timeToX(0).toInt()
            val timelineEnd = timeToX(videoDurationMs).toInt()
            val startPx = (scrollOffsetX.toInt()).coerceAtLeast(timelineStart)
            val endPx = (scrollOffsetX + width).toInt().coerceAtMost(timelineEnd)

            for (px in startPx..endPx) {
                val timeMs = xToTime(px.toFloat())
                val bucketIdx = ((timeMs.toDouble() / videoDurationMs) * (data.size - 1))
                    .toInt().coerceIn(0, data.size - 1)
                val amp = data[bucketIdx]
                val baseAmp = if (amp < 0.02f) 0.01f else amp
                val amplifiedAmp = baseAmp * 1.00f
                val barHalf = (amplifiedAmp * maxAvailableHeight).coerceIn(2f, maxAvailableHeight)
                canvas.drawLine(px.toFloat(), midY - barHalf, px.toFloat(), midY + barHalf, waveformPaint)
            }
        }
    }

    private fun drawSilencePreviews(canvas: Canvas) {
        if (silencePreviewRanges.isEmpty()) return
        silencePreviewRanges.forEach { range ->
            val startX = timeToX(range.first)
            val endX = timeToX(range.last)
            canvas.drawRect(startX, 0f, endX, height.toFloat(), silencePreviewPaint)
        }
    }

    private fun drawSegments(canvas: Canvas) {
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

            canvas.drawLine(startX, 0f, startX, height.toFloat(), handlePaint)
            canvas.drawCircle(startX, height.toFloat() - 25f, 25f, handlePaint)
            if (showHandleHint) drawHandleArrow(canvas, startX, height.toFloat() - 25f, isLeft = true)
            canvas.drawLine(endX, 0f, endX, height.toFloat(), handlePaint)
            canvas.drawCircle(endX, height.toFloat() - 25f, 25f, handlePaint)
            if (showHandleHint) drawHandleArrow(canvas, endX, height.toFloat() - 25f, isLeft = false)
            if (segment.id == selectedSegmentId) canvas.drawRect(segmentRect, selectedBorderPaint)
        }
    }
}
