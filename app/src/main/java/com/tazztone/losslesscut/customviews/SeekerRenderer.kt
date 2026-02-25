package com.tazztone.losslesscut.customviews

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase

/**
 * Extracted rendering logic for CustomVideoSeeker to reduce class size.
 */
internal class SeekerRenderer(private val seeker: CustomVideoSeeker) {

    companion object {
        private const val WAVEFORM_STROKE_WIDTH = 2f
        private const val SILENCE_PREVIEW_COLOR = 0xBB000000.toInt()
        private const val THRESHOLD_MASK_COLOR = 0x6600FFFF.toInt()
        private const val DROPPED_SILENCE_COLOR = 0x88FF0000.toInt()
        private const val BRIDGED_NOISE_COLOR = 0x88FFFF00.toInt()
        private const val PLAYHEAD_STROKE_WIDTH = 5f
        private const val KEYFRAME_STROKE_WIDTH = 2f
        private const val SELECTED_BORDER_WIDTH = 8f
        private const val HANDLE_STROKE_WIDTH = 10f
        private const val ZOOM_HINT_TEXT_SIZE = 64f
        private const val ZOOM_HINT_SHADOW_RADIUS = 4f
        private const val ZOOM_HINT_SHADOW_OFFSET_X = 2f
        private const val ZOOM_HINT_SHADOW_OFFSET_Y = 2f
        private const val ZOOM_HINT_BG_COLOR = "#99000000"
        private const val TIME_LABEL_TEXT_SIZE = 30f
        private const val FINGER_STROKE_WIDTH = 4f
        private const val HANDLE_CIRCLE_RADIUS = 25f
        private const val HANDLE_CIRCLE_OFFSET_Y = 25f
        private const val TIME_TICK_HEIGHT = 30f
        private const val TIME_TEXT_OFFSET_Y = 40f
        private const val PLAYHEAD_ARROW_SIDE = 10f
        private const val PLAYHEAD_ARROW_TIP_Y = 25f
        private const val PLAYHEAD_ROUND_RECT_RADIUS = 10f
        private const val ARROW_SIZE = 30f
        private const val ARROW_OFFSET_BASE = 60f
        private const val ARROW_OFFSET_ANIM = 30f
        private const val ALPHA_MAX = 255
        private const val WAVEFORM_HEIGHT_SCALE = 0.95f
        private const val WAVEFORM_MIN_AMP_THRESHOLD = 0.02f
        private const val WAVEFORM_MIN_AMP_VALUE = 0.01f
        private const val WAVEFORM_BAR_MIN_HALF = 2f
        private const val PLAYHEAD_HANDLE_WIDTH = 40f
        private const val PLAYHEAD_HANDLE_HEIGHT = 50f
        private const val ZOOM_HINT_PADDING = 40f
        private const val ZOOM_HINT_BG_RADIUS = 20f
        private const val ZOOM_HINT_FINGER_RADIUS = 15f
        private const val ZOOM_HINT_FINGER_SPACING_BASE = 60f
        private const val ZOOM_HINT_FINGER_SPACING_ANIM = 100f
        private const val ZOOM_HINT_FINGER_RADIUS_SHRINK = 0.5f
        private const val ZOOM_HINT_Y_OFFSET_SCALE = 1.5f
        private const val CACHE_TILE_SIZE = 2048
        private const val DURATION_3S = 3000L
        private const val DURATION_10S = 10000L
        private const val DURATION_60S = 60000L
        private const val DURATION_180S = 180000L
        private const val STEP_500MS = 500L
        private const val STEP_1S = 1000L
        private const val STEP_5S = 5000L
        private const val STEP_15S = 15000L
        private const val STEP_60S = 60000L
    }

    private val segmentRect = RectF()
    private val playheadPath = Path()
    private val arrowPath = Path()

    // Paints
    val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = WAVEFORM_STROKE_WIDTH
        style = Paint.Style.STROKE
    }

    val silencePreviewPaint = Paint().apply {
        color = SILENCE_PREVIEW_COLOR
        style = Paint.Style.FILL
    }

    val thresholdMaskPaint = Paint().apply {
        color = THRESHOLD_MASK_COLOR
        style = Paint.Style.FILL
    }

    val droppedSilencePaint = Paint().apply {
        color = DROPPED_SILENCE_COLOR
        style = Paint.Style.FILL
    }

    val bridgedNoisePaint = Paint().apply {
        color = BRIDGED_NOISE_COLOR
        style = Paint.Style.FILL
    }

    val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.WHITE
        strokeWidth = PLAYHEAD_STROKE_WIDTH 
    }

    val playheadTrianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.RED 
        style = Paint.Style.FILL 
    }

    val keyframePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.YELLOW 
        strokeWidth = KEYFRAME_STROKE_WIDTH 
    }

    val keepSegmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = SELECTED_BORDER_WIDTH
    }

    val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = HANDLE_STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    val zoomHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.WHITE
        textSize = ZOOM_HINT_TEXT_SIZE
        textAlign = Paint.Align.CENTER
        setShadowLayer(ZOOM_HINT_SHADOW_RADIUS, ZOOM_HINT_SHADOW_OFFSET_X, ZOOM_HINT_SHADOW_OFFSET_Y, Color.BLACK)
    }

    val zoomHintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(ZOOM_HINT_BG_COLOR)
        style = Paint.Style.FILL
    }

    val timeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = TIME_LABEL_TEXT_SIZE
        textAlign = Paint.Align.CENTER
    }

    private val ghostRenderer = SeekerGhostRenderer(seeker, this)

    val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = FINGER_STROKE_WIDTH
    }

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val keepColors = arrayOf(
        Color.parseColor("#6688FF88"),
        Color.parseColor("#66FF8888"),
        Color.parseColor("#668888FF"),
        Color.parseColor("#66FFFF88"),
        Color.parseColor("#6688FFFF"),
        Color.parseColor("#66FF88FF")
    )

    fun updateWaveformColor(color: Int) {
        waveformPaint.color = Color.argb(85, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun updateAccentColor(color: Int) {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        thresholdMaskPaint.color = Color.argb(0x66, r, g, b)
        droppedSilencePaint.color = Color.argb(0x88, r, g, b)
        bridgedNoisePaint.color = Color.argb(0x88, r, g, b)
    }

    private val waveformCache = WaveformCache()

    fun drawWaveform(canvas: Canvas) {
        seeker.waveformData?.let {
            val midY = seeker.height / 2f
            val maxAvailableHeight = midY * WAVEFORM_HEIGHT_SCALE

            seeker.noiseThresholdPreview?.let { threshold ->
                val thresholdH = (threshold * maxAvailableHeight).coerceIn(1f, maxAvailableHeight)
                canvas.drawRect(
                    seeker.scrollOffsetX, midY - thresholdH,
                    seeker.scrollOffsetX + seeker.width, midY + thresholdH,
                    thresholdMaskPaint
                )
            }

            waveformCache.draw(canvas)
        }
    }

    private inner class WaveformCache {
        // Keep ~50MB of bitmaps (2048 * 200 * 4 ~= 1.6MB per tile -> 30 tiles)
        private val cache = android.util.LruCache<Int, android.graphics.Bitmap>(30)

        private var lastData: FloatArray? = null
        private var lastZoom: Float = -1f
        private var lastHeight: Int = -1
        private var lastWidth: Int = -1
        private var lastColor: Int = -1

        fun draw(canvas: Canvas) {
            val currentData = seeker.waveformData ?: return
            val currentZoom = seeker.zoomFactor
            val currentHeight = seeker.height
            val currentWidth = seeker.width
            val currentColor = waveformPaint.color

            if (shouldInvalidate(currentData, currentZoom, currentHeight, currentWidth, currentColor)) {
                cache.evictAll()
                lastData = currentData
                lastZoom = currentZoom
                lastHeight = currentHeight
                lastWidth = currentWidth
                lastColor = currentColor
            }

            val timelineStart = seeker.timeToX(0).toInt()
            val timelineEnd = seeker.timeToX(seeker.videoDurationMs).toInt()

            val visibleStart = seeker.scrollOffsetX.toInt()
            val visibleEnd = (seeker.scrollOffsetX + seeker.width).toInt()

            val drawStart = visibleStart.coerceAtLeast(timelineStart)
            val drawEnd = visibleEnd.coerceAtMost(timelineEnd)

            if (drawStart >= drawEnd) return

            val startTile = drawStart / CACHE_TILE_SIZE
            val endTile = drawEnd / CACHE_TILE_SIZE

            for (i in startTile..endTile) {
                var bitmap = cache.get(i)
                if (bitmap == null || bitmap.isRecycled) {
                    bitmap = generateTile(i, currentData, timelineStart, timelineEnd)
                    cache.put(i, bitmap)
                }
                canvas.drawBitmap(bitmap, (i * CACHE_TILE_SIZE).toFloat(), 0f, null)
            }
        }

        private fun shouldInvalidate(
            data: FloatArray,
            zoom: Float,
            h: Int,
            w: Int,
            color: Int
        ): Boolean {
            return lastData !== data ||
                lastZoom != zoom ||
                lastHeight != h ||
                lastWidth != w ||
                lastColor != color
        }

        private fun generateTile(index: Int, data: FloatArray, timelineStart: Int, timelineEnd: Int): android.graphics.Bitmap {
            val height = seeker.height.coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(CACHE_TILE_SIZE, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val tileStartPx = index * CACHE_TILE_SIZE
            val tileEndPx = (index + 1) * CACHE_TILE_SIZE

            // Translate so we can draw using world coordinates relative to the tile start
            canvas.translate(-tileStartPx.toFloat(), 0f)

            // Optimized math logic
            val midY = height / 2f
            val maxAvailableHeight = midY * WAVEFORM_HEIGHT_SCALE

            // Calculate effective range for this tile
            val loopStart = tileStartPx.coerceAtLeast(timelineStart)
            val loopEnd = (tileEndPx - 1).coerceAtMost(timelineEnd)

            if (loopStart > loopEnd) return bitmap

            // Math optimization: map pixel to index directly
            // availableWidth = timelineEnd - timelineStart
            // bucketIdx = ((px - timelineStart) / availableWidth) * (data.size - 1)
            val availableWidth = (timelineEnd - timelineStart).toFloat()
            if (availableWidth <= 0) return bitmap

            val samplesPerPixel = (data.size - 1) / availableWidth

            for (px in loopStart..loopEnd) {
                val bucketIdx = ((px - timelineStart) * samplesPerPixel).toInt().coerceIn(0, data.size - 1)
                val amp = data[bucketIdx]
                val amplifiedAmp = if (amp < WAVEFORM_MIN_AMP_THRESHOLD) WAVEFORM_MIN_AMP_VALUE else amp
                val barHalf = (amplifiedAmp * maxAvailableHeight).coerceIn(WAVEFORM_BAR_MIN_HALF, maxAvailableHeight)

                canvas.drawLine(px.toFloat(), midY - barHalf, px.toFloat(), midY + barHalf, waveformPaint)
            }

            return bitmap
        }
    }

    fun drawSilencePreviews(canvas: Canvas) {
        val result = seeker.rawSilenceResult
        if (result == null) {
            drawSimpleSilencePreviews(canvas, seeker.silencePreviewRanges)
            return
        }
        when (seeker.activeSilenceVisualMode) {
            CustomVideoSeeker.SilenceVisualMode.NONE -> drawSimpleSilencePreviews(canvas, result.finalRanges)
            else -> ghostRenderer.drawSpecialSilenceOverlay(canvas, result)
        }
    }

    internal fun drawSimpleSilencePreviews(canvas: Canvas, ranges: List<LongRange>) {
        ranges.forEach { range ->
            val startX = seeker.timeToX(range.first)
            val endX = seeker.timeToX(range.last)
            canvas.drawRect(startX, 0f, endX, seeker.height.toFloat(), silencePreviewPaint)
        }
    }


    fun drawSegments(canvas: Canvas) {
        var keepSegmentIndex = 0
        for (segment in seeker.segments) {
            if (segment.action == SegmentAction.DISCARD) continue
            val startX = seeker.timeToX(segment.startMs)
            val endX = seeker.timeToX(segment.endMs)
            segmentRect.set(startX, 0f, endX, seeker.height.toFloat())
            keepSegmentPaint.color = keepColors[keepSegmentIndex % keepColors.size]
            canvas.drawRect(segmentRect, keepSegmentPaint)
            keepSegmentIndex++

            canvas.drawLine(startX, 0f, startX, seeker.height.toFloat(), handlePaint)
            val circleY = seeker.height.toFloat() - HANDLE_CIRCLE_OFFSET_Y
            canvas.drawCircle(startX, circleY, HANDLE_CIRCLE_RADIUS, handlePaint)
            if (seeker.showHandleHint) drawHandleArrow(canvas, startX, circleY, true)
            
            canvas.drawLine(endX, 0f, endX, seeker.height.toFloat(), handlePaint)
            canvas.drawCircle(endX, circleY, HANDLE_CIRCLE_RADIUS, handlePaint)
            if (seeker.showHandleHint) drawHandleArrow(canvas, endX, circleY, false)
            
            if (segment.id == seeker.selectedSegmentId) canvas.drawRect(segmentRect, selectedBorderPaint)
        }
    }

    fun drawTimeLabels(canvas: Canvas, startTime: Long, endTime: Long) {
        val visibleDuration = endTime - startTime
        val stepMs = getLabelStepMs(visibleDuration)
        var currentTime = (startTime / stepMs) * stepMs
        if (currentTime < startTime) currentTime += stepMs

        while (currentTime <= endTime) {
            val x = seeker.timeToX(currentTime)
            canvas.drawLine(x, seeker.height - TIME_TICK_HEIGHT, x, seeker.height.toFloat(), keyframePaint)
            canvas.drawText(seeker.formatTimeShort(currentTime), x, seeker.height - TIME_TEXT_OFFSET_Y, timeLabelPaint)
            currentTime += stepMs
        }
    }

    private fun getLabelStepMs(visibleDuration: Long): Long = when {
        visibleDuration < DURATION_3S -> STEP_500MS
        visibleDuration < DURATION_10S -> STEP_1S
        visibleDuration < DURATION_60S -> STEP_5S
        visibleDuration < DURATION_180S -> STEP_15S
        else -> STEP_60S
    }

    fun drawPlayhead(canvas: Canvas) {
        val seekX = seeker.timeToX(seeker.seekPositionMs)
        canvas.drawLine(seekX, 0f, seekX, seeker.height.toFloat(), playheadPaint)

        val handleRect = RectF(
            seekX - PLAYHEAD_HANDLE_WIDTH / 2, 0f,
            seekX + PLAYHEAD_HANDLE_WIDTH / 2, PLAYHEAD_HANDLE_HEIGHT
        )
        canvas.drawRoundRect(handleRect, PLAYHEAD_ROUND_RECT_RADIUS, PLAYHEAD_ROUND_RECT_RADIUS, playheadTrianglePaint)

        playheadPath.reset()
        playheadPath.moveTo(seekX - PLAYHEAD_ARROW_SIDE, PLAYHEAD_ARROW_SIDE)
        playheadPath.lineTo(seekX + PLAYHEAD_ARROW_SIDE, PLAYHEAD_ARROW_SIDE)
        playheadPath.lineTo(seekX, PLAYHEAD_ARROW_TIP_Y)
        playheadPath.close()
        playheadPaint.style = Paint.Style.FILL
        canvas.drawPath(playheadPath, playheadPaint)
        playheadPaint.style = Paint.Style.STROKE
    }

    fun drawZoomHint(canvas: Canvas) {
        val text = seeker.context.getString(R.string.hint_pinch_to_zoom)
        val textWidth = zoomHintPaint.measureText(text)
        val textHeight = zoomHintPaint.textSize
        val py = seeker.height / 2f
        val px = seeker.width / 2f
        
        // Draw a rounded rect background
        val padding = ZOOM_HINT_PADDING
        val bgRect = RectF(
            px - textWidth / 2 - padding, py - textHeight - padding / 2,
            px + textWidth / 2 + padding, py + padding / 2
        )
        canvas.drawRoundRect(bgRect, ZOOM_HINT_BG_RADIUS, ZOOM_HINT_BG_RADIUS, zoomHintBgPaint)
        
        canvas.drawText(text, px, py, zoomHintPaint)

        // Draw animated "fingers"
        val fingerSpacing = ZOOM_HINT_FINGER_SPACING_BASE + (seeker.pinchAnimValue * ZOOM_HINT_FINGER_SPACING_ANIM)
        val fingerRadius = ZOOM_HINT_FINGER_RADIUS * (1f - seeker.pinchAnimValue * ZOOM_HINT_FINGER_RADIUS_SHRINK)
        fingerPaint.alpha = ((1f - seeker.pinchAnimValue) * ALPHA_MAX).toInt()
        
        val fingerY = py - textHeight * ZOOM_HINT_Y_OFFSET_SCALE
        // Left finger
        canvas.drawCircle(px - fingerSpacing, fingerY, fingerRadius, fingerPaint)
        // Right finger
        canvas.drawCircle(px + fingerSpacing, fingerY, fingerRadius, fingerPaint)
    }

    fun drawHandleArrow(canvas: Canvas, cx: Float, cy: Float, isLeft: Boolean) {
        val offset = ARROW_OFFSET_BASE + (seeker.pinchAnimValue * ARROW_OFFSET_ANIM)
        val alpha = ((1f - seeker.pinchAnimValue) * ALPHA_MAX).toInt()
        arrowPaint.alpha = alpha
        
        val direction = if (isLeft) 1 else -1
        val tipX = cx + offset * direction
        
        arrowPath.reset()
        arrowPath.moveTo(tipX, cy - ARROW_SIZE)
        arrowPath.lineTo(tipX + ARROW_SIZE * direction, cy)
        arrowPath.lineTo(tipX, cy + ARROW_SIZE)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }
}
