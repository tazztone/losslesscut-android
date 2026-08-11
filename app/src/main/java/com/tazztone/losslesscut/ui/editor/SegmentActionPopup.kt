package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.color.MaterialColors
import com.tazztone.losslesscut.R
import kotlin.math.roundToInt

import com.tazztone.losslesscut.customviews.SegmentLongPressEvent

internal object SegmentActionPopupPosition {
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Position(val left: Int, val top: Int)

    fun calculate(
        anchorX: Int,
        anchorY: Int,
        popupWidth: Int,
        popupHeight: Int,
        visibleFrame: Bounds
    ): Position {
        val maxLeft = (visibleFrame.right - popupWidth).coerceAtLeast(visibleFrame.left)
        val maxTop = (visibleFrame.bottom - popupHeight).coerceAtLeast(visibleFrame.top)
        return Position(
            left = (anchorX - popupWidth / 2).coerceIn(visibleFrame.left, maxLeft),
            top = (anchorY - popupHeight / 2).coerceIn(visibleFrame.top, maxTop)
        )
    }
}

internal class SegmentActionPopup(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private var popupWindow: PopupWindow? = null

    fun show(
        anchorView: View,
        event: SegmentLongPressEvent,
        onDelete: () -> Unit,
        onSplit: () -> Unit,
        onDismiss: (() -> Unit)? = null
    ) {
        dismiss()

        val buttonSize = (BUTTON_SIZE_DP * density).roundToInt()
        val content = createContent(buttonSize, onDelete, onSplit)

        popupWindow = PopupWindow(
            content,
            buttonSize,
            buttonSize * ACTION_COUNT,
            true
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            isClippingEnabled = true
            setOnDismissListener {
                onDismiss?.invoke()
                popupWindow = null
            }
        }
        val position = calculatePosition(anchorView, event.x, event.y, buttonSize)
        popupWindow?.showAtLocation(anchorView, Gravity.TOP or Gravity.START, position.left, position.top)
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun createContent(
        buttonSize: Int,
        onDelete: () -> Unit,
        onSplit: () -> Unit
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Color.TRANSPARENT.toDrawable()
        elevation = ELEVATION_DP * density

        // Top button: Delete / Trash (Red tint & red border)
        addView(createActionButton(
            iconRes = R.drawable.ic_delete_24,
            descriptionRes = R.string.discard_segment,
            buttonSize = buttonSize,
            iconTint = Color.parseColor("#FF3B30"),
            borderColor = Color.parseColor("#FF3B30"),
            rotationDegrees = 0f,
            onClick = {
                dismiss()
                onDelete()
            }
        ))

        // Bottom button: Split / Scissor (White tint, blades downward rotation & white border)
        addView(createActionButton(
            iconRes = R.drawable.ic_split_24,
            descriptionRes = R.string.split,
            buttonSize = buttonSize,
            iconTint = Color.WHITE,
            borderColor = Color.WHITE,
            rotationDegrees = 180f,
            onClick = {
                dismiss()
                onSplit()
            }
        ))
    }

    private fun calculatePosition(anchorView: View, x: Float, y: Float, buttonSize: Int): SegmentActionPopupPosition.Position {
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val visibleFrame = Rect().also { anchorView.getWindowVisibleDisplayFrame(it) }
        if (visibleFrame.isEmpty) {
            visibleFrame.set(0, 0, context.resources.displayMetrics.widthPixels,
                context.resources.displayMetrics.heightPixels)
        }
        return SegmentActionPopupPosition.calculate(
            anchorX = location[0] + x.roundToInt(),
            anchorY = location[1] + y.roundToInt(),
            popupWidth = buttonSize,
            popupHeight = buttonSize * ACTION_COUNT,
            visibleFrame = SegmentActionPopupPosition.Bounds(
                left = visibleFrame.left,
                top = visibleFrame.top,
                right = visibleFrame.right,
                bottom = visibleFrame.bottom
            )
        )
    }

    @Suppress("LongParameterList")
    private fun createActionButton(
        iconRes: Int,
        descriptionRes: Int,
        buttonSize: Int,
        iconTint: Int,
        borderColor: Int,
        rotationDegrees: Float,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
            setMargins(0, (2 * density).roundToInt(), 0, (2 * density).roundToInt())
        }
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(iconTint)
        rotation = rotationDegrees
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC1A1A1A"))
            setStroke((2 * density).roundToInt(), borderColor)
        }
        contentDescription = context.getString(descriptionRes)
        tooltipText = context.getString(descriptionRes)
        setOnClickListener { onClick() }
    }

    companion object {
        private const val ACTION_COUNT = 2
        private const val BUTTON_SIZE_DP = 44f
        private const val ELEVATION_DP = 6f
    }
}

