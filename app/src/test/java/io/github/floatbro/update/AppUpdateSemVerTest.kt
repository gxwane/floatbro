/*
 * Copyright (c) 2026 FloatBro Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.floatbro.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateSemVerTest {

    @Test
    fun testCompareSemVerIdentical() {
        assertEquals(0, AppUpdateManager.compareSemVer("1.0.0", "1.0.0"))
        assertEquals(0, AppUpdateManager.compareSemVer("2.1", "2.1.0"))
    }

    @Test
    fun testCompareSemVerNewerMajor() {
        assertTrue(AppUpdateManager.compareSemVer("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun testCompareSemVerNewerMinor() {
        assertTrue(AppUpdateManager.compareSemVer("1.2.0", "1.1.9") > 0)
    }

    @Test
    fun testCompareSemVerNewerPatch() {
        assertTrue(AppUpdateManager.compareSemVer("1.0.1", "1.0.0") > 0)
        assertTrue(AppUpdateManager.compareSemVer("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun testIsNewerVersionWithVPrefix() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.0", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("V2.0.0", "1.9.5"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v0.9.9", "1.0.0"))
    }

    @Test
    fun testIsNewerVersionWithPrereleaseTag() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.0-beta.1", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0-beta.1", "1.0.0"))
    }
}
