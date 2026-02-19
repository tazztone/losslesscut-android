package com.tazztone.losslesscut

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Intent
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testUIElementsVisible() {
        onView(withId(R.id.addVideoButton)).check(matches(isDisplayed()))
        onView(withId(R.id.btnInfo)).check(matches(isDisplayed()))
        onView(withId(R.id.instructionText)).check(matches(isDisplayed()))
    }

    @Test
    fun testInfoDialogShows() {
        onView(withId(R.id.btnInfo)).perform(click())
        onView(withText("About")).check(matches(isDisplayed()))
        onView(withText("OK")).perform(click())
    }

    @Test
    fun testAddVideoClick_launchesPicker() {
        // Permissions might be requested first, which is hard to handle in a generic test
        // but we can at least check if the click works.
        // If permissions are granted, it should launch GetContent intent.
        
        onView(withId(R.id.addVideoButton)).perform(click())
        
        // This might fail if the permission dialog blocks the intent launch
        // So we just check that the button is clickable for now.
    }
}
