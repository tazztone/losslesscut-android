package com.tazztone.losslesscut.ui.compose.settings

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
import com.tazztone.losslesscut.ui.compose.theme.LosslessCutTheme
import com.tazztone.losslesscut.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private val selectFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val contentResolver = requireContext().contentResolver
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, takeFlags)
                viewModel.setCustomOutputUri(it.toString())
            } catch (_: SecurityException) {
                Toast.makeText(requireContext(), R.string.error_output_folder_access, Toast.LENGTH_SHORT).show()
            } catch (_: IllegalArgumentException) {
                Toast.makeText(requireContext(), R.string.error_output_folder_access, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
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

            LosslessCutTheme(accentColorName = uiState.accentColor) {
                SettingsScreen(
                    uiState = uiState,
                    onChangePath = { selectFolderLauncher.launch(null) },
                    onResetPath = { viewModel.setCustomOutputUri(null) },
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
                    },
                    onUndoLimitChanged = viewModel::setUndoLimit,
                    onSnapshotFormatChanged = viewModel::setSnapshotFormat,
                    onJpgQualityChanged = viewModel::setJpgQuality,
                    onDeleteOriginalAfterExportChanged = viewModel::setDeleteOriginalAfterExport,
                    onAutoExtractWaveformsChanged = viewModel::setAutoExtractWaveforms,
                    onVisualFrameStepChanged = viewModel::setVisualFrameStep,
                    onCacheCapacityChanged = viewModel::setCacheCapacityMB,
                    onCacheRetentionChanged = viewModel::setCacheRetentionDays,
                    onClearCache = viewModel::clearCache,
                    onClose = { dismiss() },
                    onOpenUrl = { url ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    onScrollChanged = { scrollValue ->
                        val bottomSheet = (dialog as? BottomSheetDialog)
                            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                        if (bottomSheet != null) {
                            BottomSheetBehavior.from(bottomSheet).isDraggable = scrollValue == 0
                        }
                    }
                )
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
        BottomSheetBehavior.from(bottomSheet).apply {
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }
}
