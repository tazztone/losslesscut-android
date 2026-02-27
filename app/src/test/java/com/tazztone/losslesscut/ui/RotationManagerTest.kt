package com.tazztone.losslesscut.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.ui.PlayerView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class RotationManagerTest {

    private lateinit var manager: RotationManager
    private val btnRotate: ImageButton = mockk(relaxed = true)
    private val tvRotateEmoji: TextView = mockk(relaxed = true)
    private val btnRotateContainer: View = mockk(relaxed = true)
    private val playerView: PlayerView = mockk(relaxed = true)

    @Before
    fun setUp() {
        manager = RotationManager(
            badgeRotate = null,
            btnRotate = btnRotate,
            tvRotateEmoji = tvRotateEmoji,
            btnRotateContainer = btnRotateContainer,
            playerView = playerView
        )
    }

    @Test
    fun `initial rotation is 0`() {
        assertEquals(0, manager.currentRotation)
    }

    @Test
    fun `rotate 90 degrees accumulates correctly`() {
        manager.rotate(90)
        assertEquals(90, manager.currentRotation)
        manager.rotate(90)
        assertEquals(180, manager.currentRotation)
        manager.rotate(90)
        assertEquals(270, manager.currentRotation)
        manager.rotate(90)
        assertEquals(0, manager.currentRotation)
    }

    @Test
    fun `rotate negative degrees wraps to positive`() {
        manager.rotate(-90)
        assertEquals(270, manager.currentRotation)
    }

    @Test
    fun `updateRotationPreview shows btnRotate when rotation is 0`() {
        manager.setRotation(0, animate = false)
        verify { btnRotate.visibility = View.VISIBLE }
        verify { tvRotateEmoji.visibility = View.GONE }
    }

    @Test
    fun `updateRotationPreview shows tvRotateEmoji when rotation is non-zero`() {
        manager.setRotation(90, animate = false)
        verify { btnRotate.visibility = View.GONE }
        verify { tvRotateEmoji.visibility = View.VISIBLE }
    }

    @Test
    fun `setRotation without animate sets rotation directly on container`() {
        manager.setRotation(180, animate = false)
        verify { btnRotateContainer.rotation = 180f }
    }
}
