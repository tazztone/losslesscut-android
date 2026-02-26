package com.tazztone.losslesscut.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.*
import com.tazztone.losslesscut.util.asString
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingEvent
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.viewmodel.ExportSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

@UnstableApi
@AndroidEntryPoint
class EditorFragment : BaseEditingFragment(R.layout.fragment_editor), SettingsBottomSheetDialogFragment.SettingsListener {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var rotationManager: RotationManager
    private lateinit var shortcutHandler: ShortcutHandler
    private lateinit var clipAdapter: MediaClipAdapter
    
    private lateinit var progressTicker: com.tazztone.losslesscut.ui.editor.PlaybackProgressTicker
    private lateinit var seekerDelegate: com.tazztone.losslesscut.ui.editor.TimelineSeekerDelegate
    private lateinit var addClipsDelegate: com.tazztone.losslesscut.ui.editor.AddClipsDelegate
    private lateinit var smartCutController: com.tazztone.losslesscut.ui.editor.SmartCutOverlayController
    private lateinit var exportOptionsController: com.tazztone.losslesscut.ui.editor.ExportOptionsDialogPresenter
    private lateinit var backPressDelegate: com.tazztone.losslesscut.ui.editor.BackPressDelegate
    
    private var isDraggingTimeline = false
    private var isLosslessMode = true
    private var lastLoadedClipId: UUID? = null

    private val addClipsLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addClipsDelegate.onClipsReceived(uris)
    }

    override fun getPlayerView() = binding.playerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditorBinding.bind(view)
        
        playerManager = PlayerManager(
            context = requireContext(),
            playerView = binding.playerView,
            viewModel = viewModel,
            onStateChanged = { state ->
                if (state == Player.STATE_READY) {
                    seekerDelegate.setVideoDuration(playerManager.duration)
                    updateDurationDisplay(playerManager.currentPosition, playerManager.duration)
                    binding.customVideoSeeker.setSeekPosition(playerManager.currentPosition)
                }
                updatePlaybackIcons()
            },
            onMediaTransition = { index ->
                if (::clipAdapter.isInitialized) {
                    val currentState = viewModel.uiState.value as? VideoEditingUiState.Success
                    val clipId = currentState?.clips?.getOrNull(index)?.id
                    clipAdapter.updateSelection(clipId)
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
            binding = binding,
            playerManager = playerManager,
            onUpdate = { current, total -> updateDurationDisplay(current, total) }
        )

        seekerDelegate = com.tazztone.losslesscut.ui.editor.TimelineSeekerDelegate(
            binding = binding,
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
        )

        exportOptionsController = com.tazztone.losslesscut.ui.editor.ExportOptionsDialogPresenter(
            requireContext(),
            layoutInflater
        ) { keepAudio, keepVideo, mergeSegments, selectedTracks ->
            val rot = if (rotationManager.currentRotation != 0) rotationManager.currentRotation else null
            val settings = ExportSettings(
                isLosslessMode, keepAudio, keepVideo, rot, mergeSegments, selectedTracks
            )
            viewModel.exportSegments(settings)
        }

        backPressDelegate = com.tazztone.losslesscut.ui.editor.BackPressDelegate(
            context = requireContext(),
            isDirty = viewModel.isDirty,
            onConfirmExit = {
                // Disable dirty check and exit
                viewModel.clearDirty()
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        )

        rotationManager = RotationManager(
            badgeRotate = binding.badgeRotate,
            btnRotate = binding.btnRotate,
            tvRotateEmoji = binding.tvRotateEmoji,
            btnRotateContainer = binding.btnRotateContainer,
            playerView = binding.playerView
        )

        shortcutHandler = ShortcutHandler(
            viewModel = viewModel,
            playerManager = playerManager,
            launchMode = VideoEditingActivity.MODE_CUT,
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

        savedInstanceState?.let {
            isLosslessMode = it.getBoolean("lossless_mode", true)
            binding.customVideoSeeker.isLosslessMode = isLosslessMode
        }
    }

    private fun initializeViews() {
        val addClipsAction = {
            playerManager.player?.pause()
            addClipsLauncher.launch(arrayOf("video/*", "audio/*"))
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                clipAdapter.moveItemVisual(from, to)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    clipAdapter.startDrag(viewHolder.bindingAdapterPosition)
                }
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                clipAdapter.commitPendingMove(viewHolder.bindingAdapterPosition)
            }
            override fun isLongPressDragEnabled(): Boolean = false
        })

        binding.btnAddClips?.setOnClickListener { addClipsAction() }
        TooltipCompat.setTooltipText(binding.btnAddClips!!, getString(R.string.add_video))

        clipAdapter = MediaClipAdapter(
            onClipSelected = { index -> 
                val currentState = viewModel.uiState.value as? VideoEditingUiState.Success
                if (currentState != null && index != currentState.selectedClipIndex) {
                    rotationManager.setRotation(0, animate = false)
                    viewModel.selectClip(index)
                }
            },
            onClipsReordered = { from, to -> 
                viewModel.reorderClips(from, to)
                playerManager.moveMediaItem(from, to)
            },
            onClipLongPressed = { index ->
                val clip = (viewModel.uiState.value as? VideoEditingUiState.Success)?.clips?.getOrNull(index)
                if (clip != null) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.delete))
                        .setMessage(getString(R.string.remove_clip_confirm))
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            viewModel.removeClip(index)
                            playerManager.removeMediaItem(index)
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            },
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            onAddClicked = { addClipsAction() }
        )
        binding.rvClips?.adapter = clipAdapter
        binding.rvClips?.let { itemTouchHelper.attachToRecyclerView(it) }

        binding.btnPlayPause?.setOnClickListener { playerManager.togglePlayback() }
        binding.btnPlayPauseControls?.setOnClickListener { playerManager.togglePlayback() }
        binding.playerView.setOnClickListener { playerManager.togglePlayback() }

        binding.btnHome.setOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
        binding.btnExport?.setOnClickListener { 
            val state = viewModel.uiState.value as? VideoEditingUiState.Success
            if (state != null) {
                playerManager.pause()
                exportOptionsController.show(state)
            }
        }
        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnRedo?.setOnClickListener { viewModel.redo() }
        
        binding.btnSettings?.setOnClickListener {
            playerManager.pause()
            val bottomSheet = SettingsBottomSheetDialogFragment()
            bottomSheet.setInitialState(isLosslessMode)
            bottomSheet.setSettingsListener(this)
            bottomSheet.show(childFragmentManager, "SettingsBottomSheet")
        }

        binding.btnSetIn?.setOnClickListener { setInPoint() }
        binding.containerSetIn?.setOnClickListener { setInPoint() }
        binding.btnSetOut?.setOnClickListener { setOutPoint() }
        binding.containerSetOut?.setOnClickListener { setOutPoint() }
        binding.btnSplit.setOnClickListener { splitCurrentSegment() }
        binding.containerSplit?.setOnClickListener { splitCurrentSegment() }
        
        binding.btnRotateContainer.setOnClickListener { rotationManager.rotate(90) }
        binding.containerRotate?.setOnClickListener { rotationManager.rotate(90) }

        binding.btnPlaybackSpeed?.setOnClickListener { playerManager.cyclePlaybackSpeed() }
        binding.btnPlaybackSpeed?.setOnLongClickListener {
            val isEnabled = playerManager.togglePitchCorrection()
            val msgRes = if (isEnabled) R.string.pitch_correction_on else R.string.pitch_correction_off
            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
            true
        }
        binding.btnSnapshot.setOnClickListener { viewModel.extractSnapshot(playerManager.currentPosition) }

        binding.btnDelete.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.markSegmentDiscarded(it) }
            }
        }
        binding.containerDelete?.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.markSegmentDiscarded(it) }
            }
        }

        binding.btnSmartCut?.setOnClickListener {
            playerManager.pause()
            smartCutController.show()
        }
        binding.containerSmartCut?.setOnClickListener {
            playerManager.pause()
            smartCutController.show()
        }

        binding.btnNudgeBack?.setOnClickListener { playerManager.seekToKeyframe(-1) }
        binding.btnNudgeForward?.setOnClickListener { playerManager.seekToKeyframe(1) }
    }

    private fun setupCustomSeeker() {
        seekerDelegate.setup()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VideoEditingUiState.Loading -> {
                        binding.loadingScreen.root.visibility = View.VISIBLE
                        binding.loadingScreen.loadingProgress.visibility = if (state.progress > 0) View.VISIBLE else View.GONE
                        binding.loadingScreen.loadingProgress.progress = state.progress
                        binding.loadingScreen.tvLoadingStatus.text = state.message?.asString(requireContext())
                    }
                    is VideoEditingUiState.Success -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        val selectedClip = state.clips[state.selectedClipIndex]
                        
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
                            binding.customVideoSeeker.resetView()
                            lastLoadedClipId = selectedClip.id
                        }
                        
                        binding.customVideoSeeker.setVideoDuration(selectedClip.durationMs)
                        if (state.clips.size > 1) {
                            binding.playlistContainer?.visibility = View.VISIBLE
                            clipAdapter.submitList(state.clips)
                            clipAdapter.updateSelection(selectedClip.id)
                        } else {
                            binding.playlistContainer?.visibility = View.GONE
                        }
                        
                        if (state.isAudioOnly) {
                            binding.playerView.visibility = View.GONE
                            binding.audioPlaceholder?.visibility = View.VISIBLE
                            binding.tvAudioFileName?.text = selectedClip.fileName
                        } else {
                            binding.playerView.visibility = View.VISIBLE
                            binding.audioPlaceholder?.visibility = View.GONE
                        }

                        if (playerManager.currentPlaybackSpeed != state.playbackSpeed || playerManager.isPitchCorrectionEnabled != state.isPitchCorrectionEnabled) {
                            playerManager.updatePlaybackSpeed(state.playbackSpeed, state.isPitchCorrectionEnabled)
                        }
                        updatePlaybackSpeedUI(state.playbackSpeed)

                        binding.customVideoSeeker.setKeyframes(state.keyframes)
                        binding.customVideoSeeker.setSegments(state.segments, state.selectedSegmentId)
                        binding.customVideoSeeker.detectionPreviewRanges = state.detectionPreviewRanges
                        binding.btnUndo.isEnabled = state.canUndo
                        binding.btnUndo.alpha = if (state.canUndo) 1.0f else 0.5f
                        binding.btnRedo?.isEnabled = state.canRedo
                        binding.btnRedo?.alpha = if (state.canRedo) 1.0f else 0.5f

                        val selectedSeg = state.segments.find { it.id == state.selectedSegmentId }
                        binding.btnDelete.setImageResource(if (selectedSeg?.action == SegmentAction.DISCARD) R.drawable.ic_restore_24 else R.drawable.ic_delete_24)
                    }
                    is VideoEditingUiState.Error -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        showErrorDialog(state.error.asString(requireContext()))
                    }
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is VideoEditingEvent.ShowToast -> Toast.makeText(requireContext(), event.message.asString(requireContext()), Toast.LENGTH_LONG).show()
                    is VideoEditingEvent.ExportComplete -> { /* keep playing or show results */ }
                    is VideoEditingEvent.DismissHints -> binding.customVideoSeeker.dismissHints()
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.waveformData.collect { waveform -> binding.customVideoSeeker.setWaveformData(waveform) }
        }
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
        binding.tvDuration?.text = getString(R.string.duration_format, TimeUtils.formatDuration(current), TimeUtils.formatDuration(total))
    }

    private fun updatePlaybackIcons() {
        val isPlaying = playerManager.isPlaying
        val iconRes = if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
        binding.btnPlayPause?.setImageResource(iconRes)
        binding.btnPlayPauseControls?.setImageResource(iconRes)
    }

    private fun updatePlaybackSpeedUI(speed: Float) {
        val formatted = if (speed % 1f == 0f) "${speed.toInt()}x" else String.format("%.2gx", speed)
        binding.btnPlaybackSpeed?.text = formatted
    }

    private fun splitCurrentSegment() {
        val currentPos = playerManager.currentPosition
        viewModel.splitSegmentAt(currentPos)
    }

    private fun setInPoint() {
        val state = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        val currentSeg = state.segments.find { it.id == state.selectedSegmentId } ?: return
        val currentPos = playerManager.currentPosition
        viewModel.updateSegmentBounds(state.selectedSegmentId ?: return, currentPos, currentSeg.endMs)
        viewModel.commitSegmentBounds()
    }

    private fun setOutPoint() {
        val state = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        val currentSeg = state.segments.find { it.id == state.selectedSegmentId } ?: return
        val currentPos = playerManager.currentPosition
        viewModel.updateSegmentBounds(state.selectedSegmentId ?: return, currentSeg.startMs, currentPos)
        viewModel.commitSegmentBounds()
    }

    override fun onLosslessModeToggled(isChecked: Boolean) {
        isLosslessMode = isChecked
        binding.customVideoSeeker.isLosslessMode = isChecked
        binding.customVideoSeeker.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
