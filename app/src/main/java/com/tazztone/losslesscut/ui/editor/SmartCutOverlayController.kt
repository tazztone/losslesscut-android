package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.View
import android.widget.ViewFlipper
import com.google.android.material.tabs.TabLayout
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Manages the unified "Smart Cut" overlay which contains Silence and Visual detection tabs.
 */
class SmartCutOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val binding: FragmentEditorBinding,
    private val viewModel: VideoEditingViewModel
) {
    private val overlayRoot: View? get() = binding.smartCutContainer?.root
    private var tabLayout: TabLayout? = null
    private var viewFlipper: ViewFlipper? = null
    
    private val silenceController: SilenceDetectionOverlayController by lazy {
        SilenceDetectionOverlayController(context, scope, binding, viewModel)
    }
    
    private var visualController: VisualDetectionOverlayController? = null

    fun show() {
        val root = overlayRoot ?: return
        root.visibility = View.VISIBLE
        
        if (tabLayout == null) {
            tabLayout = root.findViewById(R.id.tabLayout)
            viewFlipper = root.findViewById(R.id.viewFlipper)
            setupTabs()
        }

        // Default to silence tab (index 0)
        tabLayout?.getTabAt(0)?.select()
        viewFlipper?.displayedChild = 0
        silenceController.showInsideSmartCut()
        
        binding.customVideoSeeker.segmentsVisible = false
        binding.customVideoSeeker.playheadVisible = false
    }

    private fun setupTabs() {
        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val index = tab?.position ?: 0
                viewFlipper?.displayedChild = index
                
                if (index == 0) {
                    visualController?.deactivate()
                    silenceController.showInsideSmartCut()
                } else {
                    silenceController.hideInsideSmartCut()
                    if (visualController == null) {
                        visualController = VisualDetectionOverlayController(
                            context = context,
                            scope = scope,
                            viewModel = viewModel,
                            seeker = binding.customVideoSeeker,
                            root = viewFlipper!!.getChildAt(1)
                        )
                    }
                    visualController?.activate()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Disable visual tab for audio-only
        viewModel.uiState.value.let { state ->
            if (state is com.tazztone.losslesscut.viewmodel.VideoEditingUiState.Success && state.isAudioOnly) {
                tabLayout?.getTabAt(1)?.view?.isEnabled = false
                tabLayout?.getTabAt(1)?.view?.alpha = 0.5f
            }
        }
    }

    fun hide() {
        overlayRoot?.visibility = View.GONE
        silenceController.hideInsideSmartCut()
        visualController?.deactivate()
        
        binding.customVideoSeeker.segmentsVisible = true
        binding.customVideoSeeker.playheadVisible = true
        viewModel.clearSilencePreview()
        viewModel.cancelVisualDetection()
    }
    
    fun isVisible(): Boolean = overlayRoot?.visibility == View.VISIBLE
}
