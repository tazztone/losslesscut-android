package com.tazztone.losslesscut.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!::preferences.isInitialized) {
            preferences = AppPreferences(this)
        }
        val themeOverlay = runBlocking {
            val color = preferences.accentColorFlow.first()
            when (color) {
                "purple" -> R.style.AppTheme_Purple
                "green" -> R.style.AppTheme_Green
                "yellow" -> R.style.AppTheme_Yellow
                "red" -> R.style.AppTheme_Red
                "orange" -> R.style.AppTheme_Orange
                else -> null
            }
        }
        themeOverlay?.let { theme.applyStyle(it, true) }
        super.onCreate(savedInstanceState)
    }
}
