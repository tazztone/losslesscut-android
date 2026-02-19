package com.tazztone.losslesscut

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.RangeSlider
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import kotlinx.coroutines.*

@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    private val viewModel: VideoEditingViewModel by viewModels()
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var tvDuration: TextView
    private lateinit var frameRecyclerView: RecyclerView
    private lateinit var customVideoSeeker: CustomVideoSeeker
    private lateinit var loadingScreen: View
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var switchLossless: com.google.android.material.switchmaterial.SwitchMaterial
    
    private var isVideoLoaded = false

    private val mergePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        Toast.makeText(this, "Merge disabled in this version (Coming v2.0)", Toast.LENGTH_SHORT).show()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                isVideoLoaded = true
                customVideoSeeker.setVideoDuration(player.duration)
                updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                extractVideoFrames()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && isVideoLoaded) {
                updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editing)

        // Set fullscreen flags
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        setupFrameRecyclerView()
        observeViewModel()

        val videoUri: Uri? = intent.getParcelableExtra("VIDEO_URI")
        viewModel.initialize(this, videoUri)
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        tvDuration = findViewById(R.id.tvDuration)
        frameRecyclerView = findViewById(R.id.frameRecyclerView)
        customVideoSeeker = findViewById(R.id.customVideoSeeker)
        loadingScreen = findViewById(R.id.loadingScreen)
        lottieAnimationView = findViewById(R.id.lottieAnimation)
        
        try { lottieAnimationView.playAnimation() } catch (e: Exception) {}

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener { onBackPressedDispatcher.onBackPressed()}
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { 
            Toast.makeText(this, "Video is saved to Movies/LosslessCut after trim", Toast.LENGTH_LONG).show()
        }
        findViewById<ImageButton>(R.id.btnTrim).setOnClickListener { trimAction() }
        findViewById<ImageButton>(R.id.btnText).setOnClickListener { 
            Toast.makeText(this, "Text features coming in v2.0", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnAudio).setOnClickListener { 
            Toast.makeText(this, "Audio features coming in v2.0", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnCrop).setOnClickListener { cropAction() }
        findViewById<ImageButton>(R.id.btnMerge).setOnClickListener { mergePickerLauncher.launch("video/*") }
        
        switchLossless = findViewById(R.id.switchLossless)
        switchLossless.setOnCheckedChangeListener { _, isChecked ->
            customVideoSeeker.isLosslessMode = isChecked
            customVideoSeeker.invalidate()
            Toast.makeText(this, if (isChecked) "Lossless Mode: ON (Snaps to Keyframes)" else "Lossless Mode: OFF (Precise Cut)", Toast.LENGTH_SHORT).show()
        }
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
                        if (state.keyframes.isNotEmpty()) {
                            val durationSec = player.duration / 1000.0
                            if (durationSec > 0) {
                                val normalizedKeyframes = state.keyframes.map { (it / durationSec).toFloat() }
                                customVideoSeeker.setKeyframes(normalizedKeyframes)
                            }
                        }
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
        player.playWhenReady = false
        player.seekTo(0)
    }

    @SuppressLint("InflateParams")
    private fun trimAction() {
        val videoDuration = player.duration
        if (videoDuration <= 0) {
            Toast.makeText(this, "Video duration is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.trim_bottom_sheet_dialog, null)
        val rangeSlider: RangeSlider = sheetView.findViewById(R.id.rangeSlider)

        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = videoDuration.toFloat()
        rangeSlider.values = listOf(0f, videoDuration.toFloat())

        rangeSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                val start = slider.values[0].toLong()
                val end = slider.values[1].toLong()
                if (value == slider.values[0]) player.seekTo(start)
                else if (value == slider.values[1]) player.seekTo(end)
            }
        }

        sheetView.findViewById<Button>(R.id.btnDoneTrim).setOnClickListener {
            viewModel.trimVideo(this, rangeSlider.values[0].toLong(), rangeSlider.values[1].toLong(), switchLossless.isChecked)
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    @SuppressLint("InflateParams")
    private fun cropAction() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.crop_bottom_sheet_dialog, null)
        sheetView.findViewById<Button>(R.id.btnCancelCrop).setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
        Toast.makeText(this, "Crop coming in v2.0", Toast.LENGTH_SHORT).show()
    }

    private fun setupCustomSeeker() {
        customVideoSeeker.onSeekListener = { seekPosition ->
            val newSeekTime = (player.duration * seekPosition).toLong()
            if (newSeekTime in 0..player.duration) {
                player.seekTo(newSeekTime)
                updateDurationDisplay(newSeekTime.toInt(), player.duration.toInt())
            }
        }
    }

    private fun setupFrameRecyclerView() {
        frameRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        frameRecyclerView.adapter = FrameAdapter(emptyList())
    }

    private fun extractVideoFrames() {
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = player.currentMediaItem?.localConfiguration?.uri ?: return@launch
                retriever.setDataSource(this@VideoEditingActivity, uri)
                
                val duration = withContext(Dispatchers.Main) { if (player.duration > 0) player.duration else 0L }
                if (duration <= 0) return@launch

                val frameInterval = duration / 10
                val frameBitmaps = mutableListOf<Bitmap>()
                for (i in 0 until 10) {
                    val frameTime = i * frameInterval
                    val bitmap = retriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    bitmap?.let {
                        val processedBitmap = Bitmap.createScaledBitmap(it, 200, 150, false)
                        frameBitmaps.add(processedBitmap)
                    }
                }
                withContext(Dispatchers.Main) {
                    frameRecyclerView.adapter = FrameAdapter(frameBitmaps)
                    loadingScreen.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("FrameError", "Error extracting frames: ${e.message}")
            } finally {
                retriever.release()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDurationDisplay(current: Int, total: Int) {
        if (!isVideoLoaded || total <= 0) return
        tvDuration.text = "${TimeUtils.formatDuration(current.toLong())} / ${TimeUtils.formatDuration(total.toLong())}"
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}