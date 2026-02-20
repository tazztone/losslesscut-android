package com.tazztone.losslesscut

import android.app.Application
import com.google.android.material.color.DynamicColors

class LosslessCutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
