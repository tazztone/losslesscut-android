package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat
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
    private val overlayRoot: View? get() = binding.smartCutOverlay?.root

    private var tabLayout: TabLayout? = null
    private var viewFlipper: ViewFlipper? = null
    private var currentTooltipPopup: PopupWindow? = null

    private val silenceController: SilenceDetectionOverlayController by lazy {
        SilenceDetectionOverlayController(context, scope, binding, viewModel) { hide() }
    }

    private var visualController: VisualDetectionOverlayController? = null

    fun show() {
        val root = overlayRoot ?: return
        root.visibility = View.VISIBLE

        // Hide standard editor controls to make room
        binding.toolbar?.visibility = View.GONE
        binding.editingControls?.visibility = View.GONE
        binding.playlistContainer?.visibility = View.GONE
        binding.btnPlayPause?.visibility = View.GONE

        if (tabLayout == null) {
            tabLayout = root.findViewById(R.id.tabLayout)
            viewFlipper = root.findViewById(R.id.viewFlipper)
            setupTabs()
        }

        // Apply tooltip clicks to the entire overlay
        root.setupTooltipClicksForImageButtons(this)

        // Default to silence tab (index 0)
        tabLayout?.getTabAt(0)?.select()
        viewFlipper?.displayedChild = 0
        silenceController.showInsideSmartCut()

        binding.customVideoSeeker.segmentsVisible = false
        binding.customVideoSeeker.playheadVisible = true
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
                        val visualRoot = viewFlipper!!.getChildAt(1)
                        visualController = VisualDetectionOverlayController(
                            context = context,
                            scope = scope,
                            viewModel = viewModel,
                            seeker = binding.customVideoSeeker,
                            root = visualRoot,
                            onDismiss = { hide() }
                        )
                        visualRoot.setupTooltipClicksForImageButtons(this@SmartCutOverlayController)
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

        // Apply informative tooltips to tab headers
        tabLayout?.getTabAt(0)?.view?.tooltipText = context.getString(R.string.tooltip_silence_tab)
        tabLayout?.getTabAt(1)?.view?.tooltipText = context.getString(R.string.tooltip_visual_tab)
    }

    fun hide() {
        currentTooltipPopup?.dismiss()
        currentTooltipPopup = null

        overlayRoot?.visibility = View.GONE
        silenceController.hideInsideSmartCut()
        visualController?.deactivate()

        // Restore standard editor controls
        binding.toolbar?.visibility = View.VISIBLE
        binding.editingControls?.visibility = View.VISIBLE
        binding.btnPlayPause?.visibility = View.VISIBLE

        val state = viewModel.uiState.value
        if (state is com.tazztone.losslesscut.viewmodel.VideoEditingUiState.Success && state.isPlaylistVisible) {
            binding.playlistContainer?.visibility = View.VISIBLE
        }

        binding.customVideoSeeker.segmentsVisible = true
        binding.customVideoSeeker.playheadVisible = true
        viewModel.clearSilencePreview()
        viewModel.cancelVisualDetection()
    }

    fun isVisible(): Boolean = overlayRoot?.visibility == View.VISIBLE

    fun showTooltipPopup(anchor: View, text: String) {
        if (text.isBlank()) return
        currentTooltipPopup?.dismiss()

        val tv = TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(40, 24, 40, 24)
            background = ContextCompat.getDrawable(context, R.drawable.tooltip_background)
        }

        tv.measure(
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popup = android.widget.PopupWindow(
            tv,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = false
            elevation = 16f
        }
        currentTooltipPopup = popup

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val popupX = loc[0] - tv.measuredWidth / 2 + anchor.width / 2
        val popupY = loc[1] - tv.measuredHeight - 16

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
    }
}

fun View.setupTooltipClicksForImageButtons(controller: SmartCutOverlayController) {
    // Prefer Tag (where we'll store the long detailed string) over tooltipText
    val tooltip = this.tag?.toString() ?: this.tooltipText?.toString()
    
    if (this is android.widget.ImageButton && tooltip != null) {
        this.setOnClickListener {
            controller.showTooltipPopup(this, tooltip)
        }
    } else if (this is android.view.ViewGroup) {
        for (i in 0 until this.childCount) {
            this.getChildAt(i).setupTooltipClicksForImageButtons(controller)
        }
    }
}
