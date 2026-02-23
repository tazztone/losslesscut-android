package com.tazztone.losslesscut.ui
import com.tazztone.losslesscut.di.*
import com.tazztone.losslesscut.customviews.*
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.*
import com.tazztone.losslesscut.viewmodel.*
import com.tazztone.losslesscut.engine.*
import com.tazztone.losslesscut.data.*
import com.tazztone.losslesscut.utils.*

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
    private var pendingLaunchMode = VideoEditingActivity.MODE_CUT

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

        setupDashboard()

        binding.btnInfo.setOnClickListener {
            showAboutDialog()
        }

        handleIncomingIntent(intent)
    }

    private fun setupDashboard() {
        val actions = listOf(
            DashboardAction(
                id = "cut",
                title = getString(R.string.dashboard_cut_title),
                description = getString(R.string.dashboard_cut_desc),
                iconResId = R.drawable.ic_add_24,
                isPrimary = true
            ),
            DashboardAction(
                id = "remux",
                title = getString(R.string.dashboard_remux_title),
                description = getString(R.string.dashboard_remux_desc),
                iconResId = R.drawable.ic_save_24
            ),
            DashboardAction(
                id = "metadata",
                title = getString(R.string.dashboard_metadata_title),
                description = getString(R.string.dashboard_metadata_desc),
                iconResId = R.drawable.ic_settings_24
            )
        )

        binding.rvDashboard.adapter = DashboardAdapter(actions) { action ->
            when (action.id) {
                "cut"      -> selectMedia(VideoEditingActivity.MODE_CUT)
                "remux"    -> selectMedia(VideoEditingActivity.MODE_REMUX)
                "metadata" -> selectMedia(VideoEditingActivity.MODE_METADATA)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            pendingLaunchMode = VideoEditingActivity.MODE_CUT // sensible default for share
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            uris?.let {
                Log.d("IncomingIntent", "Received multiple URIs: $it")
                navigateToEditingScreen(it)
            }
        } else if ((Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) && type != null) {
            val uri = if (Intent.ACTION_SEND == action) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } else {
                intent.data
            }

            uri?.let {
                Log.d("IncomingIntent", "Received URI: $it")
                navigateToEditingScreen(listOf(it))
            }
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

    private fun selectMedia(mode: String) {
        pendingLaunchMode = mode
        Log.d("MediaSelection", "Launching media selector in mode: $mode")
        selectMediaLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    private fun navigateToEditingScreen(mediaUris: List<Uri>) {
        Log.d("Navigation", "Navigating to editing screen with URIs: $mediaUris, mode: $pendingLaunchMode")
        val intent = Intent(this, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, ArrayList(mediaUris))
            putExtra(VideoEditingActivity.EXTRA_LAUNCH_MODE, pendingLaunchMode)
        }
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Log.d("ToastMessage", "Showing toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}