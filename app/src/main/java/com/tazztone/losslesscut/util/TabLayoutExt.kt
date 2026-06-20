package com.tazztone.losslesscut.util

import com.google.android.material.tabs.TabLayout

/**
 * Adds a tab selected listener to the TabLayout that only requires the onTabSelected method to be implemented.
 */
inline fun TabLayout.addOnTabSelectedListener(
    crossinline onTabSelected: (TabLayout.Tab?) -> Unit
) {
    addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            onTabSelected(tab)
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {
            // no-op
        }

        override fun onTabReselected(tab: TabLayout.Tab?) {
            // no-op
        }
    })
}
