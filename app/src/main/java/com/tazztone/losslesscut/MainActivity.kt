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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private val selectMediaLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                Log.d("MediaSelection", "Media selected: $uri")
                navigateToEditingScreen(uri)
            } else {
                Log.e("MediaSelectionError", "No media selected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPermissions()

        binding.addVideoButton.setOnClickListener {
            if (arePermissionsGranted()) {
                Log.d("ButtonClick", "Permissions granted, launching media selection.")
                selectMedia()
            } else {
                Log.w("PermissionCheck", "Permissions not granted, showing request dialog.")
                showPermissionRequestDialog()
            }
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

    private fun setupPermissions() {
        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    Log.d("PermissionResult", "All permissions granted.")
                    showToast(getString(R.string.permissions_granted))
                } else {
                    Log.w("PermissionResult", "Some permissions were denied.")
                    showToast(getString(R.string.permissions_denied))
                }
            }

        if (!arePermissionsGranted()) {
            Log.i("PermissionSetup", "Requesting permissions.")
            showPermissionRequestDialog()
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                checkPermissions(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> true
        }
    }

    private fun checkPermissions(vararg permissions: String): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }.also { result ->
            Log.d("PermissionCheck", "Permissions checked: $result")
        }
    }

    private fun showPermissionRequestDialog() {
        Log.i("PermissionDialog", "Displaying permission request dialog.")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions_required_title)
            .setMessage(R.string.permissions_required_message)
            .setPositiveButton(R.string.grant_permission) { _, _ -> requestPermissions() }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                Log.i("PermissionDialog", "User canceled the permission request.")
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                emptyArray()
            }
        }
        if (permissions.isNotEmpty()) {
            Log.d("PermissionRequest", "Requesting permissions: ${permissions.joinToString()}")
            requestPermissionsLauncher.launch(permissions)
        } else {
            showToast(getString(R.string.permissions_granted))
        }
    }

    private fun selectMedia() {
        Log.d("MediaSelection", "Launching media selector.")
        selectMediaLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    private fun navigateToEditingScreen(mediaUri: Uri) {
        Log.d("Navigation", "Navigating to editing screen with URI: $mediaUri")
        val intent = Intent(this, VideoEditingActivity::class.java)
        intent.putExtra(VideoEditingActivity.EXTRA_VIDEO_URI, mediaUri)
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Log.d("ToastMessage", "Showing toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}