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

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import timber.log.Timber

/**
 * Core manager controlling WindowManager TYPE_APPLICATION_OVERLAY views.
 * Responsibilities:
 * 1. Orchestrates window and bubble transitions (WINDOW <-> BUBBLE <-> HIDDEN);
 * 2. Dynamically toggles FLAG_NOT_FOCUSABLE on input focus for seamless soft keyboard support;
 * 3. Maintains window coordinates, bounds, and lifecycle states.
 */
class FloatingWindowManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleContext = OverlayLifecycleContext().apply {
        onCreate()
        onStart()
        onResume()
    }

    private var windowView: FloatingWindowView? = null
    private var bubbleView: FloatingBubbleView? = null

    var currentMode: OverlayMode = OverlayMode.HIDDEN
        private set

    private val windowParams: WindowManager.LayoutParams
    private val bubbleParams: WindowManager.LayoutParams

    private var isWindowFocusable = false

    init {
        val displayMetrics = context.resources.displayMetrics
        val initialWidth = (displayMetrics.widthPixels * 0.90f).toInt().coerceAtLeast(600)
        val initialHeight = (displayMetrics.heightPixels * 0.65f).toInt().coerceAtLeast(800)
        val initialX = (displayMetrics.widthPixels - initialWidth) / 2
        val initialY = (displayMetrics.heightPixels - initialHeight) / 4

        // Window LayoutParams
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // Bubble LayoutParams
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = displayMetrics.heightPixels / 3
        }
    }

    /**
     * Shows or restores the full floating browser window.
     */
    fun showWindow(url: String? = null) {
        Timber.d("showWindow requested with url=$url, currentMode=$currentMode")

        // 1. Remove bubble if attached
        removeBubbleIfAttached()

        // 2. Prepare Window View
        if (windowView == null) {
            windowView = FloatingWindowView(
                context = context,
                windowManager = windowManager,
                layoutParams = windowParams,
                lifecycleContext = lifecycleContext
            ).apply {
                onMinimizeRequested = { minimizeToBubble() }
                onCloseRequested = { hide() }
                onInputFocusChanged = { isFocused -> setWindowFocusable(isFocused) }
                
                val defaultUrl = url ?: "https://www.google.com"
                webView.loadUrlSafely(defaultUrl)
                onDockRequested = { dockUrl, dockTitle ->
                    hide()
                    val intent = android.content.Intent(context, io.github.floatbro.ui.LauncherActivity::class.java).apply {
                        action = io.github.floatbro.ui.LauncherActivity.ACTION_DOCK_TAB
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_TAB_URL, dockUrl)
                        putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_TAB_TITLE, dockTitle)
                    }
                    context.startActivity(intent)
                }
            }
        } else if (url != null) {
            windowView?.webView?.loadUrlSafely(url)
        }

        // 3. Attach to WindowManager
        val view = windowView ?: return
        if (!view.isAttachedToWindow) {
            try {
                windowManager.addView(view, windowParams)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add FloatingWindowView to WindowManager")
            }
        }

        currentMode = OverlayMode.WINDOW
    }

    /**
     * Minimizes the floating window into an edge-docking bubble.
     */
    fun minimizeToBubble() {
        Timber.d("minimizeToBubble requested, currentMode=$currentMode")

        // 1. Remove window if attached
        removeWindowIfAttached()

        // 2. Prepare Bubble View
        if (bubbleView == null) {
            bubbleView = FloatingBubbleView(
                context = context,
                windowManager = windowManager,
                layoutParams = bubbleParams
            ).apply {
                onBubbleClicked = { showWindow() }
            }
        }

        // 3. Attach to WindowManager
        val bubble = bubbleView ?: return
        if (!bubble.isAttachedToWindow) {
            try {
                windowManager.addView(bubble, bubbleParams)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add FloatingBubbleView to WindowManager")
            }
        }

        currentMode = OverlayMode.BUBBLE
    }

    /**
     * Hides all overlay views.
     */
    fun hide() {
        Timber.d("hide overlay requested")
        removeWindowIfAttached()
        removeBubbleIfAttached()
        currentMode = OverlayMode.HIDDEN
    }

    /**
     * Dynamically switches FLAG_NOT_FOCUSABLE.
     * When focused: removes FLAG_NOT_FOCUSABLE to allow keyboard IME input.
     * When unfocused: adds FLAG_NOT_FOCUSABLE so touches pass through to underlying apps.
     */
    fun setWindowFocusable(focusable: Boolean) {
        if (isWindowFocusable == focusable) return
        isWindowFocusable = focusable

        if (focusable) {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val view = windowView
        if (view != null && view.isAttachedToWindow && currentMode == OverlayMode.WINDOW) {
            try {
                windowManager.updateViewLayout(view, windowParams)
                Timber.d("Updated WindowManager focusable=$focusable")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update layout params for focus change")
            }
        }
    }

    private fun removeWindowIfAttached() {
        val view = windowView
        if (view != null && view.isAttachedToWindow) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove FloatingWindowView")
            }
        }
    }

    private fun removeBubbleIfAttached() {
        val bubble = bubbleView
        if (bubble != null && bubble.isAttachedToWindow) {
            try {
                windowManager.removeView(bubble)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove FloatingBubbleView")
            }
        }
    }

    fun onTrimMemory(level: Int) {
        Timber.d("FloatingWindowManager onTrimMemory: level=$level")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            windowView?.webView?.clearCache(false)
        }
    }

    fun onDestroy() {
        hide()
        windowView?.webView?.destroy()
        windowView = null
        bubbleView = null
        lifecycleContext.onDestroy()
    }
}
