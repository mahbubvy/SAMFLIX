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
}
