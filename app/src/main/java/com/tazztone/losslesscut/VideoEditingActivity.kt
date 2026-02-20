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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.databinding.ActivityVideoEditingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoEditingActivity : AppCompatActivity() {

    companion object {
        private const val KEY_PLAYHEAD = "playhead_pos"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_ROTATION = "rotation_offset"
        private const val KEY_LOSSLESS_MODE = "lossless_mode"
        private const val TAG = "VideoEditingActivity"
    }

    private val viewModel: VideoEditingViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VideoEditingViewModel(application) as T
            }
        }
    }
    private lateinit var binding: ActivityVideoEditingBinding
    private lateinit var player: ExoPlayer

    private var isVideoLoaded = false
    private var updateJob: Job? = null
    private var isDraggingTimeline = false
    private var currentRotation = 0
    @Volatile private var videoFps = 30f
    
    private var savedPlayheadPos = 0L
    private var savedPlayWhenReady = false

    private val playerListener = object : Player.Listener {
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

        val videoUri: Uri? = intent.getParcelableExtra("VIDEO_URI")
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
        extractVideoFps(videoUri)
        
        savedInstanceState?.let {
            savedPlayheadPos = it.getLong(KEY_PLAYHEAD, 0L)
            savedPlayWhenReady = it.getBoolean(KEY_PLAY_WHEN_READY, false)
            currentRotation = it.getInt(KEY_ROTATION, 0)
            updateRotationBadge()
            if (::binding.isInitialized) {
                binding.switchLossless.isChecked = it.getBoolean(KEY_LOSSLESS_MODE, true)
            }
        }
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
        if (::binding.isInitialized) {
            outState.putBoolean(KEY_LOSSLESS_MODE, binding.switchLossless.isChecked)
        }
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
        
        try { 
            binding.loadingScreen.lottieAnimation.playAnimation() 
        } catch (e: Exception) {
            Log.e(TAG, "Lottie animation failed to play", e)
        }

        binding.btnHome.setOnClickListener { onBackPressedDispatcher.onBackPressed()}
        binding.btnSave.setOnClickListener { 
            showExportOptionsDialog()
        }

        binding.switchLossless.setOnCheckedChangeListener { _, isChecked ->
            binding.customVideoSeeker.isLosslessMode = isChecked
            binding.customVideoSeeker.invalidate()
            Toast.makeText(this, getString(if (isChecked) R.string.snap_mode_on else R.string.snap_mode_off), Toast.LENGTH_SHORT).show()
        }

        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnSplit.setOnClickListener { viewModel.splitSegmentAt(player.currentPosition) }
        binding.btnDelete.setOnClickListener { 
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.toggleSegmentAction(it) }
            }
        }

        binding.btnNudgeBack.setOnClickListener { performNudge(-1) }
        binding.btnNudgeForward.setOnClickListener { performNudge(1) }

        binding.btnSnapshot.setOnClickListener { extractSnapshot() }
        binding.btnRotate.setOnClickListener { 
            currentRotation = (currentRotation + 90) % 360
            updateRotationBadge()
            Toast.makeText(this, getString(R.string.export_rotation_offset, currentRotation), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRotationBadge() {
        binding.badgeRotate?.text = "${currentRotation}°"
        binding.badgeRotate?.visibility = if (currentRotation == 0) View.GONE else View.VISIBLE
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

        if (binding.switchLossless.isChecked && currentState.keyframes.isNotEmpty()) {
            val keyframesMs = currentState.keyframes.sorted()
            val currentPos = player.currentPosition
            
            val targetKf = if (direction > 0) {
                keyframesMs.firstOrNull { it > currentPos + 10 } ?: player.duration
            } else {
                keyframesMs.lastOrNull { it < currentPos - 10 } ?: 0L
            }
            player.seekTo(targetKf)
        } else {
            val step = if (videoFps > 0f) (1000L / videoFps).toLong() else 33L
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

    private fun extractVideoFps(videoUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@VideoEditingActivity, videoUri)
                val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val fps = fpsStr?.toFloatOrNull()
                if (fps != null && fps > 0f) {
                    videoFps = fps
                } else {
                    val extractor = android.media.MediaExtractor()
                    extractor.setDataSource(this@VideoEditingActivity, videoUri, null)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                        if (mime?.startsWith("video/") == true && format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                            val frameRate = format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE).toFloat()
                            if (frameRate > 0f) {
                                videoFps = frameRate
                            }
                            break
                        }
                    }
                    extractor.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract FPS", e)
            } finally {
                retriever.release()
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
        binding.customVideoSeeker.onSeekListener = { seekPositionMs ->
            isDraggingTimeline = true
            player.seekTo(seekPositionMs)
            updateDurationDisplay(seekPositionMs, player.duration)
            isDraggingTimeline = false
        }
        
        binding.customVideoSeeker.onSegmentSelected = { id ->
            viewModel.selectSegment(id)
        }
        
        binding.customVideoSeeker.onSegmentBoundsChanged = { id, start, end ->
            isDraggingTimeline = true
            viewModel.updateSegmentBounds(id, start, end)
        }
        
        binding.customVideoSeeker.onSegmentBoundsDragEnd = {
            isDraggingTimeline = false
            viewModel.commitSegmentBounds()
        }
    }

    private fun extractSnapshot() {
        val currentState = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                withContext(Dispatchers.Main) { binding.loadingScreen.root.visibility = View.VISIBLE }
                retriever.setDataSource(this@VideoEditingActivity, currentState.videoUri)
                
                val bitmap = retriever.getFrameAtTime(player.currentPosition * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                
                if (bitmap != null) {
                    val file = File(getExternalFilesDir(null), "snapshot_${System.currentTimeMillis()}.png")
                    val fos = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.close()
                    bitmap.recycle()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoEditingActivity, getString(R.string.snapshot_saved, file.name), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(this@VideoEditingActivity, getString(R.string.snapshot_failed), Toast.LENGTH_SHORT).show() }
                }

            } catch (e: Exception) {
                Log.e("VideoEditingActivity", "Snapshot error", e)
            } finally {
                retriever.release()
                withContext(Dispatchers.Main) { binding.loadingScreen.root.visibility = View.GONE }
            }
        }
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
                    viewModel.exportSegments(binding.switchLossless.isChecked, keepAudio, keepVideo, rotationOverride)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateDurationDisplay(current: Long, total: Long) {
        if (!isVideoLoaded || total <= 0) return
        binding.tvDuration.text = getString(R.string.duration_format, TimeUtils.formatDuration(current), TimeUtils.formatDuration(total))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        player.release()
    }
}