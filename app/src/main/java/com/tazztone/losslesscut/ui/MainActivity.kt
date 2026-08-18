package com.tazztone.losslesscut.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.HashUtils
import com.tazztone.losslesscut.domain.model.SessionSummary
import com.tazztone.losslesscut.domain.usecase.SessionUseCase
import com.tazztone.losslesscut.ui.compose.dashboard.MainDashboardScreen
import com.tazztone.losslesscut.ui.compose.settings.SettingsBottomSheetDialogFragment
import com.tazztone.losslesscut.ui.compose.theme.LosslessCutTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : BaseActivity() {

    @Inject
    lateinit var sessionUseCase: SessionUseCase

    private var recentSessions by mutableStateOf<List<SessionSummary>>(emptyList())
    private val snackbarHostState = SnackbarHostState()
    private var pendingDeleteJob: Job? = null

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
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val accentColorName by preferences.accentColorFlow.collectAsStateWithLifecycle(
                initialValue = preferences.getAccentColorSync()
            )

            LosslessCutTheme(accentColorName = accentColorName) {
                MainDashboardScreen(
                    recentSessions = recentSessions,
                    snackbarHostState = snackbarHostState,
                    onLoadMedia = ::selectMedia,
                    onOpenSettings = ::showSettingsDialog,
                    onOpenAbout = ::showAboutDialog,
                    onResumeSession = ::resumeSession,
                    onRemoveSession = ::removeSession
                )
            }
        }

        loadRecentSessions()
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        loadRecentSessions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action

        if (Intent.ACTION_SEND_MULTIPLE == action) {
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
        } else if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
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
        if (authority == packageName) {
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

    private fun showSettingsDialog() {
        SettingsBottomSheetDialogFragment().show(supportFragmentManager, "settings_bottom_sheet")
    }

    private fun selectMedia() {
        Log.d("MediaSelection", "Launching unified media selector")
        selectMediaLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    private fun navigateToEditingScreen(
        mediaUris: List<Uri>,
        resumeSession: Boolean = false,
        sessionId: String = java.util.UUID.randomUUID().toString()
    ) {
        mediaUris.forEach(::persistReadPermission)
        Log.d("Navigation", "Navigating ${mediaUris.size} media item(s) to unified editor")
        val intent = Intent(this, VideoEditingActivity::class.java).apply {
            setPackage(packageName)
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, ArrayList(mediaUris))
            putExtra(VideoEditingActivity.EXTRA_RESUME_SESSION, resumeSession)
            putExtra(VideoEditingActivity.EXTRA_SESSION_ID, sessionId)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

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
            recentSessions = sessionUseCase.listSavedSessions()
        }
    }

    private fun resumeSession(session: SessionSummary) {
        val uri = Uri.parse(session.uri)
        val sessionId = session.sessionId.ifEmpty { HashUtils.sha256(session.uri) }
        if (isValidUri(uri)) {
            persistReadPermission(uri)
        }
        navigateToEditingScreen(listOf(uri), resumeSession = true, sessionId = sessionId)
    }

    private fun removeSession(session: SessionSummary) {
        // Optimistically remove from state and offer undo via Snackbar
        val previousList = recentSessions
        recentSessions = recentSessions.filterNot { it.uri == session.uri }

        pendingDeleteJob?.cancel()
        pendingDeleteJob = lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(R.string.session_removed),
                actionLabel = getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // User pressed Undo
                recentSessions = previousList
            } else {
                // Actually delete from persistence
                sessionUseCase.deleteSession(session.sessionId.ifEmpty { HashUtils.sha256(session.uri) })
                loadRecentSessions()
            }
        }
    }
}
