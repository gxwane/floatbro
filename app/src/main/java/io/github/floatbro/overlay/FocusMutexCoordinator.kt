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

import android.view.WindowManager
import timber.log.Timber

/**
 * Ensures strict mutual exclusion of keyboard focus across multiple floating windows.
 * Rules:
 * 1. At most ONE floating window can clear FLAG_NOT_FOCUSABLE at any time;
 * 2. Focusable windows MUST include FLAG_NOT_TOUCH_MODAL and FLAG_WATCH_OUTSIDE_TOUCH
 *    so underlying apps and sibling floating windows can still receive touches;
 * 3. Any outside touch or dismiss event safely restores FLAG_NOT_FOCUSABLE.
 */
class FocusMutexCoordinator(
    private val windowManager: WindowManager,
) {
    private var currentFocusedSession: FloatingSession? = null

    /**
     * Safely grants keyboard focus to the target session while defocusing any active sibling.
     */
    fun acquireFocus(targetSession: FloatingSession) {
        val isAlreadyFocusable = (targetSession.windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        if (currentFocusedSession?.id == targetSession.id && isAlreadyFocusable) return

        // 1. Defocus previously focused session if different
        currentFocusedSession?.let { prev ->
            if (prev.id != targetSession.id) {
                releaseFocus(prev)
            }
        }

        // 2. Clear FLAG_NOT_FOCUSABLE and enforce non-modal outside touch watch
        targetSession.windowParams.flags = (
            targetSession.windowParams.flags
                and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        ) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        val view = targetSession.windowView
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, targetSession.windowParams)
                currentFocusedSession = targetSession
                Timber.d("FocusMutexCoordinator: Session ${targetSession.id} acquired focus")
            } catch (e: Exception) {
                Timber.e(e, "Failed to apply focus flags for session ${targetSession.id}")
            }
        }
    }

    /**
     * Releases focus from the session, re-enabling touch pass-through.
     */
    fun releaseFocus(session: FloatingSession) {
        val wasFocusable = (session.windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        session.windowParams.flags = session.windowParams.flags or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        if (currentFocusedSession?.id == session.id) {
            currentFocusedSession = null
        }

        val view = session.windowView
        if (view.isAttachedToWindow && wasFocusable) {
            try {
                windowManager.updateViewLayout(view, session.windowParams)
                Timber.d("FocusMutexCoordinator: Session ${session.id} released focus")
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore NOT_FOCUSABLE for session ${session.id}")
            }
        }
    }

    /**
     * Defocuses whichever session currently holds focus.
     */
    fun releaseAll() {
        currentFocusedSession?.let { releaseFocus(it) }
    }
}
