package com.tazztone.losslesscut.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CustomVideoSeeker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // Paint object for drawing
    private var seekPosition = 0f // Normalized seek position (0 to 1)
    private var videoDuration = 0L // Total video duration in milliseconds
    var onSeekListener: ((Float) -> Unit)? = null // Listener for seek events

    private val keyframePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 2f
    }
    private var keyframes = listOf<Float>() // Normalized positions (0..1)
    var isLosslessMode = true // Enable snapping

    init {
        paint.color = Color.WHITE // Set the color of the line to white
        paint.strokeWidth = 5f // Set the stroke width for the line
    }

    // Method to draw the seek line based on the current seek position
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw keyframes
        keyframes.forEach { kf ->
            val kfX = width * kf
            canvas.drawLine(kfX, 0f, kfX, height.toFloat() / 2, keyframePaint) // Draw tick marks
        }

        val seekX = width * seekPosition // Calculate the X position for the seek line
        canvas.drawLine(seekX, 0f, seekX, height.toFloat(), paint) // Draw the seek line
    }

    @SuppressLint("ClickableViewAccessibility")
    // Handle touch events to update seek position
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Update the normalized seek position based on touch event
                var newSeekPosition = (event.x / width).coerceIn(0f, 1f)

                // Snap to keyframe if in Lossless Mode
                // Also update onSeekListener with the SNAPPED position
                if (isLosslessMode && keyframes.isNotEmpty()) {
                    val snapThreshold = 0.05f // Snap if within 5% logic width
                    // Find nearest keyframe
                    val nearest = keyframes.minByOrNull { kotlin.math.abs(it - newSeekPosition) }
                    if (nearest != null && kotlin.math.abs(nearest - newSeekPosition) < snapThreshold) {
                         newSeekPosition = nearest
                    }
                }

                seekPosition = newSeekPosition
                val seekTime = (videoDuration * seekPosition).toLong() // Calculate seek time in milliseconds
                onSeekListener?.invoke(seekPosition) // Notify listener of the normalized position
                // onSeekListener?.invoke(seekTime.toFloat()) // REMOVED: Duplicate invoke with different meaning
                invalidate() // Redraw the view to update the seek line
                return true
            }
        }
        return super.onTouchEvent(event) // Handle other touch events normally
    }

    // Method to set the total video duration
    fun setVideoDuration(duration: Long) {
        videoDuration = duration // Store the video duration
    }

    fun setKeyframes(frames: List<Float>) {
        this.keyframes = frames
        invalidate()
    }
}