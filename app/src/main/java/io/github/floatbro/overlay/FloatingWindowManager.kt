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
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.floatbro.R
import io.github.floatbro.core.webview.TabOverlayStateBridge
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
class FloatingWindowManager(
    private val context: Context,
) {
    companion object {
        const val MAX_CONCURRENT_SESSIONS = 3
    }

    private val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_FloatBro)

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleContext =
        OverlayLifecycleContext().apply {
            onCreate()
            onStart()
            onResume()
        }

    private val focusMutex = FocusMutexCoordinator(windowManager)
    private val bubbleStack = BubbleStackCoordinator(themedContext, windowManager)

    // Multi-session registry
    val sessions = LinkedHashMap<String, FloatingSession>()

    var activeSessionId: String? = null
        private set

    fun updateActiveWindowStates() {
        val windowSessions = sessions.values.filter { it.mode == OverlayMode.WINDOW }
        val isSingleWindow = windowSessions.size <= 1
        for (session in windowSessions) {
            val isActive = isSingleWindow || (session.id == activeSessionId)
            session.windowView.setActiveWindowState(isActive)
        }
    }

    private var dismissTargetView: BubbleDismissTargetView? = null

    val activeSessionCount: Int
        get() = sessions.size

    val hasActiveOverlays: Boolean
        get() = sessions.isNotEmpty()

    var onAllSessionsClosed: (() -> Unit)? = null

    /**
     * Restores or brings the latest interacted session to front.
     */
    fun restoreOrBringToFrontLatest() {
        val targetSession = sessions.values.maxByOrNull { it.lastInteractedTimestamp } ?: return
        if (targetSession.mode == OverlayMode.BUBBLE) {
            restoreBubbleToWindow(targetSession)
        } else {
            bringSessionToFront(targetSession)
        }
    }

    private fun ensureDismissTargetAttached(): BubbleDismissTargetView {
        val target =
            dismissTargetView ?: BubbleDismissTargetView(context, windowManager).also {
                dismissTargetView = it
            }
        if (!target.isAttachedToWindow) {
            try {
                windowManager.addView(target, target.layoutParams)
            } catch (e: Exception) {
                Timber.w(e, "Failed to attach BubbleDismissTargetView to WindowManager")
            }
        }
        return target
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics,
            ).toInt()

    private fun getOverlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /**
     * Shows, restores, or launches a new floating browser session.
     */
    fun showWindow(
        url: String? = null,
        initialX: Int? = null,
        initialY: Int? = null,
        windowId: String? = null,
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

        val smallestWidthDp = minOf(screenWidth, screenHeight) / displayMetrics.density
        val isTablet = smallestWidthDp >= 600f
        val isLandscape = screenWidth > screenHeight

        val minWidthPx = dpToPx(WindowBoundsHelper.MIN_WIDTH_DP)
        val minHeightPx =
            if (isLandscape &&
                !isTablet
            ) {
                dpToPx(WindowBoundsHelper.MIN_HEIGHT_LANDSCAPE_DP)
            } else {
                dpToPx(WindowBoundsHelper.MIN_HEIGHT_PORTRAIT_DP)
            }

        val widthRatio =
            when {
                isTablet && isLandscape -> 0.52f
                isTablet && !isLandscape -> 0.65f
                !isTablet && isLandscape -> 0.45f
                else -> 0.88f
            }
        val heightRatio =
            when {
                isTablet && isLandscape -> 0.65f
                isTablet && !isLandscape -> 0.60f
                !isTablet && isLandscape -> 0.72f
                else -> 0.60f
            }

        val maxInitialWidthDp =
            if (isTablet) {
                580f
            } else if (isLandscape) {
                480f
            } else {
                380f
            }
        val maxInitialHeightDp =
            if (isTablet) {
                720f
            } else if (isLandscape) {
                320f
            } else {
                560f
            }

        val defaultWidth = (screenWidth * widthRatio).toInt().coerceIn(minWidthPx, dpToPx(maxInitialWidthDp))
        val defaultHeight = (screenHeight * heightRatio).toInt().coerceIn(minHeightPx, dpToPx(maxInitialHeightDp))

        val cascadeOffset = sessions.size * dpToPx(24f)
        val minX = dpToPx(8f)
        val maxX = (screenWidth - defaultWidth - minX).coerceAtLeast(minX)
        val targetX = ((initialX ?: (screenWidth - defaultWidth) / 2) + cascadeOffset).coerceIn(minX, maxX)

        val minY = dpToPx(36f)
        val maxY = (screenHeight - defaultHeight - dpToPx(48f)).coerceAtLeast(minY)
        val targetY = ((initialY ?: (screenHeight - defaultHeight) / 4) + cascadeOffset).coerceIn(minY, maxY)

        val windowParams =
            WindowManager
                .LayoutParams(
                    defaultWidth,
                    defaultHeight,
                    getOverlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = targetX
                    y = targetY
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }

        val bubbleSize = dpToPx(52f)
        val bubbleParams =
            WindowManager
                .LayoutParams(
                    bubbleSize,
                    bubbleSize,
                    getOverlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dpToPx(12f)
                    y = dpToPx(120f) + (sessions.size * dpToPx(62f))
                }

        val existingWv =
            TabOverlayStateBridge.takeWebView(targetId)
                ?: windowId?.let { TabOverlayStateBridge.takeWebView(it) }

        if (existingWv != null) {
            existingWv.updateBaseContext(themedContext)
        }

        val windowView =
            FloatingWindowView(
                context = themedContext,
                sessionId = targetId,
                windowManager = windowManager,
                layoutParams = windowParams,
                lifecycleContext = lifecycleContext,
                existingWebView = existingWv,
            )

        val session =
            FloatingSession(
                id = targetId,
                url = url ?: "https://cn.bing.com",
                windowView = windowView,
                windowParams = windowParams,
                bubbleParams = bubbleParams,
                mode = OverlayMode.WINDOW,
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
            val hitRadiusPx = dpToPx(110f)
            onDragStarted = {
                ensureDismissTargetAttached().showTarget()
            }
            onDragMoved = { rawX, rawY ->
                val target = ensureDismissTargetAttached()
                val (targetCenterX, targetCenterY) = target.getTargetCenter()
                val isHit =
                    OverlayPhysicsHelper.isDropTargetHit(
                        bubbleCenterX = rawX,
                        bubbleCenterY = rawY,
                        targetCenterX = targetCenterX,
                        targetCenterY = targetCenterY,
                        hitRadiusPx = hitRadiusPx.toFloat(),
                    )
                target.setHovered(isHit)
            }
            onDragEnded = { rawX, rawY ->
                val target = ensureDismissTargetAttached()
                val (targetCenterX, targetCenterY) = target.getTargetCenter()
                val isHit =
                    OverlayPhysicsHelper.isDropTargetHit(
                        bubbleCenterX = rawX,
                        bubbleCenterY = rawY,
                        targetCenterX = targetCenterX,
                        targetCenterY = targetCenterY,
                        hitRadiusPx = hitRadiusPx.toFloat(),
                    )
                target.hideTarget()
                if (isHit) {
                    Timber.d("FloatingWindow dropped onto dismiss target. Cleanly closing session ${session.id}")
                    closeSession(session)
                    true
                } else {
                    false
                }
            }
        }

        // Attach window
        try {
            windowManager.addView(windowView, windowParams)
            sessions[targetId] = session
            activeSessionId = targetId
            updateActiveWindowStates()
            val loadTarget = url ?: "https://cn.bing.com"

            windowView.webView.onPageCommitVisibleListener = {
                TabOverlayStateBridge.notifyCommit(targetId)
                if (windowId != null) {
                    TabOverlayStateBridge.notifyCommit(windowId)
                }
            }

            if (existingWv != null) {
                Timber.d("Reusing existing live EBWebView for session $targetId, skipping network load")
                existingWv.onResume()
                existingWv.resumeTimers()
                // True hardware frame commit and visual cover fadeout is coordinated by FloatingWindowView
            } else {
                val savedState =
                    TabOverlayStateBridge.takeState(targetId)
                        ?: windowId?.let { TabOverlayStateBridge.takeState(it) }

                if (savedState != null) {
                    Timber.d("Restoring state from TabOverlayStateBridge for session $targetId")
                    val restored = windowView.webView.restoreState(savedState)
                    if (restored == null || restored.size == 0) {
                        Timber.w("restoreState returned empty, fallback to loadUrlSafely")
                        windowView.webView.loadUrlSafely(loadTarget)
                    }
                } else {
                    windowView.webView.loadUrlSafely(loadTarget)
                }
            }
            Timber.d("Successfully launched floating session $targetId with url $loadTarget")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add FloatingWindowView to WindowManager for session $targetId")
        }
    }

    /**
     * Brings the session window to the top of WindowManager with spatial overlap bypass and safe deferred elevation.
     */
    fun bringSessionToFront(session: FloatingSession) {
        session.lastInteractedTimestamp = System.currentTimeMillis()
        activeSessionId = session.id
        updateActiveWindowStates()

        val windowSessions = sessions.values.filter { it.mode == OverlayMode.WINDOW }
        if (windowSessions.size <= 1 || windowSessions.lastOrNull()?.id == session.id) {
            // Already top-most window or single window; do not re-insert to preserve active touch stream
            return
        }

        if (session.windowView.isFullscreenVideoActive) {
            // Window is in fullscreen video mode; do not re-add to WindowManager to preserve player surface
            return
        }

        // Spatial Overlap Check:
        // If this window does NOT geometrically overlap with any window above it, DO NOT touch WindowManager
        // and DO NOT reorder sessions! Physical Z-order in WindowManager hasn't changed.
        if (!isSessionOverlappingHigherWindows(session)) {
            Timber.d("Activated session ${session.id} (no spatial overlap with higher windows, zero flicker)")
            return
        }

        // For overlapping windows: defer elevation via post to never abort active InputChannel on ACTION_DOWN!
        val view = session.windowView
        if (view.isAttachedToWindow && session.mode == OverlayMode.WINDOW) {
            view.post {
                if (view.isAttachedToWindow && session.mode == OverlayMode.WINDOW && sessions.values.lastOrNull()?.id != session.id) {
                    view.isReattachingForZOrder = true
                    try {
                        windowManager.removeViewImmediate(view)
                        windowManager.addView(view, session.windowParams)
                        sessions.remove(session.id)
                        sessions[session.id] = session
                        Timber.d("Elevated overlapping session ${session.id} to front safely")
                    } catch (e: Exception) {
                        Timber.w(e, "Safe Z-order re-insertion failed for session ${session.id}")
                        if (!view.isAttachedToWindow) {
                            view.exitFullscreenVideo(notifyCallback = true)
                        }
                    } finally {
                        view.isReattachingForZOrder = false
                    }
                }
            }
        }
    }

    /**
     * Checks if this session intersects with any window currently placed higher in Z-order.
     */
    fun isSessionOverlappingHigherWindows(session: FloatingSession): Boolean {
        val windowSessions = sessions.values.filter { it.mode == OverlayMode.WINDOW }
        val sessionIndex = windowSessions.indexOfFirst { it.id == session.id }
        if (sessionIndex == -1 || sessionIndex == windowSessions.size - 1) return false

        val curX = session.windowParams.x
        val curY = session.windowParams.y
        val curW = session.windowParams.width
        val curH = session.windowParams.height

        val higherSessions = windowSessions.drop(sessionIndex + 1)
        return higherSessions.any { higher ->
            WindowBoundsHelper.isOverlapping(
                x1 = curX,
                y1 = curY,
                w1 = curW,
                h1 = curH,
                x2 = higher.windowParams.x,
                y2 = higher.windowParams.y,
                w2 = higher.windowParams.width,
                h2 = higher.windowParams.height,
            )
        }
    }

    private fun setupBubbleCallbacks(
        bubble: FloatingBubbleView,
        session: FloatingSession,
    ) {
        val hitRadiusPx = dpToPx(72f).toFloat()

        bubble.onBubbleClicked = { restoreBubbleToWindow(session) }
        bubble.onPositionChanged = { newX, newY ->
            session.bubbleParams.x = newX
            session.bubbleParams.y = newY
        }
        bubble.onDismissRequested = { closeSession(session) }

        bubble.onDragStarted = {
            ensureDismissTargetAttached().showTarget()
        }

        bubble.onDragMoved = { bubbleCenterX, bubbleCenterY ->
            val target = ensureDismissTargetAttached()
            val (targetCenterX, targetCenterY) = target.getTargetCenter()
            val isHit =
                OverlayPhysicsHelper.isDropTargetHit(
                    bubbleCenterX = bubbleCenterX,
                    bubbleCenterY = bubbleCenterY,
                    targetCenterX = targetCenterX,
                    targetCenterY = targetCenterY,
                    hitRadiusPx = hitRadiusPx,
                )
            target.setHovered(isHit)
            bubble.setHoveredOnDismissTarget(isHit)
        }

        bubble.onDragEnded = { bubbleCenterX, bubbleCenterY ->
            val target = ensureDismissTargetAttached()
            val (targetCenterX, targetCenterY) = target.getTargetCenter()
            val isHit =
                OverlayPhysicsHelper.isDropTargetHit(
                    bubbleCenterX = bubbleCenterX,
                    bubbleCenterY = bubbleCenterY,
                    targetCenterX = targetCenterX,
                    targetCenterY = targetCenterY,
                    hitRadiusPx = hitRadiusPx,
                )
            target.hideTarget()
            bubble.setHoveredOnDismissTarget(false)
            if (isHit) {
                Timber.d("Bubble dropped onto dismiss target. Cleanly closing session ${session.id}")
                closeSession(session)
                true
            } else {
                false
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
        val tuckDepth = dpToPx(18f)

        val winX = session.windowView.layoutParams.x
        val winWidth =
            session.windowView.layoutParams.width
                .takeIf { it > 0 } ?: dpToPx(280f)
        val winCenterX = winX + winWidth / 2
        val isRightSide = winCenterX >= screenWidth / 2 || winX > (screenWidth * 0.4f)

        val targetX =
            if (isRightSide) {
                screenWidth - bubbleSize + tuckDepth
            } else {
                -tuckDepth
            }

        val winY = session.windowView.layoutParams.y
        val minY = dpToPx(48f)
        val maxY = screenHeight - dpToPx(96f) - bubbleSize
        val targetY = (winY + dpToPx(10f)).coerceIn(minY, maxY)

        session.bubbleParams.x = targetX
        session.bubbleParams.y = targetY

        // 4. Create or attach bubble
        if (session.bubbleView == null) {
            session.bubbleView =
                FloatingBubbleView(
                    context = context,
                    windowManager = windowManager,
                    layoutParams = session.bubbleParams,
                ).apply {
                    updateFavicon(session.favicon)
                    setupBubbleCallbacks(this, session)
                }
        } else {
            setupBubbleCallbacks(session.bubbleView!!, session)
        }

        val bubble = session.bubbleView!!
        if (!bubble.isAttachedToWindow) {
            try {
                bubbleStack.attachBubble(session)
                windowManager.addView(bubble, session.bubbleParams)
                session.mode = OverlayMode.BUBBLE
                if (activeSessionId == session.id) {
                    activeSessionId = sessions.values.lastOrNull { it.mode == OverlayMode.WINDOW }?.id
                }
                updateActiveWindowStates()
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

        val targetWinX =
            if (isBubbleOnRight) {
                (screenWidth - winWidth).coerceAtLeast(0)
            } else {
                0
            }
        val targetWinY =
            (bubbleY - dpToPx(16f)).coerceIn(
                dpToPx(36f),
                (screenHeight - winHeight - dpToPx(48f)).coerceAtLeast(dpToPx(36f)),
            )

        session.windowParams.x = targetWinX
        session.windowParams.y = targetWinY

        // 4. Attach window
        val window = session.windowView
        if (!window.isAttachedToWindow) {
            try {
                windowManager.addView(window, session.windowParams)
                session.mode = OverlayMode.WINDOW
                sessions.remove(session.id)
                sessions[session.id] = session
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
    fun closeSession(
        session: FloatingSession,
        preserveWebView: Boolean = false,
    ) {
        // Immediate zero-latency visual hide to eliminate perceived lag
        session.windowView.visibility = View.GONE
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

        // 3. Record final history before destroy
        val finalUrl = session.windowView.webView.url ?: session.url
        val finalTitle = session.windowView.webView.title ?: ""
        if (finalUrl.isNotBlank() && finalUrl != "about:blank") {
            io.github.floatbro.history.BrowsingHistoryManager
                .getInstance(context)
                .recordVisit(finalUrl, finalTitle)
        }

        // 4. Destroy WebView clean sequence (only if not preserved for docking)
        if (!preserveWebView) {
            val wv = window.webView
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.removeAllViews()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        wv.destroy()
                    } catch (e: Exception) {
                        Timber.w(e, "Error destroying WebView asynchronously for session ${session.id}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error cleaning up WebView on close for session ${session.id}")
            }
        }

        session.state = SessionLifecycleState.DETACHED
        val wasRemoved = sessions.remove(session.id) != null
        if (activeSessionId == session.id) {
            activeSessionId = sessions.values.lastOrNull { it.mode == OverlayMode.WINDOW }?.id
        }
        updateActiveWindowStates()
        if (wasRemoved && sessions.isEmpty()) {
            dismissTargetView?.hideTarget()
            onAllSessionsClosed?.invoke()
        }
        Timber.d("Cleanly closed session ${session.id} (preserveWebView=$preserveWebView). Remaining active: ${sessions.size}")
    }

    /**
     * Closes the floating session and sends its URL back to the host launcher activity.
     */
    fun dockSessionToApp(
        session: FloatingSession,
        dockUrl: String,
        dockTitle: String,
    ) {
        val windowView = session.windowView
        val wv = windowView.webView

        (wv.parent as? ViewGroup)?.removeView(wv)
        TabOverlayStateBridge.putWebView(session.id, wv)

        val stateBundle = android.os.Bundle()
        try {
            wv.saveState(stateBundle)
            TabOverlayStateBridge.putState(session.id, stateBundle, dockUrl, dockTitle)
        } catch (e: Exception) {
            Timber.w(e, "Failed to saveState before dockSessionToApp")
        }

        closeSession(session, preserveWebView = true)

        val intent =
            Intent(context, io.github.floatbro.ui.LauncherActivity::class.java).apply {
                action = io.github.floatbro.ui.LauncherActivity.ACTION_DOCK_TAB
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_TAB_URL, dockUrl)
                putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_TAB_TITLE, dockTitle)
                putExtra(io.github.floatbro.ui.LauncherActivity.EXTRA_SESSION_ID, session.id)
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
        onAllSessionsClosed = null
        focusMutex.releaseAll()
        sessions.values.toList().forEach { session ->
            closeSession(session)
        }
        sessions.clear()
        bubbleStack.clear()
        dismissTargetView?.let { target ->
            if (target.isAttachedToWindow) {
                try {
                    windowManager.removeView(target)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to remove dismissTargetView on destroy")
                }
            }
            dismissTargetView = null
        }
        lifecycleContext.onDestroy()
    }

    fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val smallestWidthDp = minOf(screenW, screenH) / displayMetrics.density
        val isTablet = smallestWidthDp >= 600f
        val isLandscape = screenW > screenH

        val minW = dpToPx(WindowBoundsHelper.MIN_WIDTH_DP)
        val minH =
            if (isLandscape &&
                !isTablet
            ) {
                dpToPx(WindowBoundsHelper.MIN_HEIGHT_LANDSCAPE_DP)
            } else {
                dpToPx(WindowBoundsHelper.MIN_HEIGHT_PORTRAIT_DP)
            }

        sessions.values.forEach { session ->
            val wp = session.windowParams
            val maxAllowedW = (screenW - dpToPx(16f)).coerceAtLeast(minW)
            val maxAllowedH = (screenH - dpToPx(36f) - dpToPx(16f)).coerceAtLeast(minH)
            wp.width = wp.width.coerceIn(minW, maxAllowedW)
            wp.height = wp.height.coerceIn(minH, maxAllowedH)

            val minX = 0
            val maxX = (screenW - wp.width).coerceAtLeast(minX)
            wp.x = wp.x.coerceIn(minX, maxX)

            val minY = dpToPx(36f)
            val maxY = (screenH - wp.height - dpToPx(16f)).coerceAtLeast(minY)
            wp.y = wp.y.coerceIn(minY, maxY)

            if (session.mode == OverlayMode.WINDOW && session.windowView.isAttachedToWindow) {
                try {
                    windowManager.updateViewLayout(session.windowView, wp)
                } catch (e: Exception) {
                    Timber.w(e, "Error updating window layout on config changed")
                }
            } else if (session.mode == OverlayMode.BUBBLE && session.bubbleView?.isAttachedToWindow == true) {
                val bp = session.bubbleParams
                val bubbleSize = dpToPx(52f)
                val tuckDepth = dpToPx(18f)
                bp.x = if (bp.x < screenW / 2) -tuckDepth else screenW - bubbleSize + tuckDepth
                bp.y = bp.y.coerceIn(dpToPx(36f), (screenH - dpToPx(96f) - bubbleSize).coerceAtLeast(dpToPx(36f)))
                try {
                    windowManager.updateViewLayout(session.bubbleView, bp)
                } catch (e: Exception) {
                    Timber.w(e, "Error updating bubble layout on config changed")
                }
            }
        }
    }
}
