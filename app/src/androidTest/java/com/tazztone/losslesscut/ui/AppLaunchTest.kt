package com.tazztone.losslesscut.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazztone.losslesscut.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_without_crashing() {
        val loadMediaText = composeTestRule.activity.getString(R.string.load_media)
        composeTestRule.onNodeWithText(loadMediaText).assertIsDisplayed()
    }
}
