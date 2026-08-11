package com.tazztone.losslesscut.ui

import com.tazztone.losslesscut.R

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.databinding.ActivityMainBinding
import com.tazztone.losslesscut.domain.model.SessionSummary
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentSessionAdapter: RecentSessionAdapter

    @Inject
    lateinit var sessionUseCase: SessionUseCase

    private val selectMediaLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            val validUris = uris.filter(::isValidUri)
            if (validUris.isNotEmpty()) {
                Log.d("MediaSelection", "Selected ${validUris.size} media item(s)")
                navigateToEditingScreen(validUris)
            } else {
                Log.e("MediaSelectionError", "No media selected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDashboard()
        loadRecentSessions()

        binding.btnInfo.setOnClickListener {
            showAboutDialog()
        }

        binding.btnLoadMedia.setOnClickListener { selectMedia() }

        handleIncomingIntent(intent)
    }

    private fun setupDashboard() {
        recentSessionAdapter = RecentSessionAdapter(
            onResume = ::resumeSession,
            onRemove = ::removeSession
        )
        binding.rvRecentSessions.adapter = recentSessionAdapter
    }

    override fun onResume() {
        super.onResume()
        if (::recentSessionAdapter.isInitialized) loadRecentSessions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            val validUris = uris?.filter { isValidUri(it) }
            if (!validUris.isNullOrEmpty()) {
                Log.d("IncomingIntent", "Received ${validUris.size} valid media item(s)")
                navigateToEditingScreen(validUris)
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

            if (isValidUri(uri)) {
                Log.d("IncomingIntent", "Received media URI from ${uri?.authority}")
                navigateToEditingScreen(listOf(uri!!))
            }
        }
    }

    private fun isValidUri(uri: Uri?): Boolean {
        if (uri == null) return false

        return when (uri.scheme) {
            "content" -> isValidContentUri(uri)
            else -> {
                Log.w("Security", "Blocked non-SAF URI with scheme: ${uri.scheme}")
                false
            }
        }
    }

    private fun isValidContentUri(uri: Uri): Boolean {
        val authority = uri.authority
        if (authority == packageName || authority == "$packageName.provider") {
            Log.w("Security", "Blocked URI with internal authority: $authority")
            return false
        }
        return try {
            val type = contentResolver.getType(uri)
            if (type != null && !type.startsWith("video/") && !type.startsWith("audio/")) {
                Log.w("Security", "Blocked non-media content URI from authority: $authority")
                return false
            }
            contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
        } catch (e: java.io.FileNotFoundException) {
            Log.w("Security", "Content URI is not readable from authority: $authority", e)
            false
        } catch (e: SecurityException) {
            Log.w("Security", "No read permission for content URI from authority: $authority", e)
            false
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
        Log.d("MediaSelection", "Launching unified media selector")
        selectMediaLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    private fun navigateToEditingScreen(mediaUris: List<Uri>, resumeSession: Boolean = false) {
        mediaUris.forEach(::persistReadPermission)
        Log.d("Navigation", "Navigating ${mediaUris.size} media item(s) to unified editor")
        val intent = Intent(this, VideoEditingActivity::class.java).apply {
            setPackage(packageName)
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, ArrayList(mediaUris))
            putExtra(VideoEditingActivity.EXTRA_RESUME_SESSION, resumeSession)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // For multiple URIs, ClipData is the standard way to grant permissions to all of them
            if (mediaUris.isNotEmpty()) {
                clipData = android.content.ClipData.newRawUri("Media", mediaUris[0])
                for (i in 1 until mediaUris.size) {
                    clipData?.addItem(android.content.ClipData.Item(mediaUris[i]))
                }
            }
        }
        startActivity(intent)
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers grant only a temporary read permission; resume will validate it later.
        }
    }

    private fun loadRecentSessions() {
        lifecycleScope.launch {
            val sessions = sessionUseCase.listSavedSessions()
            recentSessionAdapter.submitList(sessions)
            binding.tvRecentSessionsTitle.visibility = if (sessions.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            binding.rvRecentSessions.visibility = if (sessions.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            binding.tvNoRecentSessions.visibility = if (sessions.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun resumeSession(session: SessionSummary) {
        val uri = Uri.parse(session.uri)
        if (isValidUri(uri)) {
            persistReadPermission(uri)
            navigateToEditingScreen(listOf(uri), resumeSession = true)
        } else {
            lifecycleScope.launch {
                sessionUseCase.deleteSession(session.uri)
                loadRecentSessions()
            }
            android.widget.Toast.makeText(this, R.string.session_unavailable, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun removeSession(session: SessionSummary) {
        lifecycleScope.launch {
            sessionUseCase.deleteSession(session.uri)
            loadRecentSessions()
        }
    }

}
