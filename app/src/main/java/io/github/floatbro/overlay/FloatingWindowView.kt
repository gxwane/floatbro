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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import io.github.floatbro.R
import io.github.floatbro.browser.BrowserPreferences
import io.github.floatbro.core.webview.EBWebView
import io.github.floatbro.core.webview.TabOverlayStateBridge
import io.github.floatbro.core.webview.security.NavigationBlockEvent
import io.github.floatbro.core.webview.security.NavigationSecurityGuard
import io.github.floatbro.ui.theme.FloatingWindowThemeColors
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
    private val lifecycleContext: OverlayLifecycleContext,
    existingWebView: EBWebView? = null,
) : FrameLayout(context) {
    val webView: EBWebView = existingWebView ?: EBWebView(android.content.MutableContextWrapper(context))
    private val progressBar: ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)

    // Header views
    private lateinit var headerView: View
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var omniCapsule: LinearLayout
    private lateinit var urlBadgeText: TextView
    private lateinit var urlEditText: EditText
    private lateinit var clearInputButton: ImageButton
    private lateinit var btnCapsuleRetry: TextView
    private lateinit var btnCapsuleAllow: TextView
    private lateinit var webContainer: FrameLayout
    private var capsuleRestoreRunnable: Runnable? = null
    private var inWindowActionCard: View? = null
    private lateinit var btnMore: ImageButton
    private lateinit var btnDock: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnCancelEdit: TextView
    private var moreMenuPopup: PopupWindow? = null

    var onMinimizeRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onDockRequested: ((url: String, title: String) -> Unit)? = null
    var onToggleSplitRequested: (() -> Unit)? = null
    var onFaviconReceived: ((Bitmap) -> Unit)? = null
    var onInputFocusRequested: ((Boolean) -> Unit)? = null
    var onBringToFrontRequested: (() -> Unit)? = null
    var onBoundsChanged: ((width: Int, height: Int, x: Int, y: Int) -> Unit)? = null

    var onDragStarted: (() -> Unit)? = null
    var onDragMoved: ((rawX: Float, rawY: Float) -> Unit)? = null
    var onDragEnded: ((rawX: Float, rawY: Float) -> Boolean)? = null

    var currentFavicon: Bitmap? = null
        private set

    internal var isEditMode = false
    private var isResizing = false
    private var preEditY: Int? = null
    private var preWebInputAvoidanceY: Int? = null
    private var resizeHandleView: ImageView? = null

    // Video Fullscreen layer
    private lateinit var customVideoContainer: FrameLayout
    private var customVideoView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var elementZapperManager: ElementZapperManager

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

    private val minWidthPx = dpToPx(WindowBoundsHelper.MIN_WIDTH_DP)
    private val currentMinHeightPx: Int
        get() {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val isTablet =
                (minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) / resources.displayMetrics.density) >=
                    600f
            return if (isLandscape &&
                !isTablet
            ) {
                dpToPx(WindowBoundsHelper.MIN_HEIGHT_LANDSCAPE_DP)
            } else {
                dpToPx(WindowBoundsHelper.MIN_HEIGHT_PORTRAIT_DP)
            }
        }

    var isActiveWindow: Boolean = true
        private set
    private var isInterceptingForClickToFocus = false
    private var wasActiveAtOmnibarDown: Boolean = true

    val isFullscreenVideoActive: Boolean
        get() = customVideoView != null

    /**
     * Applies position and dimension changes to the window layout params,
     * dispatching to [FloatingDesktopContainer] if mounted inside a multi-window container,
     * or delegating to [WindowManager.updateViewLayout] as a fallback.
     */
    fun applyBoundsUpdate() {
        if (!isAttachedToWindow) return
        try {
            val p = parent
            if (p is FloatingDesktopContainer) {
                p.updateChildBounds(this, layoutParams.x, layoutParams.y, layoutParams.width, layoutParams.height)
            } else {
                windowManager.updateViewLayout(this, layoutParams)
            }
            onBoundsChanged?.invoke(layoutParams.width, layoutParams.height, layoutParams.x, layoutParams.y)
        } catch (e: Exception) {
            Timber.w(e, "Error updating window bounds (x=${layoutParams.x}, y=${layoutParams.y})")
        }
    }

    fun setActiveWindowState(isActive: Boolean) {
        if (isActive) {
            isInterceptingForClickToFocus = false
        }
        if (isActiveWindow == isActive) return
        isActiveWindow = isActive
        updateWindowBorderState()
    }

    private fun updateWindowBorderState() {
        val cornerRadius = dpToPx(16f).toFloat()
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val windowBg = if (isDarkMode) FloatingWindowThemeColors.DarkWindowBackground else FloatingWindowThemeColors.LightWindowBackground
        val strokeColor =
            if (isActiveWindow) {
                FloatingWindowThemeColors.ActiveBorder
            } else {
                if (isDarkMode) FloatingWindowThemeColors.InactiveBorderDark else FloatingWindowThemeColors.InactiveBorderLight
            }
        val strokeWidth = if (isActiveWindow) dpToPx(1.5f) else dpToPx(1f)

        background =
            GradientDrawable().apply {
                setColor(windowBg)
                setCornerRadius(cornerRadius)
                setStroke(strokeWidth, strokeColor)
            }
        elevation = if (isActiveWindow) dpToPx(12f).toFloat() else dpToPx(4f).toFloat()
        if (::headerView.isInitialized) {
            headerView.alpha = if (isActiveWindow) 1.0f else 0.72f
        }
    }

    init {
        lifecycleContext.attachToView(this)

        val cornerRadius = dpToPx(16f).toFloat()
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        clipToOutline = true
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        updateWindowBorderState()

        val mainLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }

        // 1. Adaptive 42dp Header Bar
        headerView = createAdaptiveHeaderView()
        mainLayout.addView(headerView)

        // 2. 2dp Progress Bar
        progressBar.apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2f))
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
            visibility = GONE
        }
        mainLayout.addView(progressBar)

        // 3. Central Web Content
        if (existingWebView == null) {
            val browserPrefs =
                io.github.floatbro.browser.BrowserPreferences
                    .getInstance(context)
            browserPrefs.resolveUserAgent(webView.settings.userAgentString)?.let { ua ->
                webView.settings.userAgentString = ua
            }
        }
        webView.apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )

            onPageStartedListener = { url ->
                restoreHeaderNormalState()
                exitFullscreenVideo(notifyCallback = true)
                moreMenuPopup?.dismiss()
                if (::elementZapperManager.isInitialized) {
                    elementZapperManager.exitZapperMode(quietly = true)
                }
                updateOmniText(url)
                updateNavigationState()
            }

            onNavigationBlockedListener = { event ->
                handleNavigationBlocked(event)
            }

            onPageFinishedListener = { url ->
                val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: url
                updateOmniText(resolvedTitle)
                updateNavigationState()
                io.github.floatbro.history.BrowsingHistoryManager
                    .getInstance(context)
                    .recordVisit(url, resolvedTitle)
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
                handleWebInputFocusChanged(isFocused)
                this@FloatingWindowView.onInputFocusRequested?.invoke(isFocused)
            }

            onReceivedIconListener = { icon ->
                currentFavicon = icon
                updateNavigationState()
                this@FloatingWindowView.onFaviconReceived?.invoke(icon)
            }

            onShowCustomViewListener = { view, callback ->
                enterFullscreenVideo(view, callback)
            }

            onHideCustomViewListener = {
                exitFullscreenVideo(notifyCallback = false)
            }
        }
        webContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
            }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        elementZapperManager = ElementZapperManager(context, webView, webContainer)

        if (existingWebView != null) {
            val initTitle = existingWebView.title?.takeIf { it.isNotBlank() } ?: existingWebView.url ?: "FloatBro 悬浮浏览"
            updateOmniText(initTitle)
            updateNavigationState()

            val snapshot = TabOverlayStateBridge.takeSnapshot(sessionId)
            if (snapshot != null && !snapshot.isRecycled) {
                val coverImageView =
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(snapshot)
                    }
                webContainer.addView(
                    coverImageView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )

                val onFrameCommitAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAttachedToWindow) {
                        try {
                            viewTreeObserver.registerFrameCommitCallback {
                                postDelayed({
                                    coverImageView
                                        .animate()
                                        .alpha(0f)
                                        .setDuration(120L)
                                        .withEndAction {
                                            coverImageView.setImageDrawable(null)
                                            webContainer.removeView(coverImageView)
                                        }.start()
                                    TabOverlayStateBridge.notifyCommit(sessionId)
                                }, 16L)
                            }
                        } catch (e: Exception) {
                            postDelayed({
                                coverImageView
                                    .animate()
                                    .alpha(0f)
                                    .setDuration(120L)
                                    .withEndAction {
                                        coverImageView.setImageDrawable(null)
                                        webContainer.removeView(coverImageView)
                                    }.start()
                                TabOverlayStateBridge.notifyCommit(sessionId)
                            }, 48L)
                        }
                    } else {
                        postDelayed({
                            coverImageView
                                .animate()
                                .alpha(0f)
                                .setDuration(120L)
                                .withEndAction {
                                    coverImageView.setImageDrawable(null)
                                    webContainer.removeView(coverImageView)
                                }.start()
                            TabOverlayStateBridge.notifyCommit(sessionId)
                        }, 48L)
                    }
                }
                if (isAttachedToWindow) {
                    onFrameCommitAction()
                } else {
                    addOnAttachStateChangeListener(
                        object : OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                removeOnAttachStateChangeListener(this)
                                onFrameCommitAction()
                            }

                            override fun onViewDetachedFromWindow(v: View) {}
                        },
                    )
                }
            } else {
                val onEmptyCommitAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAttachedToWindow) {
                        try {
                            viewTreeObserver.registerFrameCommitCallback {
                                postDelayed({
                                    TabOverlayStateBridge.notifyCommit(sessionId)
                                }, 16L)
                            }
                        } catch (e: Exception) {
                            postDelayed({
                                TabOverlayStateBridge.notifyCommit(sessionId)
                            }, 48L)
                        }
                    } else {
                        postDelayed({
                            TabOverlayStateBridge.notifyCommit(sessionId)
                        }, 48L)
                    }
                }
                if (isAttachedToWindow) {
                    onEmptyCommitAction()
                } else {
                    addOnAttachStateChangeListener(
                        object : OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                removeOnAttachStateChangeListener(this)
                                onEmptyCommitAction()
                            }

                            override fun onViewDetachedFromWindow(v: View) {}
                        },
                    )
                }
            }
        }

        mainLayout.addView(webContainer)
        addView(mainLayout)

        // Video Fullscreen Container (window-scoped overlay)
        customVideoContainer =
            FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.BLACK)
                visibility = GONE
            }
        addView(customVideoContainer)

        // 4. 48dp Bottom-Right Resize Handle
        val resizeHandle = createResizeHandle()
        addView(resizeHandle)
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics,
            ).toInt()

    private fun createAdaptiveHeaderView(): View {
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val headerBg = if (isDarkMode) FloatingWindowThemeColors.DarkHeaderBackground else FloatingWindowThemeColors.LightHeaderBackground
        val capsuleBg = if (isDarkMode) FloatingWindowThemeColors.DarkWindowBackground else FloatingWindowThemeColors.LightWindowBackground
        val capsuleStroke = if (isDarkMode) Color.parseColor("#475569") else Color.parseColor("#CBD5E1")
        val textColor = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val cancelColor = if (isDarkMode) Color.parseColor("#60A5FA") else Color.parseColor("#2563EB")

        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(4f), dpToPx(2f), dpToPx(4f), dpToPx(2f))
                setBackgroundColor(headerBg)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(44f),
                    )
            }

        // 1. Left Nav: Back & Forward buttons
        btnBack =
            createHeaderButton(R.drawable.ic_nav_back, "后退") {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dpToPx(1f)
            }

        btnForward =
            createHeaderButton(R.drawable.ic_nav_forward, "前进") {
                if (webView.canGoForward()) {
                    webView.goForward()
                }
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dpToPx(2f)
            }

        // 2. Center Omnibar Capsule (Height 34dp, Radius 17dp, Expandable)
        omniCapsule =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(0, dpToPx(34f), 1f).apply {
                        marginEnd = dpToPx(2f)
                    }
                setPadding(dpToPx(6f), 0, dpToPx(4f), 0)
                background =
                    GradientDrawable().apply {
                        setColor(capsuleBg)
                        cornerRadius = dpToPx(17f).toFloat()
                        setStroke(dpToPx(1f), capsuleStroke)
                    }

                urlBadgeText =
                    TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                        gravity = Gravity.CENTER_VERTICAL
                        includeFontPadding = false
                        setPadding(0, 0, 0, 0)
                        text = "FloatBro 悬浮浏览"
                        textSize = 11f
                        setTextColor(textColor)
                        isSingleLine = true
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        paint.isFakeBoldText = false
                        isClickable = false
                        isFocusable = false
                    }
                addView(urlBadgeText)

                urlEditText =
                    EditText(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                        gravity = Gravity.CENTER_VERTICAL
                        includeFontPadding = false
                        setPadding(0, 0, 0, 0)
                        typeface = Typeface.MONOSPACE
                        textSize = 12f
                        setTextColor(textColor)
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

                clearInputButton =
                    ImageButton(context).apply {
                        val iconSize = dpToPx(20f)
                        layoutParams =
                            LinearLayout.LayoutParams(iconSize, iconSize).apply {
                                gravity = Gravity.CENTER_VERTICAL
                            }
                        setBackgroundColor(Color.TRANSPARENT)
                        setImageResource(R.drawable.ic_clear)
                        val iconTint = if (isDarkMode) Color.parseColor("#CBD5E1") else Color.parseColor("#475569")
                        setColorFilter(iconTint)
                        visibility = GONE
                        setOnClickListener { urlEditText.setText("") }
                    }
                addView(clearInputButton)

                btnCapsuleRetry =
                    TextView(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                                marginStart = dpToPx(2f)
                            }
                        gravity = Gravity.CENTER_VERTICAL
                        text = "↻"
                        textSize = 14f
                        paint.isFakeBoldText = true
                        setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
                        visibility = GONE
                    }
                addView(btnCapsuleRetry)

                btnCapsuleAllow =
                    TextView(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                                marginStart = dpToPx(2f)
                            }
                        gravity = Gravity.CENTER_VERTICAL
                        text = "放行 ↗"
                        textSize = 11f
                        paint.isFakeBoldText = true
                        setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
                        visibility = GONE
                    }
                addView(btnCapsuleAllow)

                setOnTouchListener { _, event -> handleOmnibarTouchEvent(event) }
            }

        // 3. Cancel button for edit mode (hidden by default)
        btnCancelEdit =
            TextView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                        marginStart = dpToPx(4f)
                        marginEnd = dpToPx(4f)
                    }
                gravity = Gravity.CENTER_VERTICAL
                text = "取消"
                textSize = 13.5f
                setTextColor(cancelColor)
                visibility = GONE
                isClickable = true
                isFocusable = true
                setPadding(dpToPx(8f), 0, dpToPx(8f), 0)
                setOnClickListener { exitEditMode() }
            }

        // 4. Right Action Cluster: More (Secondary Menu), Dock, Close
        btnMore =
            createHeaderButton(R.drawable.ic_more_vert, "更多选项") {
                showMoreActionMenu(btnMore)
            }

        btnDock =
            createHeaderButton(R.drawable.ic_dock_app, "收回至应用内标签") {
                val currentUrl = webView.url ?: "about:blank"
                val currentTitle = webView.title ?: "已收回网页"
                exitFullscreenVideo(notifyCallback = true)
                onDockRequested?.invoke(currentUrl, currentTitle)
            }

        btnClose =
            createHeaderButton(R.drawable.ic_close, "关闭浮窗", widthDp = 36f) {
                // Immediate zero-latency visual hide to eliminate perceived lag
                visibility = GONE
                exitFullscreenVideo(notifyCallback = true)
                onCloseRequested?.invoke()
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = dpToPx(2f)
            }

        header.addView(btnBack)
        header.addView(btnForward)
        header.addView(omniCapsule)
        header.addView(btnCancelEdit)
        header.addView(btnMore)
        header.addView(btnDock)
        header.addView(btnClose)

        return header
    }

    private fun createHeaderButton(
        drawableRes: Int,
        tooltip: String,
        widthDp: Float = 28f,
        onClick: () -> Unit,
    ): ImageButton {
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val iconTint = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        return ImageButton(context).apply {
            val btnWidth = dpToPx(widthDp)
            layoutParams =
                LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    marginStart = dpToPx(1f)
                }
            val typedValue = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)) {
                setBackgroundResource(typedValue.resourceId)
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }
            setImageResource(drawableRes)
            setColorFilter(iconTint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dpToPx(4f), dpToPx(11f), dpToPx(4f), dpToPx(11f))
            contentDescription = tooltip
            setOnClickListener { onClick() }
        }
    }

    private fun showMoreActionMenu(anchor: View) {
        moreMenuPopup?.dismiss()

        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDarkMode) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val borderColor = if (isDarkMode) Color.parseColor("#475569") else Color.parseColor("#CBD5E1")
        val textColor = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val dividerColor = if (isDarkMode) Color.parseColor("#334155") else Color.parseColor("#E2E8F0")

        val density = context.resources.displayMetrics.density

        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val cardLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    GradientDrawable().apply {
                        setColor(bgColor)
                        cornerRadius = dp(10f).toFloat()
                        setStroke(dp(1f), borderColor)
                    }
                elevation = dp(8f).toFloat()
                setPadding(0, dp(4f), 0, dp(4f))
            }

        val popup =
            PopupWindow(
                cardLayout,
                dp(110f),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                elevation = dp(8f).toFloat()
            }
        moreMenuPopup = popup

        fun createMenuItem(
            text: String,
            onClick: () -> Unit,
        ): TextView =
            TextView(context).apply {
                this.text = text
                textSize = 13f
                setTextColor(textColor)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(14f), 0, dp(14f), 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(40f),
                    )
                val typedValue = TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
                    setBackgroundResource(typedValue.resourceId)
                }
                setOnClickListener {
                    popup.dismiss()
                    onClick()
                }
            }

        fun createDivider(): View =
            View(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0.5f)).apply {
                        setMargins(dp(8f), 0, dp(8f), 0)
                    }
                setBackgroundColor(dividerColor)
            }

        val currentUrl = webView.url ?: ""
        val host =
            try {
                android.net.Uri
                    .parse(currentUrl)
                    .host
                    ?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }

        val prefs = BrowserPreferences.getInstance(context)
        val isGlobalEnabled = prefs.isAdBlockEnabled
        val isSiteWhitelisted = host.isNotEmpty() && prefs.adBlockWhitelist.contains(host)
        val adBlockTitle = AdBlockMenuHelper.resolveMenuTitle(isGlobalEnabled, isSiteWhitelisted)

        // 1. 刷新
        cardLayout.addView(
            createMenuItem("刷新") {
                webView.reload()
            },
        )

        cardLayout.addView(createDivider())

        // 2. 复制网址
        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
            cardLayout.addView(
                createMenuItem("复制网址") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("URL", currentUrl))
                    android.widget.Toast
                        .makeText(context, "网址已复制", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
            )
            cardLayout.addView(createDivider())
        }

        // 3. 标记广告
        if (currentUrl.startsWith("http")) {
            cardLayout.addView(
                createMenuItem("标记广告") {
                    if (::elementZapperManager.isInitialized) {
                        elementZapperManager.enterZapperMode()
                    }
                },
            )
            cardLayout.addView(createDivider())
        }

        // 4. 广告拦截: 开 / 关
        cardLayout.addView(
            createMenuItem(adBlockTitle) {
                if (!isGlobalEnabled) {
                    prefs.isAdBlockEnabled = true
                    io.github.edsuns.adfilter.AdFilter
                        .get(context)
                        .setEnabled(true)
                    android.widget.Toast
                        .makeText(context, "广告拦截已开启", android.widget.Toast.LENGTH_SHORT)
                        .show()
                    webView.reload()
                } else if (host.isNotEmpty()) {
                    val enabledNow = prefs.toggleAdBlockForHost(host)
                    val msg = if (enabledNow) "已恢复拦截" else "已暂停拦截"
                    android.widget.Toast
                        .makeText(context, msg, android.widget.Toast.LENGTH_SHORT)
                        .show()
                    webView.reload()
                } else {
                    prefs.isAdBlockEnabled = !prefs.isAdBlockEnabled
                    io.github.edsuns.adfilter.AdFilter
                        .get(context)
                        .setEnabled(prefs.isAdBlockEnabled)
                    val msg = if (prefs.isAdBlockEnabled) "广告拦截已开启" else "广告拦截已暂停"
                    android.widget.Toast
                        .makeText(context, msg, android.widget.Toast.LENGTH_SHORT)
                        .show()
                    webView.reload()
                }
            },
        )

        // 5. 页面拦截诊断
        if (currentUrl.startsWith("http")) {
            cardLayout.addView(createDivider())
            cardLayout.addView(
                createMenuItem("页面拦截诊断") {
                    val diag =
                        io.github.floatbro.core.common.diagnostics.LocalDiagnostics
                            .getInstance(context.applicationContext)
                    val traces = diag.getAdFilterTracesSnapshot().filter { host.isNotEmpty() && (it.mainFrameHost.contains(host) || host.contains(it.mainFrameHost)) }
                    val report =
                        buildString {
                            appendLine("# 页面广告拦截诊断")
                            appendLine("- 目标站点: $host")
                            appendLine("- 拦截状态: $adBlockTitle")
                            appendLine("- 匹配记录数: ${traces.size}")
                            if (traces.isEmpty()) {
                                appendLine("- 暂无触发拦截或白名单的子资源")
                            } else {
                                traces.forEach { t ->
                                    appendLine("  * [${t.action}] [${t.resourceType}] ${t.resourceHost} (规则: ${t.matchedRule ?: "无"})")
                                }
                            }
                        }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("AdFilter Diagnosis", report))
                    android.widget.Toast
                        .makeText(context, "当前页面拦截诊断已复制到剪贴板", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
            )
        }

        try {
            popup.showAsDropDown(anchor, dp(-74f), dp(4f))
        } catch (e: Exception) {
            Timber.w(e, "Failed to show more menu popup")
        }
    }

    private fun createResizeHandle(): View {
        val handleSize = dpToPx(36f)
        return ImageView(context).apply {
            resizeHandleView = this
            layoutParams = LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END)
            setImageResource(R.drawable.ic_resize_grip)
            scaleType = ImageView.ScaleType.FIT_END
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(4f), dpToPx(4f))
            alpha = 0.35f
            elevation = dpToPx(16f).toFloat()
            isClickable = true
            contentDescription = "调整窗口大小"
            setOnTouchListener { _, event -> handleResizeTouchEvent(event) }
        }
    }

    private fun updateNavigationState() {
        post {
            val isDarkMode =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val activeColor = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
            val disabledColor = if (isDarkMode) Color.parseColor("#475569") else Color.parseColor("#94A3B8")

            val canBack = webView.canGoBack()
            btnBack.isEnabled = canBack
            btnBack.alpha = if (canBack) 1.0f else 0.35f
            btnBack.setColorFilter(if (canBack) activeColor else disabledColor)

            val canForward = webView.canGoForward()
            btnForward.isEnabled = canForward
            btnForward.alpha = if (canForward) 1.0f else 0.35f
            btnForward.setColorFilter(if (canForward) activeColor else disabledColor)
        }
    }

    private fun handleNavigationBlocked(event: NavigationBlockEvent) {
        post {
            if (isEditMode) return@post

            // Cancel any pending restoration
            capsuleRestoreRunnable?.let { removeCallbacks(it) }

            val isDarkMode =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            if (event.isAutoForwarded) {
                // Case A: Auto-unwrapped destination
                val forwardBg = if (isDarkMode) Color.parseColor("#064E3B") else Color.parseColor("#F0FDF4")
                val forwardStroke = if (isDarkMode) Color.parseColor("#047857") else Color.parseColor("#BBF7D0")
                val forwardText = if (isDarkMode) Color.parseColor("#6EE7B7") else Color.parseColor("#15803D")

                (omniCapsule.background as? GradientDrawable)?.apply {
                    setColor(forwardBg)
                    setStroke(dpToPx(1f), forwardStroke)
                }
                urlBadgeText.text = "🛡️ 已跳过广告直达下一页"
                urlBadgeText.setTextColor(forwardText)

                val runnable = Runnable { restoreHeaderNormalState() }
                capsuleRestoreRunnable = runnable
                postDelayed(runnable, 2500L)
            } else {
                // Case B: Pure rogue ad blocked
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Exception) {
                }

                val blockedBg = if (isDarkMode) Color.parseColor("#451A03") else Color.parseColor("#FEF3C7")
                val blockedStroke = if (isDarkMode) Color.parseColor("#D97706") else Color.parseColor("#FCD34D")
                val blockedText = if (isDarkMode) Color.parseColor("#FDE68A") else Color.parseColor("#B45309")

                (omniCapsule.background as? GradientDrawable)?.apply {
                    setColor(blockedBg)
                    setStroke(dpToPx(1f), blockedStroke)
                }

                val currentWidth = layoutParams?.width ?: width
                val isUltraCompact = currentWidth in 1 until dpToPx(300f)

                if (isUltraCompact) {
                    btnMore.visibility = GONE
                    btnDock.visibility = GONE
                    urlBadgeText.text = "🛡️ 拦截广告"
                } else {
                    urlBadgeText.text = "🛡️ 已拦截伪装广告"
                }
                urlBadgeText.setTextColor(blockedText)

                btnCapsuleRetry.visibility = VISIBLE
                btnCapsuleRetry.setTextColor(blockedText)
                btnCapsuleRetry.setOnClickListener {
                    restoreHeaderNormalState()
                    val selfHealJs =
                        """
                        (function() {
                            var el = document.querySelector('[data-floatbro-fallback-next]');
                            if (el && el.getAttribute('data-floatbro-fallback-next')) {
                                window.location.href = el.getAttribute('data-floatbro-fallback-next');
                            } else {
                                window.history.forward();
                            }
                        })();
                        """.trimIndent()
                    webView.evaluateJavascript(selfHealJs, null)
                }

                btnCapsuleAllow.visibility = VISIBLE
                btnCapsuleAllow.setTextColor(blockedText)
                btnCapsuleAllow.setOnClickListener {
                    showInWindowAllowCard(event)
                }

                val runnable = Runnable { restoreHeaderNormalState() }
                capsuleRestoreRunnable = runnable
                postDelayed(runnable, 4000L)
            }
        }
    }

    private fun showInWindowAllowCard(event: NavigationBlockEvent) {
        dismissInWindowAllowCard()
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(14f), dpToPx(10f), dpToPx(14f), dpToPx(10f))
                val cardBg = if (isDarkMode) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
                val cardStroke = if (isDarkMode) Color.parseColor("#334155") else Color.parseColor("#E2E8F0")
                background =
                    GradientDrawable().apply {
                        setColor(cardBg)
                        cornerRadius = dpToPx(12f).toFloat()
                        setStroke(dpToPx(1f), cardStroke)
                    }
                elevation = dpToPx(8f).toFloat()

                val titleView =
                    TextView(context).apply {
                        text = "允许访问外部目标？\n${event.targetHost}"
                        textSize = 12f
                        isSingleLine = false
                        maxLines = 2
                        gravity = Gravity.CENTER
                        setTextColor(if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A"))
                    }
                addView(titleView)

                val btnBar =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        layoutParams =
                            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                                topMargin = dpToPx(8f)
                            }

                        val btnCancel =
                            TextView(context).apply {
                                text = "取消"
                                textSize = 12f
                                setPadding(dpToPx(12f), dpToPx(4f), dpToPx(12f), dpToPx(4f))
                                setTextColor(if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#64748B"))
                                setOnClickListener {
                                    dismissInWindowAllowCard()
                                    restoreHeaderNormalState()
                                }
                            }
                        addView(btnCancel)

                        val btnConfirm =
                            TextView(context).apply {
                                text = "确认访问"
                                textSize = 12f
                                paint.isFakeBoldText = true
                                setPadding(dpToPx(12f), dpToPx(4f), dpToPx(12f), dpToPx(4f))
                                setTextColor(Color.parseColor("#2563EB"))
                                setOnClickListener {
                                    dismissInWindowAllowCard()
                                    restoreHeaderNormalState()
                                    val currentHost = webView.url?.let { NavigationSecurityGuard.extractHost(it) } ?: ""
                                    NavigationSecurityGuard.issueBypassToken(currentHost, event.targetHost)
                                    webView.loadUrl(event.originalUrl)
                                }
                            }
                        addView(btnConfirm)
                    }
                addView(btnBar)
            }

        val cardLp =
            FrameLayout
                .LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply {
                    topMargin = dpToPx(16f)
                    leftMargin = dpToPx(16f)
                    rightMargin = dpToPx(16f)
                }

        inWindowActionCard = card
        webContainer.addView(card, cardLp)
    }

    private fun dismissInWindowAllowCard() {
        inWindowActionCard?.let {
            webContainer.removeView(it)
            inWindowActionCard = null
        }
    }

    private fun restoreHeaderNormalState() {
        capsuleRestoreRunnable?.let { removeCallbacks(it) }
        capsuleRestoreRunnable = null

        dismissInWindowAllowCard()

        if (::omniCapsule.isInitialized) {
            val isDarkMode =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val capsuleBg = if (isDarkMode) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9")
            val capsuleStroke = if (isDarkMode) Color.parseColor("#334155") else Color.parseColor("#E2E8F0")
            val textColor = if (isDarkMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")

            (omniCapsule.background as? GradientDrawable)?.apply {
                setColor(capsuleBg)
                setStroke(dpToPx(1f), capsuleStroke)
            }

            if (::btnCapsuleRetry.isInitialized) btnCapsuleRetry.visibility = GONE
            if (::btnCapsuleAllow.isInitialized) btnCapsuleAllow.visibility = GONE

            if (::btnMore.isInitialized) btnMore.visibility = VISIBLE
            if (::btnDock.isInitialized) btnDock.visibility = VISIBLE

            if (!isEditMode && ::urlBadgeText.isInitialized) {
                val resolvedTitle = webView.title?.takeIf { it.isNotBlank() } ?: webView.url ?: "FloatBro 悬浮浏览"
                updateOmniText(resolvedTitle)
                urlBadgeText.setTextColor(textColor)
            }
        }
    }

    private fun formatDisplayUrl(raw: String): String = raw.replaceFirst(Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE), "")

    private fun updateOmniText(text: String) {
        if (!isEditMode) {
            urlBadgeText.text = formatDisplayUrl(text)
        }
    }

    private fun enterEditMode() {
        if (isEditMode) return
        restoreHeaderNormalState()
        isEditMode = true
        preEditY = layoutParams.y

        // Calculate safe keyboard avoidance Y
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val estimatedImeHeightPx = dpToPx(300f)
        val safeY =
            OverlayPhysicsHelper.calculateKeyboardAvoidanceY(
                currentY = layoutParams.y,
                windowHeight = layoutParams.height,
                screenHeight = screenHeight,
                keyboardHeight = estimatedImeHeightPx,
                safeMarginPx = dpToPx(16f),
            )
        if (safeY != layoutParams.y) {
            layoutParams.y = safeY
            applyBoundsUpdate()
        }

        // 1. Hide left & right buttons to expand omnibar to full header width
        btnBack.visibility = GONE
        btnForward.visibility = GONE
        btnMore.visibility = GONE
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

        // Restore pre-edit Y if shifted by keyboard avoidance
        preEditY?.let { originalY ->
            val minY = dpToPx(36f)
            val maxY = (resources.displayMetrics.heightPixels - layoutParams.height - dpToPx(16f)).coerceAtLeast(minY)
            val safeY = originalY.coerceIn(minY, maxY)
            if (layoutParams.y != safeY) {
                layoutParams.y = safeY
                applyBoundsUpdate()
            }
            preEditY = null
        }

        // 1. Restore capsule views
        urlEditText.visibility = GONE
        clearInputButton.visibility = GONE
        urlBadgeText.visibility = VISIBLE

        // 2. Hide cancel button and restore header buttons
        btnCancelEdit.visibility = GONE
        btnBack.visibility = VISIBLE
        btnForward.visibility = VISIBLE
        btnMore.visibility = VISIBLE
        btnDock.visibility = VISIBLE
        btnClose.visibility = VISIBLE
        updateNavigationState()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        onInputFocusRequested?.invoke(false)
        Timber.d("Exited Omnibar Edit Mode for session $sessionId")
    }

    fun enterFullscreenVideo(
        view: View,
        callback: WebChromeClient.CustomViewCallback?,
    ) {
        if (customVideoView != null) {
            callback?.onCustomViewHidden()
            return
        }
        if (isEditMode) {
            exitEditMode()
        }
        customVideoView = view
        customViewCallback = callback
        headerView.visibility = GONE
        progressBar.visibility = GONE
        customVideoContainer.removeAllViews()
        (view.parent as? ViewGroup)?.removeView(view)
        customVideoContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        customVideoContainer.visibility = VISIBLE
        resizeHandleView?.visibility = VISIBLE
        resizeHandleView?.bringToFront()
        setActiveWindowState(true)
        onInputFocusRequested?.invoke(true)
        requestFocus()
        Timber.d("Entered video fullscreen for session $sessionId")
    }

    fun exitFullscreenVideo(notifyCallback: Boolean = true) {
        if (customVideoView == null) return
        customVideoContainer.visibility = GONE
        customVideoContainer.removeAllViews()
        customVideoView = null
        if (notifyCallback) {
            try {
                customViewCallback?.onCustomViewHidden()
            } catch (e: Exception) {
                Timber.w(e, "Error calling onCustomViewHidden")
            }
        }
        customViewCallback = null
        headerView.visibility = VISIBLE
        resizeHandleView?.visibility = VISIBLE
        resizeHandleView?.bringToFront()
        onInputFocusRequested?.invoke(false)
        Timber.d("Exited video fullscreen for session $sessionId")
    }

    private fun handleWebInputFocusChanged(isFocused: Boolean) {
        if (isFocused) {
            if (!isEditMode && preWebInputAvoidanceY == null) {
                preWebInputAvoidanceY = layoutParams.y
                val displayMetrics = context.resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                val estimatedImeHeightPx = dpToPx(300f)
                val safeY =
                    OverlayPhysicsHelper.calculateKeyboardAvoidanceY(
                        currentY = layoutParams.y,
                        windowHeight = layoutParams.height,
                        screenHeight = screenHeight,
                        keyboardHeight = estimatedImeHeightPx,
                        safeMarginPx = dpToPx(16f),
                    )
                if (safeY != layoutParams.y) {
                    layoutParams.y = safeY
                    applyBoundsUpdate()
                }
            }
        } else {
            preWebInputAvoidanceY?.let { originalY ->
                if (!isEditMode && layoutParams.y != originalY) {
                    layoutParams.y = originalY
                    applyBoundsUpdate()
                }
                preWebInputAvoidanceY = null
            }
        }
    }

    private fun commitUrlInput() {
        val input = urlEditText.text.toString().trim()
        if (input.isNotEmpty()) {
            val prefs =
                io.github.floatbro.browser.BrowserPreferences
                    .getInstance(context)
            val resolved =
                io.github.floatbro.browser.BrowserUrlResolver.resolve(
                    input,
                    prefs.searchEngine,
                    prefs.customSearchUrl,
                )
            webView.loadUrlSafely(resolved)
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
                wasActiveAtOmnibarDown = isActiveWindow
                dragStartX = event.rawX
                dragStartY = event.rawY
                initialWindowX = layoutParams.x
                initialWindowY = layoutParams.y
                touchStartTime = System.currentTimeMillis()
                onBringToFrontRequested?.invoke()
                onDragStarted?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartX).toInt()
                val dy = (event.rawY - dragStartY).toInt()
                if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    layoutParams.x = initialWindowX + dx
                    layoutParams.y = initialWindowY + dy
                    applyBoundsUpdate()
                    onDragMoved?.invoke(event.rawX, event.rawY)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                val distance = hypot(dx.toDouble(), dy.toDouble())

                if (distance <= touchSlop) {
                    if (!wasActiveAtOmnibarDown) {
                        // First tap on inactive omnibar only focused the window; do NOT enter edit mode
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        } else {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        return true
                    }
                    // Tap without drag -> Enter Omnibar Edit Mode!
                    enterEditMode()
                    return true
                } else {
                    return finishDragGesture(event.rawX, event.rawY)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                return finishDragGesture(event.rawX, event.rawY)
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isResizing) {
            return false
        }
        val wasActive = isActiveWindow
        if (ev.action == MotionEvent.ACTION_DOWN) {
            isInterceptingForClickToFocus = false
            onBringToFrontRequested?.invoke()
            if (customVideoView != null) {
                // Reclaim input focus for Back key / gesture to reliably exit video fullscreen
                onInputFocusRequested?.invoke(true)
                requestFocus()
            }
        }

        val x = ev.x
        val y = ev.y
        val edgeSize = dpToPx(16f)
        val resizeSize = dpToPx(48f)
        val headerHeight = dpToPx(44f)

        // Bottom-right corner reserved for diagonal resizing
        val inResizeCorner = x >= width - resizeSize && y >= height - resizeSize
        if (inResizeCorner) {
            return false
        }

        // Outer borders intercept for dragging
        val isVideoFullscreen = customVideoView != null
        val onOuterEdge =
            if (isVideoFullscreen) {
                // In video fullscreen: allow top and side edges to drag the window; do not intercept bottom edge to protect player progress bar
                (y <= edgeSize || x <= edgeSize || x >= width - edgeSize)
            } else {
                y > headerHeight && (x <= edgeSize || x >= width - edgeSize || y >= height - edgeSize)
            }
        if (onOuterEdge) {
            return true
        }

        // Click-To-Focus on Inactive Window (Web Content Area)
        if (!wasActive && customVideoView == null) {
            val isClickToFocus = BrowserPreferences.getInstance(context).isClickToFocusEnabled
            val isOverWebContent = y > headerHeight && !onOuterEdge
            if (isClickToFocus && isOverWebContent) {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    isInterceptingForClickToFocus = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                return true
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            if (isEditMode) {
                exitEditMode()
            }
            onInputFocusRequested?.invoke(false)
            return true
        }

        if (isInterceptingForClickToFocus) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isInterceptingForClickToFocus = false
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
                onDragStarted?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartX).toInt()
                val dy = (event.rawY - dragStartY).toInt()
                layoutParams.x = initialWindowX + dx
                layoutParams.y = initialWindowY + dy
                applyBoundsUpdate()
                onDragMoved?.invoke(event.rawX, event.rawY)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return finishDragGesture(event.rawX, event.rawY)
            }
        }
        return false
    }

    private fun finishDragGesture(
        dropX: Float = 0f,
        dropY: Float = 0f,
    ): Boolean {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val winWidth = layoutParams.width

        val isDismissHit = onDragEnded?.invoke(dropX, dropY) ?: false

        val action =
            OverlayPhysicsHelper.resolveWindowDragAction(
                currentY = layoutParams.y,
                currentX = layoutParams.x,
                windowWidth = winWidth,
                screenWidth = screenWidth,
                isDismissHit = isDismissHit,
            )

        return when (action) {
            WindowDragAction.DISMISS_TO_TARGET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
                exitFullscreenVideo(notifyCallback = true)
                onCloseRequested?.invoke()
                true
            }
            WindowDragAction.COLLAPSE_TO_BUBBLE -> {
                exitFullscreenVideo(notifyCallback = true)
                onMinimizeRequested?.invoke()
                true
            }
            WindowDragAction.SNAP_EDGE -> {
                val edgeDetectRange = dpToPx(36f)
                val startX = layoutParams.x
                val targetX =
                    OverlayPhysicsHelper.calculateSnapTargetX(
                        currentX = startX,
                        windowWidth = winWidth,
                        screenWidth = screenWidth,
                        snapThresholdPx = edgeDetectRange,
                    )

                // Clamp Y in safe bounds
                val minY = dpToPx(36f)
                val maxY = (screenHeight - dpToPx(120f)).coerceAtLeast(minY)
                layoutParams.y = layoutParams.y.coerceIn(minY, maxY)

                if (startX != targetX) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    ValueAnimator.ofInt(startX, targetX).apply {
                        duration = 200L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { animation ->
                            layoutParams.x = animation.animatedValue as Int
                            applyBoundsUpdate()
                        }
                        addListener(
                            object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    onBringToFrontRequested?.invoke()
                                }
                            },
                        )
                        start()
                    }
                } else {
                    applyBoundsUpdate()
                    onBringToFrontRequested?.invoke()
                }
                true
            }
        }
    }

    private fun handleResizeTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isResizing = true
                parent?.requestDisallowInterceptTouchEvent(true)
                resizeHandleView
                    ?.animate()
                    ?.scaleX(1.15f)
                    ?.scaleY(1.15f)
                    ?.alpha(1.0f)
                    ?.setDuration(120)
                    ?.start()
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
                val maxAllowedWidth = displayMetrics.widthPixels - layoutParams.x
                val maxAllowedHeight = displayMetrics.heightPixels - layoutParams.y

                val (newWidth, newHeight) =
                    WindowBoundsHelper.clampDimensions(
                        rawWidth = initialWindowWidth + dx,
                        rawHeight = initialWindowHeight + dy,
                        minWidth = minWidthPx,
                        maxWidth = maxAllowedWidth,
                        minHeight = currentMinHeightPx,
                        maxHeight = maxAllowedHeight,
                    )

                if (newWidth != layoutParams.width || newHeight != layoutParams.height) {
                    layoutParams.width = newWidth
                    layoutParams.height = newHeight
                    applyBoundsUpdate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isResizing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                resizeHandleView
                    ?.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.alpha(0.5f)
                    ?.setDuration(120)
                    ?.start()
                return true
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                if (::elementZapperManager.isInitialized && elementZapperManager.isZapperActive) {
                    elementZapperManager.exitZapperMode(quietly = false)
                    return true
                }
                if (isEditMode) {
                    exitEditMode()
                    return true
                }
                val action =
                    BackNavigationDispatcher.resolveBackAction(
                        isVideoFullscreen = customVideoView != null,
                        isMenuOpen = false,
                        canPane2GoBack = false,
                        canPane1GoBack = webView.canGoBack(),
                    )
                return when (action) {
                    BackAction.EXIT_VIDEO_FULLSCREEN -> {
                        exitFullscreenVideo(notifyCallback = true)
                        true
                    }
                    BackAction.CLOSE_MENU -> true
                    BackAction.GO_BACK_PANE2 -> true
                    BackAction.GO_BACK_PANE1 -> {
                        webView.goBack()
                        true
                    }
                    BackAction.MINIMIZE_TO_BUBBLE -> {
                        onMinimizeRequested?.invoke()
                        true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInterceptingForClickToFocus = false
    }

    override fun onDetachedFromWindow() {
        isInterceptingForClickToFocus = false
        moreMenuPopup?.dismiss()
        moreMenuPopup = null
        if (::elementZapperManager.isInitialized) {
            elementZapperManager.exitZapperMode(quietly = true)
        }
        restoreHeaderNormalState()
        exitFullscreenVideo(notifyCallback = true)
        super.onDetachedFromWindow()
    }
}
