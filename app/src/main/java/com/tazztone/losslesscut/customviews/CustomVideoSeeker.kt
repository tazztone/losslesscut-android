package com.tazztone.losslesscut.customviews

import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.viewmodel.ClipController
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.UUID
import androidx.core.view.ViewCompat

class CustomVideoSeeker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    internal var seekPositionMs = 0L
    internal var videoDurationMs = 0L

    var onSeekListener: ((Long) -> Unit)? = null
    var onSeekStart: (() -> Unit)? = null
    var onSeekEnd: (() -> Unit)? = null
    var onSegmentSelected: ((UUID?) -> Unit)? = null
    var onSegmentBoundsChanged: ((UUID, Long, Long, Long) -> Unit)? = null
    var onSegmentBoundsDragEnd: (() -> Unit)? = null

    internal var keyframes = listOf<Long>() // milliseconds
    var isLosslessMode = true
    var isRemuxMode = false

    internal var segments = listOf<TrimSegment>()
    internal var selectedSegmentId: UUID? = null

    var segmentsVisible: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                accessibilityHelper.invalidateRoot()
                invalidate()
            }
        }

    var playheadVisible: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                accessibilityHelper.invalidateVirtualView(0) // VIRTUAL_ID_PLAYHEAD
                invalidate()
            }
        }

    // Silence Detection Preview
    enum class SilenceVisualMode { NONE, PADDING, MIN_SILENCE, MIN_SEGMENT }
    var activeSilenceVisualMode: SilenceVisualMode = SilenceVisualMode.NONE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var noiseThresholdPreview: Float? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var internalSilencePreviewRanges = listOf<LongRange>()
    var silencePreviewRanges: List<LongRange>
        get() = internalSilencePreviewRanges
        set(value) {
            internalSilencePreviewRanges = value
            invalidate()
        }

    var rawSilenceResult: SilenceDetectionUseCase.DetectionResult? = null
        set(value) {
            field = value
            invalidate()
        }

    // Hit Testing
    enum class TouchTarget { NONE, HANDLE_LEFT, HANDLE_RIGHT, PLAYHEAD }
    internal var currentTouchTarget = TouchTarget.NONE

    internal var waveformData: FloatArray? = null

    // Colors
    private val colorOnSurface: Int by lazy { resolveColor(R.attr.colorOnSurface, Color.WHITE) }
    private val colorOnSurfaceVariant: Int by lazy { resolveColor(R.attr.colorOnSurfaceVariant, Color.LTGRAY) }
    private val colorPrimary: Int by lazy { resolveColor(android.R.attr.colorPrimary, Color.BLUE) }
    private val colorAccent: Int by lazy { resolveColor(R.attr.colorAccent, Color.CYAN) }
    private val colorSegmentKeep: Int by lazy { resolveColor(R.attr.colorSegmentKeep, Color.GREEN) }
    private val colorSegmentDiscard: Int by lazy { resolveColor(R.attr.colorSegmentDiscard, Color.RED) }

    // Zoom and Pan
    internal var zoomFactor = 1f
    internal var scrollOffsetX = 0f
    private val timelinePadding = 50f

    internal val timeStringBuilder = StringBuilder()

    internal var pinchAnimValue = 0f
    private var pinchAnimator: ValueAnimator? = null
    private val accessibilityHelper = SeekerAccessibilityHelper(this)
    private val touchHandler = SeekerTouchHandler(this)
    private val seekerRenderer = SeekerRenderer(this)

    internal var showZoomHint = true
    internal var showHandleHint = true
    private val dismissRunnable = Runnable { dismissHints() }


    companion object {
        private const val HINT_DISMISS_DELAY_MS = 5000L
    }

    init {
        seekerRenderer.updateWaveformColor(colorOnSurfaceVariant)
        seekerRenderer.updateAccentColor(colorAccent)
        seekerRenderer.keepSegmentPaint.color = colorSegmentKeep
        contentDescription = context.getString(R.string.video_timeline_description)
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        startPinchAnimation()
    }

    internal fun resolveColor(attr: Int, default: Int): Int {
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

    internal fun dismissHints() {
        if (!showZoomHint && !showHandleHint && pinchAnimator == null) return
        showZoomHint = false
        showHandleHint = false
        stopPinchAnimation()
        removeCallbacks(dismissRunnable)
        invalidate()
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (showZoomHint || showHandleHint) {
            postDelayed(dismissRunnable, HINT_DISMISS_DELAY_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        touchHandler.isAutoPanning = false
        removeCallbacks(touchHandler.autoPanRunnable)
        removeCallbacks(dismissRunnable)
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event)
    }

    internal fun maxScrollOffset(): Float {
        val logicalWidth = width.toFloat() * zoomFactor
        return (logicalWidth - width).coerceAtLeast(0f)
    }

    internal fun timeToX(timeMs: Long): Float {
        if (videoDurationMs == 0L || width == 0) return timelinePadding
        val logicalWidthRaw = width.toFloat() * zoomFactor
        val availableWidth = logicalWidthRaw - 2 * timelinePadding
        return timelinePadding + (timeMs.toFloat() / videoDurationMs) * availableWidth
    }

    internal fun xToTime(x: Float): Long {
        val availableWidth = (width.toFloat() * zoomFactor) - (2 * timelinePadding)
        if (width == 0 || availableWidth <= 0f) return 0L
        return (((x - timelinePadding) / availableWidth) * videoDurationMs).toLong()
            .coerceIn(0L, videoDurationMs)
    }

    internal fun durationToWidth(durationMs: Long): Float {
        if (videoDurationMs == 0L || width == 0) return 0f
        val logicalWidthRaw = width.toFloat() * zoomFactor
        val availableWidth = logicalWidthRaw - 2 * timelinePadding
        return (durationMs.toFloat() / videoDurationMs) * availableWidth
    }

    internal fun formatTimeShort(ms: Long): String {
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

            seekerRenderer.drawTimeLabels(canvas, startTime, endTime)
        }

        // Draw waveform
        seekerRenderer.drawWaveform(canvas)
        seekerRenderer.drawSilencePreviews(canvas)
        if (segmentsVisible) {
            seekerRenderer.drawSegments(canvas)
        }

        // Draw keyframes
        for (kfMs in keyframes) {
            val kfX = timeToX(kfMs)
            if (kfX >= scrollOffsetX && kfX <= scrollOffsetX + width) {
                canvas.drawLine(kfX, 0f, kfX, height.toFloat() / 4, seekerRenderer.keyframePaint)
            }
        }

        // Draw Playhead
        if (playheadVisible) {
            seekerRenderer.drawPlayhead(canvas)
        }

        canvas.restore()

        if (showZoomHint) {
            seekerRenderer.drawZoomHint(canvas)
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
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

}
