package com.tazztone.losslesscut.ui
import com.tazztone.losslesscut.di.*
import com.tazztone.losslesscut.customviews.*
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.*
import com.tazztone.losslesscut.viewmodel.*
import com.tazztone.losslesscut.data.*
import com.tazztone.losslesscut.utils.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tazztone.losslesscut.databinding.BottomSheetSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    private val selectFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            // Take persistable permission
            val contentResolver = requireContext().contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, takeFlags)
            
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setCustomOutputUri(it.toString())
            }
        }
    }

    interface SettingsListener {
        fun onLosslessModeToggled(isChecked: Boolean)
    }

    private var listener: SettingsListener? = null
    private var initialLosslessState: Boolean = true
    
    @Inject
    lateinit var preferences: AppPreferences

    fun setSettingsListener(listener: SettingsListener) {
        this.listener = listener
    }

    fun setInitialState(isLossless: Boolean) {
        this.initialLosslessState = isLossless
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (listener == null) {
            if (context is SettingsListener) {
                listener = context
            } else {
                // It's okay if activity doesn't implement it if a fragment set it manually
            }
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
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.undoLimitFlow.collect { currentLimit ->
                binding.tvUndoValue.text = currentLimit.toString()
                binding.seekBarUndoLimit.progress = currentLimit
            }
        }
        
        binding.seekBarUndoLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val limit = progress.coerceAtLeast(1)
                    binding.tvUndoValue.text = limit.toString()
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferences.setUndoLimit(limit)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initialize Snapshot Format Toggle
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.snapshotFormatFlow.collect { currentFormat ->
                val isJpeg = currentFormat == "JPEG"
                binding.switchSnapshotJpeg.isChecked = isJpeg
                binding.layoutJpgQuality.visibility = if (isJpeg) View.VISIBLE else View.GONE
            }
        }

        binding.switchSnapshotJpeg.setOnCheckedChangeListener { _, isChecked ->
            val format = if (isChecked) "JPEG" else "PNG"
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setSnapshotFormat(format)
            }
            binding.layoutJpgQuality.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Initialize JPG Quality
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.jpgQualityFlow.collect { currentJpgQuality ->
                binding.tvJpgQualityValue.text = currentJpgQuality.toString()
                binding.seekBarJpgQuality.progress = currentJpgQuality
            }
        }
        
        binding.seekBarJpgQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val quality = progress.coerceAtLeast(1)
                    binding.tvJpgQualityValue.text = quality.toString()
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferences.setJpgQuality(quality)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Initialize Custom Output URI
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.customOutputUriFlow.collect { uriString ->
                if (uriString != null) {
                    binding.tvExportPath.text = Uri.parse(uriString).path
                    binding.btnResetPath.visibility = View.VISIBLE
                } else {
                    binding.tvExportPath.text = "Default (Movies/LosslessCut)"
                    binding.btnResetPath.visibility = View.GONE
                }
            }
        }

        binding.btnChangePath.setOnClickListener {
            selectFolderLauncher.launch(null)
        }

        binding.btnResetPath.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setCustomOutputUri(null)
            }
        }

        setupColorPicker()
    }

    private fun setupColorPicker() {
        val colors = listOf(
            binding.colorCyan,
            binding.colorPurple,
            binding.colorGreen,
            binding.colorYellow,
            binding.colorRed,
            binding.colorOrange
        )

        viewLifecycleOwner.lifecycleScope.launch {
            preferences.accentColorFlow.collect { currentColor ->
                colors.forEach { view ->
                    val isSelected = view.tag == currentColor
                    view.scaleX = if (isSelected) 1.2f else 1.0f
                    view.scaleY = if (isSelected) 1.2f else 1.0f
                    view.alpha = if (isSelected) 1.0f else 0.6f
                }
            }
        }

        colors.forEach { view ->
            view.setOnClickListener {
                val colorName = it.tag as String
                viewLifecycleOwner.lifecycleScope.launch {
                    preferences.setAccentColor(colorName)
                    activity?.recreate()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
