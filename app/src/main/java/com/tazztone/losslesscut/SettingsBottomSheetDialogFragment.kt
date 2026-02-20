package com.tazztone.losslesscut

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tazztone.losslesscut.databinding.BottomSheetSettingsBinding

class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    interface SettingsListener {
        fun onLosslessModeToggled(isChecked: Boolean)
    }

    private var listener: SettingsListener? = null
    private var initialLosslessState: Boolean = true

    fun setInitialState(isLossless: Boolean) {
        this.initialLosslessState = isLossless
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SettingsListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement SettingsListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Ensure background is dark
        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        binding.switchLossless.isChecked = initialLosslessState
        binding.switchLossless.setOnCheckedChangeListener { _, isChecked ->
            listener?.onLosslessModeToggled(isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
