package com.tazztone.losslesscut.util

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Attaches a touch listener to a View (e.g., ImageButton) that performs [onStep] on initial touch down,
 * and continuously auto-repeats [onStep] while held down.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.setupAutoRepeat(
    initialDelayMs: Long = 300L,
    repeatIntervalMs: Long = 80L,
    onStep: () -> Unit
) {
    val handler = Handler(Looper.getMainLooper())
    var isTouchStepping = false

    val repeatRunnable = object : Runnable {
        override fun run() {
            if (isTouchStepping && isEnabled) {
                onStep()
                handler.postDelayed(this, repeatIntervalMs)
            }
        }
    }

    setOnClickListener {
        if (!isTouchStepping) {
            onStep()
        }
    }

    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (v.isEnabled) {
                    isTouchStepping = true
                    onStep()
                    handler.removeCallbacks(repeatRunnable)
                    handler.postDelayed(repeatRunnable, initialDelayMs)
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTouchStepping) {
                    handler.removeCallbacks(repeatRunnable)
                    v.post { isTouchStepping = false }
                }
                true
            }
            else -> false
        }
    }
}
