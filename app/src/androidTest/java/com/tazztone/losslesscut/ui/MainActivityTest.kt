package com.tazztone.losslesscut.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazztone.losslesscut.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testUIElementsVisible() {
        val loadMediaText = composeTestRule.activity.getString(R.string.load_media)
        composeTestRule.onNodeWithText(loadMediaText).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("About LosslessCut").assertIsDisplayed()
    }

    @Test
    fun testAboutDialogShows() {
        composeTestRule.onNodeWithContentDescription("About LosslessCut").performClick()
        val okText = composeTestRule.activity.getString(R.string.ok)
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(okText))
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(okText))
            .perform(androidx.test.espresso.action.ViewActions.click())
    }
}
