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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private lateinit var defaultVisualFrameStepFlow: MutableStateFlow<Int>

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
        defaultVisualFrameStepFlow = MutableStateFlow(5)

        every { viewModel.visualDetectionProgress } returns visualDetectionProgressFlow
        every { viewModel.detectionPreviewRanges } returns detectionPreviewRangesFlow
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.defaultVisualFrameStepFlow } returns defaultVisualFrameStepFlow

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
    fun `activate_doesNotStartFiltering`() {
        controller.activate()

        verify(exactly = 0) { viewModel.previewVisualSegments(any()) }
        verify(exactly = 0) { viewModel.filterVisualSegments(any()) }
    }

    @Test
    fun `preference_updatesFrameStepSliderAndDisplay`() {
        controller.activate()
        defaultVisualFrameStepFlow.value = 30
        testScope.advanceUntilIdle()

        assertEquals(30f, root.findViewById<Slider>(R.id.sliderInterval).value, 0.001f)
        assertTrue(root.findViewById<android.widget.TextView>(R.id.tvIntervalValue).text.contains("30"))
    }

    @Test
    fun `sceneStrategy_hidesModeControls`() {
        controller.activate()
        val modeLayout = root.findViewById<View>(R.id.layoutVisualMode)

        assertEquals(View.GONE, modeLayout.visibility)
        root.findViewById<View>(R.id.btnBlackFrames).performClick()
        assertEquals(View.VISIBLE, modeLayout.visibility)
        root.findViewById<View>(R.id.btnSceneChange).performClick()
        assertEquals(View.GONE, modeLayout.visibility)
    }

    @Test
    fun `sliderFiltering_startsOnlyAfterDetect`() {
        controller.activate()
        root.findViewById<Slider>(R.id.sliderSensitivity).value = 13f
        testScope.advanceTimeBy(101)
        testScope.advanceUntilIdle()
        verify(exactly = 0) { viewModel.filterVisualSegments(any()) }

        root.findViewById<MaterialButton>(R.id.btnDetectAction).performClick()
        root.findViewById<Slider>(R.id.sliderSensitivity).value = 14f
        testScope.advanceTimeBy(101)
        testScope.advanceUntilIdle()
        verify { viewModel.filterVisualSegments(any()) }
    }

    @Test
    fun `progress_usesSampledFrameLabel`() {
        controller.activate()
        visualDetectionProgressFlow.value = 2 to 10
        testScope.advanceUntilIdle()

        assertTrue(root.findViewById<android.widget.TextView>(R.id.tvProgressText).text.contains("sampled frames"))
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
