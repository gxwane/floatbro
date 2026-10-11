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

package io.github.floatbro.overlay

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Pure JVM Unit Tests for OverlayLifecycleContext.
 * Verifies lifecycle state progression, saved state registry availability,
 * and view tree binding readiness.
 */
class OverlayLifecycleContextTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `scenario - lifecycle progresses through create start and resume states`() {
        // GIVEN: Fresh context
        val context = OverlayLifecycleContext()
        assertEquals(Lifecycle.State.INITIALIZED, context.lifecycle.currentState)

        // WHEN: onCreate
        context.onCreate()
        assertEquals(Lifecycle.State.CREATED, context.lifecycle.currentState)
        assertNotNull(context.savedStateRegistry)

        // WHEN: onStart
        context.onStart()
        assertEquals(Lifecycle.State.STARTED, context.lifecycle.currentState)

        // WHEN: onResume
        context.onResume()
        assertEquals(Lifecycle.State.RESUMED, context.lifecycle.currentState)

        // WHEN: onPause and onStop
        context.onPause()
        assertEquals(Lifecycle.State.STARTED, context.lifecycle.currentState)
        context.onStop()
        assertEquals(Lifecycle.State.CREATED, context.lifecycle.currentState)

        // WHEN: onDestroy
        context.onDestroy()
        assertEquals(Lifecycle.State.DESTROYED, context.lifecycle.currentState)
    }

    @Test
    fun `scenario - viewModelStore is accessible and initialized`() {
        val context = OverlayLifecycleContext()
        assertNotNull(context.viewModelStore)
    }
}
