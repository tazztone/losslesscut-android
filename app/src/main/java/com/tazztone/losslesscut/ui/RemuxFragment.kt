package com.tazztone.losslesscut.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.FragmentRemuxBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class RemuxFragment : BaseEditingFragment(R.layout.fragment_remux) {
    private var _binding: FragmentRemuxBinding? = null
    private val binding get() = _binding!!

    override fun getPlayerView() = binding.playerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentRemuxBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        
        playerManager = PlayerManager(
            context = requireContext(),
            playerView = getPlayerView(),
            viewModel = viewModel,
            onPlaybackParametersChanged = { speed, _ ->
                val formatted = if (speed % 1f == 0f) "${speed.toInt()}x" else String.format("%.2gx", speed)
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

        binding.btnNudgeBack?.setOnClickListener { playerManager.seekToKeyframe(-1) }
        binding.btnNudgeForward?.setOnClickListener { playerManager.seekToKeyframe(1) }
        
        binding.seekerContainer?.visibility = View.GONE
        binding.editingControls?.visibility = View.GONE
        
        lifecycleScope.launch {
            val finalState = viewModel.uiState.first { it is VideoEditingUiState.Success || it is VideoEditingUiState.Error }
            if (finalState is VideoEditingUiState.Success) {
                showRemuxDialog()
            } else {
                activity?.finish()
            }
        }
    }

    private fun showRemuxDialog() {
        if (viewModel.uiState.value !is VideoEditingUiState.Success) {
            Toast.makeText(requireContext(), R.string.please_wait, Toast.LENGTH_SHORT).show()
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dashboard_remux_title)
            .setMessage(R.string.remux_dialog_message)
            .setPositiveButton(R.string.export) { _, _ ->
                viewModel.exportSegments(
                    isLossless = true,
                    keepAudio = true,
                    keepVideo = true,
                    rotationOverride = null,
                    mergeSegments = false,
                    selectedTracks = null
                )
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
