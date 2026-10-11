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
import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import io.github.floatbro.R
import io.github.floatbro.core.webview.twopane.TwoPaneLayout

/**
 * Main floating window container.
 * Features:
 * 1. Top drag handle & window control buttons (Split, Rotate, Swap, Menu, Minimize, Close);
 * 2. Embedded TwoPaneLayout with symmetric dual-pane viewports;
 * 3. Bottom-right ResizeHandle supporting smooth gesture scaling;
 * 4. Embedded ComposeView overlay host eliminating BadTokenException.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingWindowView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val lifecycleContext: OverlayLifecycleContext
) : FrameLayout(context) {

    val twoPaneLayout: TwoPaneLayout = TwoPaneLayout(context)
    private val composeOverlayView = ComposeView(context)
    private val isMenuOpenState = mutableStateOf(false)
    private lateinit var urlBadgeText: TextView

    var onMinimizeRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onInputFocusChanged: ((Boolean) -> Unit)? = null
    var onBoundsChanged: ((width: Int, height: Int, x: Int, y: Int) -> Unit)? = null

    // Drag move coordinates
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
        // Bind Lifecycle, ViewModelStore, and SavedStateRegistry owners to the top-level root view
        lifecycleContext.attachToView(this)

        // Set rounded corners and elevation
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
            setStroke(dpToPx(1f), Color.parseColor("#CCCCCC"))
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 1. Top Window Control Header
        val headerView = createHeaderView()
        mainLayout.addView(headerView)

        // 2. Central TwoPaneLayout
        twoPaneLayout.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            // Suppress pane1 internal toolbar to avoid double-toolbar stacking (Gate 3 Architecture Rule)
            pane1.isToolbarVisible = false

            pane1.webView.onPageFinishedListener = { url ->
                if (::urlBadgeText.isInitialized) {
                    urlBadgeText.text = url
                }
            }
            pane1.webView.onPageStartedListener = { url ->
                if (::urlBadgeText.isInitialized) {
                    urlBadgeText.text = url
                }
            }

            pane2.onClosePaneRequested = {
                isSecondPaneVisible = false
                pane1.isToolbarVisible = false
            }

            onInputFocusChanged = { isFocused ->
                this@FloatingWindowView.onInputFocusChanged?.invoke(isFocused)
            }
        }
        mainLayout.addView(twoPaneLayout)

        addView(mainLayout)

        // 3. Bottom-Right Resize Handle
        val resizeHandle = createResizeHandle()
        addView(resizeHandle)

        // 4. Compose In-Window Overlay Host (for crash-free menus/dialogs)
        setupComposeOverlay()
        addView(composeOverlayView)
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
                dpToPx(44f)
            )
        }

        // App Logo / Favicon (Dual-color brand identity, zero monochrome tint)
        val brandLogo = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(20f), dpToPx(20f)).apply {
                marginEnd = dpToPx(8f)
            }
            setImageResource(R.drawable.ic_bubble_logo)
        }

        // Primary Drag Omni-Capsule (SSL Lock + URL Badge matching prototype .float-url-badge)
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

        // Action Buttons: Split, Menu, Close (Zero desktop minimize button!)
        val btnSplit = createHeaderButton(R.drawable.ic_split_pane, "切换分屏") {
            val nextVisible = !twoPaneLayout.isSecondPaneVisible
            twoPaneLayout.isSecondPaneVisible = nextVisible
            twoPaneLayout.pane1.isToolbarVisible = nextVisible
            twoPaneLayout.pane2.isToolbarVisible = nextVisible
        }

        val btnMenu = createHeaderButton(R.drawable.ic_menu, "菜单") {
            isMenuOpenState.value = true
        }

        val btnClose = createHeaderButton(R.drawable.ic_close, "关闭浮窗") {
            onCloseRequested?.invoke()
        }

        header.addView(brandLogo)
        header.addView(dragHandleArea)
        header.addView(btnSplit)
        header.addView(btnMenu)
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
            // Invisible touch zone for common sense diagonal resize
            setOnTouchListener { _, event -> handleResizeTouchEvent(event) }
        }
    }

    private fun setupComposeOverlay() {
        composeOverlayView.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            lifecycleContext.attachToView(this)
            setContent {
                InWindowOverlayMenu(
                    isOpen = isMenuOpenState.value,
                    onClose = { isMenuOpenState.value = false },
                    onResetSplit = { twoPaneLayout.resetSplitRatio() },
                    onToggleSplit = {
                        twoPaneLayout.isSecondPaneVisible = !twoPaneLayout.isSecondPaneVisible
                    },
                    onClearCache = {
                        twoPaneLayout.pane1.webView.clearCache(true)
                        twoPaneLayout.pane2.webView.clearCache(true)
                    }
                )
            }
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

        // Check if touch starts on any outer border (left, right, bottom)
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
            val canPane2Back = twoPaneLayout.isSecondPaneVisible && twoPaneLayout.pane2.webView.canGoBack()
            val canPane1Back = twoPaneLayout.pane1.webView.canGoBack()

            when (BackNavigationDispatcher.resolveBackAction(isMenuOpenState.value, canPane2Back, canPane1Back)) {
                BackAction.CLOSE_MENU -> {
                    isMenuOpenState.value = false
                }
                BackAction.GO_BACK_PANE2 -> {
                    twoPaneLayout.pane2.webView.goBack()
                }
                BackAction.GO_BACK_PANE1 -> {
                    twoPaneLayout.pane1.webView.goBack()
                }
                BackAction.MINIMIZE_TO_BUBBLE -> {
                    onMinimizeRequested?.invoke()
                }
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
