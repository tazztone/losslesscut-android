package com.tazztone.losslesscut.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.ui.PlayerView

class RotationManager(
    private val badgeRotate: TextView?,
    private val btnRotate: ImageButton,
    private val tvRotateEmoji: TextView,
    private val btnRotateContainer: View,
    private val playerView: PlayerView
) {
    var currentRotation = 0
        private set

    fun rotate(degrees: Int) {
        currentRotation = (currentRotation + degrees) % 360
        if (currentRotation < 0) currentRotation += 360
        updateRotationPreview()
    }

    fun setRotation(rotation: Int, animate: Boolean = false) {
        currentRotation = rotation
        updateRotationPreview(animate)
    }

    fun updateRotationPreview(animate: Boolean = true) {
        badgeRotate?.visibility = View.GONE
        
        val isZero = currentRotation == 0
        btnRotate.visibility = if (isZero) View.VISIBLE else View.GONE
        tvRotateEmoji.visibility = if (isZero) View.GONE else View.VISIBLE
        
        if (animate) {
            btnRotateContainer.animate()
                .rotation(currentRotation.toFloat())
                .setDuration(250)
                .start()
        } else {
            btnRotateContainer.rotation = currentRotation.toFloat()
        }

        playerView.scaleX = 1f
        playerView.scaleY = 1f
        (playerView.parent as? View)?.requestLayout()
    }
}
