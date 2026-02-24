package com.tazztone.losslesscut.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.NavHostFragment
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.ActivityVideoEditingBinding
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class VideoEditingActivity : BaseActivity() {

    companion object {
        const val EXTRA_VIDEO_URIS = "com.tazztone.losslesscut.EXTRA_VIDEO_URIS"
        const val EXTRA_LAUNCH_MODE = "com.tazztone.losslesscut.EXTRA_LAUNCH_MODE"

        const val MODE_CUT      = "cut"
        const val MODE_REMUX    = "remux"
        const val MODE_METADATA = "metadata"
    }

    private val viewModel: VideoEditingViewModel by viewModels()
    private lateinit var binding: ActivityVideoEditingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_URIS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_VIDEO_URIS)
        }

        if (videoUris.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_video_uri), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        hideSystemUI()

        if (savedInstanceState == null) {
            viewModel.initialize(videoUris)
            setupNavigation()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserInteraction()
    }

    private fun setupNavigation() {
        val launchMode = intent.getStringExtra(EXTRA_LAUNCH_MODE) ?: MODE_CUT
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(
            when (launchMode) {
                MODE_REMUX -> R.id.remuxFragment
                MODE_METADATA -> R.id.metadataFragment
                else -> R.id.editorFragment
            }
        )
        navController.graph = navGraph
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
}
