package com.tazztone.losslesscut.ui

import android.view.View
import com.tazztone.losslesscut.databinding.ActivityVideoEditingBinding

class RotationManager(
    private val binding: ActivityVideoEditingBinding
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
        // Hide the degree text badge since the icon is now the visual indicator
        binding.badgeRotate?.visibility = View.GONE
        
        val isZero = currentRotation == 0
        binding.btnRotate.visibility = if (isZero) View.VISIBLE else View.GONE
        binding.tvRotateEmoji.visibility = if (isZero) View.GONE else View.VISIBLE
        
        // Always rotate the container. This makes the logic much simpler and more robust.
        if (animate) {
            binding.btnRotateContainer.animate()
                .rotation(currentRotation.toFloat())
                .setDuration(250)
                .start()
        } else {
            binding.btnRotateContainer.rotation = currentRotation.toFloat()
        }

        // Ensure the video player stays completely un-squished and un-rotated
        binding.playerView.scaleX = 1f
        binding.playerView.scaleY = 1f
        binding.playerView.parent.requestLayout()
    }
}
