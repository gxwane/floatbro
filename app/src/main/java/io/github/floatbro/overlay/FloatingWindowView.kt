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
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import io.github.floatbro.R
import io.github.floatbro.core.webview.EBWebView
import timber.log.Timber
import kotlin.math.hypot

/**
 * Clean Single-Viewport Floating Window Container with Adaptive Responsive Header.
 * Features:
 * 1. Adaptive 42dp Header with Dynamic Back/Favicon pill and >= 160dp Omnibar;
 * 2. Tap-to-edit Omnibar with Soft Keyboard coordination and smart URL / search submission;
 * 3. Navigation controls via Dynamic Pill (Back) and Overflow Menu (Forward, Refresh, Maximize);
 * 4. Safe Z-order elevation on touch and WindowBoundsHelper clamping;
 * 5. Full-edge dragging and invisible diagonal corner resize.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingWindowView(
    context: Context,
    val sessionId: String,
    private val windowManager: WindowManager,
    val layoutParams: WindowManager.LayoutParams,
    private val lifecycleContext: OverlayLifecycleContext
) : FrameLayout(context) {

    val webView: EBWebView = EBWebView(context)
    private val progressBar: ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)

    // Header views
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var omniCapsule: LinearLayout
    private lateinit var urlBadgeText: TextView
    private lateinit var urlEditText: EditText
    private lateinit var clearInputButton: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnDock: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnCancelEdit: TextView

    var onMinimizeRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onDockRequested: ((url: String, title: String) -> Unit)? = null
    var onFaviconReceived: ((Bitmap) -> Unit)? = null
    var onInputFocusRequested: ((Boolean) -> Unit)? = null
    var onBringToFrontRequested: (() -> Unit)? = null
    var onBoundsChanged: ((width: Int, height: Int, x: Int, y: Int) -> Unit)? = null

    var currentFavicon: Bitmap? = null
        private set

    private var isEditMode = false

    // Drag coordinates
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var touchStartTime = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Resize coordinates
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var initialWindowWidth = 0
    private var initialWindowHeight = 0

    private val minWidthPx = dpToPx(280f)
    private val minHeightPx = dpToPx(340f)

    init {
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

        // 1. Adaptive 42dp Header Bar
        val headerView = createAdaptiveHeaderView()
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
                updateOmniText(url)
                updateNavigationState()
            }

            onPageFinishedListener = { url ->
                val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: url
                updateOmniText(resolvedTitle)
                updateNavigationState()
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
                this@FloatingWindowView.onInputFocusRequested?.invoke(isFocused)
            }

            onReceivedIconListener = { icon ->
                currentFavicon = icon
                updateNavigationState()
                this@FloatingWindowView.onFaviconReceived?.invoke(icon)
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

    private fun createAdaptiveHeaderView(): View {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6f), dpToPx(3f), dpToPx(6f), dpToPx(3f))
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44f)
            )
        }

        // 1. Left Nav: Back & Forward buttons
        btnBack = createHeaderButton(R.drawable.ic_nav_back, "后退") {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dpToPx(2f)
        }

        btnForward = createHeaderButton(R.drawable.ic_nav_forward, "前进") {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dpToPx(4f)
        }

        // 2. Center Omnibar Capsule (Height 34dp, Radius 17dp, Expandable)
        omniCapsule = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34f), 1f).apply {
                marginEnd = dpToPx(4f)
            }
            setPadding(dpToPx(10f), 0, dpToPx(6f), 0)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(17f).toFloat()
                setStroke(dpToPx(1f), Color.parseColor("#CBD5E1"))
            }

            urlBadgeText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                text = "FloatBro 悬浮浏览"
                textSize = 12f
                setTextColor(Color.parseColor("#0F172A"))
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                paint.isFakeBoldText = true
                isClickable = false
                isFocusable = false
            }
            addView(urlBadgeText)

            urlEditText = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                textSize = 12.5f
                setTextColor(Color.parseColor("#0F172A"))
                setBackgroundColor(Color.TRANSPARENT)
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_GO
                visibility = GONE
                setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_GO ||
                        actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                    ) {
                        commitUrlInput()
                        true
                    } else {
                        false
                    }
                }
            }
            addView(urlEditText)

            clearInputButton = ImageButton(context).apply {
                val iconSize = dpToPx(22f)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                setBackgroundColor(Color.TRANSPARENT)
                setImageResource(R.drawable.ic_clear)
                visibility = GONE
                setOnClickListener { urlEditText.setText("") }
            }
            addView(clearInputButton)

            setOnTouchListener { _, event -> handleOmnibarTouchEvent(event) }
        }

        // 3. Cancel button for edit mode (hidden by default)
        btnCancelEdit = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                marginStart = dpToPx(4f)
                marginEnd = dpToPx(4f)
            }
            gravity = Gravity.CENTER_VERTICAL
            text = "取消"
            textSize = 13f
            setTextColor(Color.parseColor("#2563EB"))
            visibility = GONE
            setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
            setOnClickListener { exitEditMode() }
        }

        // 4. Right Action Cluster: Refresh, Dock, Close
        btnRefresh = createHeaderButton(R.drawable.ic_refresh, "刷新页面") {
            webView.reload()
        }

        btnDock = createHeaderButton(R.drawable.ic_dock_app, "收回至应用内标签") {
            val currentUrl = webView.url ?: "about:blank"
            val currentTitle = webView.title ?: "已收回网页"
            onDockRequested?.invoke(currentUrl, currentTitle)
        }

        btnClose = createHeaderButton(R.drawable.ic_close, "关闭浮窗") {
            onCloseRequested?.invoke()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dpToPx(4f)
        }

        header.addView(btnBack)
        header.addView(btnForward)
        header.addView(omniCapsule)
        header.addView(btnCancelEdit)
        header.addView(btnRefresh)
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
            scaleType = ImageView.ScaleType.CENTER_INSIDE
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

    private fun updateNavigationState() {
        post {
            val canBack = webView.canGoBack()
            btnBack.isEnabled = canBack
            btnBack.alpha = if (canBack) 1.0f else 0.35f
            btnBack.setColorFilter(if (canBack) Color.parseColor("#0F172A") else Color.parseColor("#94A3B8"))

            val canForward = webView.canGoForward()
            btnForward.isEnabled = canForward
            btnForward.alpha = if (canForward) 1.0f else 0.35f
            btnForward.setColorFilter(if (canForward) Color.parseColor("#0F172A") else Color.parseColor("#94A3B8"))
        }
    }

    private fun formatDisplayUrl(raw: String): String {
        return raw.replaceFirst(Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE), "")
    }

    private fun updateOmniText(text: String) {
        if (!isEditMode) {
            urlBadgeText.text = formatDisplayUrl(text)
        }
    }

    private fun enterEditMode() {
        if (isEditMode) return
        isEditMode = true

        // 1. Hide left & right buttons to expand omnibar to full header width
        btnBack.visibility = GONE
        btnForward.visibility = GONE
        btnRefresh.visibility = GONE
        btnDock.visibility = GONE
        btnClose.visibility = GONE
        btnCancelEdit.visibility = VISIBLE

        // 2. Switch inside capsule
        urlBadgeText.visibility = GONE
        urlEditText.visibility = VISIBLE
        clearInputButton.visibility = VISIBLE

        val currentUrl = webView.url ?: ""
        urlEditText.setText(currentUrl)
        urlEditText.selectAll()

        onInputFocusRequested?.invoke(true)
        urlEditText.requestFocus()

        urlEditText.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(urlEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        Timber.d("Entered Omnibar Edit Mode (Full-width expanded) for session $sessionId")
    }

    fun exitEditMode() {
        if (!isEditMode) return
        isEditMode = false

        // 1. Restore capsule views
        urlEditText.visibility = GONE
        clearInputButton.visibility = GONE
        urlBadgeText.visibility = VISIBLE

        // 2. Hide cancel button and restore header buttons
        btnCancelEdit.visibility = GONE
        btnBack.visibility = VISIBLE
        btnForward.visibility = VISIBLE
        btnRefresh.visibility = VISIBLE
        btnDock.visibility = VISIBLE
        btnClose.visibility = VISIBLE
        updateNavigationState()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        onInputFocusRequested?.invoke(false)
        Timber.d("Exited Omnibar Edit Mode for session $sessionId")
    }

    private fun commitUrlInput() {
        val input = urlEditText.text.toString().trim()
        if (input.isNotEmpty()) {
            webView.loadUrlSafely(input)
        }
        exitEditMode()
    }

    private fun handleOmnibarTouchEvent(event: MotionEvent): Boolean {
        if (isEditMode) {
            // When in edit mode, direct clicks are for text editing, do not drag window
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                initialWindowX = layoutParams.x
                initialWindowY = layoutParams.y
                touchStartTime = System.currentTimeMillis()
                onBringToFrontRequested?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartX).toInt()
                val dy = (event.rawY - dragStartY).toInt()
                if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    layoutParams.x = initialWindowX + dx
                    layoutParams.y = initialWindowY + dy
                    if (isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(this, layoutParams)
                            onBoundsChanged?.invoke(layoutParams.width, layoutParams.height, layoutParams.x, layoutParams.y)
                        } catch (e: Exception) {
                            Timber.w(e, "Error updating window layout during drag")
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                val distance = hypot(dx.toDouble(), dy.toDouble())

                if (distance <= touchSlop) {
                    // Tap without drag -> Enter Omnibar Edit Mode!
                    enterEditMode()
                    return true
                } else {
                    return finishDragGesture()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                return finishDragGesture()
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            onBringToFrontRequested?.invoke()
        }

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
                handleEdgeDragTouchEvent(ev)
            }
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            if (isEditMode) {
                exitEditMode()
            }
            return true
        }
        return handleEdgeDragTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun handleEdgeDragTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                initialWindowX = layoutParams.x
                initialWindowY = layoutParams.y
                onBringToFrontRequested?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartX).toInt()
                val dy = (event.rawY - dragStartY).toInt()
                layoutParams.x = initialWindowX + dx
                layoutParams.y = initialWindowY + dy
                if (isAttachedToWindow) {
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                        onBoundsChanged?.invoke(layoutParams.width, layoutParams.height, layoutParams.x, layoutParams.y)
                    } catch (e: Exception) {
                        Timber.w(e, "Error updating window layout during edge drag")
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return finishDragGesture()
            }
        }
        return false
    }

    private fun finishDragGesture(): Boolean {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val winWidth = layoutParams.width

        // 1. Check Downward Drag-to-Dock
        val bottomDockThreshold = screenHeight - dpToPx(90f)
        if (layoutParams.y > bottomDockThreshold) {
            val currentUrl = webView.url ?: "about:blank"
            val currentTitle = webView.title ?: "已收回网页"
            onDockRequested?.invoke(currentUrl, currentTitle)
            return true
        }

        // 2. Check Push Off-Screen (>= 40% window width pushed outside) to collapse into bubble
        val isPushedOffLeft = layoutParams.x < -(winWidth * 0.40f)
        val isPushedOffRight = (layoutParams.x + winWidth) > (screenWidth + winWidth * 0.40f)

        if (isPushedOffLeft || isPushedOffRight) {
            onMinimizeRequested?.invoke()
            return true
        }

        // 3. Magnetic Edge Snapping: Stay as window, clamp smoothly against edges
        val snapMargin = dpToPx(8f)
        val edgeDetectRange = dpToPx(36f)

        if (layoutParams.x < edgeDetectRange) {
            layoutParams.x = snapMargin
        } else if (layoutParams.x + winWidth > screenWidth - edgeDetectRange) {
            layoutParams.x = screenWidth - winWidth - snapMargin
        }

        // Clamp Y in safe bounds
        val minY = dpToPx(36f)
        val maxY = screenHeight - dpToPx(120f)
        layoutParams.y = layoutParams.y.coerceIn(minY, maxY)

        if (isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(this, layoutParams)
                onBoundsChanged?.invoke(layoutParams.width, layoutParams.height, layoutParams.x, layoutParams.y)
            } catch (e: Exception) {
                Timber.w(e, "Error updating window layout after snap")
            }
        }
        return true
    }

    private fun handleResizeTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartX = event.rawX
                resizeStartY = event.rawY
                initialWindowWidth = layoutParams.width
                initialWindowHeight = layoutParams.height
                onBringToFrontRequested?.invoke()
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
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                        onBoundsChanged?.invoke(newWidth, newHeight, layoutParams.x, layoutParams.y)
                    } catch (e: Exception) {
                        Timber.w(e, "Error updating window layout during resize")
                    }
                }
                return true
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (isEditMode) {
                exitEditMode()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
            onMinimizeRequested?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
