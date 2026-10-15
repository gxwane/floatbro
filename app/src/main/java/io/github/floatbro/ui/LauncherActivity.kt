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

package io.github.floatbro.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.BuildConfig
import io.github.floatbro.R
import io.github.floatbro.browser.BrowserPreferences
import io.github.floatbro.browser.BrowserUrlResolver
import io.github.floatbro.browser.SearchEngine
import io.github.floatbro.browser.UserAgentMode
import io.github.floatbro.core.webview.EBWebView
import io.github.floatbro.core.webview.TabOverlayStateBridge
import io.github.floatbro.history.BrowsingHistoryManager
import io.github.floatbro.overlay.AdBlockMenuHelper
import io.github.floatbro.overlay.ElementZapperManager
import io.github.floatbro.service.FloatBrowserService
import io.github.floatbro.ui.components.MarkdownContent
import io.github.floatbro.ui.theme.*
import io.github.floatbro.ui.theme.BrandBlue
import io.github.floatbro.ui.theme.BrandEmerald
import io.github.floatbro.ui.theme.FloatBroTheme
import io.github.floatbro.ui.theme.Slate200
import io.github.floatbro.ui.theme.Slate50
import io.github.floatbro.ui.theme.Slate600
import io.github.floatbro.ui.theme.Slate900
import io.github.floatbro.update.AppUpdateManager
import io.github.floatbro.update.DownloadProgress
import io.github.floatbro.update.ReleaseInfo
import io.github.floatbro.update.UpdateCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class OpenMode {
    FLOATING, // 默认直接弹射为独立悬浮窗
    TAB, // 默认在主应用内并列标签打开
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    val url: String,
    var favicon: Bitmap? = null,
)

class LauncherActivity : ComponentActivity() {
    companion object {
        const val ACTION_DOCK_TAB = "io.github.floatbro.action.DOCK_TAB"
        const val EXTRA_TAB_URL = "extra_tab_url"
        const val EXTRA_TAB_TITLE = "extra_tab_title"
        const val EXTRA_SESSION_ID = "extra_session_id"

        fun resolveBrowserUrl(input: String): String = BrowserUrlResolver.resolve(input)

        fun resolveBrowserUrl(
            context: Context,
            input: String,
        ): String {
            val prefs = BrowserPreferences.getInstance(context)
            return BrowserUrlResolver.resolve(input, prefs.searchEngine, prefs.customSearchUrl)
        }
    }

    private var hasOverlayPermission by mutableStateOf(false)
    private var showPermissionRationaleDialog by mutableStateOf(false)
    private var openMode by mutableStateOf(OpenMode.TAB)
    private val tabs = mutableStateListOf<BrowserTab>()
    private val recentClosedTabs = mutableStateListOf<BrowserTab>()
    private val activeWebViews = mutableMapOf<String, EBWebView>()
    private var activeTabId by mutableStateOf("home")
    private var previousTabId by mutableStateOf("home")

    override fun onDestroy() {
        super.onDestroy()
        activeWebViews.values.forEach { wv ->
            try {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.removeAllViews()
                wv.destroy()
            } catch (_: Exception) {
            }
        }
        activeWebViews.clear()
    }

    private fun captureWebViewHardwareBitmap(
        view: android.view.View,
        scale: Float = 0.5f,
        onCaptured: (Bitmap?) -> Unit,
    ) {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
            onCaptured(null)
            return
        }
        val decorView = window.decorView
        val windowW = decorView.width
        val windowH = decorView.height
        if (windowW <= 0 || windowH <= 0) {
            onCaptured(null)
            return
        }

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val left = location[0].coerceIn(0, windowW)
        val top = location[1].coerceIn(0, windowH)
        val right = (location[0] + view.width).coerceIn(left, windowW)
        val bottom = (location[1] + view.height).coerceIn(top, windowH)
        val srcW = right - left
        val srcH = bottom - top
        if (srcW <= 0 || srcH <= 0) {
            onCaptured(null)
            return
        }

        try {
            val targetW = (srcW * scale).toInt().coerceAtLeast(1)
            val targetH = (srcH * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
            val srcRect = Rect(left, top, right, bottom)

            PixelCopy.request(
                window,
                srcRect,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        onCaptured(bitmap)
                    } else {
                        onCaptured(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Throwable) {
            Timber.w(e, "PixelCopy request failed")
            onCaptured(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure status bar icon color adapts dynamically to dark mode
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode

        loadPreferences()
        checkPermission()
        handleIncomingIntent(intent)

        setContent {
            FloatBroTheme {
                val isDark = isSystemInDarkTheme()
                DisposableEffect(isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
                    onDispose {}
                }

                if (showPermissionRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationaleDialog = false },
                        title = { Text(OverlayPermissionStrings.RATIONALE_TITLE) },
                        text = {
                            Text(
                                OverlayPermissionStrings.RATIONALE_MESSAGE,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showPermissionRationaleDialog = false
                                requestOverlayPermission()
                            }) {
                                Text(OverlayPermissionStrings.ACTION_CONFIRM)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionRationaleDialog = false }) {
                                Text(OverlayPermissionStrings.ACTION_DISMISS)
                            }
                        },
                    )
                }

                // BackHandler for dedicated Settings secondary page
                BackHandler(enabled = activeTabId == "settings") {
                    activeTabId = previousTabId
                }

                // BackHandler for active tab at root to return to Home
                BackHandler(enabled = activeTabId != "home" && activeTabId != "settings" && tabs.isNotEmpty()) {
                    val currentWv = activeWebViews[activeTabId]
                    if (currentWv == null || !currentWv.canGoBack()) {
                        activeTabId = "home"
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current
                val view = LocalView.current
                var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                // Drag state
                var draggedTab by remember { mutableStateOf<BrowserTab?>(null) }
                var originOffset by remember { mutableStateOf(Offset.Zero) }
                var originSize by remember { mutableStateOf(IntSize.Zero) }
                var isDetached by remember { mutableStateOf(false) }
                var hasFiredDetachedHaptic by remember { mutableStateOf(false) }
                var isCommittingOrCancelling by remember { mutableStateOf(false) }
                var snapshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var isCapturingSnapshot by remember { mutableStateOf(false) }

                var totalDragY by remember { mutableFloatStateOf(0f) }
                var totalDragX by remember { mutableFloatStateOf(0f) }

                val dragOffsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                val cardWidthAnim = remember { Animatable(100f) }
                val cardHeightAnim = remember { Animatable(36f) }
                val cardScaleAnim = remember { Animatable(1.0f) }
                val cardCornerRadiusAnim = remember { Animatable(10f) }
                val cardTiltAnim = remember { Animatable(0f) }
                val cardAlphaAnim = remember { Animatable(1.0f) }

                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    // Safe inset padding avoiding status bar and camera hole
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { rootLayoutCoordinates = it },
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 1. Tab Strip (Sole Top Bar when browsing)
                            if (activeTabId != "settings") {
                                AppTabStrip(
                                    tabs = tabs,
                                    activeTabId = activeTabId,
                                    draggingTabId = draggedTab?.id,
                                    rootLayoutCoordinates = rootLayoutCoordinates,
                                    onSelectTab = { activeTabId = it },
                                    onCloseTab = { tabId ->
                                        // 1. Immediately update UI state so Compose dismisses the tab card with 0ms latency
                                        val closed = tabs.find { it.id == tabId }
                                        tabs.removeAll { it.id == tabId }
                                        if (activeTabId == tabId) {
                                            activeTabId = "home"
                                        }

                                        // 2. Record recently closed and visit history safely
                                        if (closed != null && closed.url != "about:home" && closed.url != "about:blank" && closed.url.isNotBlank()) {
                                            if (!recentClosedTabs.any { it.url == closed.url }) {
                                                recentClosedTabs.add(0, closed)
                                                if (recentClosedTabs.size > 5) {
                                                    recentClosedTabs.removeAt(recentClosedTabs.lastIndex)
                                                }
                                            }
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                BrowsingHistoryManager.getInstance(applicationContext).recordVisit(closed.url, closed.title)
                                            }
                                        }

                                        // 3. Clean up detached WebView asynchronously without about:blank IPC contention
                                        activeWebViews.remove(tabId)?.let { wv ->
                                            try {
                                                wv.stopLoading()
                                                wv.removeAllViews()
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    try {
                                                        wv.destroy()
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                    onNewTab = {
                                        val newTab = BrowserTab(title = "新标签页", url = "about:home")
                                        tabs.add(newTab)
                                        activeTabId = newTab.id
                                    },
                                    onOpenSettings = {
                                        previousTabId = activeTabId
                                        activeTabId = "settings"
                                    },
                                    onTabDragStart = { tab, origin, size ->
                                        if (!isCommittingOrCancelling) {
                                            snapshotBitmap = null
                                            isCapturingSnapshot = false
                                            draggedTab = tab
                                            originOffset = origin
                                            originSize = size
                                            isDetached = false
                                            hasFiredDetachedHaptic = false
                                            totalDragX = 0f
                                            totalDragY = 0f

                                            val currentWv = activeWebViews[tab.id]
                                            if (currentWv != null && !isCapturingSnapshot) {
                                                isCapturingSnapshot = true
                                                captureWebViewHardwareBitmap(currentWv, scale = 0.5f) { bmp ->
                                                    isCapturingSnapshot = false
                                                    if (draggedTab?.id == tab.id) {
                                                        snapshotBitmap = bmp
                                                    }
                                                }
                                            }

                                            coroutineScope.launch {
                                                dragOffsetAnim.snapTo(origin)
                                                cardWidthAnim.snapTo(size.width.toFloat())
                                                cardHeightAnim.snapTo(size.height.toFloat())
                                                cardScaleAnim.snapTo(1.0f)
                                                cardCornerRadiusAnim.snapTo(10f)
                                                cardTiltAnim.snapTo(0f)
                                                cardAlphaAnim.snapTo(1.0f)
                                            }
                                        }
                                    },
                                    onTabDrag = { delta ->
                                        if (!isCommittingOrCancelling && draggedTab != null) {
                                            totalDragX += delta.x
                                            totalDragY += delta.y
                                            val tearThresholdPx = with(density) { 44.dp.toPx() }
                                            val maxTearDistancePx = with(density) { 200.dp.toPx() }

                                            if (!isDetached) {
                                                if (totalDragY < tearThresholdPx) {
                                                    // 1. 触摸上拉阻尼与弹性形变阶段 (0 - 44dp)
                                                    val progress = (totalDragY / tearThresholdPx).coerceIn(0f, 1f)
                                                    val yResistance = totalDragY * (1f - (totalDragY / maxTearDistancePx).coerceIn(0f, 0.4f))
                                                    val xResistance = totalDragX * 0.6f

                                                    coroutineScope.launch {
                                                        dragOffsetAnim.snapTo(Offset(originOffset.x + xResistance, originOffset.y + yResistance))
                                                        cardCornerRadiusAnim.snapTo(10f + 2f * progress)
                                                        cardScaleAnim.snapTo(1.0f + 0.04f * progress)
                                                    }
                                                } else {
                                                    // 2. 突破撕脱阈值，切换为独立悬浮卡片并采集快照
                                                    isDetached = true
                                                    val sourceWv = activeWebViews[draggedTab?.id]
                                                    if (sourceWv != null && snapshotBitmap == null && !isCapturingSnapshot) {
                                                        isCapturingSnapshot = true
                                                        captureWebViewHardwareBitmap(sourceWv, scale = 0.5f) { bmp ->
                                                            isCapturingSnapshot = false
                                                            if (isDetached) {
                                                                snapshotBitmap = bmp
                                                            }
                                                        }
                                                    }
                                                    if (!hasFiredDetachedHaptic) {
                                                        hasFiredDetachedHaptic = true
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                                                        } else {
                                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        }
                                                    }

                                                    val previewWidthPx = with(density) { 240.dp.toPx() }
                                                    val previewHeightPx = with(density) { 160.dp.toPx() }

                                                    coroutineScope.launch {
                                                        launch {
                                                            cardScaleAnim.animateTo(0.98f, tween(60))
                                                            cardScaleAnim.animateTo(1.0f, tween(60))
                                                        }
                                                        launch {
                                                            cardWidthAnim.animateTo(previewWidthPx, tween(160, easing = FastOutSlowInEasing))
                                                        }
                                                        launch {
                                                            cardHeightAnim.animateTo(previewHeightPx, tween(160, easing = FastOutSlowInEasing))
                                                        }
                                                        launch {
                                                            cardCornerRadiusAnim.animateTo(14f, tween(120))
                                                        }
                                                    }

                                                    coroutineScope.launch {
                                                        dragOffsetAnim.snapTo(dragOffsetAnim.value + delta)
                                                    }
                                                }
                                            } else {
                                                // 3. 撕脱后自由 2D 拖拽跟随与动态倾斜计算
                                                val targetTilt = (delta.x * 0.4f).coerceIn(-7f, 7f)
                                                coroutineScope.launch {
                                                    dragOffsetAnim.snapTo(dragOffsetAnim.value + delta)
                                                    cardTiltAnim.animateTo(targetTilt, tween(50))
                                                }
                                            }
                                        }
                                    },
                                    onTabDragEnd = {
                                        if (draggedTab != null && !isCommittingOrCancelling) {
                                            isCommittingOrCancelling = true
                                            val tearThresholdPx = with(density) { 44.dp.toPx() }
                                            val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this@LauncherActivity)
                                            val isCommitted = totalDragY >= tearThresholdPx && isDetached && hasPermission

                                            if (isCommitted) {
                                                // 4A. 手势释放确认：无缝移交至系统全局悬浮窗
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                } else {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                }

                                                val dropX = dragOffsetAnim.value.x.roundToInt()
                                                val dropY = dragOffsetAnim.value.y.roundToInt()

                                                coroutineScope.launch {
                                                    launch {
                                                        cardTiltAnim.animateTo(0f, tween(80))
                                                    }

                                                    val committingTab = draggedTab ?: return@launch
                                                    val sourceWv = activeWebViews.remove(committingTab.id)
                                                    if (sourceWv != null) {
                                                        (sourceWv.parent as? ViewGroup)?.removeView(sourceWv)
                                                        val themedServiceContext = ContextThemeWrapper(applicationContext, R.style.Theme_FloatBro)
                                                        sourceWv.updateBaseContext(themedServiceContext)
                                                        TabOverlayStateBridge.putWebView(committingTab.id, sourceWv)
                                                    }

                                                    val stateBundle = Bundle()
                                                    try {
                                                        sourceWv?.saveState(stateBundle)
                                                        TabOverlayStateBridge.putState(committingTab.id, stateBundle, committingTab.url, committingTab.title)
                                                    } catch (e: Exception) {
                                                        Timber.w(e, "Failed to saveState for tab ${committingTab.id}")
                                                    }

                                                    if (snapshotBitmap != null && !snapshotBitmap!!.isRecycled) {
                                                        TabOverlayStateBridge.putSnapshot(committingTab.id, snapshotBitmap!!)
                                                    }

                                                    // Immediately remove from tabs & switch activeTabId to prevent Compose recomposition from stealing or recreating the tab
                                                    val remainingTab = tabs.lastOrNull { it.id != committingTab.id }
                                                    tabs.removeAll { it.id == committingTab.id }
                                                    if (activeTabId == committingTab.id) {
                                                        activeTabId = remainingTab?.id ?: "home"
                                                    }

                                                    startOverlayService(
                                                        FloatBrowserService.ACTION_SHOW,
                                                        committingTab.url,
                                                        dropX,
                                                        dropY,
                                                        committingTab.id,
                                                    )

                                                    // Wait for target floating window's first visible frame commit with 450ms safety timeout
                                                    withTimeoutOrNull(450L) {
                                                        suspendCancellableCoroutine<Unit> { cont ->
                                                            TabOverlayStateBridge.registerCommitListener(committingTab.id) {
                                                                if (cont.isActive) cont.resume(Unit)
                                                            }
                                                        }
                                                    }

                                                    // Smooth quick fade out handover right at drop location
                                                    cardAlphaAnim.animateTo(0f, tween(100))

                                                    snapshotBitmap = null
                                                    isCapturingSnapshot = false
                                                    draggedTab = null
                                                    isCommittingOrCancelling = false
                                                }
                                            } else {
                                                // 4B. 手势释放取消：未达阈值，物理弹簧动画回弹复位
                                                if (totalDragY >= tearThresholdPx && isDetached && !hasPermission) {
                                                    showPermissionRationaleDialog = true
                                                }
                                                coroutineScope.launch {
                                                    launch {
                                                        cardTiltAnim.animateTo(0f, spring(Spring.DampingRatioNoBouncy))
                                                    }
                                                    launch {
                                                        cardScaleAnim.animateTo(1.0f, spring(Spring.DampingRatioLowBouncy))
                                                    }
                                                    launch {
                                                        cardCornerRadiusAnim.animateTo(10f, spring(Spring.DampingRatioLowBouncy))
                                                    }
                                                    launch {
                                                        cardWidthAnim.animateTo(
                                                            originSize.width.toFloat(),
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessMediumLow,
                                                            ),
                                                        )
                                                    }
                                                    launch {
                                                        cardHeightAnim.animateTo(
                                                            originSize.height.toFloat(),
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessMediumLow,
                                                            ),
                                                        )
                                                    }
                                                    launch {
                                                        dragOffsetAnim.animateTo(
                                                            originOffset,
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessMediumLow,
                                                            ),
                                                        )
                                                    }.join()

                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                                                    TabOverlayStateBridge.clear(draggedTab?.id ?: "")
                                                    snapshotBitmap = null
                                                    isCapturingSnapshot = false
                                                    draggedTab = null
                                                    isCommittingOrCancelling = false
                                                }
                                            }
                                        }
                                    },
                                    onTabDragCancel = {
                                        if (draggedTab != null && !isCommittingOrCancelling) {
                                            isCommittingOrCancelling = true
                                            coroutineScope.launch {
                                                launch {
                                                    cardTiltAnim.animateTo(0f, spring(Spring.DampingRatioNoBouncy))
                                                }
                                                launch {
                                                    cardScaleAnim.animateTo(1.0f, spring(Spring.DampingRatioLowBouncy))
                                                }
                                                launch {
                                                    cardCornerRadiusAnim.animateTo(10f, spring(Spring.DampingRatioLowBouncy))
                                                }
                                                launch {
                                                    cardWidthAnim.animateTo(
                                                        originSize.width.toFloat(),
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                }
                                                launch {
                                                    cardHeightAnim.animateTo(
                                                        originSize.height.toFloat(),
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                }
                                                launch {
                                                    dragOffsetAnim.animateTo(
                                                        originOffset,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                }.join()

                                                TabOverlayStateBridge.clear(draggedTab?.id ?: "")
                                                snapshotBitmap = null
                                                isCapturingSnapshot = false
                                                draggedTab = null
                                                isCommittingOrCancelling = false
                                            }
                                        }
                                    },
                                )
                            }

                            // 2. Main Viewport Content
                            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                when (activeTabId) {
                                    "settings" -> {
                                        SettingsScreen(
                                            openMode = openMode,
                                            onOpenModeChange = { mode ->
                                                openMode = mode
                                                savePreferences()
                                            },
                                            hasOverlayPermission = hasOverlayPermission,
                                            onRequestOverlayPermission = { requestOverlayPermission() },
                                            onClearData = { cache, hist, cookies -> clearBrowsingData(cache, hist, cookies) },
                                            onBack = { activeTabId = previousTabId },
                                            onOpenUrl = { url, title -> handleOpenUrl(url, title) },
                                            activeWebViews = activeWebViews,
                                        )
                                    }
                                    "home" -> {
                                        BrowserHomeView(
                                            onOpenUrl = { url, title ->
                                                handleOpenUrl(url, title)
                                            },
                                            onPopoutFloating = { url, _ ->
                                                startOverlayService(FloatBrowserService.ACTION_SHOW, url)
                                            },
                                        )
                                    }
                                    else -> {
                                        val currentTab = tabs.find { it.id == activeTabId }
                                        if (currentTab != null) {
                                            if (currentTab.url == "about:home" || currentTab.url == "about:blank" || currentTab.url.isEmpty()) {
                                                BrowserHomeView(
                                                    onOpenUrl = { url, title ->
                                                        val resolvedUrl = resolveBrowserUrl(this@LauncherActivity, url)
                                                        val idx = tabs.indexOfFirst { it.id == currentTab.id }
                                                        if (idx >= 0) {
                                                            tabs[idx] = tabs[idx].copy(url = resolvedUrl, title = title.ifBlank { "正在加载..." })
                                                        }
                                                    },
                                                    onPopoutFloating = { url, _ ->
                                                        startOverlayService(FloatBrowserService.ACTION_SHOW, url)
                                                    },
                                                )
                                            } else {
                                                key(currentTab.id) {
                                                    val webView =
                                                        activeWebViews.getOrPut(currentTab.id) {
                                                            val wrapper = android.content.MutableContextWrapper(this@LauncherActivity)
                                                            EBWebView(wrapper).apply {
                                                                layoutParams =
                                                                    ViewGroup.LayoutParams(
                                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                                    )
                                                                val bp = BrowserPreferences.getInstance(this@LauncherActivity)
                                                                bp.resolveUserAgent(settings.userAgentString)?.let { ua ->
                                                                    settings.userAgentString = ua
                                                                }
                                                                val restored = TabOverlayStateBridge.takeState(currentTab.id)
                                                                if (restored != null) {
                                                                    Timber.d("Restoring state from TabOverlayStateBridge for tab ${currentTab.id}")
                                                                    restoreState(restored)
                                                                } else {
                                                                    loadUrlSafely(currentTab.url)
                                                                }
                                                            }
                                                        }
                                                    InAppWebViewPane(
                                                        tab = currentTab,
                                                        webView = webView,
                                                        onTearOff = {
                                                            val sourceWv = activeWebViews.remove(currentTab.id)
                                                            if (sourceWv != null) {
                                                                (sourceWv.parent as? ViewGroup)?.removeView(sourceWv)
                                                                val themedServiceContext = ContextThemeWrapper(applicationContext, R.style.Theme_FloatBro)
                                                                sourceWv.updateBaseContext(themedServiceContext)
                                                                TabOverlayStateBridge.putWebView(currentTab.id, sourceWv)
                                                            }
                                                            val stateBundle = Bundle()
                                                            try {
                                                                sourceWv?.saveState(stateBundle)
                                                                TabOverlayStateBridge.putState(currentTab.id, stateBundle, currentTab.url, currentTab.title)
                                                            } catch (e: Exception) {
                                                                Timber.w(e, "Failed to saveState on tear off")
                                                            }

                                                            val remainingTab = tabs.lastOrNull { it.id != currentTab.id }
                                                            tabs.removeAll { it.id == currentTab.id }
                                                            if (activeTabId == currentTab.id) {
                                                                activeTabId = remainingTab?.id ?: "home"
                                                            }
                                                            startOverlayService(FloatBrowserService.ACTION_SHOW, currentTab.url, windowId = currentTab.id)
                                                        },
                                                        onUrlChanged = { newUrl ->
                                                            BrowsingHistoryManager.getInstance(this@LauncherActivity).recordVisit(newUrl, currentTab.title)
                                                            val idx = tabs.indexOfFirst { it.id == currentTab.id }
                                                            if (idx >= 0) {
                                                                tabs[idx] = tabs[idx].copy(url = newUrl)
                                                            }
                                                        },
                                                        onTitleReceived = { newTitle ->
                                                            BrowsingHistoryManager.getInstance(this@LauncherActivity).recordVisit(currentTab.url, newTitle)
                                                            val idx = tabs.indexOfFirst { it.id == currentTab.id }
                                                            if (idx >= 0) {
                                                                tabs[idx] = tabs[idx].copy(title = newTitle)
                                                            }
                                                        },
                                                        onFaviconReceived = { icon ->
                                                            val idx = tabs.indexOfFirst { it.id == currentTab.id }
                                                            if (idx >= 0) {
                                                                tabs[idx] = tabs[idx].copy(favicon = icon)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Top-level unclipped Drag Overlay Layer
                        val currentDraggedTab = draggedTab
                        if (currentDraggedTab != null) {
                            DraggedTabOverlay(
                                tab = currentDraggedTab,
                                offset = dragOffsetAnim.value,
                                width = with(density) { cardWidthAnim.value.toDp() },
                                height = with(density) { cardHeightAnim.value.toDp() },
                                scale = cardScaleAnim.value,
                                tilt = cardTiltAnim.value,
                                alpha = cardAlphaAnim.value,
                                cornerRadius = cardCornerRadiusAnim.value.dp,
                                isDetached = isDetached,
                                snapshotBitmap = snapshotBitmap,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
        BrowsingHistoryManager.getInstance(this).refreshFromDisk()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == FloatBrowserService.ACTION_SHOW) {
            val url = intent.getStringExtra(FloatBrowserService.EXTRA_URL) ?: intent.dataString ?: "https://m.baidu.com"
            startOverlayService(FloatBrowserService.ACTION_SHOW, url)
            return
        }

        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            handleOpenUrl(intent.data.toString(), "新标签页")
            return
        }

        val extraUrl = intent.getStringExtra(FloatBrowserService.EXTRA_URL)
        if (!extraUrl.isNullOrEmpty()) {
            handleOpenUrl(extraUrl, "新标签页")
            return
        }

        if (intent.action == ACTION_DOCK_TAB) {
            val url = intent.getStringExtra(EXTRA_TAB_URL) ?: return
            val title = intent.getStringExtra(EXTRA_TAB_TITLE) ?: "已收回网页"
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            val newTab =
                if (sessionId != null) {
                    BrowserTab(id = sessionId, title = title, url = url)
                } else {
                    BrowserTab(title = title, url = url)
                }

            if (sessionId != null) {
                val existingWv = TabOverlayStateBridge.takeWebView(sessionId)
                if (existingWv != null) {
                    existingWv.updateBaseContext(this@LauncherActivity)
                    (existingWv.parent as? ViewGroup)?.removeView(existingWv)
                    existingWv.onResume()
                    existingWv.resumeTimers()
                    activeWebViews[newTab.id] = existingWv
                    Timber.d("Reusing live EBWebView from bridge for docked tab ${newTab.id}")
                }
            }

            tabs.add(newTab)
            activeTabId = newTab.id
        }
    }

    private fun handleOpenUrl(
        url: String,
        title: String,
    ) {
        val resolvedUrl = resolveBrowserUrl(this, url)

        if (openMode == OpenMode.FLOATING) {
            startOverlayService(FloatBrowserService.ACTION_SHOW, resolvedUrl)
        } else {
            val newTab = BrowserTab(title = title, url = resolvedUrl)
            tabs.add(newTab)
            activeTabId = newTab.id
        }
    }

    private fun clearBrowsingData(
        clearCache: Boolean = true,
        clearHistory: Boolean = true,
        clearCookies: Boolean = false,
    ) {
        try {
            if (clearCache) {
                WebView(this).apply {
                    clearCache(true)
                    clearFormData()
                }
            }
            if (clearHistory) {
                WebView(this).clearHistory()
                recentClosedTabs.clear()
                BrowsingHistoryManager.getInstance(this).clearHistory()
            }
            if (clearCookies) {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            Toast.makeText(this, "已完成选定数据清理", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.w(e, "Error clearing browsing data")
        }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("floatbro_prefs", Context.MODE_PRIVATE)
        val modeStr = sp.getString("pref_open_mode", OpenMode.TAB.name)
        openMode =
            try {
                OpenMode.valueOf(modeStr ?: OpenMode.TAB.name)
            } catch (_: Exception) {
                OpenMode.TAB
            }
    }

    private fun savePreferences() {
        val sp = getSharedPreferences("floatbro_prefs", Context.MODE_PRIVATE)
        sp.edit().putString("pref_open_mode", openMode.name).apply()
    }

    private fun checkPermission() {
        hasOverlayPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            startActivity(intent)
        } else {
            Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlayService(
        action: String,
        url: String? = null,
        initialX: Int? = null,
        initialY: Int? = null,
        windowId: String? = null,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionRationaleDialog = true
            return
        }

        val intent =
            Intent(this, FloatBrowserService::class.java).apply {
                this.action = action
                if (url != null) {
                    putExtra(FloatBrowserService.EXTRA_URL, url)
                }
                if (initialX != null) {
                    putExtra(FloatBrowserService.EXTRA_INITIAL_X, initialX)
                }
                if (initialY != null) {
                    putExtra(FloatBrowserService.EXTRA_INITIAL_Y, initialY)
                }
                if (windowId != null) {
                    putExtra(FloatBrowserService.EXTRA_WINDOW_ID, windowId)
                }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun AppTabStrip(
    tabs: List<BrowserTab>,
    activeTabId: String,
    draggingTabId: String? = null,
    rootLayoutCoordinates: LayoutCoordinates? = null,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenSettings: () -> Unit,
    onTabDragStart: (BrowserTab, Offset, IntSize) -> Unit = { _, _, _ -> },
    onTabDrag: (Offset) -> Unit = {},
    onTabDragEnd: () -> Unit = {},
    onTabDragCancel: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    val currentRootCoords by rememberUpdatedState(rootLayoutCoordinates)
    val currentOnTabDragStart by rememberUpdatedState(onTabDragStart)
    val currentOnTabDrag by rememberUpdatedState(onTabDrag)
    val currentOnTabDragEnd by rememberUpdatedState(onTabDragEnd)
    val currentOnTabDragCancel by rememberUpdatedState(onTabDragCancel)

    // Smooth auto-scroll when active tab changes or new tab is added
    LaunchedEffect(activeTabId, tabs.size) {
        if (activeTabId != "home" && activeTabId != "settings") {
            val idx = tabs.indexOfFirst { it.id == activeTabId }
            if (idx >= 0) {
                scrollState.animateScrollTo((idx * 260).coerceAtMost(scrollState.maxValue))
            }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FloatBroUiTokens.ToolbarButtonTouchTarget)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Horizontally scrolling tabs container
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Home Tab
            val isHomeActive = activeTabId == "home"
            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                color = if (isHomeActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier =
                    Modifier
                        .height(36.dp)
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .clickable { onSelectTab("home") },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "主页",
                        modifier = Modifier.size(15.dp),
                        tint = if (isHomeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "主页",
                        fontSize = 12.sp,
                        fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHomeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Web Tabs (Supports clean rounded ripple and downward drag-to-tear-off)
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                val isBeingDragged = tab.id == draggingTabId
                var tabCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    color =
                        when {
                            isBeingDragged -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                            isActive -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    border =
                        if (isBeingDragged) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .height(36.dp)
                            .padding(end = 4.dp)
                            .onGloballyPositioned { tabCoordinates = it }
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .pointerInput(tab.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isDragging = false
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    val touchSlop = viewConfiguration.touchSlop

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            if (isDragging) {
                                                currentOnTabDragEnd()
                                            }
                                            break
                                        }

                                        val dragAmount = change.positionChange()
                                        if (!isDragging) {
                                            totalDx += dragAmount.x
                                            totalDy += dragAmount.y
                                            val dist = kotlin.math.sqrt(totalDx * totalDx + totalDy * totalDy)
                                            if (dist > touchSlop) {
                                                // Check if downward tear-off gesture
                                                if (totalDy > 0 && totalDy > 1.3f * kotlin.math.abs(totalDx)) {
                                                    isDragging = true
                                                    change.consume()
                                                    val myCoords = tabCoordinates
                                                    if (myCoords != null) {
                                                        val rootCoords = currentRootCoords
                                                        val origin =
                                                            if (rootCoords != null && rootCoords.isAttached && myCoords.isAttached) {
                                                                rootCoords.localPositionOf(myCoords, Offset.Zero)
                                                            } else {
                                                                myCoords.positionInRoot()
                                                            }
                                                        Timber.d("AppTabStrip onDragStart tab=${tab.title}, origin=$origin, size=${myCoords.size}")
                                                        currentOnTabDragStart(tab, origin, myCoords.size)
                                                        currentOnTabDrag(Offset(totalDx, totalDy))
                                                    }
                                                } else {
                                                    // Dominant horizontal movement or upward - pass to parent horizontalScroll!
                                                    break
                                                }
                                            }
                                        } else {
                                            change.consume()
                                            currentOnTabDrag(dragAmount)
                                        }
                                    }
                                }
                            }.clickable { onSelectTab(tab.id) },
                ) {
                    Row(
                        modifier =
                            Modifier
                                .padding(horizontal = 10.dp)
                                .then(if (isBeingDragged) Modifier.alpha(0.35f) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Favicon or Web Globe Icon
                        if (tab.favicon != null) {
                            Image(
                                bitmap = tab.favicon!!.asImageBitmap(),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 60.dp, max = 110.dp),
                        )
                        Spacer(modifier = Modifier.width(FloatBroUiTokens.GridSpacingStandard))
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = { onCloseTab(tab.id) },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(FloatBroUiTokens.TabCloseCapsuleSize)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭 ${tab.title} 标签页",
                                    modifier = Modifier.size(FloatBroUiTokens.TabCloseIconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // New Tab Button aligned to 36dp baseline
            Box(
                modifier =
                    Modifier
                        .height(36.dp)
                        .width(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onNewTab,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建标签页",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Settings Button pinned at top-right aligned to 36dp baseline
        Box(
            modifier =
                Modifier
                    .height(36.dp)
                    .width(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Top-level unclipped Drag Overlay rendering the lifted tab or glass preview card.
 */
@Composable
fun DraggedTabOverlay(
    tab: BrowserTab,
    offset: Offset,
    width: Dp,
    height: Dp,
    scale: Float,
    tilt: Float,
    alpha: Float,
    cornerRadius: Dp,
    isDetached: Boolean,
    snapshotBitmap: Bitmap? = null,
) {
    Surface(
        modifier =
            Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(width = width, height = height)
                .scale(scale)
                .rotate(tilt)
                .alpha(alpha)
                .shadow(
                    elevation = if (isDetached) 16.dp else 8.dp,
                    shape = RoundedCornerShape(cornerRadius),
                ),
        shape = RoundedCornerShape(cornerRadius),
        color =
            if (isDetached) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            BorderStroke(
                1.dp,
                if (isDetached) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        if (!isDetached) {
            // 标签拖起初始态（胶囊胶带样式）
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.favicon != null) {
                    Image(
                        bitmap = tab.favicon!!.asImageBitmap(),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.title,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 标签撕脱完成态（毛玻璃悬浮预览卡片）
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar (44dp aligned to standard)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tab.favicon != null) {
                        Image(
                            bitmap = tab.favicon!!.asImageBitmap(),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "悬浮窗",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Translucent Body Preview with live hardware snapshot
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (snapshotBitmap != null && !snapshotBitmap.isRecycled) {
                        Image(
                            bitmap = snapshotBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tab.url.removePrefix("https://").removePrefix("http://"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    openMode: OpenMode,
    onOpenModeChange: (OpenMode) -> Unit,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onClearData: (clearCache: Boolean, clearHistory: Boolean, clearCookies: Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenUrl: (String, String) -> Unit = { _, _ -> },
    activeWebViews: Map<String, EBWebView> = emptyMap(),
) {
    val context = LocalContext.current
    val browserPrefs = remember { BrowserPreferences.getInstance(context) }
    var userAgentMode by remember { mutableStateOf(browserPrefs.userAgentMode) }
    var searchEngine by remember { mutableStateOf(browserPrefs.searchEngine) }
    var showFullHistoryDialog by remember { mutableStateOf(false) }
    var showUpdateCenterDialog by remember { mutableStateOf(false) }
    var showDiagnosticLogsDialog by remember { mutableStateOf(false) }

    var showClearDialog by remember { mutableStateOf(false) }
    var showGesturesDialog by remember { mutableStateOf(false) }
    var clearCacheSelected by remember { mutableStateOf(true) }
    var clearHistorySelected by remember { mutableStateOf(true) }
    var clearCookiesSelected by remember { mutableStateOf(false) }

    var isAdBlockEnabled by remember { mutableStateOf(browserPrefs.isAdBlockEnabled) }
    var showAdBlockRulesDialog by remember { mutableStateOf(false) }

    if (showGesturesDialog) {
        AlertDialog(
            onDismissRequest = { showGesturesDialog = false },
            title = {
                Text(
                    text = "手势与多窗口操作指南",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Slate900,
                )
            },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text("1. 下拉撕裂浮窗", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        Text("在顶栏标签按住任意网页垂直向下拉动，平滑脱离为独立桌面悬浮窗。", fontSize = 12.sp, color = Slate600, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Text("2. 顶边上推收回", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        Text("按住悬浮窗向上拖动触碰屏幕顶边，或点击顶栏「收回」图标，一键收回至应用内标签。", fontSize = 12.sp, color = Slate600, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Text("3. 底边下拉关闭", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        Text("拖动悬浮窗或边缘气泡至屏幕底部中央的关闭靶心，即可快速销毁会话。", fontSize = 12.sp, color = Slate600, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Text("4. 左右外推折叠气泡", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        Text("将悬浮窗向屏幕左侧或右侧边缘快速抛掷，将自动折叠为贴边气泡。", fontSize = 12.sp, color = Slate600, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Text("5. 右下角自由缩放", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        Text("按住悬浮窗右下角的抓握角标自由拖动，即可调整窗口宽高尺寸。", fontSize = 12.sp, color = Slate600, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGesturesDialog = false }) {
                    Text("知道了", color = BrandBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "清理缓存与网站数据",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Slate900,
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "选择需要清理的数据类型：",
                        fontSize = 13.sp,
                        color = Slate600,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { clearCacheSelected = !clearCacheSelected }
                                .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = clearCacheSelected,
                            onCheckedChange = { clearCacheSelected = it },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("缓存文件与临时存储", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Slate900)
                            Text("释放设备临时磁盘占用空间", fontSize = 11.5.sp, color = Slate500)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { clearHistorySelected = !clearHistorySelected }
                                .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = clearHistorySelected,
                            onCheckedChange = { clearHistorySelected = it },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("浏览历史记录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Slate900)
                            Text("清除已访问过的网页历史记录", fontSize = 11.5.sp, color = Slate500)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { clearCookiesSelected = !clearCookiesSelected }
                                .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = clearCookiesSelected,
                            onCheckedChange = { clearCookiesSelected = it },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Cookies 与网站数据", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Slate900)
                            Text("清理后可能需要重新登录已保存的网站", fontSize = 11.5.sp, color = Slate500)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearData(clearCacheSelected, clearHistorySelected, clearCookiesSelected)
                    },
                ) {
                    Text("确认清理", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = Slate600)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
        )
    }

    if (showAdBlockRulesDialog) {
        AdBlockRulesDialog(onDismiss = { showAdBlockRulesDialog = false })
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Slate50),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
        ) {
            // Standard Secondary Page Top Bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.White)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Slate900,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                )
            }

            HorizontalDivider(color = Slate200)

            // Settings Preferences Grouped Inset Cards
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                // Group 1: 浏览与多窗口
                Text(
                    text = "浏览与多窗口",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "默认打开网页方式",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = Slate900,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilterChip(
                                selected = openMode == OpenMode.TAB,
                                onClick = { onOpenModeChange(OpenMode.TAB) },
                                label = { Text("应用内标签") },
                            )
                            FilterChip(
                                selected = openMode == OpenMode.FLOATING,
                                onClick = { onOpenModeChange(OpenMode.FLOATING) },
                                label = { Text("独立悬浮窗") },
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Slate100,
                        )

                        // User-Agent (网页标识符)
                        Text(
                            text = "网页标识符 (User-Agent)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = Slate900,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UserAgentMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = userAgentMode == mode,
                                    onClick = {
                                        userAgentMode = mode
                                        browserPrefs.userAgentMode = mode
                                        activeWebViews.values.forEach { wv ->
                                            browserPrefs.resolveUserAgent(wv.settings.userAgentString)?.let { ua ->
                                                wv.settings.userAgentString = ua
                                            }
                                        }
                                    },
                                    label = { Text(mode.label) },
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Slate100,
                        )

                        // 默认搜索引擎
                        Text(
                            text = "默认搜索引擎",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = Slate900,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchEngine.entries.forEach { engine ->
                                FilterChip(
                                    selected = searchEngine == engine,
                                    onClick = {
                                        searchEngine = engine
                                        browserPrefs.searchEngine = engine
                                    },
                                    label = { Text(engine.label) },
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Slate100,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "系统悬浮窗权限",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            if (!hasOverlayPermission) {
                                Button(onClick = onRequestOverlayPermission) {
                                    Text("去授权", fontSize = 12.sp)
                                }
                            } else {
                                Text(
                                    text = "已授权",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BrandEmerald,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Slate100,
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showGesturesDialog = true }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "手势与多窗口操作指南",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "查看指南",
                                tint = Slate600,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Group 2: 广告拦截
                Text(
                    text = "广告拦截",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                ) {
                    Column {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "广告拦截",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            Switch(
                                checked = isAdBlockEnabled,
                                onCheckedChange = {
                                    isAdBlockEnabled = it
                                    browserPrefs.isAdBlockEnabled = it
                                    io.github.edsuns.adfilter.AdFilter
                                        .get(context)
                                        .setEnabled(it)
                                },
                            )
                        }

                        if (isAdBlockEnabled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Slate100,
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { showAdBlockRulesDialog = true }
                                        .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "规则管理",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate900,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "规则管理",
                                    tint = Slate400,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Group 3: 数据与隐私
                Text(
                    text = "数据与隐私",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                ) {
                    Column {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showFullHistoryDialog = true }
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "浏览历史记录管理",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "查看",
                                tint = Slate600,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        HorizontalDivider(color = Slate100)

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showClearDialog = true }
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "清理缓存与网站数据",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清除",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        HorizontalDivider(color = Slate100)

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showDiagnosticLogsDialog = true }
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "本地诊断与日志",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "查看",
                                tint = Slate600,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Group 4: 关于应用
                Text(
                    text = "关于",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bubble_logo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "FloatBro",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Slate900,
                                )
                                Text(
                                    text = "版本 ${BuildConfig.VERSION_NAME} · GPL-3.0 开源许可",
                                    fontSize = 12.sp,
                                    color = Slate500,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate100)

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showUpdateCenterDialog = true }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "检查新版本",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900,
                            )
                            Button(
                                onClick = { showUpdateCenterDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("检查更新", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullHistoryDialog) {
        FullHistoryManagementDialog(
            onDismiss = { showFullHistoryDialog = false },
            onOpenUrl = { url, title ->
                showFullHistoryDialog = false
                onBack()
                onOpenUrl(url, title)
            },
        )
    }

    if (showUpdateCenterDialog) {
        AppUpdateCenterDialog(
            onDismiss = { showUpdateCenterDialog = false },
        )
    }

    if (showDiagnosticLogsDialog) {
        DiagnosticLogsDialog(
            onDismiss = { showDiagnosticLogsDialog = false },
        )
    }
}

@Composable
fun FullHistoryManagementDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val historyManager = remember { BrowsingHistoryManager.getInstance(context) }
    val fullList by historyManager.historyFlow.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredList =
        remember(searchQuery, fullList) {
            if (searchQuery.isBlank()) {
                fullList
            } else {
                fullList.filter {
                    it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "浏览历史记录 (${fullList.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                )
                if (fullList.isNotEmpty()) {
                    Text(
                        text = "清空全部",
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    historyManager.clearHistory()
                                }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索历史记录...", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (filteredList.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无历史记录", fontSize = 13.sp, color = Slate500)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            val brandRes =
                                when {
                                    item.url.contains("baidu.com") -> R.drawable.ic_brand_baidu
                                    item.url.contains("bing.com") -> R.drawable.ic_brand_bing
                                    item.url.contains("zhihu.com") -> R.drawable.ic_brand_zhihu
                                    item.url.contains("bilibili.com") -> R.drawable.ic_brand_bilibili
                                    else -> null
                                }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenUrl(item.url, item.title) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (brandRes != null) {
                                    Image(
                                        painter = painterResource(id = brandRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)),
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = BrandBlue.copy(alpha = 0.12f),
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val initial =
                                                try {
                                                    java.net
                                                        .URI(item.url)
                                                        .host
                                                        ?.removePrefix("www.")
                                                        ?.firstOrNull()
                                                        ?.uppercaseChar()
                                                        ?.toString() ?: "W"
                                                } catch (_: Exception) {
                                                    "W"
                                                }
                                            Text(initial, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Slate900,
                                    )
                                    Text(
                                        text = item.url,
                                        fontSize = 10.5.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Slate500,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        historyManager.removeHistory(item.id)
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = Slate400,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = BrandBlue, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun AppUpdateCenterDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val updateManager = remember { AppUpdateManager(context) }
    var candidates by remember { mutableStateOf<UpdateCandidates?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var activeDownloadId by remember { mutableStateOf<Long?>(null) }
    var activeRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        updateManager.fetchReleasesAsync(
            onSuccess = {
                candidates = it
                isLoading = false
            },
            onError = { err ->
                errorMessage = err
                isLoading = false
            },
        )
    }

    LaunchedEffect(activeDownloadId) {
        val downloadId = activeDownloadId ?: return@LaunchedEffect
        while (activeDownloadId != null) {
            val progress = updateManager.queryProgress(downloadId)
            downloadProgress = progress
            if (progress != null && progress.status == DownloadManager.STATUS_SUCCESSFUL) {
                break
            }
            if (progress != null && progress.status == DownloadManager.STATUS_FAILED) {
                break
            }
            delay(1000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "检查更新",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandBlue.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "v${updateManager.currentVersion}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandBlue, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在连接 GitHub 获取更新通道...", fontSize = 12.sp, color = Slate500)
                        }
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(errorMessage!!, fontSize = 13.sp, color = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                updateManager.fetchReleasesAsync(
                                    onSuccess = {
                                        candidates = it
                                        isLoading = false
                                    },
                                    onError = {
                                        errorMessage = it
                                        isLoading = false
                                    },
                                )
                            },
                        ) {
                            Text("重试", fontSize = 12.sp)
                        }
                    }
                } else {
                    val candidateData = candidates
                    if (candidateData != null) {
                        if (candidateData.stable == null && candidateData.prerelease == null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandEmerald.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BrandEmerald,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "当前已是最新版本 (v${candidateData.currentVersion})",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate900,
                                    )
                                }
                            }
                        }

                        UpdateReleaseCard(
                            channelName = "正式版通道",
                            isBeta = false,
                            release = candidateData.stable,
                            currentVersion = candidateData.currentVersion,
                            onDownload = { rel ->
                                activeRelease = rel
                                activeDownloadId = updateManager.enqueueDownload(rel)
                                Toast.makeText(context, "已加入系统下载队列，可在通知栏查看进度", Toast.LENGTH_SHORT).show()
                            },
                        )

                        UpdateReleaseCard(
                            channelName = "开发测试版通道",
                            isBeta = true,
                            release = candidateData.prerelease,
                            currentVersion = candidateData.currentVersion,
                            onDownload = { rel ->
                                activeRelease = rel
                                activeDownloadId = updateManager.enqueueDownload(rel)
                                Toast.makeText(context, "已加入系统下载队列，可在通知栏查看进度", Toast.LENGTH_SHORT).show()
                            },
                        )

                        if (activeDownloadId != null && activeRelease != null) {
                            val prog = downloadProgress
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Slate100),
                                border = BorderStroke(1.dp, Slate300),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "下载进度：${prog?.percent ?: 0}%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900,
                                        )
                                        TextButton(
                                            onClick = {
                                                activeDownloadId?.let { updateManager.cancelDownload(it) }
                                                activeDownloadId = null
                                                activeRelease = null
                                            },
                                        ) {
                                            Text("取消", fontSize = 11.5.sp, color = Color(0xFFEF4444))
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { (prog?.percent ?: 0) / 100f },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = BrandBlue,
                                        trackColor = Slate200,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (prog != null && prog.status == DownloadManager.STATUS_SUCCESSFUL) {
                                        val apkFile = updateManager.getDownloadedFile(activeRelease!!)
                                        Button(
                                            onClick = {
                                                if (activity != null && !updateManager.canInstallPackages()) {
                                                    updateManager.openInstallPermissionSettings(activity)
                                                } else {
                                                    updateManager.installApk(apkFile)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("下载完成，立即安装", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Slate600)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun UpdateReleaseCard(
    channelName: String,
    isBeta: Boolean,
    release: ReleaseInfo?,
    currentVersion: String,
    onDownload: (ReleaseInfo) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardBg =
        if (isBeta) {
            if (isDark) Color(0xFF451A03) else Color(0xFFFFFBEB)
        } else {
            if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
        }
    val cardBorder =
        if (isBeta) {
            if (isDark) Color(0xFF78350F) else Color(0xFFFDE68A)
        } else {
            if (isDark) Color(0xFF334155) else Slate200
        }
    val channelTextColor =
        if (isBeta) {
            if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
        } else {
            if (isDark) Color(0xFFF8FAFC) else Slate900
        }
    val tagTextColor = if (isDark) Color(0xFFF8FAFC) else Slate800

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = channelName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = channelTextColor,
                )
                if (release != null) {
                    Text(
                        text = release.publishedAt,
                        fontSize = 11.sp,
                        color = Slate500,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (release == null) {
                Text("暂无可升级版本", fontSize = 12.sp, color = Slate500)
            } else {
                Text(
                    text = release.tagName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tagTextColor,
                )
                if (release.body.isNotBlank()) {
                    MarkdownContent(
                        markdown = release.body,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                val isNewer = AppUpdateManager.isNewerVersion(release.tagName, currentVersion)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isNewer) {
                        Button(
                            onClick = { onDownload(release) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(if (isBeta) "下载测试版" else "下载正式版", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onDownload(release) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("重新下载安装包", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserHomeView(
    onOpenUrl: (String, String) -> Unit,
    onPopoutFloating: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val historyManager = remember { BrowsingHistoryManager.getInstance(context) }
    val allHistory by historyManager.historyFlow.collectAsState()
    val historyList by remember { derivedStateOf { allHistory.take(30) } }

    val prefs = remember { BrowserPreferences.getInstance(context) }

    // Clipboard URL detection
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var clipboardTimestamp by remember { mutableStateOf(0L) }
    var isClipboardDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text =
                    clip
                        .getItemAt(0)
                        ?.text
                        ?.toString()
                        ?.trim()
                val timestamp = clip.description?.timestamp ?: 0L
                if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://"))) {
                    val isSuppressed =
                        (text == prefs.lastDismissedClipboardUrl) &&
                            (timestamp != 0L && timestamp <= prefs.lastDismissedClipboardTimestamp)
                    if (!isSuppressed) {
                        clipboardUrl = text
                        clipboardTimestamp = timestamp
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600
    val homeLogoSize = if (isLargeScreen) 72.dp else 56.dp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Clean App Logo Header (Responsive: 56dp phone / 72dp tablet)
            Box(
                modifier =
                    Modifier
                        .padding(top = if (isLargeScreen) 36.dp else 24.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bubble_logo),
                    contentDescription = "FloatBro",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(homeLogoSize),
                )
            }

            // 2. Clean Search Bar Capsule (Thumb Zone)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索或输入网址", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) {
                            onOpenUrl(searchQuery, searchQuery)
                            searchQuery = ""
                        }
                    }),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Speed Dials (Bing, Bilibili, Zhihu, Baidu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QuickDialItem("必应", R.drawable.ic_brand_bing) {
                    onOpenUrl("https://cn.bing.com", "必应搜索")
                }
                QuickDialItem("哔哩哔哩", R.drawable.ic_brand_bilibili) {
                    onOpenUrl("https://m.bilibili.com", "哔哩哔哩")
                }
                QuickDialItem("知乎", R.drawable.ic_brand_zhihu) {
                    onOpenUrl("https://www.zhihu.com", "知乎")
                }
                QuickDialItem("百度", R.drawable.ic_brand_baidu) {
                    onOpenUrl("https://www.baidu.com", "百度")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Card 1: Clipboard URL Detection Banner (智能剪贴板链接探测)
            if (clipboardUrl != null && !isClipboardDismissed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    Text(
                                        text = "剪贴板",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = "检测到网页链接",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardUrl?.let { url ->
                                        prefs.lastDismissedClipboardUrl = url
                                        prefs.lastDismissedClipboardTimestamp = clipboardTimestamp
                                    }
                                    isClipboardDismissed = true
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "忽略",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Text(
                            text = clipboardUrl!!,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    val targetUrl = clipboardUrl!!
                                    prefs.lastDismissedClipboardUrl = targetUrl
                                    prefs.lastDismissedClipboardTimestamp = clipboardTimestamp
                                    isClipboardDismissed = true
                                    onPopoutFloating(targetUrl, "网页")
                                },
                            ) {
                                Text("悬浮打开", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val targetUrl = clipboardUrl!!
                                    prefs.lastDismissedClipboardUrl = targetUrl
                                    prefs.lastDismissedClipboardTimestamp = clipboardTimestamp
                                    isClipboardDismissed = true
                                    onOpenUrl(targetUrl, "网页")
                                },
                            ) {
                                Text("直接打开", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 6. Recent Browsing History from persistent storage
            if (historyList.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "最近浏览历史 (${historyList.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "清除全部",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            BrowsingHistoryManager.getInstance(context).clearHistory()
                                        }.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        historyList.forEach { item ->
                            val brandRes =
                                when {
                                    item.url.contains("baidu.com") -> R.drawable.ic_brand_baidu
                                    item.url.contains("bing.com") -> R.drawable.ic_brand_bing
                                    item.url.contains("zhihu.com") -> R.drawable.ic_brand_zhihu
                                    item.url.contains("bilibili.com") -> R.drawable.ic_brand_bilibili
                                    else -> null
                                }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onOpenUrl(item.url, item.title)
                                        }.padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (brandRes != null) {
                                    Image(
                                        painter = painterResource(id = brandRes),
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val hostInitial =
                                                try {
                                                    java.net
                                                        .URI(item.url)
                                                        .host
                                                        ?.removePrefix("www.")
                                                        ?.firstOrNull()
                                                        ?.uppercaseChar()
                                                        ?.toString() ?: "W"
                                                } catch (_: Exception) {
                                                    "W"
                                                }
                                            Text(
                                                text = hostInitial,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = item.url,
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        BrowsingHistoryManager.getInstance(context).removeHistory(item.id)
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除记录",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun QuickDialItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { onClick() }
                .padding(4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 2.dp,
            modifier = Modifier.size(50.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun InAppWebViewPane(
    tab: BrowserTab,
    webView: EBWebView,
    onTearOff: () -> Unit,
    onUrlChanged: (String) -> Unit = {},
    onTitleReceived: (String) -> Unit,
    onFaviconReceived: (Bitmap) -> Unit,
) {
    var loadingProgress by remember { mutableStateOf(0) }
    var isEditingUrl by remember { mutableStateOf(false) }
    var editingUrlText by remember(tab.url) { mutableStateOf(tab.url) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var zapperManager by remember { mutableStateOf<io.github.floatbro.overlay.ElementZapperManager?>(null) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Level 0: Highest priority BackHandler for Element Zapper mode
    BackHandler(enabled = zapperManager?.isZapperActive == true) {
        zapperManager?.exitZapperMode()
    }

    // Level 1: Priority BackHandler for URL editing mode
    BackHandler(enabled = isEditingUrl) {
        isEditingUrl = false
        keyboardController?.hide()
    }

    // Level 3: BackHandler for WebView web history navigation
    BackHandler(enabled = !isEditingUrl && canGoBack) {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mini Toolbar matching FloatingWindowView: [Back] [Forward] [Capsule 34dp] [Refresh] [Popout]
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (canGoBack) webView.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_back),
                    contentDescription = "后退",
                    modifier = Modifier.size(18.dp),
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = { if (canGoForward) webView.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_forward),
                    contentDescription = "前进",
                    modifier = Modifier.size(18.dp),
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            if (isEditingUrl) {
                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .padding(horizontal = 2.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = editingUrlText,
                            onValueChange = { editingUrlText = it },
                            singleLine = true,
                            textStyle =
                                TextStyle(
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions =
                                KeyboardActions(onGo = {
                                    if (editingUrlText.isNotBlank()) {
                                        val resolved = LauncherActivity.resolveBrowserUrl(context, editingUrlText)
                                        onUrlChanged(resolved)
                                        webView.loadUrlSafely(resolved)
                                    }
                                    isEditingUrl = false
                                    keyboardController?.hide()
                                }),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                        )
                        IconButton(
                            onClick = {
                                isEditingUrl = false
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消编辑",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .padding(horizontal = 2.dp)
                            .clickable {
                                editingUrlText = tab.url
                                isEditingUrl = true
                            },
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "SSL",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.url,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(2.dp))
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_vert),
                        contentDescription = "更多选项",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                val currentWvUrl = webView.url ?: tab.url
                val host =
                    remember(currentWvUrl) {
                        try {
                            Uri.parse(currentWvUrl).host?.lowercase() ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                    }
                val prefs = remember { BrowserPreferences.getInstance(context) }
                val isGlobalEnabled = prefs.isAdBlockEnabled
                val isSiteWhitelisted = host.isNotEmpty() && prefs.adBlockWhitelist.contains(host)
                val adBlockTitle = AdBlockMenuHelper.resolveMenuTitle(isGlobalEnabled, isSiteWhitelisted)

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier =
                        Modifier
                            .width(110.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                            ),
                ) {
                    // 1. 刷新
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "刷新",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            webView.reload()
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp),
                    )

                    // 2. 复制网址
                    if (currentWvUrl.isNotEmpty() && currentWvUrl != "about:blank") {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "复制网址",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("URL", currentWvUrl))
                                Toast.makeText(context, "网址已复制", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp),
                        )
                    }

                    // 3. 标记广告
                    if (currentWvUrl.startsWith("http")) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "标记广告",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                zapperManager?.enterZapperMode()
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp),
                        )
                    }

                    // 4. 广告拦截: 开 / 关
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = adBlockTitle,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            if (!isGlobalEnabled) {
                                prefs.isAdBlockEnabled = true
                                AdFilter.get(context).setEnabled(true)
                                Toast.makeText(context, "广告拦截已开启", Toast.LENGTH_SHORT).show()
                                webView.reload()
                            } else if (host.isNotEmpty()) {
                                val enabledNow = prefs.toggleAdBlockForHost(host)
                                val msg = if (enabledNow) "已恢复拦截" else "已暂停拦截"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                webView.reload()
                            } else {
                                prefs.isAdBlockEnabled = !prefs.isAdBlockEnabled
                                AdFilter.get(context).setEnabled(prefs.isAdBlockEnabled)
                                val msg = if (prefs.isAdBlockEnabled) "广告拦截已开启" else "广告拦截已暂停"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                webView.reload()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = {
                    zapperManager?.exitZapperMode(quietly = true)
                    onTearOff()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "弹射为独立悬浮窗",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Loading Progress Indicator
        if (loadingProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Web Content reusing cached webView
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            AndroidView(
                factory = { ctx ->
                    val frameLayout =
                        FrameLayout(ctx).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                        }
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    frameLayout.addView(webView)

                    val ezm = ElementZapperManager(ctx, webView, frameLayout)
                    zapperManager = ezm

                    webView.apply {
                        updateThemeBackgroundColor()
                        onProgressChangedListener = { progress ->
                            loadingProgress = progress
                            canGoBack = webView.canGoBack()
                            canGoForward = webView.canGoForward()
                        }
                        onTitleChangedListener = { title -> onTitleReceived(title) }
                        onReceivedIconListener = { icon -> onFaviconReceived(icon) }
                        onPageStartedListener = { startedUrl ->
                            ezm.exitZapperMode(quietly = true)
                            onUrlChanged(startedUrl)
                            canGoBack = webView.canGoBack()
                            canGoForward = webView.canGoForward()
                        }
                        onNavigationBlockedListener = { event ->
                            val msg =
                                if (event.isAutoForwarded) {
                                    "已自动直达真实目标页"
                                } else {
                                    "已拦截伪装跳转外链"
                                }
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    frameLayout
                },
                update = { _ ->
                    webView.onProgressChangedListener = { progress ->
                        loadingProgress = progress
                        canGoBack = webView.canGoBack()
                        canGoForward = webView.canGoForward()
                    }
                    webView.onTitleChangedListener = { title -> onTitleReceived(title) }
                    webView.onReceivedIconListener = { icon -> onFaviconReceived(icon) }
                    webView.onPageStartedListener = { startedUrl ->
                        zapperManager?.exitZapperMode(quietly = true)
                        onUrlChanged(startedUrl)
                        canGoBack = webView.canGoBack()
                        canGoForward = webView.canGoForward()
                    }
                    webView.onNavigationBlockedListener = { event ->
                        val msg =
                            if (event.isAutoForwarded) {
                                "已自动直达真实目标页"
                            } else {
                                "已拦截伪装跳转外链"
                            }
                        Toast.makeText(webView.context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onRelease = { frameLayout ->
                    zapperManager?.exitZapperMode(quietly = true)
                    webView.onNavigationBlockedListener = null
                    frameLayout.removeView(webView)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
