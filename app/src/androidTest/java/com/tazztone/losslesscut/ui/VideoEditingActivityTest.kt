package com.tazztone.losslesscut.ui
import com.tazztone.losslesscut.di.*
import com.tazztone.losslesscut.customviews.*
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.*
import com.tazztone.losslesscut.viewmodel.*
import com.tazztone.losslesscut.engine.*
import com.tazztone.losslesscut.data.*
import com.tazztone.losslesscut.utils.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoEditingActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val videoUri = Uri.parse("content://mock/video.mp4")
    
    @get:Rule
    val activityRule = ActivityScenarioRule<VideoEditingActivity>(
        Intent(context, VideoEditingActivity::class.java).apply {
            putParcelableArrayListExtra(VideoEditingActivity.EXTRA_VIDEO_URIS, arrayListOf(videoUri))
        }
    )

    @Test
    fun testUIElementsVisible() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        onView(withId(R.id.btnHome)).check(matches(isDisplayed()))
        onView(withId(R.id.btnSave)).check(matches(isDisplayed()))
        onView(withId(R.id.btnUndo)).check(matches(isDisplayed()))
        onView(withId(R.id.btnSplit)).check(matches(isDisplayed()))
        onView(withId(R.id.btnDelete)).check(matches(isDisplayed()))
        onView(withId(R.id.tvDuration)).check(matches(isDisplayed()))
        onView(withId(R.id.customVideoSeeker)).check(matches(isDisplayed()))
    }

    @Test
    fun testNLEButtonsAreClickable() {
        onView(withId(R.id.btnSplit)).check(matches(isClickable()))
        onView(withId(R.id.btnDelete)).check(matches(isClickable()))
        onView(withId(R.id.btnUndo)).check(matches(isClickable()))
        
        // Ensure action performs without crashing
        onView(withId(R.id.btnSplit)).perform(androidx.test.espresso.action.ViewActions.click())
    }
}
