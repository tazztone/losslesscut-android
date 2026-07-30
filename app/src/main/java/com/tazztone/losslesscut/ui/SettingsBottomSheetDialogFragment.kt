package com.tazztone.losslesscut.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.compose.settings.SettingsScreen
import com.tazztone.losslesscut.ui.compose.theme.LosslessCutTheme
import com.tazztone.losslesscut.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private val selectFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val contentResolver = requireContext().contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.setCustomOutputUri(it.toString())
        }
    }

    interface SettingsListener {
        fun onLosslessModeToggled(isChecked: Boolean)
    }

    private var listener: SettingsListener? = null
    private var initialLosslessState: Boolean = true

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
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = requireContext()

                LaunchedEffect(uiState.cacheClearSuccessMessage) {
                    if (uiState.cacheClearSuccessMessage) {
                        Toast.makeText(context, context.getString(R.string.cache_cleared_success), Toast.LENGTH_SHORT).show()
                        viewModel.clearCacheMessageShown()
                    }
                }

                LosslessCutTheme {
                    SettingsScreen(
                        uiState = uiState,
                        initialLosslessState = initialLosslessState,
                        onLosslessModeToggled = { isChecked ->
                            listener?.onLosslessModeToggled(isChecked)
                        },
                        onChangePath = {
                            selectFolderLauncher.launch(null)
                        },
                        onResetPath = {
                            viewModel.setCustomOutputUri(null)
                        },
                        onLanguageChanged = { langCode ->
                            viewModel.setLanguage(langCode)
                            val locales = if (langCode == "system") {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(langCode)
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        },
                        onAccentColorChanged = { colorName ->
                            viewModel.setAccentColor(colorName)
                            activity?.recreate()
                        },
                        onUndoLimitChanged = { viewModel.setUndoLimit(it) },
                        onSnapshotFormatChanged = { viewModel.setSnapshotFormat(it) },
                        onJpgQualityChanged = { viewModel.setJpgQuality(it) },
                        onAutoExtractWaveformsChanged = { viewModel.setAutoExtractWaveforms(it) },
                        onVisualFrameStepChanged = { viewModel.setVisualFrameStep(it) },
                        onCacheCapacityChanged = { viewModel.setCacheCapacityMB(it) },
                        onCacheRetentionChanged = { viewModel.setCacheRetentionDays(it) },
                        onClearCache = { viewModel.clearCache() },
                        onScrollChanged = { scrollValue ->
                            val bottomSheetDialog = dialog as? BottomSheetDialog
                            val bottomSheet = bottomSheetDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                            if (bottomSheet != null) {
                                val behavior = BottomSheetBehavior.from(bottomSheet)
                                behavior.isDraggable = (scrollValue == 0)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isFitToContents = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }
}
