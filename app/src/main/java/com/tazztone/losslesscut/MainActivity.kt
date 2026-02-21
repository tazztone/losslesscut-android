package com.tazztone.losslesscut

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private val selectMediaLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                Log.d("MediaSelection", "Media selected: $uris")
                navigateToEditingScreen(uris)
            } else {
                Log.e("MediaSelectionError", "No media selected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addVideoButton.setOnClickListener {
            Log.d("ButtonClick", "Launching media selection.")
            selectMedia()
        }

        binding.btnInfo.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val message = getString(R.string.about_message, com.tazztone.losslesscut.BuildConfig.VERSION_NAME)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(android.text.Html.fromHtml(message, android.text.Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun selectMedia() {
        Log.d("MediaSelection", "Launching media selector.")
        selectMediaLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    private fun navigateToEditingScreen(mediaUris: List<Uri>) {
        Log.d("Navigation", "Navigating to editing screen with URIs: $mediaUris")
        val intent = Intent(this, VideoEditingActivity::class.java)
        intent.putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, ArrayList(mediaUris))
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Log.d("ToastMessage", "Showing toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}