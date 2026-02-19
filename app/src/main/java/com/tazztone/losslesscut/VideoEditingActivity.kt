package com.tazztone.losslesscut

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
import com.google.android.material.textfield.TextInputEditText
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var tvDuration: TextView
    private lateinit var frameRecyclerView: RecyclerView
    private lateinit var customVideoSeeker: CustomVideoSeeker
    private var videoUri: Uri? = null
    private var videoFileName: String = ""
    private lateinit var loadingScreen: View
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var switchLossless: com.google.android.material.switchmaterial.SwitchMaterial
    private var isVideoLoaded = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val mergePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        videoUri?.let { currentUri ->
            mergeVideos(currentUri, uris)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                isVideoLoaded = true
                customVideoSeeker.setVideoDuration(player.duration)
                updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())

                // Probe keyframes and extract frames on load
                videoUri?.let { uri ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val keyframes = LosslessEngine.probeKeyframes(this@VideoEditingActivity, uri)
                            val durationSec = player.duration / 1000.0
                            if (durationSec > 0) {
                                val normalizedKeyframes = keyframes.map { (it / durationSec).toFloat() }
                                withContext(Dispatchers.Main) {
                                    customVideoSeeker.setKeyframes(normalizedKeyframes)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("KeyframeProbe", "Failed to probe keyframes: ${e.message}")
                        }
                    }
                    
                    // Refresh frame strip
                    extractVideoFrames()
                }
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

        // Set loading and animation view
        loadingScreen = findViewById(R.id.loadingScreen)
        lottieAnimationView = findViewById(R.id.lottieAnimation)
        try {
            lottieAnimationView.playAnimation()
        } catch (e: Exception) {
            Log.e("LottieError", "Error loading Lottie animation: ${e.message}")
        }

        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        setupFrameRecyclerView()
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        tvDuration = findViewById(R.id.tvDuration)
        frameRecyclerView = findViewById(R.id.frameRecyclerView)
        customVideoSeeker = findViewById(R.id.customVideoSeeker)

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener { onBackPressedDispatcher.onBackPressed()}
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveAction() }
        findViewById<ImageButton>(R.id.btnTrim).setOnClickListener { trimAction() }
        findViewById<ImageButton>(R.id.btnText).setOnClickListener { textAction() }
        findViewById<ImageButton>(R.id.btnAudio).setOnClickListener { audioAction() }
        findViewById<ImageButton>(R.id.btnCrop).setOnClickListener { cropAction() }
        findViewById<ImageButton>(R.id.btnMerge).setOnClickListener { mergeAction() }
        
        switchLossless = findViewById(R.id.switchLossless)
        switchLossless.setOnCheckedChangeListener { _, isChecked ->
            customVideoSeeker.isLosslessMode = isChecked
            customVideoSeeker.invalidate()
            Toast.makeText(this, if (isChecked) "Lossless Mode: ON (Snaps to Keyframes)" else "Lossless Mode: OFF (Precise Cut)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mergeAction() {
        mergePickerLauncher.launch("video/*")
    }

    private fun mergeVideos(currentVideoUri: Uri, selectedVideoUris: List<Uri>) {
        if (selectedVideoUris.isEmpty()) return
        Toast.makeText(this, "Merge disabled in this version (Coming v2.0)", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("InflateParams")
    private fun cropAction() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.crop_bottom_sheet_dialog, null)
        sheetView.findViewById<TextView>(R.id.tvTitleCrop).text = getString(R.string.select_aspect_ratio)

        sheetView.findViewById<FrameLayout>(R.id.frameAspectRatio1).setOnClickListener {
            cropVideo("16:9")
            bottomSheetDialog.dismiss()
        }
        sheetView.findViewById<FrameLayout>(R.id.frameAspectRatio2).setOnClickListener {
            cropVideo("9:16")
            bottomSheetDialog.dismiss()
        }
        sheetView.findViewById<FrameLayout>(R.id.frameAspectRatio3).setOnClickListener {
            cropVideo("1:1")
            bottomSheetDialog.dismiss()
        }
        sheetView.findViewById<Button>(R.id.btnCancelCrop).setOnClickListener {
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun cropVideo(aspectRatio: String) {
        Toast.makeText(this@VideoEditingActivity, "Crop disabled in this version (Coming v2.0)", Toast.LENGTH_SHORT).show()
    }

    private fun audioAction() {
        Toast.makeText(this, "Audio features coming in v2.0", Toast.LENGTH_SHORT).show()
    }

    private fun textAction() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.text_bottom_sheet_dialog, null)
        val etTextInput = view.findViewById<TextInputEditText>(R.id.etTextInput)
        val fontSizeInput = view.findViewById<TextInputEditText>(R.id.fontSize)
        val spinnerTextPosition = view.findViewById<Spinner>(R.id.spinnerTextPosition)
        val btnDone = view.findViewById<Button>(R.id.btnDoneText)

        val positionOptions = arrayOf("Bottom Right", "Top Right", "Top Left", "Bottom Left", "Center Bottom", "Center Top", "Center Align")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, positionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTextPosition.adapter = adapter

        btnDone.setOnClickListener {
            Toast.makeText(this@VideoEditingActivity, "Text features coming in v2.0", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    @SuppressLint("InflateParams")
    private fun trimAction() {
        val videoDuration = player.duration
        if (videoDuration <= 0) {
            Toast.makeText(this, "Video duration is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetDialog = BottomSheetDialog(this@VideoEditingActivity)
        val sheetView = layoutInflater.inflate(R.layout.trim_bottom_sheet_dialog, null)
        val rangeSlider: RangeSlider = sheetView.findViewById(R.id.rangeSlider)

        val formattedValueTo = videoDuration.toFloat()
        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = formattedValueTo
        rangeSlider.values = listOf(0f, formattedValueTo)

        rangeSlider.addOnChangeListener { slider, value, fromUser ->
            val start = slider.values[0].toLong()
            val end = slider.values[1].toLong()
            if (fromUser) {
                if (value == slider.values[0]) player.seekTo(start)
                else if (value == slider.values[1]) player.seekTo(end)
            }
        }

        sheetView.findViewById<Button>(R.id.btnDoneTrim).setOnClickListener {
            trimVideo(rangeSlider.values[0].toLong(), rangeSlider.values[1].toLong())
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun trimVideo(trimBeginningTime: Long, trimEndTime: Long) {
        lifecycleScope.launch {
            val currentUri = videoUri ?: return@launch
            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) outputDir.mkdirs()
            
            val isLossless = switchLossless.isChecked
            val prefix = if (isLossless) "lossless_" else "precise_"
            val outputPath = File(outputDir, "${prefix}trimmed_${System.currentTimeMillis()}.mp4").absolutePath
            
            if (isLossless) {
                 val success = LosslessEngine.executeLosslessCut(this@VideoEditingActivity, currentUri, File(outputPath), trimBeginningTime, trimEndTime)
                 if (success) {
                     val newFile = File(outputPath)
                     videoUri = FileProvider.getUriForFile(this@VideoEditingActivity, "$packageName.provider", newFile)
                     videoFileName = newFile.name
                     initializePlayer() // Re-initialize player with new file
                     Toast.makeText(this@VideoEditingActivity, "Video saved to Downloads", Toast.LENGTH_SHORT).show()
                 } else {
                     showError("Lossless cut failed.")
                 }
            } else {
                showError("Precise mode (Re-encoding) is coming in v2.0")
            }
        }
    }

    private fun showError(error: String) {
        Log.e("VideoEditingError", error)
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun saveAction() {
        Toast.makeText(this, "Video is auto-saved to Downloads after trim", Toast.LENGTH_LONG).show()
    }

    private fun setupExoPlayer() {
        videoUri = intent.getParcelableExtra("VIDEO_URI")
        if (videoUri != null) {
            initializePlayer()
            initializeVideoData()
        } else {
            showError("Error loading video")
        }
    }

    private fun initializePlayer() {
        if (::player.isInitialized) {
            player.release()
        }
        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            videoUri?.let { setMediaItem(MediaItem.fromUri(it)) }
            prepare()
            playWhenReady = false
            seekTo(0)
            addListener(playerListener)
        }
        loadingScreen.visibility = View.VISIBLE
    }

    private fun initializeVideoData() {
        lifecycleScope.launch {
            try {
                videoUri?.let { uri ->
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(this@VideoEditingActivity, uri)
                        videoFileName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                            ?: uri.lastPathSegment ?: "video.mp4"
                    } finally {
                        retriever.release()
                    }
                }
            } catch (e: Exception) {
                showError("Error initializing video: ${e.message}")
            }
        }
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
                videoUri?.let { uri -> retriever.setDataSource(this@VideoEditingActivity, uri) } ?: return@launch
                
                // Wait for duration to be valid
                val duration = withContext(Dispatchers.Main) { 
                    if (player.duration > 0) player.duration else 0L 
                }
                
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
        tvDuration.text = "${formatDuration(current)} / ${formatDuration(total)}"
    }

    private fun formatDuration(milliseconds: Int): String {
        val secondsTotal = milliseconds / 1000
        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
        coroutineScope.cancel()
    }

    data class Media(val uri: Uri, val name: String, val size: Long, val mimeType: String)

    companion object {
        private const val TAG = "VideoMetadata"
    }
}