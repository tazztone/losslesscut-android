package com.tazztone.losslesscut.ui

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardAdapterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
    }

    @Test
    fun testGetItemCount() {
        val actions = listOf(
            DashboardAction("1", "Title1", "Desc1", android.R.drawable.ic_menu_add)
        )
        val adapter = DashboardAdapter(actions) {}
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun testOnBindViewHolderPrimary() {
        var clickedAction: DashboardAction? = null
        val actions = listOf(
            DashboardAction("1", "Title1", "Desc1", android.R.drawable.ic_menu_add, isPrimary = true)
        )
        val adapter = DashboardAdapter(actions) { clickedAction = it }
        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        assertEquals("Title1", viewHolder.binding.tvActionTitle.text)
        assertEquals("Desc1", viewHolder.binding.tvActionDesc.text)

        viewHolder.binding.root.performClick()
        assertEquals(actions[0], clickedAction)
    }

    @Test
    fun testOnBindViewHolderNonPrimary() {
        val actions = listOf(
            DashboardAction("1", "Title1", "Desc1", android.R.drawable.ic_menu_add, isPrimary = false)
        )
        val adapter = DashboardAdapter(actions) {}
        val parent = FrameLayout(context)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        assertEquals("Title1", viewHolder.binding.tvActionTitle.text)
        assertEquals("Desc1", viewHolder.binding.tvActionDesc.text)
    }
}
