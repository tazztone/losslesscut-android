package com.tazztone.losslesscut.ui

import androidx.lifecycle.Lifecycle
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel
import com.tazztone.losslesscut.launchFragmentInHiltContainer
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class EditorFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val viewModel: VideoEditingViewModel = mockk(relaxed = true)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun `fragment should initialize without crash`() {
        launchFragmentInHiltContainer<EditorFragment> {
            // If it reaches here without crash, the basic initialization is verified.
        }
    }

    @Test
    @Config(qualifiers = "land")
    fun `fragment should initialize without crash in landscape`() {
        launchFragmentInHiltContainer<EditorFragment> {
        }
    }
}
