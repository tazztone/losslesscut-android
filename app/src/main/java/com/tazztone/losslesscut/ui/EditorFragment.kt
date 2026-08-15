package com.tazztone.losslesscut.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.*
import com.tazztone.losslesscut.util.asString
import com.tazztone.losslesscut.utils.MediaDeletionResult
import com.tazztone.losslesscut.utils.StorageUtils
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingEvent
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.viewmodel.ExportSettings
import com.tazztone.losslesscut.ui.editor.SegmentActionPopup
import com.tazztone.losslesscut.ui.compose.settings.SettingsBottomSheetDialogFragment
import com.tazztone.losslesscut.ui.compose.loading.LoadingOverlay
import androidx.compose.ui.platform.ViewCompositionStrategy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class EditorFragment : BaseEditingFragment(R.layout.fragment_editor) {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var rotationManager: RotationManager
    private lateinit var shortcutHandler: ShortcutHandler
    private lateinit var playlistDelegate: com.tazztone.losslesscut.ui.editor.PlaylistDelegate
    
    private lateinit var progressTicker: com.tazztone.losslesscut.ui.editor.PlaybackProgressTicker
    private lateinit var seekerDelegate: com.tazztone.losslesscut.ui.editor.TimelineSeekerDelegate
    private lateinit var addClipsDelegate: com.tazztone.losslesscut.ui.editor.AddClipsDelegate
    private lateinit var smartCutController: com.tazztone.losslesscut.ui.editor.SmartCutOverlayController
    private lateinit var exportOptionsController: com.tazztone.losslesscut.ui.editor.ExportOptionsDialogPresenter
    private lateinit var backPressDelegate: com.tazztone.losslesscut.ui.editor.BackPressDelegate
    private lateinit var segmentActionPopup: SegmentActionPopup
    
    private var isDraggingTimeline = false
    private var lastLoadedClipId: UUID? = null

    @Inject
    lateinit var storageUtils: StorageUtils

    private var pendingDeleteUris: List<Uri> = emptyList()

    private val deletePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), R.string.original_clip_deleted, Toast.LENGTH_SHORT).show()
            viewModel.onOriginalClipsDeleted(pendingDeleteUris)
        } else {
            Toast.makeText(requireContext(), R.string.original_clip_retained, Toast.LENGTH_SHORT).show()
        }
        pendingDeleteUris = emptyList()
    }


    private val addClipsLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(::persistReadPermission)
        addClipsDelegate.onClipsReceived(uris)
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers grant only a temporary read permission.
        }
    }

    override fun getPlayerView() = binding.playerSection.playerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditorBinding.bind(view)
        segmentActionPopup = SegmentActionPopup(requireContext())
        
        playerManager = PlayerManager(
            context = requireContext(),
            playerView = binding.playerSection.playerView,
            viewModel = viewModel,
            onStateChanged = { state ->
                if (state == Player.STATE_READY) {
                    seekerDelegate.setVideoDuration(playerManager.duration)
                    updateDurationDisplay(playerManager.currentPosition, playerManager.duration)
                    binding.seekerContainer.customVideoSeeker.setSeekPosition(playerManager.currentPosition)
                }
                updatePlaybackIcons()
            },
            onMediaTransition = { index ->
                if (::playlistDelegate.isInitialized) {
                    playlistDelegate.updateSelection(index)
                }
            },
            onIsPlayingChanged = { isPlaying ->
                updatePlaybackIcons()
                if (isPlaying) progressTicker.start() else progressTicker.stop()
            },
            onPlaybackParametersChanged = { speed, pitch ->
                updatePlaybackSpeedUI(speed)
                viewModel.setPlaybackParameters(speed, pitch)
            }
        )
        playerManager.initialize()

        progressTicker = com.tazztone.losslesscut.ui.editor.PlaybackProgressTicker(
            scope = viewLifecycleOwner.lifecycleScope,
            seeker = binding.seekerContainer.customVideoSeeker,
            playerManager = playerManager,
            onUpdate = { current, total -> updateDurationDisplay(current, total) }
        )

        seekerDelegate = com.tazztone.losslesscut.ui.editor.TimelineSeekerDelegate(
            seeker = binding.seekerContainer.customVideoSeeker,
            viewModel = viewModel,
            playerManager = playerManager,
            onSeek = { pos -> updateDurationDisplay(pos, playerManager.duration) },
            onDraggingChanged = { dragging -> 
                isDraggingTimeline = dragging
                progressTicker.isDraggingTimeline = dragging
            }
        )

        addClipsDelegate = com.tazztone.losslesscut.ui.editor.AddClipsDelegate(requireContext(), viewModel)
        
        smartCutController = com.tazztone.losslesscut.ui.editor.SmartCutOverlayController(
            requireContext(), viewLifecycleOwner.lifecycleScope, binding, viewModel
        ).apply {
            viewLifecycleOwner.lifecycle.addObserver(this)
        }

        exportOptionsController = com.tazztone.losslesscut.ui.editor.ExportOptionsDialogPresenter(
            context = requireContext(),
            layoutInflater = layoutInflater,
            onExport = viewModel::exportSegments
        )

        backPressDelegate = com.tazztone.losslesscut.ui.editor.BackPressDelegate(
            context = requireContext(),
            isDirty = viewModel.isDirty,
            onConfirmExit = {
                viewModel.discardSession {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        )

        rotationManager = RotationManager(
            badgeRotate = binding.editingControls.badgeRotate,
            btnRotate = binding.editingControls.btnRotate,
            tvRotateEmoji = binding.editingControls.tvRotateEmoji,
            btnRotateContainer = binding.editingControls.btnRotateContainer,
            playerView = binding.playerSection.playerView
        )

        shortcutHandler = ShortcutHandler(
            viewModel = viewModel,
            playerManager = playerManager,
            onSplit = { splitCurrentSegment() },
            onSetIn = { setInPoint() },
            onSetOut = { setOutPoint() },
            onRestore = { 
                val uris = activity?.intent?.let { intent ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS)
                    }
                }
                if (uris != null && uris.isNotEmpty()) {
                    viewModel.restoreSession(uris[0])
                }
            }
        )

        initializeViews()
        setupCustomSeeker()
        observeViewModel()
        setupBackPressed()

        binding.seekerContainer.customVideoSeeker.isLosslessMode = true
    }

    private fun initializeViews() {
        binding.loadingScreen.composeLoadingView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )

        val addClipsAction = {
            playerManager.player?.pause()
            addClipsLauncher.launch(arrayOf("video/*", "audio/*"))
        }

        binding.navBar.btnAddClips.setOnClickListener { addClipsAction() }
        TooltipCompat.setTooltipText(binding.navBar.btnAddClips, getString(R.string.add_video))

        playlistDelegate = com.tazztone.losslesscut.ui.editor.PlaylistDelegate(
            container = binding.playlistArea.root,
            recyclerView = binding.playlistArea.rvClips,
            viewModel = viewModel,
            playerManager = playerManager,
            rotationManager = rotationManager,
            onAddClicked = addClipsAction
        ).also { it.setup() }

        binding.playerSection.btnPlayPause.setOnClickListener { playerManager.togglePlayback() }
        binding.playerSection.btnPlayPauseControls.setOnClickListener { playerManager.togglePlayback() }
        binding.playerSection.playerView.setOnClickListener { playerManager.togglePlayback() }

        binding.navBar.btnHome.setOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        binding.navBar.btnExport.setOnClickListener { 
            val state = viewModel.uiState.value as? VideoEditingUiState.Success
            if (state != null) {
                playerManager.pause()
                exportOptionsController.show(state, rotationManager.currentRotation)
            }
        }
        binding.navBar.btnUndo.setOnClickListener { viewModel.undo() }
        binding.navBar.btnRedo.setOnClickListener { viewModel.redo() }
        
        binding.navBar.btnSettings.setOnClickListener {
            playerManager.pause()
            val bottomSheet = SettingsBottomSheetDialogFragment()
            bottomSheet.show(childFragmentManager, "SettingsBottomSheet")
        }

        binding.editingControls.btnSetIn.setOnClickListener { setInPoint() }
        binding.editingControls.containerSetIn.setOnClickListener { setInPoint() }
        binding.editingControls.btnSetOut.setOnClickListener { setOutPoint() }
        binding.editingControls.containerSetOut.setOnClickListener { setOutPoint() }
        binding.editingControls.btnSplit.setOnClickListener { splitCurrentSegment() }
        binding.editingControls.containerSplit.setOnClickListener { splitCurrentSegment() }
        
        binding.editingControls.btnRotateContainer.setOnClickListener { rotationManager.rotate(90) }
        binding.editingControls.containerRotate.setOnClickListener { rotationManager.rotate(90) }

        binding.playerSection.btnPlaybackSpeed.setOnClickListener { playerManager.cyclePlaybackSpeed() }
        binding.playerSection.btnPlaybackSpeed.setOnLongClickListener {
            val isEnabled = playerManager.togglePitchCorrection()
            val msgRes = if (isEnabled) R.string.pitch_correction_on else R.string.pitch_correction_off
            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
            true
        }
        binding.navBar.btnSnapshot.setOnClickListener { viewModel.extractSnapshot(playerManager.currentPosition) }

        binding.editingControls.btnDelete.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.markSegmentDiscarded(it) }
            }
        }
        binding.editingControls.containerDelete.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.markSegmentDiscarded(it) }
            }
        }

        binding.editingControls.btnSmartCut.setOnClickListener {
            playerManager.pause()
            smartCutController.show()
        }
        binding.editingControls.containerSmartCut.setOnClickListener {
            playerManager.pause()
            smartCutController.show()
        }

        val handleResetAction = {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_segments_confirm_title)
                .setMessage(R.string.reset_segments_confirm_message)
                .setPositiveButton(R.string.reset_segments) { _, _ ->
                    viewModel.resetClipSegments()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.editingControls.btnReset.setOnClickListener { handleResetAction() }
        binding.editingControls.containerReset.setOnClickListener { handleResetAction() }

        binding.playerSection.btnNudgeBack.setOnClickListener { playerManager.seekToKeyframe(-1) }
        binding.playerSection.btnNudgeForward.setOnClickListener { playerManager.seekToKeyframe(1) }
    }

    private fun setupCustomSeeker() {
        seekerDelegate.setup()
        binding.seekerContainer.customVideoSeeker.onSegmentLongPress = { event ->
            segmentActionPopup.show(
                anchorView = binding.seekerContainer.customVideoSeeker,
                event = event,
                onDelete = { viewModel.markSegmentDiscarded(event.segment.id) },
                onSplit = { viewModel.splitSegmentAt(event.timeMs) },
                onDismiss = { binding.seekerContainer.customVideoSeeker.splitPreviewTimeMs = null }
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VideoEditingUiState.Loading -> {
                        binding.loadingScreen.root.visibility = View.VISIBLE
                        binding.loadingScreen.composeLoadingView.setContent {
                            LoadingOverlay(
                                progress = state.progress,
                                message = state.message?.asString(requireContext()),
                                isVisible = true
                            )
                        }
                    }
                    is VideoEditingUiState.Success -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        binding.loadingScreen.composeLoadingView.setContent {}
                        handleSuccessState(state)
                    }
                    is VideoEditingUiState.Error -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        binding.loadingScreen.composeLoadingView.setContent {}
                        showErrorDialog(state.error.asString(requireContext()))
                    }
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is VideoEditingEvent.ShowToast -> {
                        val msg = event.message.asString(requireContext())
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                    is VideoEditingEvent.ExportComplete -> {
                        if (event.success && event.outputUris.isNotEmpty()) {
                            val bottomSheet = com.tazztone.losslesscut.ui.compose.export.ExportSuccessBottomSheetDialogFragment.newInstance(
                                outputUris = event.outputUris,
                                isAudioOnly = event.isAudioOnly
                            )
                            bottomSheet.show(childFragmentManager, "ExportSuccessBottomSheet")
                        }
                        if (event.success && event.deleteOriginalAfterExport && event.sourceUris.isNotEmpty()) {
                            lifecycleScope.launch {
                                val uris = event.sourceUris.map { Uri.parse(it) }
                                when (val result = storageUtils.deleteOriginalMedia(uris)) {
                                    is MediaDeletionResult.Success -> {
                                        Toast.makeText(requireContext(), R.string.original_clip_deleted, Toast.LENGTH_SHORT).show()
                                        viewModel.onOriginalClipsDeleted(uris)
                                    }
                                    is MediaDeletionResult.RequiresPermissionPrompt -> {
                                        pendingDeleteUris = uris
                                        deletePermissionLauncher.launch(
                                            IntentSenderRequest.Builder(result.intentSender).build()
                                        )
                                    }
                                    is MediaDeletionResult.Failed -> {
                                        val msg = getString(R.string.failed_to_delete_original, result.exception.message ?: "")
                                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    is VideoEditingEvent.DismissHints -> binding.seekerContainer.customVideoSeeker.dismissHints()
                    is VideoEditingEvent.SeekToPosition -> playerManager.seekTo(event.positionMs)
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.waveformData.collect { waveform -> binding.seekerContainer.customVideoSeeker.setWaveformData(waveform) }
        }
    }


    private fun handleSuccessState(state: VideoEditingUiState.Success) {
        binding.loadingScreen.root.visibility = View.GONE
        binding.loadingScreen.composeLoadingView.setContent {}
        val selectedClip = state.clips.getOrNull(state.selectedClipIndex) ?: return

        val newStateUris = state.clips.map { Uri.parse(it.uri) }
        val currentUris = playerManager.player?.mediaItemCount?.let { count ->
            (0 until count).map { i -> playerManager.player?.getMediaItemAt(i)?.localConfiguration?.uri }
        } ?: emptyList<Uri?>()

        if (currentUris != newStateUris) {
            playerManager.setMediaItems(newStateUris.filterNotNull(), state.selectedClipIndex)
        } else if (playerManager.currentMediaItemIndex != state.selectedClipIndex) {
            playerManager.seekTo(state.selectedClipIndex, 0L)
        }

        if (lastLoadedClipId != selectedClip.id) {
            binding.seekerContainer.customVideoSeeker.resetView()
            lastLoadedClipId = selectedClip.id
        }

        binding.seekerContainer.customVideoSeeker.setVideoDuration(selectedClip.durationMs)
        playlistDelegate.submitList(state)

        if (state.isAudioOnly) {
            binding.playerSection.playerView.visibility = View.GONE
            binding.playerSection.audioPlaceholder.visibility = View.VISIBLE
            binding.playerSection.tvAudioFileName.text = selectedClip.fileName
        } else {
            binding.playerSection.playerView.visibility = View.VISIBLE
            binding.playerSection.audioPlaceholder.visibility = View.GONE
        }

        if (playerManager.currentPlaybackSpeed != state.playbackSpeed || playerManager.isPitchCorrectionEnabled != state.isPitchCorrectionEnabled) {
            playerManager.updatePlaybackSpeed(state.playbackSpeed, state.isPitchCorrectionEnabled)
        }
        updatePlaybackSpeedUI(state.playbackSpeed)

        binding.seekerContainer.customVideoSeeker.setKeyframes(state.keyframes)
        binding.seekerContainer.customVideoSeeker.setSegments(state.segments, state.selectedSegmentId)
        binding.seekerContainer.customVideoSeeker.detectionPreviewRanges = state.detectionPreviewRanges
        binding.navBar.btnUndo.isEnabled = state.canUndo
        binding.navBar.btnUndo.alpha = if (state.canUndo) 1.0f else 0.5f
        binding.navBar.btnRedo.isEnabled = state.canRedo
        binding.navBar.btnRedo.alpha = if (state.canRedo) 1.0f else 0.5f

        binding.editingControls.btnReset.isEnabled = state.canResetSegments
        binding.editingControls.btnReset.alpha = if (state.canResetSegments) 1.0f else 0.5f
        binding.editingControls.containerReset.isEnabled = state.canResetSegments

        val selectedSeg = state.segments.find { it.id == state.selectedSegmentId }
        val deleteIcon = if (selectedSeg?.action == SegmentAction.DISCARD) {
            R.drawable.ic_restore_24
        } else {
            R.drawable.ic_delete_24
        }
        binding.editingControls.btnDelete.setImageResource(deleteIcon)
    }

    private fun setupBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (smartCutController.isVisible()) {
                    smartCutController.hide()
                    return
                }
                if (!backPressDelegate.handleBackPress()) {
                    isEnabled = false
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        })
    }

    private fun updateDurationDisplay(current: Long, total: Long) {
        if (total <= 0) return
        val currentStr = TimeUtils.formatDuration(current)
        val totalStr = TimeUtils.formatDuration(total)
        binding.playerSection.tvDuration.text = getString(R.string.duration_format, currentStr, totalStr)
    }

    private fun updatePlaybackIcons() {
        val isPlaying = playerManager.isPlaying
        val iconRes = if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
        
        binding.playerSection.btnPlayPause.setImageResource(iconRes)
        binding.playerSection.btnPlayPauseControls.setImageResource(iconRes)

        // Animate central play/pause button visibility
        if (isPlaying) {
            binding.playerSection.btnPlayPause.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.playerSection.btnPlayPause.visibility = View.GONE }
                .start()
        } else {
            binding.playerSection.btnPlayPause.visibility = View.VISIBLE
            binding.playerSection.btnPlayPause.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun updatePlaybackSpeedUI(speed: Float) {
        val formatted = if (speed % 1f == 0f) "${speed.toInt()}x" else String.format(Locale.ROOT, "%.2gx", speed)
        binding.playerSection.btnPlaybackSpeed.text = formatted
    }

    private fun splitCurrentSegment() {
        val currentPos = playerManager.currentPosition
        viewModel.splitSegmentAt(currentPos)
    }

    private fun setInPoint() {
        val currentPos = playerManager.currentPosition
        viewModel.setInPoint(currentPos)
    }

    private fun setOutPoint() {
        val currentPos = playerManager.currentPosition
        viewModel.setOutPoint(currentPos)
    }

    override fun onDestroyView() {
        if (::segmentActionPopup.isInitialized) {
            segmentActionPopup.dismiss()
        }
        super.onDestroyView()
        _binding = null
    }
}
