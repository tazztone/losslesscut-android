package com.tazztone.losslesscut.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.databinding.ItemDashboardActionBinding
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardAdapterViewHolderTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
    }

    @Test
    fun testViewHolderInitialization() {
        val parent = FrameLayout(context)
        val inflater = LayoutInflater.from(context)
        val binding = ItemDashboardActionBinding.inflate(inflater, parent, false)

        val viewHolder = DashboardAdapter.ViewHolder(binding)

        assertEquals(binding, viewHolder.binding)
        assertEquals(binding.root, viewHolder.itemView)
    }
}
