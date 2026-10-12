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
 * Unit Tests for BrowserPreferences AdBlock toggle and domain whitelist logic.
 */
class BrowserPreferencesAdBlockTest {

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
        every { mockPrefs.getString(any(), any()) } answers {
            (inMemoryPrefs[firstArg()] as? String) ?: secondArg()
        }
        every { mockPrefs.getStringSet(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<Set<String>?>()
            val stored = inMemoryPrefs[key] as? Set<String>
            (stored ?: def ?: emptySet()).toMutableSet()
        }

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } answers {
            inMemoryPrefs[firstArg()] = secondArg<Boolean>()
            mockEditor
        }
        every { mockEditor.putString(any(), any()) } answers {
            inMemoryPrefs[firstArg()] = secondArg<String>()
            mockEditor
        }
        every { mockEditor.putStringSet(any(), any()) } answers {
            inMemoryPrefs[firstArg()] = secondArg<Set<String>>()
            mockEditor
        }
        every { mockEditor.apply() } returns Unit
    }

    @Test
    fun `adblock is enabled by default and whitelist is empty`() {
        val prefs = BrowserPreferences(mockContext)
        assertTrue(prefs.isAdBlockEnabled)
        assertTrue(prefs.adBlockWhitelist.isEmpty())
        assertTrue(prefs.isAdBlockEnabledForHost("m.piquge.com"))
    }

    @Test
    fun `global adblock disable turns off blocking for all hosts`() {
        val prefs = BrowserPreferences(mockContext)
        prefs.isAdBlockEnabled = false
        assertFalse(prefs.isAdBlockEnabledForHost("m.piquge.com"))
        assertFalse(prefs.isAdBlockEnabledForHost("baidu.com"))
    }

    @Test
    fun `whitelisting exact host bypasses adblock for that host`() {
        val prefs = BrowserPreferences(mockContext)
        assertTrue(prefs.isAdBlockEnabledForHost("m.piquge.com"))

        // Whitelist m.piquge.com
        prefs.adBlockWhitelist = setOf("m.piquge.com")
        assertFalse(prefs.isAdBlockEnabledForHost("m.piquge.com"))

        // Other hosts remain blocked
        assertTrue(prefs.isAdBlockEnabledForHost("other.site.com"))
    }

    @Test
    fun `whitelisting parent domain matches subdomains`() {
        val prefs = BrowserPreferences(mockContext)
        prefs.adBlockWhitelist = setOf("piquge.com")

        // Subdomain matches parent domain whitelist
        assertFalse(prefs.isAdBlockEnabledForHost("m.piquge.com"))
        assertFalse(prefs.isAdBlockEnabledForHost("www.piquge.com"))

        // Unrelated domain does not match
        assertTrue(prefs.isAdBlockEnabledForHost("notpiquge.com"))
    }

    @Test
    fun `toggleAdBlockForHost alternates between enabled and whitelisted`() {
        val prefs = BrowserPreferences(mockContext)
        assertTrue(prefs.isAdBlockEnabledForHost("m.piquge.com"))

        // First toggle: adds to whitelist (now disabled)
        val nowEnabled1 = prefs.toggleAdBlockForHost("m.piquge.com")
        assertFalse(nowEnabled1)
        assertFalse(prefs.isAdBlockEnabledForHost("m.piquge.com"))

        // Second toggle: removes from whitelist (now re-enabled)
        val nowEnabled2 = prefs.toggleAdBlockForHost("m.piquge.com")
        assertTrue(nowEnabled2)
        assertTrue(prefs.isAdBlockEnabledForHost("m.piquge.com"))
    }
}
