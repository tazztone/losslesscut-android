package com.tazztone.losslesscut.ui

import android.view.View
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.databinding.FragmentEditorBinding
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

    @BindValue
    @JvmField
    val playerManager: com.tazztone.losslesscut.ui.PlayerManager = mockk(relaxed = true)

    @Before
    fun init() {
        hiltRule.inject()
    }

    private fun getBinding(fragment: EditorFragment): FragmentEditorBinding {
        val field = EditorFragment::class.java.getDeclaredField("_binding")
        field.isAccessible = true
        return field.get(fragment) as FragmentEditorBinding
    }

    @Test
    fun `fragment should initialize without crash`() {
        launchFragmentInHiltContainer<EditorFragment> {
            val fragment = this as EditorFragment
            val binding = getBinding(fragment)
            // Verify critical components exist
            assert(binding.navBar.root != null)
            assert(binding.editingControls.root != null)
            assert(binding.playerSection.root != null)
            assert(binding.seekerContainer.root != null)
        }
    }

    @Test
    @Config(qualifiers = "land")
    fun `fragment should initialize without crash in landscape`() {
        launchFragmentInHiltContainer<EditorFragment> {
            val fragment = this as EditorFragment
            val binding = getBinding(fragment)
            // Verify sidebars exist in landscape
            assert(view?.findViewById<View>(R.id.navSidebar) != null)
            assert(view?.findViewById<View>(R.id.editingControlsSidebar) != null)
            
            // Verify critical views resolve
            assert(binding.navBar.btnExport != null)
            assert(binding.editingControls.btnRotate != null)
        }
    }
}
