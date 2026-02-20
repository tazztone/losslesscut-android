package com.tazztone.losslesscut

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
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

        // Initialize Undo Limit
        val currentLimit = AppPreferences.getUndoLimit(requireContext())
        binding.tvUndoValue.text = currentLimit.toString()
        binding.seekBarUndoLimit.progress = currentLimit
        binding.seekBarUndoLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val limit = progress.coerceAtLeast(1)
                binding.tvUndoValue.text = limit.toString()
                if (fromUser) {
                    AppPreferences.setUndoLimit(requireContext(), limit)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initialize Snapshot Format Toggle
        val currentFormat = AppPreferences.getSnapshotFormat(requireContext())
        val isJpeg = currentFormat == "JPEG"
        binding.switchSnapshotJpeg.isChecked = isJpeg
        binding.layoutJpgQuality.visibility = if (isJpeg) View.VISIBLE else View.GONE

        binding.switchSnapshotJpeg.setOnCheckedChangeListener { _, isChecked ->
            val format = if (isChecked) "JPEG" else "PNG"
            AppPreferences.setSnapshotFormat(requireContext(), format)
            binding.layoutJpgQuality.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Initialize JPG Quality
        val currentJpgQuality = AppPreferences.getJpgQuality(requireContext())
        binding.tvJpgQualityValue.text = currentJpgQuality.toString()
        binding.seekBarJpgQuality.progress = currentJpgQuality
        binding.seekBarJpgQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val quality = progress.coerceAtLeast(1)
                binding.tvJpgQualityValue.text = quality.toString()
                if (fromUser) {
                    AppPreferences.setJpgQuality(requireContext(), quality)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
