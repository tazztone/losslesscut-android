package com.tazztone.losslesscut.ui

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardAdapterBenchmarkTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
    }

    @Test
    fun benchmarkOnBindViewHolder() {
        val actions = List(100) { i ->
            DashboardAction(
                id = i.toString(),
                title = "Title $i",
                description = "Desc $i",
                iconResId = android.R.drawable.ic_menu_add,
                isPrimary = i % 2 == 0
            )
        }

        val adapter = DashboardAdapter(actions) {}
        val parent = FrameLayout(context)

        // Pre-create viewholders
        val viewHolders = List(10) {
            adapter.onCreateViewHolder(parent, 0)
        }

        // Warmup
        for (i in 0 until 100) {
            adapter.onBindViewHolder(viewHolders[i % 10], i)
        }

        val iterations = 10000
        val time = measureTimeMillis {
            for (i in 0 until iterations) {
                adapter.onBindViewHolder(viewHolders[i % 10], i % 100)
            }
        }

        File("benchmark_result.txt").writeText("BENCHMARK_RESULT: $time ms for $iterations bindings\n")
    }
}
