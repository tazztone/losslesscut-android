package com.tazztone.losslesscut.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.ActivityVideoEditingBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.data.MediaClip
import com.tazztone.losslesscut.data.SegmentAction
import com.tazztone.losslesscut.data.TrimSegment
import com.tazztone.losslesscut.utils.TimeUtils
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

@UnstableApi
@AndroidEntryPoint
class VideoEditingActivity : AppCompatActivity(), SettingsBottomSheetDialogFragment.SettingsListener {
 
    companion object {
        const val EXTRA_VIDEO_URIS = "com.tazztone.losslesscut.EXTRA_VIDEO_URIS"
        const val EXTRA_LAUNCH_MODE = "com.tazztone.losslesscut.EXTRA_LAUNCH_MODE"

        const val MODE_CUT      = "cut"
        const val MODE_REMUX    = "remux"
        const val MODE_METADATA = "metadata"

        private const val KEY_PLAYHEAD = "playhead_pos"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_ROTATION = "rotation_offset"
        private const val KEY_LOSSLESS_MODE = "lossless_mode"
        private const val TAG = "VideoEditingActivity"
    }
 
    private val viewModel: VideoEditingViewModel by viewModels()
    private lateinit var binding: ActivityVideoEditingBinding
    private lateinit var player: ExoPlayer
    private lateinit var clipAdapter: MediaClipAdapter
    private lateinit var launchMode: String

    private var isVideoLoaded = false
    private var updateJob: Job? = null
    private var isDraggingTimeline = false
    private var currentRotation = 0
    private var isLosslessMode = true
    private var currentPlaybackSpeed = 1.0f
    
    private var savedPlayheadPos = 0L
    private var savedPlayWhenReady = false
    
    private val addClipsLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val currentState = viewModel.uiState.value
            if (currentState is VideoEditingUiState.Success) {
                val existingUris = currentState.clips.map { it.uri }.toSet()
                val duplicateUris = uris.filter { it in existingUris }
                val nonDuplicateUris = uris.filter { it !in existingUris }

                if (duplicateUris.isEmpty()) {
                    viewModel.addClips(uris)
                } else if (nonDuplicateUris.isEmpty()) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.add_video)
                        .setMessage(R.string.duplicate_files_msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.add_video)
                        .setMessage(getString(R.string.duplicate_files_confirm_msg, duplicateUris.size))
                        .setPositiveButton(R.string.skip_duplicates) { _, _ ->
                            viewModel.addClips(nonDuplicateUris)
                        }
                        .setNegativeButton(R.string.import_all) { _, _ ->
                            viewModel.addClips(uris)
                        }
                        .setNeutralButton(android.R.string.cancel, null)
                        .show()
                }
            } else {
                viewModel.addClips(uris)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player.currentMediaItemIndex
            viewModel.selectClip(index)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && !isVideoLoaded) {
                isVideoLoaded = true
                binding.customVideoSeeker.setVideoDuration(player.duration)
                updateDurationDisplay(player.currentPosition, player.duration)
                binding.customVideoSeeker.setSeekPosition(player.currentPosition)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::binding.isInitialized) {
                binding.btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
                binding.btnPlayPauseControls?.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
                if (isPlaying) {
                    binding.btnPlayPause?.animate()?.alpha(0f)?.setStartDelay(500)?.setDuration(300)?.start()
                } else {
                    binding.btnPlayPause?.animate()?.cancel()
                    binding.btnPlayPause?.alpha = 1f
                }
            }
            if (isPlaying) {
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        launchMode = intent.getStringExtra(EXTRA_LAUNCH_MODE) ?: MODE_CUT

        val videoUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_URIS)
        }
        
        if (videoUris.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_video_uri), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        hideSystemUI()

        initializeViews()
        initializePlayer()
        setupCustomSeeker()
        observeViewModel()

        if (launchMode != MODE_CUT) {
            binding.btnAddClips?.visibility = View.GONE
            binding.btnUndo?.visibility = View.GONE
            binding.btnSnapshot?.visibility = View.GONE
            binding.btnSettings?.visibility = View.GONE
            binding.containerAddClips?.visibility = View.GONE
            binding.containerUndo?.visibility = View.GONE
            binding.containerSnapshot?.visibility = View.GONE
            binding.containerSettings?.visibility = View.GONE
        }

        viewModel.initialize(videoUris)

        when (launchMode) {
            MODE_REMUX    -> configureForRemux()
            MODE_METADATA -> configureForMetadata()
            else          -> { /* default cut UI */ }
        }
        
        // Check for saved session
        lifecycleScope.launch {
            if (launchMode == MODE_CUT && viewModel.hasSavedSession(videoUris[0])) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@VideoEditingActivity)
                    .setTitle(R.string.restore_session_title)
                    .setMessage(R.string.restore_session_message)
                    .setPositiveButton(R.string.restore) { _, _ ->
                        viewModel.restoreSession(videoUris[0])
                    }
                    .setNegativeButton(R.string.ignore, null)
                    .show()
            }
        }
        
        savedInstanceState?.let {
            savedPlayheadPos = it.getLong(KEY_PLAYHEAD, 0L)
            savedPlayWhenReady = it.getBoolean(KEY_PLAY_WHEN_READY, false)
            currentRotation = it.getInt(KEY_ROTATION, 0)
            updateRotationPreview(animate = false)
            isLosslessMode = it.getBoolean(KEY_LOSSLESS_MODE, true)
            if (::binding.isInitialized) {
                binding.customVideoSeeker.isLosslessMode = isLosslessMode
            }
        }

        setupBackPressed()
    }

    private fun setupBackPressed() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (launchMode == MODE_CUT && viewModel.isDirty.value) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@VideoEditingActivity)
                        .setTitle(R.string.exit_confirm_title)
                        .setMessage(R.string.exit_confirm_message)
                        .setPositiveButton(R.string.exit_confirm_discard) { _, _ ->
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .setNegativeButton(R.string.exit_confirm_keep_editing, null)
                        .show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (::player.isInitialized && player.isPlaying) startProgressUpdate()
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.pause()
            if (launchMode == MODE_CUT) viewModel.saveSession()
        }
        stopProgressUpdate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::player.isInitialized) {
            outState.putLong(KEY_PLAYHEAD, player.currentPosition)
            outState.putBoolean(KEY_PLAY_WHEN_READY, player.playWhenReady)
        }
        outState.putInt(KEY_ROTATION, currentRotation)
        outState.putBoolean(KEY_LOSSLESS_MODE, isLosslessMode)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun initializeViews() {
        val addClipsAction = {
            addClipsLauncher.launch(arrayOf("video/*", "audio/*"))
        }

        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val success = clipAdapter.moveItem(from, to)
                return success
            }
            override fun onSwiped(vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, dir: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        })

        clipAdapter = MediaClipAdapter(
            onClipSelected = { index -> 
                val currentState = viewModel.uiState.value as? VideoEditingUiState.Success
                if (currentState != null && index != currentState.selectedClipIndex) {
                    currentRotation = 0
                    viewModel.selectClip(index)
                }
            },
            onClipsReordered = { from, to -> 
                viewModel.reorderClips(from, to)
                // Update player playlist order
                player.moveMediaItem(from, to)
            },
            onClipLongPressed = { index ->
                val clip = (viewModel.uiState.value as? VideoEditingUiState.Success)?.clips?.getOrNull(index)
                if (clip != null) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.delete))
                        .setMessage(getString(R.string.remove_clip_confirm))
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            viewModel.removeClip(index)
                            player.removeMediaItem(index)
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onAddClicked = { addClipsAction() }
        )
        binding.rvClips?.adapter = clipAdapter
        binding.rvClips?.let { itemTouchHelper.attachToRecyclerView(it) }

        binding.btnPlayPause?.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        binding.btnPlayPause?.let { TooltipCompat.setTooltipText(it, getString(R.string.play_pause)) }

        binding.playerView.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        
        try { 
            binding.loadingScreen.lottieAnimation.playAnimation() 
        } catch (e: Exception) {
            Log.e(TAG, "Lottie animation failed to play", e)
        }

        binding.btnHome.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        TooltipCompat.setTooltipText(binding.btnHome, getString(R.string.home))

        binding.btnAddClips?.setOnClickListener { addClipsAction() }
        binding.btnAddClips?.let { TooltipCompat.setTooltipText(it, getString(R.string.add_video)) }
        
        binding.btnSave.setOnClickListener { 
            when (launchMode) {
                MODE_REMUX -> showRemuxDialog()
                MODE_METADATA -> showMetadataDialog()
                else -> showExportOptionsDialog()
            }
        }
        TooltipCompat.setTooltipText(binding.btnSave, getString(R.string.export))

        binding.btnSettings?.setOnClickListener {
            val bottomSheet = SettingsBottomSheetDialogFragment()
            bottomSheet.setInitialState(isLosslessMode)
            bottomSheet.show(supportFragmentManager, "SettingsBottomSheet")
        }
        binding.btnSettings?.let { TooltipCompat.setTooltipText(it, getString(R.string.settings)) }

        binding.btnUndo.setOnClickListener { viewModel.undo() }
        TooltipCompat.setTooltipText(binding.btnUndo, getString(R.string.undo))
        
        val setInAction = { setInPoint() }
        binding.btnSetIn?.setOnClickListener { setInAction() }
        binding.containerSetIn?.setOnClickListener { setInAction() }
        binding.btnSetIn?.let { TooltipCompat.setTooltipText(it, getString(R.string.set_in_point)) }
        binding.containerSetIn?.let { TooltipCompat.setTooltipText(it, getString(R.string.set_in_point)) }
        
        val setOutAction = { setOutPoint() }
        binding.btnSetOut?.setOnClickListener { setOutAction() }
        binding.containerSetOut?.setOnClickListener { setOutAction() }
        binding.btnSetOut?.let { TooltipCompat.setTooltipText(it, getString(R.string.set_out_point)) }
        binding.containerSetOut?.let { TooltipCompat.setTooltipText(it, getString(R.string.set_out_point)) }
        
        val splitAction = { splitCurrentSegment() }
        binding.btnSplit.setOnClickListener { splitAction() }
        binding.containerSplit?.setOnClickListener { splitAction() }
        TooltipCompat.setTooltipText(binding.btnSplit, getString(R.string.split))
        binding.containerSplit?.let { TooltipCompat.setTooltipText(it, getString(R.string.split)) }
        
        val deleteAction = { 
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.markSegmentDiscarded(it) }
            }
        }
        binding.btnDelete.setOnClickListener { deleteAction() }
        binding.containerDelete?.setOnClickListener { deleteAction() }
        TooltipCompat.setTooltipText(binding.btnDelete, getString(R.string.discard_segment))
        binding.containerDelete?.let { TooltipCompat.setTooltipText(it, getString(R.string.discard_segment)) }

        binding.btnNudgeBack?.setOnClickListener { performNudge(-1) }
        binding.btnNudgeBack?.let { TooltipCompat.setTooltipText(it, getString(R.string.nudge_backward)) }
        
        binding.btnNudgeForward?.setOnClickListener { performNudge(1) }
        binding.btnNudgeForward?.let { TooltipCompat.setTooltipText(it, getString(R.string.nudge_forward)) }

        binding.btnPlayPauseControls?.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        binding.btnPlayPauseControls?.let { TooltipCompat.setTooltipText(it, getString(R.string.play_pause)) }

        binding.btnPlaybackSpeed?.setOnClickListener {
            cyclePlaybackSpeed()
        }

        val snapshotAction = { extractSnapshot() }
        binding.btnSnapshot.setOnClickListener { snapshotAction() }
        TooltipCompat.setTooltipText(binding.btnSnapshot, getString(R.string.snapshot))

        val silenceCutAction = { showSilenceDetectionDialog() }
        binding.btnSilenceCut?.setOnClickListener { silenceCutAction() }
        binding.containerSilenceCut?.setOnClickListener { silenceCutAction() }
        binding.btnSilenceCut?.let { TooltipCompat.setTooltipText(it, getString(R.string.auto_detect_silence)) }
        binding.containerSilenceCut?.let { TooltipCompat.setTooltipText(it, getString(R.string.auto_detect_silence)) }
        
        val rotateAction = { 
            currentRotation = (currentRotation + 90) % 360
            updateRotationPreview(animate = true)
            Toast.makeText(this, getString(R.string.export_rotation_offset, currentRotation), Toast.LENGTH_SHORT).show()
        }
        binding.btnRotateContainer.setOnClickListener { rotateAction() }
        binding.containerRotate?.setOnClickListener { rotateAction() }
        TooltipCompat.setTooltipText(binding.btnRotateContainer, getString(R.string.rotate))
        binding.containerRotate?.let { TooltipCompat.setTooltipText(it, getString(R.string.rotate)) }
    }

    private fun updateRotationPreview(animate: Boolean = true) {
        // Hide the degree text badge since the icon is now the visual indicator
        binding.badgeRotate?.visibility = View.GONE
        
        val isZero = currentRotation == 0
        binding.btnRotate.visibility = if (isZero) View.VISIBLE else View.GONE
        binding.tvRotateEmoji.visibility = if (isZero) View.GONE else View.VISIBLE
        
        // Always rotate the container. This makes the logic much simpler and more robust.
        if (animate) {
            binding.btnRotateContainer.animate()
                .rotation(currentRotation.toFloat())
                .setDuration(250)
                .start()
        } else {
            binding.btnRotateContainer.rotation = currentRotation.toFloat()
        }

        // Ensure the video player stays completely un-squished and un-rotated
        binding.playerView.scaleX = 1f
        binding.playerView.scaleY = 1f
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_SPACE -> {
                    if (player.isPlaying) player.pause() else player.play()
                    return true
                }
                android.view.KeyEvent.KEYCODE_I -> {
                    setInPoint()
                    return true
                }
                android.view.KeyEvent.KEYCODE_O -> {
                    setOutPoint()
                    return true
                }
                android.view.KeyEvent.KEYCODE_S -> {
                    splitCurrentSegment()
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.isAltPressed) {
                        performNudge(-1)
                    } else {
                        player.seekToPrevious()
                    }
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.isAltPressed) {
                        performNudge(1)
                    } else {
                        player.seekToNext()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun splitCurrentSegment() {
        val currentPos = player.currentPosition
        val state = viewModel.uiState.value as? VideoEditingUiState.Success
        
        val splitPos = if (isLosslessMode && state?.keyframes?.isNotEmpty() == true) {
            state.keyframes.minByOrNull { kotlin.math.abs(it - currentPos) } ?: currentPos
        } else {
            currentPos
        }

        viewModel.splitSegmentAt(splitPos)
        player.seekTo(splitPos)
        binding.customVideoSeeker.setSeekPosition(splitPos)
    }

    private fun setInPoint() {
        val state = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        val currentPos = player.currentPosition
        
        val snapPos = if (isLosslessMode && state.keyframes.isNotEmpty()) {
            state.keyframes.minByOrNull { kotlin.math.abs(it - currentPos) } ?: currentPos
        } else {
            currentPos
        }

        val keepSegments = state.segments.filter { it.action == SegmentAction.KEEP }.sortedBy { it.startMs }

        val segment = keepSegments.find { it.id == state.selectedSegmentId }
            ?: keepSegments.find { snapPos in it.startMs..it.endMs }
            ?: keepSegments.filter { it.startMs > snapPos }.minByOrNull { it.startMs }
            ?: return

        val currentIndex = keepSegments.indexOfFirst { it.id == segment.id }
        val prevSegment = if (currentIndex > 0) keepSegments[currentIndex - 1] else null
        val minAllowed = prevSegment?.endMs ?: 0L
        
        val newStart = snapPos.coerceIn(minAllowed, segment.endMs - VideoEditingViewModel.MIN_SEGMENT_DURATION_MS)

        if (newStart < segment.endMs) {
            viewModel.updateSegmentBounds(segment.id, newStart, segment.endMs)
            viewModel.commitSegmentBounds()
            player.seekTo(newStart)
            binding.customVideoSeeker.setSeekPosition(newStart)
        }
    }

    private fun setOutPoint() {
        val state = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        val currentPos = player.currentPosition
        
        val snapPos = if (isLosslessMode && state.keyframes.isNotEmpty()) {
            state.keyframes.minByOrNull { kotlin.math.abs(it - currentPos) } ?: currentPos
        } else {
            currentPos
        }

        val keepSegments = state.segments.filter { it.action == SegmentAction.KEEP }.sortedBy { it.startMs }

        val segment = keepSegments.find { it.id == state.selectedSegmentId }
            ?: keepSegments.find { snapPos in it.startMs..it.endMs }
            ?: keepSegments.filter { it.endMs < snapPos }.maxByOrNull { it.endMs }
            ?: return

        val currentIndex = keepSegments.indexOfFirst { it.id == segment.id }
        val nextSegment = if (currentIndex >= 0 && currentIndex < keepSegments.size - 1) keepSegments[currentIndex + 1] else null
        val maxAllowed = nextSegment?.startMs ?: Long.MAX_VALUE
        
        val newEnd = snapPos.coerceIn(segment.startMs + VideoEditingViewModel.MIN_SEGMENT_DURATION_MS, maxAllowed)

        if (newEnd > segment.startMs) {
            viewModel.updateSegmentBounds(segment.id, segment.startMs, newEnd)
            viewModel.commitSegmentBounds()
            player.seekTo(newEnd)
            binding.customVideoSeeker.setSeekPosition(newEnd)
        }
    }

    private fun startProgressUpdate() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            while (isActive && player.isPlaying) {
                if (!isDraggingTimeline) {
                    binding.customVideoSeeker.setSeekPosition(player.currentPosition)
                    updateDurationDisplay(player.currentPosition, player.duration)
                }
                delay(30)
            }
        }
    }

    private fun stopProgressUpdate() {
        updateJob?.cancel()
    }

    private fun performNudge(direction: Int) {
        val currentState = viewModel.uiState.value as? VideoEditingUiState.Success ?: return

        if (isLosslessMode && currentState.keyframes.isNotEmpty()) {
            val keyframesMs = currentState.keyframes.sorted()
            val currentPos = player.currentPosition
            
            val targetKf = if (direction > 0) {
                keyframesMs.firstOrNull { it > currentPos + 10 } ?: player.duration
            } else {
                keyframesMs.lastOrNull { it < currentPos - 10 } ?: 0L
            }
            player.seekTo(targetKf)
            binding.customVideoSeeker.setSeekPosition(targetKf)
            updateDurationDisplay(targetKf, player.duration)
        } else {
            val step = if (currentState.videoFps > 0f) (1000L / currentState.videoFps).toLong() else 33L
            val target = (player.currentPosition + (direction * step)).coerceIn(0, player.duration)
            player.seekTo(target)
            binding.customVideoSeeker.setSeekPosition(target)
            updateDurationDisplay(target, player.duration)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VideoEditingUiState.Loading -> binding.loadingScreen.root.visibility = View.VISIBLE
                    is VideoEditingUiState.Success -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        
                        val selectedClip = state.clips[state.selectedClipIndex]
                        
                        // Check if we need to initialize or update the player items
                        val currentUris = List(player.mediaItemCount) { player.getMediaItemAt(it).localConfiguration?.uri }
                        val newStateUris = state.clips.map { it.uri }
                        
                        if (currentUris != newStateUris) {
                            initializePlayer(newStateUris, state.selectedClipIndex)
                            binding.customVideoSeeker.resetView()
                        } else if (player.currentMediaItemIndex != state.selectedClipIndex) {
                            player.seekTo(state.selectedClipIndex, 0L)
                            binding.customVideoSeeker.resetView()
                        }

                        // Update timeline duration for the selected clip
                        binding.customVideoSeeker.setVideoDuration(selectedClip.durationMs)
                        
                        // Update clip list visibility and adapter
                        if (state.clips.size > 1) {
                            binding.playlistContainer?.visibility = View.VISIBLE
                            clipAdapter.submitList(state.clips)
                            clipAdapter.updateSelection(state.selectedClipIndex)
                        } else {
                            binding.playlistContainer?.visibility = View.GONE
                        }
                        
                        if (state.isAudioOnly) {
                            binding.playerView.visibility = View.GONE
                            binding.audioPlaceholder?.visibility = View.VISIBLE
                            binding.tvAudioFileName?.text = state.clips[state.selectedClipIndex].fileName
                            binding.containerRotate?.visibility = View.GONE
                            binding.containerSnapshot?.visibility = View.GONE
                        } else {
                            binding.playerView.visibility = View.VISIBLE
                            binding.audioPlaceholder?.visibility = View.GONE
                            if (launchMode == MODE_CUT) {
                                binding.containerRotate?.visibility = View.VISIBLE
                                binding.containerSnapshot?.visibility = View.VISIBLE
                            }
                        }

                        binding.customVideoSeeker.setKeyframes(state.keyframes)
                        binding.customVideoSeeker.setSegments(state.segments, state.selectedSegmentId)
                        binding.customVideoSeeker.silencePreviewRanges = state.silencePreviewRanges
                        binding.btnUndo.isEnabled = state.canUndo
                        binding.btnUndo.alpha = if (state.canUndo) 1.0f else 0.5f

                        val selectedSeg = state.segments.find { it.id == state.selectedSegmentId }
                        if (selectedSeg != null && selectedSeg.action == SegmentAction.DISCARD) {
                            binding.btnDelete.setImageResource(R.drawable.ic_restore_24)
                        } else {
                            binding.btnDelete.setImageResource(R.drawable.ic_delete_24)
                        }
                    }
                    is VideoEditingUiState.Error -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        Toast.makeText(this@VideoEditingActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            viewModel.uiEvents.collect { message ->
                Toast.makeText(this@VideoEditingActivity, message, Toast.LENGTH_LONG).show()
            }
        }
        lifecycleScope.launch {
            viewModel.waveformData.collect { waveform ->
                binding.customVideoSeeker.setWaveformData(waveform)
            }
        }
    }

    private fun initializePlayer() {
        isVideoLoaded = false
        player = ExoPlayer.Builder(this).build().apply {
            binding.playerView.player = this
            addListener(playerListener)
        }
    }

    private fun initializePlayer(uris: List<Uri>, initialIndex: Int = 0) {
        val mediaItems = uris.map { MediaItem.fromUri(it) }
        player.setMediaItems(mediaItems)
        player.prepare()
        player.playWhenReady = savedPlayWhenReady
        player.seekTo(initialIndex, savedPlayheadPos)
        
        // Reset speed to 1.0x when loading new media
        currentPlaybackSpeed = 1.0f
        updatePlaybackSpeedUI()
    }

    private fun cyclePlaybackSpeed() {
        currentPlaybackSpeed = when (currentPlaybackSpeed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.5f
            else -> 1.0f
        }
        updatePlaybackSpeedUI()
    }

    private fun updatePlaybackSpeedUI() {
        binding.btnPlaybackSpeed?.text = getString(R.string.speed_format, currentPlaybackSpeed)
        if (::player.isInitialized) {
            player.playbackParameters = androidx.media3.common.PlaybackParameters(currentPlaybackSpeed)
        }
    }

    private fun setupCustomSeeker() {
        binding.customVideoSeeker.onSeekStart = {
            isDraggingTimeline = true
            player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        }
        
        binding.customVideoSeeker.onSeekEnd = {
            isDraggingTimeline = false
            player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
        }

        binding.customVideoSeeker.onSeekListener = { seekPositionMs ->
            player.seekTo(seekPositionMs)
            updateDurationDisplay(seekPositionMs, player.duration)
        }
        
        binding.customVideoSeeker.onSegmentSelected = { id ->
            viewModel.selectSegment(id)
        }
        
        binding.customVideoSeeker.onSegmentBoundsChanged = { id, start, end, seekPos ->
            isDraggingTimeline = true
            viewModel.updateSegmentBounds(id, start, end)
            player.seekTo(seekPos)
            updateDurationDisplay(seekPos, player.duration)
        }
        
        binding.customVideoSeeker.onSegmentBoundsDragEnd = {
            isDraggingTimeline = false
            viewModel.commitSegmentBounds()
        }
    }

    private fun extractSnapshot() {
        if (viewModel.uiState.value !is VideoEditingUiState.Success) return
        viewModel.extractSnapshot(player.currentPosition)
    }

    private fun showExportOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbKeepVideo = dialogView.findViewById<android.widget.CheckBox>(R.id.cbKeepVideo)
        val cbKeepAudio = dialogView.findViewById<android.widget.CheckBox>(R.id.cbKeepAudio)
        val cbMergeSegments = dialogView.findViewById<android.widget.CheckBox>(R.id.cbMergeSegments)
        val tvTracksHeader = dialogView.findViewById<android.widget.TextView>(R.id.tvTracksHeader)
        val tracksContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.tracksContainer)
        
        val currentState = viewModel.uiState.value as? VideoEditingUiState.Success
        val totalKeepSegments = currentState?.clips?.sumOf { clip -> 
            clip.segments.count { it.action == SegmentAction.KEEP } 
        } ?: 0
        
        if (totalKeepSegments > 1 || (currentState?.clips?.size ?: 0) > 1) {
            cbMergeSegments.visibility = android.view.View.VISIBLE
        }

        val availableTracks = currentState?.availableTracks ?: emptyList()
        val selectedTracks = mutableSetOf<Int>()
        
        if (availableTracks.size > 2) { // More than just 1 video + 1 audio
            tvTracksHeader.visibility = android.view.View.VISIBLE
            tracksContainer.visibility = android.view.View.VISIBLE
            
            availableTracks.forEach { track ->
                val cb = android.widget.CheckBox(this).apply {
                    val type = if (track.isVideo) "Video" else if (track.isAudio) "Audio" else "Other"
                    text = getString(R.string.track_item_format, track.id, type, track.mimeType)
                    isChecked = true
                    selectedTracks.add(track.id)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedTracks.add(track.id) else selectedTracks.remove(track.id)
                    }
                }
                tracksContainer.addView(cb)
            }
        }

        // Disable unchecking video if there's no audio track to keep
        if (currentState?.hasAudioTrack == false) {
            cbKeepVideo.isEnabled = false
            cbKeepVideo.alpha = 0.5f
            cbKeepVideo.setOnClickListener {
                Toast.makeText(this, getString(R.string.error_cannot_exclude_video), Toast.LENGTH_SHORT).show()
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.export)) { _, _ ->
                val keepVideo = cbKeepVideo.isChecked
                val keepAudio = cbKeepAudio.isChecked
                val mergeSegments = cbMergeSegments.isChecked
                if (!keepVideo && !keepAudio && selectedTracks.isEmpty()) {
                    Toast.makeText(this, getString(R.string.select_track_export), Toast.LENGTH_SHORT).show()
                } else {
                    val rotationOverride = if (currentRotation != 0) currentRotation else null
                    // If complex track selection was used, pass it; otherwise let ViewModel/Engine handle it via booleans
                    val trackList = if (selectedTracks.isNotEmpty() && availableTracks.size > 2) selectedTracks.toList() else null
                    viewModel.exportSegments(isLosslessMode, keepAudio, keepVideo, rotationOverride, mergeSegments, trackList)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateDurationDisplay(current: Long, total: Long) {
        if (!isVideoLoaded || total <= 0) return
        binding.tvDuration?.text = getString(R.string.duration_format, TimeUtils.formatDuration(current), TimeUtils.formatDuration(total))
    }

    private fun configureForRemux() {
        // Hide all cut/trim controls and timeline
        binding.seekerContainer?.visibility = View.GONE
        binding.editingControls?.visibility = View.GONE
        binding.rightSidebar?.visibility = View.GONE
        binding.customVideoSeeker.isRemuxMode = true

        lifecycleScope.launch {
            val finalState = viewModel.uiState.first { it is VideoEditingUiState.Success || it is VideoEditingUiState.Error }
            if (finalState is VideoEditingUiState.Success) {
                showRemuxDialog()
            } else {
                finish()
            }
        }
    }

    private fun showRemuxDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    private fun configureForMetadata() {
        binding.seekerContainer?.visibility = View.GONE
        binding.editingControls?.visibility = View.GONE
        binding.rightSidebar?.visibility = View.GONE

        lifecycleScope.launch {
            val finalState = viewModel.uiState.first { it is VideoEditingUiState.Success || it is VideoEditingUiState.Error }
            if (finalState is VideoEditingUiState.Success) {
                showMetadataDialog()
            } else {
                finish()
            }
        }
    }

    private fun showMetadataDialog() {
        val state = viewModel.uiState.value as? VideoEditingUiState.Success ?: run {
            Toast.makeText(this, R.string.please_wait, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_metadata_editor, null)
        val spinnerRotation = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerRotation)
        
        // Default to "Keep Original" (Index 0)
        spinnerRotation.setSelection(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dashboard_metadata_title)
            .setView(dialogView)
            .setPositiveButton(R.string.apply) { _, _ ->
                val selectedRotation = when (spinnerRotation.selectedItemPosition) {
                    1 -> 0
                    2 -> 90
                    3 -> 180
                    4 -> 270
                    else -> null // Keep Original
                }
                currentRotation = selectedRotation ?: state.clips[state.selectedClipIndex].rotation
                viewModel.exportSegments(true, keepAudio = true, keepVideo = true,
                    rotationOverride = selectedRotation, mergeSegments = false)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }

    override fun onLosslessModeToggled(isChecked: Boolean) {
        isLosslessMode = isChecked
        binding.customVideoSeeker.isLosslessMode = isChecked
        binding.customVideoSeeker.invalidate()
    }

    private fun showSilenceDetectionDialog() {
        val overlay = binding.silenceDetectionContainer?.root ?: return
        overlay.visibility = View.VISIBLE
        
        val sliderThreshold = overlay.findViewById<com.google.android.material.slider.Slider>(R.id.sliderThreshold)
        val sliderDuration = overlay.findViewById<com.google.android.material.slider.Slider>(R.id.sliderDuration)
        val sliderMinSegment = overlay.findViewById<com.google.android.material.slider.Slider>(R.id.sliderMinSegment)
        val sliderPadding = overlay.findViewById<com.google.android.material.slider.Slider>(R.id.sliderPadding)
        
        val tvThresholdValue = overlay.findViewById<TextView>(R.id.tvThresholdValue)
        val tvDurationValue = overlay.findViewById<TextView>(R.id.tvDurationValue)
        val tvMinSegmentValue = overlay.findViewById<TextView>(R.id.tvMinSegmentValue)
        val tvPaddingValue = overlay.findViewById<TextView>(R.id.tvPaddingValue)
        
        val tvEstimatedCut = overlay.findViewById<TextView>(R.id.tvEstimatedCut)
        val btnCancel = overlay.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnApply = overlay.findViewById<android.widget.Button>(R.id.btnApply)

        val updatePreview = {
            val threshold = sliderThreshold.value
            val duration = sliderDuration.value.toLong()
            val minSegment = sliderMinSegment.value.toLong()
            val padding = sliderPadding.value.toLong()
            
            tvThresholdValue.text = String.format("%.1f%%", threshold * 100)
            tvDurationValue.text = String.format("%.1fs", duration / 1000f)
            tvMinSegmentValue.text = String.format("%.1fs", minSegment / 1000f)
            tvPaddingValue.text = String.format("%.1fs", padding / 1000f)
            
            viewModel.previewSilenceSegments(threshold, duration, padding, minSegment)
        }

        sliderThreshold.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderDuration.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderMinSegment.addOnChangeListener { _, _, _ -> updatePreview() }
        sliderPadding.addOnChangeListener { _, _, _ -> updatePreview() }

        val silencePreviewJob = lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state is VideoEditingUiState.Success) {
                    val ranges = state.silencePreviewRanges
                    if (ranges.isNotEmpty()) {
                        val totalSilenceMs = ranges.sumOf { it.last - it.first }
                        tvEstimatedCut.text = getString(R.string.silence_detected_preview, TimeUtils.formatDuration(totalSilenceMs), ranges.size)
                        btnApply.isEnabled = true
                    } else {
                        tvEstimatedCut.text = getString(R.string.no_silence_detected)
                        btnApply.isEnabled = false
                    }
                }
            }
        }

        btnCancel.setOnClickListener {
            silencePreviewJob.cancel()
            viewModel.clearSilencePreview()
            overlay.visibility = View.GONE
        }

        btnApply.setOnClickListener {
            silencePreviewJob.cancel()
            viewModel.applySilenceDetection()
            overlay.visibility = View.GONE
        }

        updatePreview() // Initial run
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        player.release()
    }
}