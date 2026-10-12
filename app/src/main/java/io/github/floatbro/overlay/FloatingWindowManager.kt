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
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import timber.log.Timber
import java.util.UUID

/**
 * Core manager controlling multiple concurrent TYPE_APPLICATION_OVERLAY floating windows.
 * Responsibilities:
 * 1. Manages multi-session registry (LinkedHashMap<String, FloatingSession>, max 3 concurrent);
 * 2. Cascades initial window positioning and coordinates vertical edge bubble stacking;
 * 3. Enforces strict single-window keyboard focus mutex via FocusMutexCoordinator;
 * 4. Safe Z-order elevation on touch and CPU/memory throttling on inactive windows;
 * 5. Full bidirectional docking back to host app tabs.
 */
class FloatingWindowManager(private val context: Context) {

    companion object {
        const val MAX_CONCURRENT_SESSIONS = 3
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleContext = OverlayLifecycleContext().apply {
        onCreate()
        onStart()
        onResume()
    }

    private val focusMutex = FocusMutexCoordinator(windowManager)
    private val bubbleStack = BubbleStackCoordinator(context, windowManager)

    // Multi-session registry
    val sessions = LinkedHashMap<String, FloatingSession>()

    val activeSessionCount: Int
        get() = sessions.size

    val hasActiveOverlays: Boolean
        get() = sessions.isNotEmpty()

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * Shows, restores, or launches a new floating browser session.
     */
    fun showWindow(
        url: String? = null,
        initialX: Int? = null,
        initialY: Int? = null,
        windowId: String? = null
    ) {
        val targetId = windowId ?: UUID.randomUUID().toString()
        Timber.d("showWindow requested: id=$targetId, url=$url, initialX=$initialX, initialY=$initialY")

        val existingSession = sessions[targetId]
        if (existingSession != null) {
            // Restore or update existing session
            if (existingSession.mode == OverlayMode.BUBBLE) {
                restoreBubbleToWindow(existingSession)
            }
            if (url != null) {
                existingSession.windowView.webView.loadUrlSafely(url)
            }
            bringSessionToFront(existingSession)
            return
        }

        // Enforce max concurrent sessions policy
        if (sessions.size >= MAX_CONCURRENT_SESSIONS) {
            evictOrMinimizeOldestSession()
        }

        // Calculate initial cascaded window bounds
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val defaultWidth = (screenWidth * 0.88f).toInt().coerceIn(dpToPx(280f), dpToPx(380f))
        val defaultHeight = (screenHeight * 0.60f).toInt().coerceIn(dpToPx(380f), dpToPx(560f))

        val cascadeOffset = sessions.size * dpToPx(24f)
        val targetX = ((initialX ?: (screenWidth - defaultWidth) / 2) + cascadeOffset)
            .coerceIn(dpToPx(8f), (screenWidth - defaultWidth - dpToPx(8f)).coerceAtLeast(0))
        val targetY = ((initialY ?: (screenHeight - defaultHeight) / 4) + cascadeOffset)
            .coerceIn(dpToPx(36f), (screenHeight - defaultHeight - dpToPx(48f)).coerceAtLeast(0))

        val windowParams = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(12f)
            y = dpToPx(120f) + (sessions.size * dpToPx(62f))
        }

        val windowView = FloatingWindowView(
            context = context,
            sessionId = targetId,
            windowManager = windowManager,
            layoutParams = windowParams,
            lifecycleContext = lifecycleContext
        )

        val session = FloatingSession(
            id = targetId,
            url = url ?: "https://cn.bing.com",
            windowView = windowView,
            windowParams = windowParams,
            bubbleParams = bubbleParams,
            mode = OverlayMode.WINDOW
        )

        // Setup session callbacks
        windowView.apply {
            onMinimizeRequested = { minimizeToBubble(session) }
            onCloseRequested = { closeSession(session) }
            onBringToFrontRequested = { bringSessionToFront(session) }
            onInputFocusRequested = { isFocused ->
                if (isFocused) {
                    focusMutex.acquireFocus(session)
                } else {
                    focusMutex.releaseFocus(session)
                }
            }
            onFaviconReceived = { icon ->
                session.favicon = icon
                session.bubbleView?.updateFavicon(icon)
            }
            onDockRequested = { dockUrl, dockTitle ->
                dockSessionToApp(session, dockUrl, dockTitle)
            }
        }

        // Attach window
        try {
            windowManager.addView(windowView, windowParams)
            sessions[targetId] = session
            val loadTarget = url ?: "https://cn.bing.com"
            windowView.webView.loadUrlSafely(loadTarget)
            Timber.d("Successfully launched floating session $targetId with url $loadTarget")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add FloatingWindowView to WindowManager for session $targetId")
        }
    }

    /**
     * Brings the session window to the top of WindowManager via safe view re-insertion.
     */
    fun bringSessionToFront(session: FloatingSession) {
        session.lastInteractedTimestamp = System.currentTimeMillis()
        if (sessions.values.lastOrNull()?.id == session.id) {
            // Already top-most window; do not re-insert to preserve active touch stream
            return
        }
        val view = session.windowView
        if (view.isAttachedToWindow && session.mode == OverlayMode.WINDOW) {
            try {
                windowManager.removeViewImmediate(view)
                windowManager.addView(view, session.windowParams)
                sessions.remove(session.id)
                sessions[session.id] = session
                Timber.d("Elevated session ${session.id} to front")
            } catch (e: Exception) {
                Timber.w(e, "Safe Z-order re-insertion failed for session ${session.id}")
            }
        }
    }

    /**
     * Collapses an active window into an edge-docked bubble.
     */
    fun minimizeToBubble(session: FloatingSession) {
        if (session.mode == OverlayMode.BUBBLE) return
        focusMutex.releaseFocus(session)

        // 1. Throttling WebView background execution
        session.windowView.webView.onPause()
        session.windowView.webView.pauseTimers()

        // 2. Remove window view
        if (session.windowView.isAttachedToWindow) {
            try {
                windowManager.removeView(session.windowView)
            } catch (e: Exception) {
                Timber.w(e, "Failed to remove window view during minimize for session ${session.id}")
            }
        }

        // 3. Natural Spatial Continuity: dock bubble to nearest edge at window's current height
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleSize = dpToPx(52f)
        val edgeMargin = dpToPx(12f)

        val winX = session.windowView.layoutParams.x
        val winWidth = session.windowView.layoutParams.width.takeIf { it > 0 } ?: dpToPx(280f)
        val winCenterX = winX + winWidth / 2
        val isRightSide = winCenterX >= screenWidth / 2 || winX > (screenWidth * 0.4f)

        val targetX = if (isRightSide) {
            screenWidth - bubbleSize - edgeMargin
        } else {
            edgeMargin
        }

        val winY = session.windowView.layoutParams.y
        val minY = dpToPx(48f)
        val maxY = screenHeight - dpToPx(96f) - bubbleSize
        val targetY = (winY + dpToPx(10f)).coerceIn(minY, maxY)

        session.bubbleParams.x = targetX
        session.bubbleParams.y = targetY

        // 4. Create or attach bubble
        if (session.bubbleView == null) {
            session.bubbleView = FloatingBubbleView(
                context = context,
                windowManager = windowManager,
                layoutParams = session.bubbleParams
            ).apply {
                updateFavicon(session.favicon)
                onBubbleClicked = { restoreBubbleToWindow(session) }
                onPositionChanged = { newX, newY ->
                    session.bubbleParams.x = newX
                    session.bubbleParams.y = newY
                }
            }
        }

        val bubble = session.bubbleView!!
        if (!bubble.isAttachedToWindow) {
            try {
                bubbleStack.attachBubble(session)
                windowManager.addView(bubble, session.bubbleParams)
                session.mode = OverlayMode.BUBBLE
                Timber.d("Minimized session ${session.id} to edge bubble at ($targetX, ${session.bubbleParams.y})")
            } catch (e: Exception) {
                Timber.e(e, "Failed to add bubble view for session ${session.id}")
            }
        }
    }

    /**
     * Restores a bubble back into its full floating browser window.
     */
    fun restoreBubbleToWindow(session: FloatingSession) {
        if (session.mode == OverlayMode.WINDOW) return

        // 1. Remove bubble
        session.bubbleView?.let { bubble ->
            if (bubble.isAttachedToWindow) {
                try {
                    windowManager.removeView(bubble)
                    bubbleStack.detachBubble(session)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to remove bubble during restore for session ${session.id}")
                }
            }
        }

        // 2. Resume WebView
        session.windowView.webView.onResume()
        session.windowView.webView.resumeTimers()

        // 3. Inherit bubble position so window expands naturally from where bubble was
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val winWidth = session.windowParams.width.takeIf { it > 0 } ?: dpToPx(280f)
        val winHeight = session.windowParams.height.takeIf { it > 0 } ?: dpToPx(340f)

        val bubbleX = session.bubbleParams.x
        val bubbleY = session.bubbleParams.y
        val isBubbleOnRight = bubbleX > screenWidth / 2

        val targetWinX = if (isBubbleOnRight) {
            (screenWidth - winWidth - dpToPx(12f)).coerceAtLeast(dpToPx(8f))
        } else {
            dpToPx(12f)
        }
        val targetWinY = (bubbleY - dpToPx(16f)).coerceIn(
            dpToPx(36f),
            (screenHeight - winHeight - dpToPx(48f)).coerceAtLeast(dpToPx(36f))
        )

        session.windowParams.x = targetWinX
        session.windowParams.y = targetWinY

        // 4. Attach window
        val window = session.windowView
        if (!window.isAttachedToWindow) {
            try {
                windowManager.addView(window, session.windowParams)
                session.mode = OverlayMode.WINDOW
                bringSessionToFront(session)
                Timber.d("Restored session ${session.id} to window at ($targetWinX, $targetWinY)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to re-add window view during restore for session ${session.id}")
            }
        }
    }

    /**
     * Completely destroys a floating session and its views.
     */
    fun closeSession(session: FloatingSession) {
        focusMutex.releaseFocus(session)

        // 1. Remove bubble
        session.bubbleView?.let { bubble ->
            if (bubble.isAttachedToWindow) {
                try {
                    windowManager.removeView(bubble)
                    bubbleStack.detachBubble(session)
                } catch (e: Exception) {
                    Timber.w(e, "Error removing bubble on close for session ${session.id}")
                }
            }
        }

        // 2. Remove window
        val window = session.windowView
        if (window.isAttachedToWindow) {
            try {
                windowManager.removeView(window)
            } catch (e: Exception) {
                Timber.w(e, "Error removing window on close for session ${session.id}")
            }
        }

        // 3. Destroy WebView clean sequence
        try {
            window.webView.stopLoading()
            window.webView.loadUrl("about:blank")
            window.webView.clearHistory()
            window.webView.removeAllViews()
            window.webView.destroy()
        } catch (e: Exception) {
            Timber.w(e, "Error destroying WebView for session ${session.id}")
        }

        session.state = SessionLifecycleState.DETACHED
        sessions.remove(session.id)
        Timber.d("Cleanly closed and destroyed session ${session.id}. Remaining active: ${sessions.size}")
    }

    /**
     * Closes the floating session and sends its URL back to the host launcher activity.
     */
    fun dockSessionToApp(session: FloatingSession, dockUrl: String, dockTitle: String) {
        closeSession(session)
        val intent = Intent(context, io.github.floatbro.ui.LauncherActivity::class.java).apply {
            action = io.github.floatbro.ui.LauncherActivity.ACTION_DOCK_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_TAB_URL, dockUrl)
            putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_TAB_TITLE, dockTitle)
        }
        context.startActivity(intent)
    }

    /**
     * Minimizes all active windows to edge bubbles.
     */
    fun minimizeAllToBubbles() {
        sessions.values.toList().forEach { session ->
            if (session.mode == OverlayMode.WINDOW) {
                minimizeToBubble(session)
            }
        }
    }

    /**
     * Evicts or minimizes the least-recently-interacted session to maintain max limit.
     */
    private fun evictOrMinimizeOldestSession() {
        val oldest = sessions.values.minByOrNull { it.lastInteractedTimestamp } ?: return
        if (oldest.mode == OverlayMode.WINDOW) {
            minimizeToBubble(oldest)
        } else {
            // Already a bubble, dock back to app
            dockSessionToApp(oldest, oldest.url, oldest.title.takeIf { it.isNotBlank() } ?: "已收回网页")
        }
    }

    fun onTrimMemory(level: Int) {
        Timber.d("FloatingWindowManager onTrimMemory: level=$level")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            sessions.values.forEach { session ->
                if (session.mode == OverlayMode.BUBBLE) {
                    session.windowView.webView.clearCache(false)
                }
            }
        }
    }

    fun onDestroy() {
        focusMutex.releaseAll()
        sessions.values.toList().forEach { session ->
            closeSession(session)
        }
        sessions.clear()
        bubbleStack.clear()
        lifecycleContext.onDestroy()
    }
}
