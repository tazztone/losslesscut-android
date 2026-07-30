package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VisualDetectionOverlayControllerTest {

    private lateinit var context: Context
    private lateinit var root: View
    private lateinit var seeker: CustomVideoSeeker
    private lateinit var viewModel: VideoEditingViewModel
    private val testScope = TestScope()
    private var dismissCalled = false

    private lateinit var visualDetectionProgressFlow: MutableStateFlow<Pair<Int, Int>?>
    private lateinit var detectionPreviewRangesFlow: MutableStateFlow<List<LongRange>>
    private lateinit var uiStateFlow: MutableStateFlow<VideoEditingUiState>

    private lateinit var controller: VisualDetectionOverlayController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.AppTheme)

        val inflater = LayoutInflater.from(context)
        root = inflater.inflate(R.layout.dialog_visual_detection, null, false)
        seeker = CustomVideoSeeker(context)

        viewModel = mockk(relaxed = true)
        visualDetectionProgressFlow = MutableStateFlow(null)
        detectionPreviewRangesFlow = MutableStateFlow(emptyList())
        uiStateFlow = MutableStateFlow(VideoEditingUiState.Initial)

        every { viewModel.visualDetectionProgress } returns visualDetectionProgressFlow
        every { viewModel.detectionPreviewRanges } returns detectionPreviewRangesFlow
        every { viewModel.uiState } returns uiStateFlow

        dismissCalled = false

        controller = VisualDetectionOverlayController(
            context = context,
            scope = testScope,
            viewModel = viewModel,
            seeker = seeker,
            root = root,
            onDismiss = { dismissCalled = true }
        )
    }

    @Test
    fun `activate_initializesViewsAndState`() {
        controller.activate()
        assert(root.visibility == View.VISIBLE)
    }

    @Test
    fun `stepButtons_modifyIntervalSlider`() {
        controller.activate()
        val sliderInterval = root.findViewById<Slider>(R.id.sliderInterval)
        val btnPlus = root.findViewById<View>(R.id.btnIntervalPlus)
        val btnMinus = root.findViewById<View>(R.id.btnIntervalMinus)

        val initialValue = sliderInterval.value
        btnPlus.performClick()
        assertEquals(initialValue + 1f, sliderInterval.value, 0.001f)

        btnMinus.performClick()
        assertEquals(initialValue, sliderInterval.value, 0.001f)
    }

    @Test
    fun `stepButtons_modifySensitivitySlider`() {
        controller.activate()
        val sliderSensitivity = root.findViewById<Slider>(R.id.sliderSensitivity)
        val btnPlus = root.findViewById<View>(R.id.btnSensitivityPlus)
        val btnMinus = root.findViewById<View>(R.id.btnSensitivityMinus)

        val initialValue = sliderSensitivity.value
        btnPlus.performClick()
        assertEquals(initialValue + 1f, sliderSensitivity.value, 0.001f)

        btnMinus.performClick()
        assertEquals(initialValue, sliderSensitivity.value, 0.001f)
    }

    @Test
    fun `btnDetectAction_triggersDetection`() {
        controller.activate()
        val btnDetectAction = root.findViewById<MaterialButton>(R.id.btnDetectAction)
        btnDetectAction.performClick()

        verify { viewModel.previewVisualSegments(any()) }
    }
}
