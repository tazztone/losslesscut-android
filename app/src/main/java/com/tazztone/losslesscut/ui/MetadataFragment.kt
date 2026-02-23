package com.tazztone.losslesscut.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.FragmentMetadataBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class MetadataFragment : BaseEditingFragment(R.layout.fragment_metadata) {
    private var _binding: FragmentMetadataBinding? = null
    private val binding get() = _binding!!

    override fun getPlayerView() = binding.playerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMetadataBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        
        playerManager = PlayerManager(
            context = requireContext(),
            playerView = getPlayerView(),
            viewModel = viewModel,
            onSpeedChanged = { speed ->
                val formatted = if (speed == 0.25f) {
                    String.format("%.2fx", speed)
                } else {
                    String.format("%.1fx", speed).replace(".0", "")
                }
                binding.btnPlaybackSpeed?.text = formatted
            }
        )
        playerManager.initialize()

        binding.btnPlaybackSpeed?.setOnClickListener { playerManager.cyclePlaybackSpeed() }
        binding.btnPlaybackSpeed?.setOnLongClickListener {
            val isEnabled = playerManager.togglePitchCorrection()
            val msgRes = if (isEnabled) R.string.pitch_correction_on else R.string.pitch_correction_off
            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
            true
        }

        binding.seekerContainer?.visibility = View.GONE
        binding.editingControls?.visibility = View.GONE
        
        lifecycleScope.launch {
            val finalState = viewModel.uiState.first { it is VideoEditingUiState.Success || it is VideoEditingUiState.Error }
            if (finalState is VideoEditingUiState.Success) {
                showMetadataDialog()
            } else {
                activity?.finish()
            }
        }
    }

    private fun showMetadataDialog() {
        val state = viewModel.uiState.value as? VideoEditingUiState.Success ?: run {
            Toast.makeText(requireContext(), R.string.please_wait, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_metadata_editor, null)
        val spinnerRotation = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerRotation)
        
        spinnerRotation.setSelection(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dashboard_metadata_title)
            .setView(dialogView)
            .setPositiveButton(R.string.apply) { _, _ ->
                val selectedRotation = when (spinnerRotation.selectedItemPosition) {
                    1 -> 0
                    2 -> 90
                    3 -> 180
                    4 -> 270
                    else -> null // Keep Original
                }
                viewModel.exportSegments(true, keepAudio = true, keepVideo = true,
                    rotationOverride = selectedRotation, mergeSegments = false)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                activity?.finish()
            }
            .setOnCancelListener {
                activity?.finish()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
