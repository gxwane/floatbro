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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.floatbro.R
import io.github.floatbro.core.webview.EBWebView

/**
 * Clean Single-Viewport Floating Window Container.
 * Features:
 * 1. Single 42dp Omni-bar (Logo, SSL lock, URL drag handle, Dock-back, Close);
 * 2. Dedicated EBWebView viewport with progress indicator;
 * 3. Bidirectional interaction: Dock back to host app tab via onDockRequested;
 * 4. Outer 16dp border dragging and invisible 36dp bottom-right corner resize;
 * 5. Edge snapping into 52dp circular floating bubble.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingWindowView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val lifecycleContext: OverlayLifecycleContext
) : FrameLayout(context) {

    val webView: EBWebView = EBWebView(context)
    private val progressBar: ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private lateinit var urlBadgeText: TextView

    var onMinimizeRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onDockRequested: ((url: String, title: String) -> Unit)? = null
    var onInputFocusChanged: ((Boolean) -> Unit)? = null
    var onBoundsChanged: ((width: Int, height: Int, x: Int, y: Int) -> Unit)? = null

    // Drag coordinates
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0

    // Resize coordinates
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var initialWindowWidth = 0
    private var initialWindowHeight = 0

    private val minWidthPx = dpToPx(280f)
    private val minHeightPx = dpToPx(340f)

    init {
        // Bind Lifecycle owners to prevent BadTokenException
        lifecycleContext.attachToView(this)

        val cornerRadius = dpToPx(16f).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = true
        elevation = dpToPx(12f).toFloat()

        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setCornerRadius(cornerRadius)
            setStroke(dpToPx(1f), Color.parseColor("#CBD5E1"))
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 1. Single 42dp Header Bar
        val headerView = createHeaderView()
        mainLayout.addView(headerView)

        // 2. 2dp Progress Bar
        progressBar.apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2f))
            max = 100
            progress = 0
            visibility = GONE
        }
        mainLayout.addView(progressBar)

        // 3. Central Web Content
        webView.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            onPageStartedListener = { url ->
                if (::urlBadgeText.isInitialized) {
                    urlBadgeText.text = url
                }
            }

            onPageFinishedListener = { url ->
                if (::urlBadgeText.isInitialized) {
                    val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: url
                    urlBadgeText.text = resolvedTitle
                }
            }

            onProgressChangedListener = { progress ->
                if (progress in 1..99) {
                    progressBar.visibility = VISIBLE
                    progressBar.progress = progress
                } else {
                    progressBar.visibility = GONE
                }
            }

            onInputFocusListener = { isFocused ->
                this@FloatingWindowView.onInputFocusChanged?.invoke(isFocused)
            }
        }
        mainLayout.addView(webView)

        addView(mainLayout)

        // 4. Invisible 36dp Bottom-Right Resize Handle
        val resizeHandle = createResizeHandle()
        addView(resizeHandle)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun createHeaderView(): View {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(42f)
            )
        }

        // App Logo / Favicon (Unfiltered dual-brand rings)
        val brandLogo = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(20f), dpToPx(20f)).apply {
                marginEnd = dpToPx(8f)
            }
            setImageResource(R.drawable.ic_bubble_logo)
        }

        // Primary Drag Omni-Capsule (SSL Lock + URL Badge)
        val dragHandleArea = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28f), 1f).apply {
                marginEnd = dpToPx(6f)
            }
            setPadding(dpToPx(8f), 0, dpToPx(8f), 0)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(14f).toFloat()
                setStroke(dpToPx(1f), Color.parseColor("#CBD5E1"))
            }

            val sslIcon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(13f), dpToPx(13f)).apply {
                    marginEnd = dpToPx(6f)
                }
                setImageResource(R.drawable.ic_shield_ssl)
            }
            addView(sslIcon)

            urlBadgeText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                text = "FloatBro 悬浮浏览"
                textSize = 11.5f
                setTextColor(Color.parseColor("#0F172A"))
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                paint.isFakeBoldText = true
            }
            addView(urlBadgeText)

            setOnTouchListener { _, event -> handleDragTouchEvent(event) }
        }

        // Action Buttons: Dock-Back to In-App Tab & Close
        val btnDock = createHeaderButton(R.drawable.ic_dock_app, "收回至应用内标签") {
            val currentUrl = webView.url ?: "about:blank"
            val currentTitle = webView.title ?: "已收回网页"
            onDockRequested?.invoke(currentUrl, currentTitle)
        }

        val btnClose = createHeaderButton(R.drawable.ic_close, "关闭浮窗") {
            onCloseRequested?.invoke()
        }

        header.addView(brandLogo)
        header.addView(dragHandleArea)
        header.addView(btnDock)
        header.addView(btnClose)

        return header
    }

    private fun createHeaderButton(drawableRes: Int, tooltip: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            val btnSize = dpToPx(28f)
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                marginStart = dpToPx(2f)
            }
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(drawableRes)
            setColorFilter(Color.parseColor("#475569"))
            contentDescription = tooltip
            setOnClickListener { onClick() }
        }
    }

    private fun createResizeHandle(): View {
        return View(context).apply {
            val handleSize = dpToPx(36f)
            layoutParams = LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END)
            setOnTouchListener { _, event -> handleResizeTouchEvent(event) }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        val edgeSize = dpToPx(16f)
        val resizeSize = dpToPx(36f)

        // Bottom-right corner reserved for diagonal resizing
        val inResizeCorner = x >= width - resizeSize && y >= height - resizeSize
        if (inResizeCorner) {
            return false
        }

        // Outer borders intercept for dragging
        val onOuterEdge = x <= edgeSize || x >= width - edgeSize || y >= height - edgeSize
        if (onOuterEdge) {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                handleDragTouchEvent(ev)
            }
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleDragTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun handleDragTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                initialWindowX = layoutParams.x
                initialWindowY = layoutParams.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartX).toInt()
                val dy = (event.rawY - dragStartY).toInt()
                layoutParams.x = initialWindowX + dx
                layoutParams.y = initialWindowY + dy
                if (isAttachedToWindow) {
                    windowManager.updateViewLayout(this, layoutParams)
                    onBoundsChanged?.invoke(layoutParams.width, layoutParams.height, layoutParams.x, layoutParams.y)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val displayMetrics = context.resources.displayMetrics
                val edgeThreshold = dpToPx(24f)
                if (layoutParams.x <= edgeThreshold ||
                    layoutParams.x + layoutParams.width >= displayMetrics.widthPixels - edgeThreshold
                ) {
                    onMinimizeRequested?.invoke()
                    return true
                }
            }
        }
        return false
    }

    private fun handleResizeTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartX = event.rawX
                resizeStartY = event.rawY
                initialWindowWidth = layoutParams.width
                initialWindowHeight = layoutParams.height
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - resizeStartX).toInt()
                val dy = (event.rawY - resizeStartY).toInt()

                val displayMetrics = context.resources.displayMetrics
                val maxAllowedWidth = displayMetrics.widthPixels
                val maxAllowedHeight = displayMetrics.heightPixels

                val (newWidth, newHeight) = WindowBoundsHelper.clampDimensions(
                    rawWidth = initialWindowWidth + dx,
                    rawHeight = initialWindowHeight + dy,
                    minWidth = minWidthPx,
                    maxWidth = maxAllowedWidth,
                    minHeight = minHeightPx,
                    maxHeight = maxAllowedHeight
                )

                layoutParams.width = newWidth
                layoutParams.height = newHeight
                if (isAttachedToWindow) {
                    windowManager.updateViewLayout(this, layoutParams)
                    onBoundsChanged?.invoke(newWidth, newHeight, layoutParams.x, layoutParams.y)
                }
                return true
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            when (BackNavigationDispatcher.resolveBackAction(webView.canGoBack())) {
                BackAction.GO_BACK_PANE1 -> webView.goBack()
                else -> onMinimizeRequested?.invoke()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

object WindowBoundsHelper {
    fun clampDimensions(
        rawWidth: Int,
        rawHeight: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
        minRatio: Float = 0.45f,
        maxRatio: Float = 2.20f
    ): Pair<Int, Int> {
        val clampedWidth = rawWidth.coerceIn(minWidth, maxWidth)
        val clampedHeight = rawHeight.coerceIn(minHeight, maxHeight)
        val currentRatio = clampedWidth.toFloat() / clampedHeight.toFloat()

        return when {
            currentRatio < minRatio -> {
                val adjustedHeight = (clampedWidth / minRatio).toInt().coerceIn(minHeight, maxHeight)
                clampedWidth to adjustedHeight
            }
            currentRatio > maxRatio -> {
                val adjustedWidth = (clampedHeight * maxRatio).toInt().coerceIn(minWidth, maxWidth)
                adjustedWidth to clampedHeight
            }
            else -> clampedWidth to clampedHeight
        }
    }
}
