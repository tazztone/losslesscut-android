package com.tazztone.losslesscut

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
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.databinding.ActivityVideoEditingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@UnstableApi
@AndroidEntryPoint
class VideoEditingActivity : AppCompatActivity(), SettingsBottomSheetDialogFragment.SettingsListener {
 
    companion object {
        const val EXTRA_VIDEO_URI = "com.tazztone.losslesscut.EXTRA_VIDEO_URI"
        private const val KEY_PLAYHEAD = "playhead_pos"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_ROTATION = "rotation_offset"
        private const val KEY_LOSSLESS_MODE = "lossless_mode"
        private const val TAG = "VideoEditingActivity"
    }
 
    private val viewModel: VideoEditingViewModel by viewModels()
    private lateinit var binding: ActivityVideoEditingBinding
    private lateinit var player: ExoPlayer

    private var isVideoLoaded = false
    private var updateJob: Job? = null
    private var isDraggingTimeline = false
    private var currentRotation = 0
    private var isLosslessMode = true
    
    private var savedPlayheadPos = 0L
    private var savedPlayWhenReady = false
    

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
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
                binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
                binding.btnPlayPauseControls.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
                if (isPlaying) {
                    binding.btnPlayPause.animate().alpha(0f).setStartDelay(500).setDuration(300).start()
                } else {
                    binding.btnPlayPause.animate().cancel()
                    binding.btnPlayPause.alpha = 1f
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

        val videoUri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VIDEO_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO_URI)
        }
        if (videoUri == null) {
            Toast.makeText(this, getString(R.string.invalid_video_uri), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        hideSystemUI()

        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        observeViewModel()

        viewModel.initialize(videoUri)
        
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
                if (viewModel.isDirty.value) {
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
        if (::player.isInitialized) player.pause()
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
        binding.btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        TooltipCompat.setTooltipText(binding.btnPlayPause, getString(R.string.play_pause))

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
        
        binding.btnSave.setOnClickListener { 
            showExportOptionsDialog()
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
        
        val splitAction = { 
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
        binding.btnSplit.setOnClickListener { splitAction() }
        binding.containerSplit?.setOnClickListener { splitAction() }
        TooltipCompat.setTooltipText(binding.btnSplit, getString(R.string.split))
        binding.containerSplit?.let { TooltipCompat.setTooltipText(it, getString(R.string.split)) }
        
        val deleteAction = { 
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.toggleSegmentAction(it) }
            }
        }
        binding.btnDelete.setOnClickListener { deleteAction() }
        binding.containerDelete?.setOnClickListener { deleteAction() }
        TooltipCompat.setTooltipText(binding.btnDelete, getString(R.string.discard_segment))
        binding.containerDelete?.let { TooltipCompat.setTooltipText(it, getString(R.string.discard_segment)) }

        binding.btnNudgeBack.setOnClickListener { performNudge(-1) }
        TooltipCompat.setTooltipText(binding.btnNudgeBack, getString(R.string.nudge_backward))
        
        binding.btnNudgeForward.setOnClickListener { performNudge(1) }
        TooltipCompat.setTooltipText(binding.btnNudgeForward, getString(R.string.nudge_forward))

        binding.btnPlayPauseControls.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        TooltipCompat.setTooltipText(binding.btnPlayPauseControls, getString(R.string.play_pause))

        val snapshotAction = { extractSnapshot() }
        binding.btnSnapshot.setOnClickListener { snapshotAction() }
        binding.containerSnapshot?.setOnClickListener { snapshotAction() }
        TooltipCompat.setTooltipText(binding.btnSnapshot, getString(R.string.snapshot))
        binding.containerSnapshot?.let { TooltipCompat.setTooltipText(it, getString(R.string.snapshot)) }
        
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
        binding.playerView.rotation = 0f
        binding.playerView.scaleX = 1f
        binding.playerView.scaleY = 1f
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
        } else {
            val step = if (currentState.videoFps > 0f) (1000L / currentState.videoFps).toLong() else 33L
            val target = (player.currentPosition + (direction * step)).coerceIn(0, player.duration)
            player.seekTo(target)
        }
        binding.customVideoSeeker.setSeekPosition(player.currentPosition)
        updateDurationDisplay(player.currentPosition, player.duration)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VideoEditingUiState.Loading -> binding.loadingScreen.root.visibility = View.VISIBLE
                    is VideoEditingUiState.Success -> {
                        binding.loadingScreen.root.visibility = View.GONE
                        if (player.currentMediaItem?.localConfiguration?.uri != state.videoUri) {
                            initializePlayer(state.videoUri)
                        }
                        
                        if (state.isAudioOnly) {
                            binding.playerView.visibility = View.GONE
                            binding.audioPlaceholder?.visibility = View.VISIBLE
                            binding.tvAudioFileName?.text = state.videoFileName
                            binding.containerRotate?.visibility = View.GONE
                            binding.containerSnapshot?.visibility = View.GONE
                        } else {
                            binding.playerView.visibility = View.VISIBLE
                            binding.audioPlaceholder?.visibility = View.GONE
                            binding.containerRotate?.visibility = View.VISIBLE
                            binding.containerSnapshot?.visibility = View.VISIBLE
                        }

                        binding.customVideoSeeker.setKeyframes(state.keyframes)
                        binding.customVideoSeeker.setSegments(state.segments, state.selectedSegmentId)
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
    }

    private fun setupExoPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            binding.playerView.player = this
            addListener(playerListener)
        }
    }

    private fun initializePlayer(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = savedPlayWhenReady
        player.seekTo(savedPlayheadPos)
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
        val currentState = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        viewModel.extractSnapshot(player.currentPosition)
    }

    private fun showExportOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbKeepVideo = dialogView.findViewById<android.widget.CheckBox>(R.id.cbKeepVideo)
        val cbKeepAudio = dialogView.findViewById<android.widget.CheckBox>(R.id.cbKeepAudio)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_options))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.export)) { _, _ ->
                val keepVideo = cbKeepVideo.isChecked
                val keepAudio = cbKeepAudio.isChecked
                if (!keepVideo && !keepAudio) {
                    Toast.makeText(this, getString(R.string.select_track_export), Toast.LENGTH_SHORT).show()
                } else {
                    val rotationOverride = if (currentRotation != 0) currentRotation else null
                    viewModel.exportSegments(isLosslessMode, keepAudio, keepVideo, rotationOverride)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateDurationDisplay(current: Long, total: Long) {
        if (!isVideoLoaded || total <= 0) return
        binding.tvDuration.text = getString(R.string.duration_format, TimeUtils.formatDuration(current), TimeUtils.formatDuration(total))
    }

    override fun onLosslessModeToggled(isChecked: Boolean) {
        isLosslessMode = isChecked
        binding.customVideoSeeker.isLosslessMode = isChecked
        binding.customVideoSeeker.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        player.release()
    }
}