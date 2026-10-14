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

package io.github.floatbro.browser

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit Tests for BrowserPreferences Click-to-Focus settings.
 */
class BrowserPreferencesClickToFocusTest {
    private val inMemoryPrefs = HashMap<String, Any?>()
    private val mockContext = mockk<Context>()
    private val mockPrefs = mockk<SharedPreferences>()
    private val mockEditor = mockk<SharedPreferences.Editor>()

    @Before
    fun setup() {
        inMemoryPrefs.clear()
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        every { mockPrefs.getBoolean(any(), any()) } answers {
            (inMemoryPrefs[firstArg()] as? Boolean) ?: secondArg()
        }

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } answers {
            inMemoryPrefs[firstArg()] = secondArg<Boolean>()
            mockEditor
        }
        every { mockEditor.apply() } returns Unit
    }

    @Test
    fun `isClickToFocusEnabled defaults to true for desktop-grade windowing safety`() {
        val prefs = BrowserPreferences(mockContext)
        assertTrue("Click-to-Focus should be enabled by default to prevent accidental link clicks", prefs.isClickToFocusEnabled)
    }

    @Test
    fun `isClickToFocusEnabled can be toggled to false and back to true`() {
        val prefs = BrowserPreferences(mockContext)
        prefs.isClickToFocusEnabled = false
        assertFalse(prefs.isClickToFocusEnabled)

        prefs.isClickToFocusEnabled = true
        assertTrue(prefs.isClickToFocusEnabled)
    }
}
