package com.tazztone.losslesscut.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app plays local SAF/MediaStore URIs only. Playback AARs (Media3) contribute
 * network permissions via manifest merge; they must stay stripped.
 */
class ManifestPermissionTest {

    @Test
    fun testNetworkPermissionsAreStripped() {
        val manifest = File("src/main/AndroidManifest.xml")
        require(manifest.exists()) { "expected :app module working dir" }
        val text = manifest.readText()
        for (permission in listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        )) {
            val granted = text.lines().filter { line ->
                line.contains("uses-permission") &&
                    line.contains(permission) &&
                    !line.contains("tools:node=\"remove\"")
            }
            assertTrue("no granted $permission declaration allowed: $granted", granted.isEmpty())
            assertTrue(
                "$permission must carry tools:node=\"remove\"",
                text.lines().any { line ->
                    line.contains("uses-permission") &&
                        line.contains(permission) &&
                        line.contains("tools:node=\"remove\"")
                }
            )
        }
    }
}
