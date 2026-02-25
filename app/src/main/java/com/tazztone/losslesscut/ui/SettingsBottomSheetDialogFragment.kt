package com.tazztone.losslesscut.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tazztone.losslesscut.data.AppPreferences
import com.tazztone.losslesscut.ui.compose.settings.SettingsScreen
import com.tazztone.losslesscut.ui.compose.theme.LosslessCutTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

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
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (listener == null) {
            if (context is SettingsListener) {
                listener = context
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LosslessCutTheme {
                    SettingsScreen(
                        preferences = preferences,
                        initialLosslessState = initialLosslessState,
                        onLosslessModeToggled = { isChecked ->
                            listener?.onLosslessModeToggled(isChecked)
                        },
                        onChangePath = {
                            selectFolderLauncher.launch(null)
                        },
                        onResetPath = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                preferences.setCustomOutputUri(null)
                            }
                        },
                        onAccentColorChanged = { colorName ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                preferences.setAccentColor(colorName)
                                activity?.recreate()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Ensure background is transparent to allow Compose background to show
        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
}
