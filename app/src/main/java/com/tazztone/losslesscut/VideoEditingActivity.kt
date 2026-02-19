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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.tazztone.losslesscut.LosslessEngine
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputEditText
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var tempInputFile: File
    private lateinit var loadingScreen: View
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var switchLossless: com.google.android.material.switchmaterial.SwitchMaterial
    private var isVideoLoaded = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

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
            // Handle the error gracefully
        }

        // Initialize UI components and setup the player
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

        // Set up button click listeners
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
        openFilePickerMerge()
    }

    private fun openFilePickerMerge() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Video"), PICK_VIDEO_REQUEST)
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let {
                val currentVideoUri = videoUri // The current video URI
                val selectedVideoUris = mutableListOf<Uri>()

                if (it.clipData != null) {
                    val itemCount = it.clipData!!.itemCount
                    for (i in 0 until itemCount) {
                        selectedVideoUris.add(it.clipData!!.getItemAt(i).uri)
                    }
                } else {
                    it.data?.let { selectedUri -> selectedVideoUris.add(selectedUri) }
                }

                if (currentVideoUri != null) {
                    mergeVideos(currentVideoUri, selectedVideoUris)
                } // Pass the URIs to mergeVideos
            }
        }
    }

    private fun mergeVideos(currentVideoUri: Uri, selectedVideoUris: List<Uri>) {
        // Merge feature disabled for v1.0 (requires FFmpeg)
        Toast.makeText(this, "Merge disabled in this version", Toast.LENGTH_SHORT).show()
    }




    @SuppressLint("InflateParams")
    private fun cropAction() {
        // Create BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.crop_bottom_sheet_dialog, null)

        // Set title
        sheetView.findViewById<TextView>(R.id.tvTitleCrop).text =
            getString(R.string.select_aspect_ratio)

        // Set button click listeners for aspect ratios
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

        // Set cancel button listener
        sheetView.findViewById<Button>(R.id.btnCancelCrop).setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun cropVideo(aspectRatio: String) {
        // Retrieve the video URI from the intent
        val videoUri = intent.getParcelableExtra<Uri>("VIDEO_URI")
        if (videoUri == null) {
            Toast.makeText(this, "Error retrieving video URI", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch video metadata asynchronously to get the file path
        lifecycleScope.launch {
            try {
                val media = getVideoMetadata(this@VideoEditingActivity, videoUri)
                val inputPath = media.uri.toString() // Get the actual file path
                val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                if (!outputDir.exists()) {
                    outputDir.mkdirs() // Create output directory if it doesn't exist
                }

                val outputPath = File(outputDir, "cropped_${System.currentTimeMillis()}.mp4").absolutePath
                Log.d("CropAction", "Output file path: $outputPath")

                // Define crop parameters based on aspect ratio
                    val cropCommand = when (aspectRatio) {
                        "16:9" -> "-vf \"crop=iw:iw*9/16\""
                        "9:16" -> "-vf \"crop=ih*9/16:ih\""
                        "1:1" -> "-vf \"crop=iw:iw\""
                        else -> return@launch
                    }

                // Build the FFmpeg command correctly
                val command = "-i \"$inputPath\" $cropCommand -c:a copy \"$outputPath\""
                Log.d("FFmpegCommand", "FFmpeg command: $command")

                executeFFmpegCommand(command, outputPath)

            } catch (e: Exception) {
                Log.e("MetadataError", "Error fetching video metadata: ${e.message}")
                Toast.makeText(this@VideoEditingActivity, "Error fetching video metadata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun audioAction() {
        TODO("Not yet implemented")
    }

    private fun textAction() {
        // FFmpegKitConfig.setFontDirectory(this@VideoEditingActivity, "/system/fonts", null)
        // Create the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)

        // Inflate the layout for the bottom sheet
        val view = layoutInflater.inflate(R.layout.text_bottom_sheet_dialog, null)

        // Initialize the EditText, Spinners, and Buttons from the inflated view
        val etTextInput = view.findViewById<TextInputEditText>(R.id.etTextInput)
        val fontSizeInput = view.findViewById<TextInputEditText>(R.id.fontSize)
        val spinnerTextPosition = view.findViewById<Spinner>(R.id.spinnerTextPosition)
        val btnDone = view.findViewById<Button>(R.id.btnDoneText)

        // Define position values for the spinner
        val positionOptions = arrayOf(
            "Bottom Right",
            "Top Right",
            "Top Left",
            "Bottom Left",
            "Center Bottom",
            "Center Top",
            "Center Align"
        )

        // Create an adapter to set the spinner data
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, positionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTextPosition.adapter = adapter

        btnDone.setOnClickListener {
            // Retrieve the text input, font size, and position selection
            val text = etTextInput.text.toString()
            val fontSize = fontSizeInput.text.toString().toIntOrNull() ?: 16 // Default font size is 16 if invalid input
            val textPosition = spinnerTextPosition.selectedItem.toString()

            // Map the position string to FFmpeg position parameters (x and y coordinates)
            val positionString = when (textPosition) {
                "Bottom Right" -> "x=w-tw:y=h-th"
                "Top Right" -> "x=w-tw:y=0"
                "Top Left" -> "x=0:y=0"
                "Bottom Left" -> "x=0:y=h-th"
                "Center Bottom" -> "x=(w-text_w)/2:y=h-th"
                "Center Top" -> "x=(w-text_w)/2:y=0"
                "Center Align" -> "x=(w-text_w)/2:y=(h-text_h)/2"
                else -> "x=(w-text_w)/2:y=(h-text_h)/2"
            }

            // Show a toast with the entered data (for debugging purposes)
            Toast.makeText(this, "Text: $text, Font Size: $fontSize, Position: $positionString", Toast.LENGTH_SHORT).show()

            // Call function to add the text to the video
            addTextToVideo(text, fontSize, positionString)

            // Close the bottom sheet
            bottomSheetDialog.dismiss()
        }

        // Set the view for the bottom sheet dialog
        bottomSheetDialog.setContentView(view)

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }

    private fun addTextToVideo(text: String, fontSize: Int, position: String) {
        lifecycleScope.launch {
            val videoUri = videoUri // Assuming this is your video URI
            val media = videoUri?.let { getVideoMetadata(this@VideoEditingActivity, it) }
            val realFilePath = media?.uri.toString()

            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) {
                outputDir.mkdirs() // Create output directory if it doesn't exist
            }
            val outputPath = File(outputDir, "video_with_text_${System.currentTimeMillis()}.mp4").absolutePath
            Log.d("TextAction", "Output file path: $outputPath")

            // FFmpeg command to overlay text on the video
            val command = "-i \"$realFilePath\" -vf \"drawtext=text='$text':fontcolor=white:fontsize=$fontSize:$position\" -c:a copy \"$outputPath\""
            Log.d("FFmpegCommand", "FFmpeg command: $command")
            executeFFmpegCommand(command, outputPath)
        }
    }

    @SuppressLint("InflateParams")
    private fun trimAction() {
        val videoDuration = player.duration

        // Validate the video duration
        if (videoDuration <= 0) {
            Toast.makeText(this, "Video duration is invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this@VideoEditingActivity)
        val sheetView = layoutInflater.inflate(R.layout.trim_bottom_sheet_dialog, null)

        val rangeSlider: RangeSlider = sheetView.findViewById(R.id.rangeSlider)

        // Convert duration to minutes and seconds
        val durationInMillis: Long = videoDuration
        val totalMinutes = (durationInMillis / 60000).toInt()
        val totalSeconds = ((durationInMillis % 60000) / 1000).toInt()

        // Format as float for the RangeSlider (00.00)
        val formattedValueTo = (totalMinutes * 60 + totalSeconds).toFloat() // Total seconds as float

        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = formattedValueTo
        rangeSlider.values = listOf(0f, formattedValueTo) // Set initial range

        // Log the values for debugging
        Log.d("RangeSlider", "Value from: ${rangeSlider.valueFrom}, Value to: ${rangeSlider.valueTo}")

        rangeSlider.addOnChangeListener { slider, value, fromUser ->
            val start = slider.values[0].toLong() * 1000 // Convert to milliseconds
            val end = slider.values[1].toLong() * 1000 // Convert to milliseconds

            // Update the player’s playback position based on the start value
            if (fromUser) {
                if (value == slider.values[0]) {
                    player.seekTo(start)
                }
                else if (value == slider.values[1]) {
                    player.seekTo(end)
                }
            }
        }

        // Set up button listeners
        sheetView.findViewById<Button>(R.id.btnDoneTrim).setOnClickListener {
            trimVideo(rangeSlider.values[0].toLong(), rangeSlider.values[1].toLong())
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun trimVideo(trimBeginingTime: Long, trimEndTime: Long) {
        lifecycleScope.launch {
            val media = videoUri?.let { getVideoMetadata(this@VideoEditingActivity, it) }
            val realFilePath = media?.uri.toString() ?: return@launch

            val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!outputDir.exists()) {
                outputDir.mkdirs() // Create output directory if it doesn't exist
            }
            
            val isLossless = switchLossless.isChecked
            val prefix = if (isLossless) "lossless_" else "precise_"
            val outputPath = File(outputDir, "${prefix}trimmed_${System.currentTimeMillis()}.mp4").absolutePath
            Log.d("TrimAction", "Output file path: $outputPath")
            
            if (isLossless) {
                 val success = LosslessEngine.executeLosslessCut(this@VideoEditingActivity, videoUri!!, File(outputPath), trimBeginingTime, trimEndTime)
                 if (success) {
                     videoUri = Uri.parse(outputPath)
                     refreshPlayer()
                     refreshUI()
                 } else {
                     showError("Lossless cut failed.")
                 }
            } else {
                // Precise mode: Re-encoding disabled for v1.0 as we are pure SDK
                showError("Precise mode (Re-encoding) is coming in v2.0")
                // TODO: Re-enable when standalone FFmpeg binary is integrated
                /*
                // Convert ms to seconds for -ss
                val startSec = trimBeginingTime / 1000.0
                val durationSec = (trimEndTime - trimBeginingTime) / 1000.0
                // Use h264_mediacodec for hardware encoding if possible, or mpeg4 as fallback since libx264 is gone
                val command = "-ss $startSec -i \"$realFilePath\" -t $durationSec -c:v h264_mediacodec -c:a aac \"$outputPath\""
                Log.d("FFmpegCommand", "FFmpeg command (Precise): $command")
                executeFFmpegCommand(command, outputPath)
                */
            }
        }
    }

    private fun executeFFmpegCommand(command: String, outputPath: String) {
        // executeFFmpegCommand is disabled in v1.0 pure SDK mode
        showError("FFmpeg commands are disabled in this version.")
    }

    private fun showError(error: String) {
        Log.e("VideoEditingError", error)
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUI() {
        // Update UI elements based on the player's current state
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    customVideoSeeker.setVideoDuration(player.duration)
                    updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                    extractVideoFrames() // Refresh frame list for the trimmed video
                }
            }
        })

        // Call to extract frames for display
        extractVideoFrames()
    }

    private fun refreshPlayer() {
        player.release() // Release the current player instance

        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this // Bind player to the player view
            setMediaItem(MediaItem.fromUri(videoUri!!)) // Set the new media item
            prepare() // Prepare the player
            playWhenReady = false // Start playback automatically
            seekTo(0) // Seek to the start of the video
        }

        // Update the custom seeker to reflect the new video's duration
        customVideoSeeker.setVideoDuration(player.duration)
        updateDurationDisplay(0, player.duration.toInt()) // Reset duration display
    }


    private fun saveAction() {
        // Placeholder for future implementation of save functionality
    }

    private fun setupExoPlayer() {
        videoUri = intent.getParcelableExtra("VIDEO_URI")
        if (videoUri != null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player

            val mediaItem = MediaItem.fromUri(videoUri!!)
            player.setMediaItem(mediaItem)
            loadingScreen.visibility = View.VISIBLE

            player.prepare()

            // Add listener for player events
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        isVideoLoaded = true
                        customVideoSeeker.setVideoDuration(player.duration)
                        customVideoSeeker.setVideoDuration(player.duration)
                        updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                        
                        // Probe keyframes if not already done
                        if (videoUri != null) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val keyframes = LosslessEngine.probeKeyframes(this@VideoEditingActivity, videoUri!!)
                                val durationSec = player.duration / 1000.0
                                if (durationSec > 0) {
                                    val normalizedKeyframes = keyframes.map { (it / durationSec).toFloat() }
                                    withContext(Dispatchers.Main) {
                                        customVideoSeeker.setKeyframes(normalizedKeyframes)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && isVideoLoaded) {
                        updateDurationDisplay(player.currentPosition.toInt(), player.duration.toInt())
                    }
                }
            })

            // Initialize video metadata and frames
            initializeVideoData()
        } else {
            showError("Error loading video")
        }
    }

    private fun initializeVideoData() {
        coroutineScope.launch {
            try {
                val videoFilePath = getFilePathFromUri(videoUri!!)
                if (videoFilePath != null) {
                    tempInputFile = File(videoFilePath)
                    videoFileName = tempInputFile.name

                    extractVideoFrames()
                }
            } catch (e: Exception) {
                showError("Error initializing video: ${e.message}")
            }
        }
    }


    private fun getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null

        when (uri.scheme) {
            "content" -> {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val dataIndex = it.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (dataIndex != -1) {
                            filePath = it.getString(dataIndex) // Fetch file path
                        } else {
                            Log.e("PathError", "Column '_data' not found in cursor")
                        }
                    } else {
                        Log.e("PathError", "Cursor is empty for URI: $uri")
                    }
                } ?: Log.e("PathError", "Cursor is null for URI: $uri")
            }
            "file" -> {
                filePath = uri.path // Directly get path from URI
            }
            else -> {
                Log.e("PathError", "Unsupported URI scheme: ${uri.scheme}")
            }
        }

        Log.d("PathInfo", "File path: $filePath")
        return filePath
    }


    private fun setupCustomSeeker() {
        // Configure the custom video seeker for seeking playback
        customVideoSeeker.onSeekListener = { seekPosition ->
            val newSeekTime = (player.duration * seekPosition).toLong()

            // Ensure new seek time is within valid bounds
            if (newSeekTime >= 0 && newSeekTime <= player.duration) {
                player.seekTo(newSeekTime) // Seek to new position
                updateDurationDisplay(newSeekTime.toInt(), player.duration.toInt()) // Update duration display
            } else {
                Log.d("SeekError", "Seek position out of bounds.")
            }
        }
    }

    private fun setupFrameRecyclerView() {
        frameRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        frameRecyclerView.adapter = FrameAdapter(emptyList()) // Initialize frame adapter with video name
    }

    private fun extractVideoFrames() {
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempInputFile.absolutePath)

            val duration = withContext(Dispatchers.Main) { player.duration }
            val frameInterval = duration / 10 // Extract fewer frames

            val frameBitmaps = mutableListOf<Bitmap>()
            val frameCount = 10 // Adjust to change how many frames you extract

            try {
                for (i in 0 until frameCount) {
                    val frameTime = (i * frameInterval) // Time in microseconds
                    val bitmap = retriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    bitmap?.let {
                        val processedBitmap = Bitmap.createScaledBitmap(it, 200, 150, false)
                        frameBitmaps.add(processedBitmap)
                    }
                }
            } finally {
                retriever.release()
            }

            withContext(Dispatchers.Main) {
                frameRecyclerView.adapter = FrameAdapter(frameBitmaps)
                // Update Loading screen after completion
                loadingScreen.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDurationDisplay(current: Int, total: Int) {
        if (!isVideoLoaded || total <= 0) return

        val currentFormatted = formatDuration(current)
        val totalFormatted = formatDuration(total)

        tvDuration.text = "$currentFormatted / $totalFormatted"
    }

    private fun formatDuration(milliseconds: Int): String {
        val minutes = milliseconds / 60000
        val seconds = (milliseconds % 60000) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all active FFmpeg sessions
        // Cancel all active FFmpeg sessions
//        activeFFmpegSessions.forEach { session ->
//            FFmpegKit.cancel(session.sessionId)
//        }
//        activeFFmpegSessions.clear()

        // Release resources
        player.release()
        coroutineScope.cancel()
    }

    private suspend fun getVideoMetadata(context: Context, uri: Uri): Media {
        return withContext(Dispatchers.IO) {
            val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            // Define projection for querying video metadata
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATA // Fetch the real file path
            )

            val selection = "${MediaStore.Video.Media._ID} = ?"
            val selectionArgs = arrayOf(uri.lastPathSegment)

            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val displayNameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                if (cursor.moveToFirst()) {
                    // Retrieve metadata from cursor
                    val fileName = cursor.getString(displayNameColumnIndex)
                    val size = cursor.getLong(sizeColumnIndex)
                    val mimeType = cursor.getString(mimeTypeColumnIndex)
                    val realFilePath = cursor.getString(dataColumnIndex)

                    // Log metadata for debugging
                    Log.d(TAG, "File Name: $fileName")
                    Log.d(TAG, "File Size: $size bytes")
                    Log.d(TAG, "MIME Type: $mimeType")
                    Log.d("MetadataInfo", "File Name: $fileName, Size: $size bytes, MIME Type: $mimeType, Real File Path: $realFilePath")


                    // Return Media object containing the metadata
                    return@use Media(Uri.parse(realFilePath), fileName, size, mimeType)
                } else {
                    Log.e("MetadataError", "cursor.moveToFirst() returned false")
                    throw Error("cursor.moveToFirst() method returned false")
                }
            } ?: run {
                Log.e("MetadataError", "Unexpected null from contentResolver query")
                throw Error("Unexpected null returned by contentResolver query")
            }
        }
    }

    data class Media(
        val uri: Uri,
        val name: String,
        val size: Long,
        val mimeType: String
    )

    companion object {
        private const val TAG = "VideoMetadata"
        private const val PICK_VIDEO_REQUEST = 1
    }
}