package com.tazztone.losslesscut.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.tazztone.losslesscut.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BaseActivityPerfTest {

    @Test
    fun benchmarkRunBlockingVsSharedPrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)

        // Warmup
        runBlocking { prefs.accentColorFlow.first() }

        var runBlockingTotal = 0L
        val iterations = 50
        for (i in 1..iterations) {
            val time = measureTimeMillis {
                runBlocking {
                    prefs.accentColorFlow.first()
                }
            }
            runBlockingTotal += time
        }

        val sharedPrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("accent_color", "cyan").commit()

        var sharedPrefsTotal = 0L
        for (i in 1..iterations) {
            val time = measureTimeMillis {
                sharedPrefs.getString("accent_color", "cyan")
            }
            sharedPrefsTotal += time
        }

        println("BENCHMARK_RESULT: runBlocking avg=${runBlockingTotal.toDouble()/iterations}ms")
        println("BENCHMARK_RESULT: SharedPreferences avg=${sharedPrefsTotal.toDouble()/iterations}ms")
    }
}
