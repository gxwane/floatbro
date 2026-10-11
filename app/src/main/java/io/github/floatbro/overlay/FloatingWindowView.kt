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
            setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
            setBackgroundColor(Color.parseColor("#F5F5F7"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40f)
            )
        }

        // App Logo / Title (Draggable Area)
        val titleText = TextView(context).apply {
            text = "FloatBro"
            textSize = 14f
            setTextColor(Color.parseColor("#1976D2"))
            paint.isFakeBoldText = true
            setPadding(dpToPx(4f), 0, dpToPx(8f), 0)
        }

        // Draggable Area filling remaining space
        val dragHandleArea = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val dragIndicator = View(context).apply {
                layoutParams = LayoutParams(dpToPx(36f), dpToPx(4f), Gravity.CENTER)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#BBBBBB"))
                    cornerRadius = dpToPx(2f).toFloat()
                }
            }
            addView(dragIndicator)
            setOnTouchListener { _, event -> handleDragTouchEvent(event) }
        }

        // Action Buttons
        val btnSplit = createHeaderButton(R.drawable.ic_split_pane, "切换分屏") {
            twoPaneLayout.isSecondPaneVisible = !twoPaneLayout.isSecondPaneVisible
        }

        val btnRotate = createHeaderButton(R.drawable.ic_rotate, "旋转分屏") {
            twoPaneLayout.splitOrientation = if (twoPaneLayout.splitOrientation == TwoPaneLayout.Orientation.HORIZONTAL) {
                TwoPaneLayout.Orientation.VERTICAL
            } else {
                TwoPaneLayout.Orientation.HORIZONTAL
            }
        }

        val btnSwap = createHeaderButton(R.drawable.ic_swap, "对调视口") {
            twoPaneLayout.swapPanes()
        }

        val btnMenu = createHeaderButton(R.drawable.ic_menu, "菜单") {
            isMenuOpenState.value = true
        }

        val btnMinimize = createHeaderButton(R.drawable.ic_minimize, "最小化为气泡") {
            onMinimizeRequested?.invoke()
        }

        val btnClose = createHeaderButton(R.drawable.ic_close, "关闭浮窗") {
            onCloseRequested?.invoke()
        }

        header.addView(titleText)
        header.addView(dragHandleArea)
        header.addView(btnSplit)
        header.addView(btnRotate)
        header.addView(btnSwap)
        header.addView(btnMenu)
        header.addView(btnMinimize)
        header.addView(btnClose)

        return header
    }

    private fun createHeaderButton(drawableRes: Int, tooltip: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(30f), dpToPx(30f))
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(drawableRes)
            setColorFilter(Color.parseColor("#444444"))
            tooltipText = tooltip
            setOnClickListener { onClick() }
        }
    }

    private fun createResizeHandle(): View {
        return ImageView(context).apply {
            val handleSize = dpToPx(28f)
            layoutParams = LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END)
            setImageResource(R.drawable.ic_resize_handle)
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
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

                val newWidth = (initialWindowWidth + dx).coerceIn(minWidthPx, maxAllowedWidth)
                val newHeight = (initialWindowHeight + dy).coerceIn(minHeightPx, maxAllowedHeight)

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
}
