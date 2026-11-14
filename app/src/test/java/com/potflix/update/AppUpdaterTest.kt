package com.potflix.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun releaseTagsMapToTheBuildsVersionCodes() {
        // Must match app/build.gradle.kts: versionCode = 400 + commit count, versionName = 3.1.<count>.
        assertEquals(457, AppUpdater.versionCodeFromTag("v3.1.57"))
        assertEquals(400, AppUpdater.versionCodeFromTag("3.1.0"))
    }

    @Test
    fun foreignTagsAreIgnored() {
        assertNull(AppUpdater.versionCodeFromTag("Android.v2.1.2"))
        assertNull(AppUpdater.versionCodeFromTag("v3.0.0-samonline"))
        assertNull(AppUpdater.versionCodeFromTag("v4.0.1"))
    }

    @Test
    fun latestReleaseIsReadFromTheRedirect() {
        assertEquals("v3.1.26", AppUpdater.tagFromLatestRedirect("https://github.com/mahbubvy/SAMFLIX/releases/tag/v3.1.26"))
        assertEquals(null, AppUpdater.tagFromLatestRedirect("https://github.com/mahbubvy/SAMFLIX/releases"))
        assertEquals(
            "https://github.com/mahbubvy/SAMFLIX/releases/download/v3.1.26/SAMFLIX-3.1.26-arm64-v8a.apk",
            AppUpdater.apkUrlFor("v3.1.26", "arm64-v8a")
        )
    }
}
