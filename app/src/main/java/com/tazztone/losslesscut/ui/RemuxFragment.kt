package com.tazztone.losslesscut.ui

import android.os.Bundle
import android.view.View
import androidx.media3.common.util.UnstableApi
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.FragmentRemuxBinding
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class RemuxFragment : BaseEditingFragment(R.layout.fragment_remux) {
    private var _binding: FragmentRemuxBinding? = null
    private val binding get() = _binding!!

    override fun getPlayerView() = binding.playerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentRemuxBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        
        binding.seekerContainer?.visibility = View.GONE
        binding.editingControls?.visibility = View.GONE
        
        // Remux logic should be ported from Activity here
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
