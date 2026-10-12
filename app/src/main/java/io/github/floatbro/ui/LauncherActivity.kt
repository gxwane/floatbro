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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import io.github.floatbro.ui.theme.FloatBroUiTokens
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import timber.log.Timber
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.floatbro.R
import io.github.floatbro.core.webview.EBWebView
import io.github.floatbro.service.FloatBrowserService
import io.github.floatbro.ui.theme.BrandBlue
import io.github.floatbro.ui.theme.BrandEmerald
import io.github.floatbro.ui.theme.FloatBroTheme
import io.github.floatbro.ui.theme.Slate200
import io.github.floatbro.ui.theme.Slate50
import io.github.floatbro.ui.theme.Slate600
import io.github.floatbro.ui.theme.Slate900
import java.util.UUID
import kotlin.math.roundToInt

enum class OpenMode {
    FLOATING, // 默认直接弹射为独立悬浮窗
    TAB       // 默认在主应用内并列标签打开
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    val url: String,
    var favicon: Bitmap? = null
)

class LauncherActivity : ComponentActivity() {

    companion object {
        const val ACTION_DOCK_TAB = "io.github.floatbro.action.DOCK_TAB"
        const val EXTRA_TAB_URL = "extra_tab_url"
        const val EXTRA_TAB_TITLE = "extra_tab_title"

        fun resolveBrowserUrl(input: String): String {
            val trimmed = input.trim()
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.startsWith("about:") -> trimmed
                trimmed.startsWith("www.") -> "https://$trimmed"
                trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
                else -> "https://cn.bing.com/search?q=" + Uri.encode(trimmed)
            }
        }
    }

    private var hasOverlayPermission by mutableStateOf(false)
    private var showPermissionRationaleDialog by mutableStateOf(false)
    private var openMode by mutableStateOf(OpenMode.TAB)
    private val tabs = mutableStateListOf<BrowserTab>()
    private val recentClosedTabs = mutableStateListOf<BrowserTab>()
    private var activeTabId by mutableStateOf("home")
    private var previousTabId by mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure light status bar icons (dark icons on light background) for clear battery/signal visibility
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        loadPreferences()
        checkPermission()
        handleIncomingIntent(intent)

        setContent {
            FloatBroTheme {
                if (showPermissionRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionRationaleDialog = false },
                        title = { Text("开启悬浮窗权限") },
                        text = {
                            Text(
                                "FloatBro 需要悬浮窗权限以在其他应用上方无缝浏览并支持贴边气泡收纳。点击前往开启权限。",
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showPermissionRationaleDialog = false
                                requestOverlayPermission()
                            }) {
                                Text("前往授权")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionRationaleDialog = false }) {
                                Text("暂不开启")
                            }
                        }
                    )
                }

                // BackHandler for dedicated Settings secondary page
                BackHandler(enabled = activeTabId == "settings") {
                    activeTabId = previousTabId
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
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(), // Safe inset padding avoiding status bar and camera hole
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { rootLayoutCoordinates = it }
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
                                        val closed = tabs.find { it.id == tabId }
                                        if (closed != null && closed.url != "about:home" && closed.url != "about:blank" && closed.url.isNotBlank()) {
                                            if (!recentClosedTabs.any { it.url == closed.url }) {
                                                recentClosedTabs.add(0, closed)
                                                if (recentClosedTabs.size > 5) {
                                                    recentClosedTabs.removeAt(recentClosedTabs.lastIndex)
                                                }
                                            }
                                        }
                                        tabs.removeAll { it.id == tabId }
                                        if (activeTabId == tabId) {
                                            activeTabId = "home"
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
                                            draggedTab = tab
                                            originOffset = origin
                                            originSize = size
                                            isDetached = false
                                            hasFiredDetachedHaptic = false
                                            totalDragX = 0f
                                            totalDragY = 0f
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
                                                    // Phase 1: Touch, Lift & Tension (0 - 44dp)
                                                    val progress = (totalDragY / tearThresholdPx).coerceIn(0f, 1f)
                                                    val yResistance = totalDragY * (1f - (totalDragY / maxTearDistancePx).coerceIn(0f, 0.4f))
                                                    val xResistance = totalDragX * 0.6f

                                                    coroutineScope.launch {
                                                        dragOffsetAnim.snapTo(Offset(originOffset.x + xResistance, originOffset.y + yResistance))
                                                        cardCornerRadiusAnim.snapTo(10f + 2f * progress)
                                                        cardScaleAnim.snapTo(1.0f + 0.04f * progress)
                                                    }
                                                } else {
                                                    // Phase 2: Detachment Threshold Crossed!
                                                    isDetached = true
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
                                                // Phase 2: Detached Free 2D Tracking with aerodynamic tilt
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
                                            val isCommitted = totalDragY >= tearThresholdPx && isDetached

                                            if (isCommitted) {
                                                // Phase 3A: Commit & Handover!
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                } else {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                }

                                                val rootSize = rootLayoutCoordinates?.size
                                                val screenW = rootSize?.width?.toFloat() ?: 1080f
                                                val screenH = rootSize?.height?.toFloat() ?: 2160f
                                                val targetW = (screenW * 0.90f).coerceAtLeast(with(density) { 280.dp.toPx() })
                                                val targetH = (screenH * 0.65f).coerceAtLeast(with(density) { 340.dp.toPx() })
                                                val targetX = (screenW - targetW) / 2f
                                                val targetY = (screenH - targetH) / 4f

                                                coroutineScope.launch {
                                                    launch {
                                                        cardTiltAnim.animateTo(0f, tween(120))
                                                    }
                                                    launch {
                                                        dragOffsetAnim.animateTo(
                                                            Offset(targetX, targetY),
                                                            tween(240, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
                                                        )
                                                    }
                                                    launch {
                                                        cardWidthAnim.animateTo(
                                                            targetW,
                                                            tween(240, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
                                                        )
                                                    }
                                                    launch {
                                                        cardHeightAnim.animateTo(
                                                            targetH,
                                                            tween(240, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
                                                        )
                                                    }

                                                    delay(200)
                                                    cardAlphaAnim.animateTo(0f, tween(60))

                                                    val committingTab = draggedTab ?: return@launch
                                                    tabs.removeAll { it.id == committingTab.id }
                                                    if (activeTabId == committingTab.id) {
                                                        activeTabId = "home"
                                                    }
                                                    startOverlayService(
                                                        FloatBrowserService.ACTION_SHOW,
                                                        committingTab.url,
                                                        targetX.roundToInt(),
                                                        targetY.roundToInt(),
                                                        committingTab.id
                                                    )
                                                    Toast.makeText(this@LauncherActivity, "网页已撕裂转化为全局悬浮窗", Toast.LENGTH_SHORT).show()

                                                    draggedTab = null
                                                    isCommittingOrCancelling = false
                                                }
                                            } else {
                                                // Phase 3B: Cancel & Snapback with Physics Spring!
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
                                                                stiffness = Spring.StiffnessMediumLow
                                                            )
                                                        )
                                                    }
                                                    launch {
                                                        cardHeightAnim.animateTo(
                                                            originSize.height.toFloat(),
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessMediumLow
                                                            )
                                                        )
                                                    }
                                                    launch {
                                                        dragOffsetAnim.animateTo(
                                                            originOffset,
                                                            spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessMediumLow
                                                            )
                                                        )
                                                    }.join()

                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

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
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    cardHeightAnim.animateTo(
                                                        originSize.height.toFloat(),
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    dragOffsetAnim.animateTo(
                                                        originOffset,
                                                        spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }.join()

                                                draggedTab = null
                                                isCommittingOrCancelling = false
                                            }
                                        }
                                    }
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
                                            onClearData = { clearBrowsingData() },
                                            onBack = { activeTabId = previousTabId }
                                        )
                                    }
                                    "home" -> {
                                        BrowserHomeView(
                                            hasPermission = hasOverlayPermission,
                                            onRequestPermission = { requestOverlayPermission() },
                                            onOpenUrl = { url, title ->
                                                handleOpenUrl(url, title)
                                            },
                                            recentClosedTabs = recentClosedTabs,
                                            onRestoreTab = { restored ->
                                                if (!tabs.any { it.id == restored.id }) {
                                                    tabs.add(restored)
                                                }
                                                activeTabId = restored.id
                                                recentClosedTabs.removeAll { it.id == restored.id }
                                            },
                                            onPopoutFloating = { url, _ ->
                                                startOverlayService(FloatBrowserService.ACTION_SHOW, url)
                                            }
                                        )
                                    }
                                    else -> {
                                        val currentTab = tabs.find { it.id == activeTabId }
                                        if (currentTab != null) {
                                            if (currentTab.url == "about:home" || currentTab.url == "about:blank" || currentTab.url.isEmpty()) {
                                                BrowserHomeView(
                                                    hasPermission = hasOverlayPermission,
                                                    onRequestPermission = { requestOverlayPermission() },
                                                    onOpenUrl = { url, title ->
                                                        val resolvedUrl = resolveBrowserUrl(url)
                                                        val idx = tabs.indexOfFirst { it.id == currentTab.id }
                                                        if (idx >= 0) {
                                                            tabs[idx] = tabs[idx].copy(url = resolvedUrl, title = title.ifBlank { "正在加载..." })
                                                        }
                                                    },
                                                    recentClosedTabs = recentClosedTabs,
                                                    onRestoreTab = { restored ->
                                                        if (!tabs.any { it.id == restored.id }) {
                                                            tabs.add(restored)
                                                        }
                                                        activeTabId = restored.id
                                                        recentClosedTabs.removeAll { it.id == restored.id }
                                                    },
                                                    onPopoutFloating = { url, _ ->
                                                        startOverlayService(FloatBrowserService.ACTION_SHOW, url)
                                                    }
                                                )
                                            } else {
                                                key(currentTab.id) {
                                                    InAppWebViewPane(
                                                        tab = currentTab,
                                                        onTearOff = {
                                                            tabs.removeAll { it.id == currentTab.id }
                                                            activeTabId = "home"
                                                            startOverlayService(FloatBrowserService.ACTION_SHOW, currentTab.url, windowId = currentTab.id)
                                                            Toast.makeText(this@LauncherActivity, "网页已撕裂转化为全局悬浮窗", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onUrlChanged = { newUrl ->
                                                            val idx = tabs.indexOfFirst { it.id == currentTab.id }
                                                            if (idx >= 0) {
                                                                tabs[idx] = tabs[idx].copy(url = newUrl)
                                                            }
                                                        },
                                                        onTitleReceived = { newTitle ->
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
                                                        }
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
                                isDetached = isDetached
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == ACTION_DOCK_TAB) {
            val url = intent.getStringExtra(EXTRA_TAB_URL) ?: return
            val title = intent.getStringExtra(EXTRA_TAB_TITLE) ?: "已收回网页"
            val newTab = BrowserTab(title = title, url = url)
            tabs.add(newTab)
            activeTabId = newTab.id
            Toast.makeText(this, "网页已吸附收回至标签页", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOpenUrl(url: String, title: String) {
        val resolvedUrl = resolveBrowserUrl(url)

        if (openMode == OpenMode.FLOATING) {
            startOverlayService(FloatBrowserService.ACTION_SHOW, resolvedUrl)
            Toast.makeText(this, "已弹射为独立悬浮网页", Toast.LENGTH_SHORT).show()
        } else {
            val newTab = BrowserTab(title = title, url = resolvedUrl)
            tabs.add(newTab)
            activeTabId = newTab.id
        }
    }

    private fun clearBrowsingData() {
        try {
            WebView(this).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
            }
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            Toast.makeText(this, "所有网页缓存与 Cookie 已彻底清理", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("floatbro_prefs", Context.MODE_PRIVATE)
        val modeStr = sp.getString("pref_open_mode", OpenMode.TAB.name)
        openMode = try {
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
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
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
        windowId: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionRationaleDialog = true
            return
        }

        val intent = Intent(this, FloatBrowserService::class.java).apply {
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
    onTabDragCancel: () -> Unit = {}
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
        modifier = Modifier
            .fillMaxWidth()
            .height(FloatBroUiTokens.ToolbarButtonTouchTarget)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Horizontally scrolling tabs container
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.Bottom
        ) {
            // Home Tab
            val isHomeActive = activeTabId == "home"
            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                color = if (isHomeActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .height(36.dp)
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .clickable { onSelectTab("home") }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "主页",
                        modifier = Modifier.size(15.dp),
                        tint = if (isHomeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "主页",
                        fontSize = 12.sp,
                        fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHomeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = when {
                        isBeingDragged -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                        isActive -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    border = if (isBeingDragged) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    } else null,
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 4.dp)
                        .onGloballyPositioned { tabCoordinates = it }
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .pointerInput(tab.id) {
                            detectDragGestures(
                                onDragStart = {
                                    val myCoords = tabCoordinates
                                    if (myCoords != null) {
                                        val rootCoords = currentRootCoords
                                        val origin = if (rootCoords != null && rootCoords.isAttached && myCoords.isAttached) {
                                            rootCoords.localPositionOf(myCoords, Offset.Zero)
                                        } else {
                                            myCoords.positionInRoot()
                                        }
                                        Timber.d("AppTabStrip onDragStart tab=${tab.title}, origin=$origin, size=${myCoords.size}")
                                        currentOnTabDragStart(tab, origin, myCoords.size)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnTabDrag(dragAmount)
                                },
                                onDragEnd = {
                                    Timber.d("AppTabStrip onDragEnd tab=${tab.title}")
                                    currentOnTabDragEnd()
                                },
                                onDragCancel = {
                                    Timber.d("AppTabStrip onDragCancel tab=${tab.title}")
                                    currentOnTabDragCancel()
                                }
                            )
                        }
                        .clickable { onSelectTab(tab.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .then(if (isBeingDragged) Modifier.alpha(0.35f) else Modifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favicon or Web Globe Icon
                        if (tab.favicon != null) {
                            Image(
                                bitmap = tab.favicon!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                            modifier = Modifier.widthIn(min = 60.dp, max = 110.dp)
                        )
                        Spacer(modifier = Modifier.width(FloatBroUiTokens.GridSpacingStandard))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onCloseTab(tab.id) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(FloatBroUiTokens.TabCloseCapsuleSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭 ${tab.title} 标签页",
                                    modifier = Modifier.size(FloatBroUiTokens.TabCloseIconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // New Tab Button
            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(FloatBroUiTokens.ToolbarButtonTouchTarget)
                    .padding(bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建标签页",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Settings Button pinned at top-right
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(FloatBroUiTokens.ToolbarButtonTouchTarget)
                .padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    isDetached: Boolean
) {
    Surface(
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(width = width, height = height)
            .scale(scale)
            .rotate(tilt)
            .alpha(alpha)
            .shadow(
                elevation = if (isDetached) 16.dp else 8.dp,
                shape = RoundedCornerShape(cornerRadius)
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = if (isDetached) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isDetached) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        if (!isDetached) {
            // Phase 1: Lifted Tab Pill appearance
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.favicon != null) {
                    Image(
                        bitmap = tab.favicon!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
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
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Phase 2 & 3: Floating Window Preview Card (Glassmorphism)
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar (34dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tab.favicon != null) {
                        Image(
                            bitmap = tab.favicon!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "悬浮窗",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Translucent Body Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tab.url.removePrefix("https://").removePrefix("http://"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
    onClearData: () -> Unit,
    onBack: () -> Unit
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "清除所有浏览数据？",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Slate900
                )
            },
            text = {
                Text(
                    text = "此操作将彻底清空当前所有已缓存的网页、Cookies 与本地存储数据，操作不可撤销。",
                    fontSize = 13.sp,
                    color = Slate600,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearData()
                    }
                ) {
                    Text("确认清除", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消", color = Slate600)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        // Standard Secondary Page Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Slate900
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        }

        HorizontalDivider(color = Slate200)

        // Settings Preferences Group List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Group 1: 浏览偏好
            Text(
                text = "浏览偏好",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrandBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "默认打开网页方式",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = Slate900
                    )
                    Text(
                        text = "选择点击常用导航或搜索链接时，默认采用悬浮窗弹出或在应用内标签打开",
                        fontSize = 12.sp,
                        color = Slate600,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = openMode == OpenMode.TAB,
                            onClick = { onOpenModeChange(OpenMode.TAB) },
                            label = { Text("内部标签页") }
                        )
                        FilterChip(
                            selected = openMode == OpenMode.FLOATING,
                            onClick = { onOpenModeChange(OpenMode.FLOATING) },
                            label = { Text("独立悬浮窗") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Group 2: 悬浮窗高级特性
            Text(
                text = "悬浮窗高级特性",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrandBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统悬浮窗权限",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = Slate900
                            )
                            Text(
                                text = if (hasOverlayPermission) "悬浮权限已正常开启" else "尚未授权，点击跳转系统设置授权",
                                fontSize = 12.sp,
                                color = if (hasOverlayPermission) BrandEmerald else Color(0xFFEF4444),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (!hasOverlayPermission) {
                            Button(onClick = onRequestOverlayPermission) {
                                Text("去授权", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Slate200
                    )

                    Text(
                        text = "贴边吸附与手势交互",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = Slate900
                    )
                    Text(
                        text = "支持靠边磁吸停靠、推入屏幕外超过 40% 折叠为悬浮球，以及向下拉至底部收回应用内。",
                        fontSize = 12.sp,
                        color = Slate600,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Group 3: 数据与隐私
            Text(
                text = "数据与隐私",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrandBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearConfirmDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "清除浏览数据与缓存",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = Slate900
                        )
                        Text(
                            text = "清除所有已保存的网页缓存、Cookies 与临时存储",
                            fontSize = 12.sp,
                            color = Slate600,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "清除",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Group 4: 关于应用
            Text(
                text = "关于应用",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrandBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bubble_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "FloatBro 浮窗浏览器",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Text(
                            text = "版本 1.0.0 · 遵循 Tier 3 Clean Craftsmanship 规范",
                            fontSize = 12.sp,
                            color = Slate600,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserHomeView(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenUrl: (String, String) -> Unit,
    recentClosedTabs: List<BrowserTab> = emptyList(),
    onRestoreTab: (BrowserTab) -> Unit = {},
    onPopoutFloating: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Clipboard URL detection
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var isClipboardDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()?.trim()
                if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://"))) {
                    clipboardUrl = text
                }
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Hero Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bubble_logo),
                contentDescription = "FloatBro",
                tint = Color.Unspecified,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "FloatBro 浮窗浏览器",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "全局悬浮 · 联动分屏 · 纯净去广告",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    tint = MaterialTheme.colorScheme.outline
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清空",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchQuery.isNotBlank()) {
                    onOpenUrl(searchQuery, searchQuery)
                    searchQuery = ""
                }
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Speed Dials (Android, Bing, Wikipedia, GitHub)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickDialItem("Android", R.drawable.ic_brand_android) {
                onOpenUrl("https://developer.android.com", "Android Docs")
            }
            QuickDialItem("必应", R.drawable.ic_brand_bing) {
                onOpenUrl("https://cn.bing.com", "必应搜索")
            }
            QuickDialItem("百科", R.drawable.ic_brand_wikipedia) {
                onOpenUrl("https://wikipedia.org", "维基百科")
            }
            QuickDialItem("GitHub", R.drawable.ic_brand_github) {
                onOpenUrl("https://github.com/floatbro", "GitHub · FloatBro")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Overlay Permission Banner (Soft tip card adhering to Value-First UX)
        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "开启悬浮窗权限",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "解锁全局悬浮与贴边气泡多任务体验",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(onClick = onRequestPermission) {
                        Text("开启", fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 5. Card 1: Clipboard URL Detection Banner (智能剪贴板链接探测)
        if (clipboardUrl != null && !isClipboardDismissed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "剪贴板",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "检测到网页链接",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { isClipboardDismissed = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "忽略",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
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
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onPopoutFloating(clipboardUrl!!, "网页") }
                        ) {
                            Text("悬浮打开", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onOpenUrl(clipboardUrl!!, "网页") }
                        ) {
                            Text("直接打开", fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 6. Card 2: Recent Closed Sessions / Quick Restore (最近关闭的浮窗卡片流)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "最近关闭的网页",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (recentClosedTabs.isNotEmpty()) "点击快速复原" else "历史推荐",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                val itemsToShow = if (recentClosedTabs.isNotEmpty()) {
                    recentClosedTabs.take(3)
                } else {
                    listOf(
                        BrowserTab(title = "Bilibili · 视频与弹幕", url = "https://m.bilibili.com"),
                        BrowserTab(title = "必应 · AI 搜索", url = "https://cn.bing.com"),
                        BrowserTab(title = "GitHub · 极客社区", url = "https://github.com")
                    )
                }

                itemsToShow.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (recentClosedTabs.contains(item)) {
                                    onRestoreTab(item)
                                } else {
                                    onOpenUrl(item.url, item.title)
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.url,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = if (recentClosedTabs.contains(item)) "复原" else "打开",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Card 3: AdBlock Pure Shield Stats Card (纯净去广告守护看板)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_ssl),
                            contentDescription = "守护盾",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FloatBro 纯净守护盾",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "实时生效中",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "1,280+",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "已拦截广告",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "340+",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "阻断追踪器",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "18.5 MB",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "预估节省流量",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "C++ BloomFilter 内核 · 原生 CSS :has() 元素隐藏 · 零留白纯净浏览",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun QuickDialItem(title: String, iconRes: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 2.dp,
            modifier = Modifier.size(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun InAppWebViewPane(
    tab: BrowserTab,
    onTearOff: () -> Unit,
    onUrlChanged: (String) -> Unit = {},
    onTitleReceived: (String) -> Unit,
    onFaviconReceived: (Bitmap) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<EBWebView?>(null) }
    var loadingProgress by remember { mutableStateOf(0) }
    var isEditingUrl by remember { mutableStateOf(false) }
    var editingUrlText by remember(tab.url) { mutableStateOf(tab.url) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Mini Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatBroUiTokens.ToolbarButtonTouchTarget)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webViewInstance?.let { if (it.canGoBack()) it.goBack() } },
                modifier = Modifier.size(FloatBroUiTokens.ToolbarButtonTouchTarget)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退", modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(FloatBroUiTokens.GridSpacingStandard))
            IconButton(
                onClick = { webViewInstance?.let { if (it.canGoForward()) it.goForward() } },
                modifier = Modifier.size(FloatBroUiTokens.ToolbarButtonTouchTarget)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进", modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(FloatBroUiTokens.GridSpacingStandard))
            IconButton(
                onClick = { webViewInstance?.reload() },
                modifier = Modifier.size(FloatBroUiTokens.ToolbarButtonTouchTarget)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(FloatBroUiTokens.GridSpacingStandard))

            if (isEditingUrl) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = editingUrlText,
                            onValueChange = { editingUrlText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (editingUrlText.isNotBlank()) {
                                    val resolved = LauncherActivity.resolveBrowserUrl(editingUrlText)
                                    onUrlChanged(resolved)
                                    webViewInstance?.loadUrlSafely(resolved)
                                }
                                isEditingUrl = false
                                keyboardController?.hide()
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                        IconButton(
                            onClick = {
                                isEditingUrl = false
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消编辑",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .padding(horizontal = 4.dp)
                        .clickable {
                            editingUrlText = tab.url
                            isEditingUrl = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "SSL",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.url,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tear-Off button to directly pop out to floating overlay
            IconButton(
                onClick = onTearOff,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "弹射为独立悬浮窗",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Loading Progress Indicator
        if (loadingProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Web Content
        AndroidView(
            factory = { ctx ->
                EBWebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onProgressChangedListener = { progress ->
                        loadingProgress = progress
                    }
                    onTitleChangedListener = { title -> onTitleReceived(title) }
                    onReceivedIconListener = { icon -> onFaviconReceived(icon) }
                    onPageStartedListener = { startedUrl -> onUrlChanged(startedUrl) }
                    loadUrlSafely(tab.url)
                    webViewInstance = this
                }
            },
            update = { wv ->
                webViewInstance = wv
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}
