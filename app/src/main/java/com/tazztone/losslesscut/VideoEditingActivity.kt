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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    companion object {
        private const val KEY_PLAYHEAD = "playhead_pos"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_ROTATION = "rotation_offset"
        private const val KEY_LOSSLESS_MODE = "lossless_mode"
        private const val TAG = "VideoEditingActivity"
    }

    private val viewModel: VideoEditingViewModel by viewModels()
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var tvDuration: TextView
    private lateinit var customVideoSeeker: CustomVideoSeeker
    private lateinit var loadingScreen: View
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var switchLossless: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var btnPlayPause: ImageButton
    
    private lateinit var btnUndo: ImageButton
    private lateinit var btnSplit: ImageButton
    private lateinit var btnDelete: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnSnapshot: ImageButton

    private lateinit var btnNudgeBack: ImageButton
    private lateinit var btnNudgeForward: ImageButton

    private var isVideoLoaded = false
    private var updateJob: Job? = null
    private var isDraggingTimeline = false
    private var currentRotation = 0
    
    private var savedPlayheadPos = 0L
    private var savedPlayWhenReady = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && !isVideoLoaded) {
                isVideoLoaded = true
                customVideoSeeker.setVideoDuration(player.duration)
                updateDurationDisplay(player.currentPosition, player.duration)
                customVideoSeeker.setSeekPosition(player.currentPosition)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::btnPlayPause.isInitialized) {
                btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
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
        setContentView(R.layout.activity_video_editing)

        val videoUri: Uri? = intent.getParcelableExtra("VIDEO_URI")
        if (videoUri == null) {
            Toast.makeText(this, "Invalid Video URI", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        hideSystemUI()

        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        observeViewModel()

        viewModel.initialize(this, videoUri)
        
        savedInstanceState?.let {
            savedPlayheadPos = it.getLong(KEY_PLAYHEAD, 0L)
            savedPlayWhenReady = it.getBoolean(KEY_PLAY_WHEN_READY, false)
            currentRotation = it.getInt(KEY_ROTATION, 0)
            if (::switchLossless.isInitialized) {
                switchLossless.isChecked = it.getBoolean(KEY_LOSSLESS_MODE, true)
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
        if (::switchLossless.isInitialized) {
            outState.putBoolean(KEY_LOSSLESS_MODE, switchLossless.isChecked)
        }
    }

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        tvDuration = findViewById(R.id.tvDuration)
        customVideoSeeker = findViewById(R.id.customVideoSeeker)
        loadingScreen = findViewById(R.id.loadingScreen)
        lottieAnimationView = findViewById(R.id.lottieAnimation)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        
        try { 
            lottieAnimationView.playAnimation() 
        } catch (e: Exception) {
            Log.e(TAG, "Lottie animation failed to play", e)
        }

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener { onBackPressedDispatcher.onBackPressed()}
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { 
            showExportOptionsDialog()
        }
        
        btnUndo = findViewById(R.id.btnUndo)
        btnSplit = findViewById(R.id.btnSplit)
        btnDelete = findViewById(R.id.btnDelete)
        btnSnapshot = findViewById(R.id.btnSnapshot)
        btnRotate = findViewById(R.id.btnRotate)

        btnNudgeBack = findViewById(R.id.btnNudgeBack)
        btnNudgeForward = findViewById(R.id.btnNudgeForward)

        switchLossless = findViewById(R.id.switchLossless)
        switchLossless.setOnCheckedChangeListener { _, isChecked ->
            customVideoSeeker.isLosslessMode = isChecked
            customVideoSeeker.invalidate()
            Toast.makeText(this, if (isChecked) "Snap Mode ON" else "Snap Mode OFF", Toast.LENGTH_SHORT).show()
        }

        btnUndo.setOnClickListener { viewModel.undo() }
        btnSplit.setOnClickListener { viewModel.splitSegmentAt(player.currentPosition) }
        btnDelete.setOnClickListener { 
            val state = viewModel.uiState.value
            if (state is VideoEditingUiState.Success) {
                state.selectedSegmentId?.let { viewModel.toggleSegmentAction(it) }
            }
        }

        btnNudgeBack.setOnClickListener { performNudge(-1) }
        btnNudgeForward.setOnClickListener { performNudge(1) }

        btnSnapshot.setOnClickListener { extractSnapshot() }
        btnRotate.setOnClickListener { 
            currentRotation = (currentRotation + 90) % 360
            Toast.makeText(this, "Export rotation offset: $currentRotation°", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startProgressUpdate() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            while (isActive && player.isPlaying) {
                if (!isDraggingTimeline) {
                    customVideoSeeker.setSeekPosition(player.currentPosition)
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

        if (switchLossless.isChecked && currentState.keyframes.isNotEmpty()) {
            val keyframesMs = currentState.keyframes.map { it.toLong() }.sorted()
            val currentPos = player.currentPosition
            
            val targetKf = if (direction > 0) {
                keyframesMs.firstOrNull { it > currentPos + 10 } ?: player.duration
            } else {
                keyframesMs.lastOrNull { it < currentPos - 10 } ?: 0L
            }
            player.seekTo(targetKf)
        } else {
            val step = 33L // ~1 frame at 30fps
            val target = (player.currentPosition + (direction * step)).coerceIn(0, player.duration)
            player.seekTo(target)
        }
        customVideoSeeker.setSeekPosition(player.currentPosition)
        updateDurationDisplay(player.currentPosition, player.duration)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VideoEditingUiState.Loading -> loadingScreen.visibility = View.VISIBLE
                    is VideoEditingUiState.Success -> {
                        loadingScreen.visibility = View.GONE
                        if (player.currentMediaItem?.localConfiguration?.uri != state.videoUri) {
                            initializePlayer(state.videoUri)
                        }
                        
                        customVideoSeeker.setKeyframes(state.keyframes.map { it.toLong() })
                        customVideoSeeker.setSegments(state.segments, state.selectedSegmentId)
                        btnUndo.isEnabled = state.canUndo
                        btnUndo.alpha = if (state.canUndo) 1.0f else 0.5f

                        val selectedSeg = state.segments.find { it.id == state.selectedSegmentId }
                        if (selectedSeg != null && selectedSeg.action == SegmentAction.DISCARD) {
                            btnDelete.setImageResource(android.R.drawable.ic_menu_edit) // 'Restore' conceptually
                        } else {
                            btnDelete.setImageResource(android.R.drawable.ic_menu_delete)
                        }
                    }
                    is VideoEditingUiState.EventMessage -> {
                        loadingScreen.visibility = View.GONE
                        Toast.makeText(this@VideoEditingActivity, state.message, Toast.LENGTH_LONG).show()
                        viewModel.acknowledgeMessage()
                    }
                    is VideoEditingUiState.Error -> {
                        loadingScreen.visibility = View.GONE
                        Toast.makeText(this@VideoEditingActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupExoPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
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
        customVideoSeeker.onSeekListener = { seekPositionMs ->
            isDraggingTimeline = true
            player.seekTo(seekPositionMs)
            updateDurationDisplay(seekPositionMs, player.duration)
            isDraggingTimeline = false
        }
        
        customVideoSeeker.onSegmentSelected = { id ->
            viewModel.selectSegment(id)
        }
        
        customVideoSeeker.onSegmentBoundsChanged = { id, start, end ->
            isDraggingTimeline = true
            viewModel.updateSegmentBounds(id, start, end)
        }
        
        customVideoSeeker.onSegmentBoundsDragEnd = {
            isDraggingTimeline = false
            viewModel.commitSegmentBounds()
        }
    }

    private fun extractSnapshot() {
        val currentState = viewModel.uiState.value as? VideoEditingUiState.Success ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                withContext(Dispatchers.Main) { loadingScreen.visibility = View.VISIBLE }
                retriever.setDataSource(this@VideoEditingActivity, currentState.videoUri)
                
                val bitmap = retriever.getFrameAtTime(player.currentPosition * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                
                if (bitmap != null) {
                    val file = File(getExternalFilesDir(null), "snapshot_${System.currentTimeMillis()}.png")
                    val fos = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.close()
                    bitmap.recycle()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoEditingActivity, "Snapshot saved: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(this@VideoEditingActivity, "Failed to capture snapshot", Toast.LENGTH_SHORT).show() }
                }

            } catch (e: Exception) {
                Log.e("VideoEditingActivity", "Snapshot error", e)
            } finally {
                retriever.release()
                withContext(Dispatchers.Main) { loadingScreen.visibility = View.GONE }
            }
        }
    }

    private fun showExportOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val cbKeepVideo = dialogView.findViewById<android.widget.CheckBox>(R.id.cbKeepVideo)
        val cbKeepAudio = dialogView.findViewById<android.widget.CheckBox>(R.id.cbKeepAudio)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Export Options")
            .setView(dialogView)
            .setPositiveButton("Export") { _, _ ->
                val keepVideo = cbKeepVideo.isChecked
                val keepAudio = cbKeepAudio.isChecked
                if (!keepVideo && !keepAudio) {
                    Toast.makeText(this, "Select at least one track to export", Toast.LENGTH_SHORT).show()
                } else {
                    val rotationOverride = if (currentRotation != 0) currentRotation else null
                    viewModel.exportSegments(this, switchLossless.isChecked, keepAudio, keepVideo, rotationOverride)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateDurationDisplay(current: Long, total: Long) {
        if (!isVideoLoaded || total <= 0) return
        tvDuration.text = "${TimeUtils.formatDuration(current)} / ${TimeUtils.formatDuration(total)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        player.release()
    }
}