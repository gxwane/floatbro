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

import android.graphics.Bitmap
import android.view.WindowManager

/**
 * State lifecycle of an active floating overlay session.
 */
enum class SessionLifecycleState {
    ATTACHED,
    DETACHING,
    DETACHED
}



/**
 * Encapsulates a distinct floating browser session.
 * Each session has an independent WebView viewport, WindowManager layout params,
 * and optional edge-docked bubble.
 */
data class FloatingSession(
    val id: String,
    var url: String,
    var title: String = "",
    var favicon: Bitmap? = null,
    val windowView: FloatingWindowView,
    val windowParams: WindowManager.LayoutParams,
    var bubbleView: FloatingBubbleView? = null,
    val bubbleParams: WindowManager.LayoutParams,
    var mode: OverlayMode = OverlayMode.WINDOW,
    var state: SessionLifecycleState = SessionLifecycleState.ATTACHED,
    var lastInteractedTimestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FloatingSession) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
