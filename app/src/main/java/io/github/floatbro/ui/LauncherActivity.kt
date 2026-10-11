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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.floatbro.R
import io.github.floatbro.core.webview.EBWebView
import io.github.floatbro.service.FloatBrowserService
import io.github.floatbro.ui.theme.FloatBroTheme
import java.util.UUID
import kotlin.math.roundToInt

enum class OpenMode {
    FLOATING, // 默认直接弹射为独立悬浮窗
    TAB       // 默认在主应用内并列标签打开
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String
)

class LauncherActivity : ComponentActivity() {

    companion object {
        const val ACTION_DOCK_TAB = "io.github.floatbro.action.DOCK_TAB"
        const val EXTRA_TAB_URL = "extra_tab_url"
        const val EXTRA_TAB_TITLE = "extra_tab_title"
    }

    private var hasOverlayPermission by mutableStateOf(false)
    private var openMode by mutableStateOf(OpenMode.TAB)
    private val tabs = mutableStateListOf<BrowserTab>()
    private var activeTabId by mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        checkPermission()
        handleIncomingIntent(intent)

        setContent {
            FloatBroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. PC-Style Tab Strip directly at the top (Sole 42dp Top Bar)
                        AppTabStrip(
                            tabs = tabs,
                            activeTabId = activeTabId,
                            onSelectTab = { activeTabId = it },
                            onCloseTab = { tabId ->
                                tabs.removeAll { it.id == tabId }
                                if (activeTabId == tabId) {
                                    activeTabId = "home"
                                }
                            },
                            onNewTab = {
                                val newTab = BrowserTab(title = "新标签页", url = "about:blank")
                                tabs.add(newTab)
                                activeTabId = newTab.id
                            },
                            onTearOffToFloating = { tab ->
                                tabs.removeAll { it.id == tab.id }
                                activeTabId = "home"
                                startOverlayService(FloatBrowserService.ACTION_SHOW, tab.url)
                                Toast.makeText(this@LauncherActivity, "网页已撕裂转化为全局悬浮窗", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 3. Viewport Content
                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            if (activeTabId == "home") {
                                BrowserHomeView(
                                    hasPermission = hasOverlayPermission,
                                    openMode = openMode,
                                    onRequestPermission = { requestOverlayPermission() },
                                    onOpenUrl = { url, title ->
                                        handleOpenUrl(url, title)
                                    }
                                )
                            } else {
                                val currentTab = tabs.find { it.id == activeTabId }
                                if (currentTab != null) {
                                    InAppWebViewPane(
                                        tab = currentTab,
                                        onTearOff = {
                                            tabs.removeAll { it.id == currentTab.id }
                                            activeTabId = "home"
                                            startOverlayService(FloatBrowserService.ACTION_SHOW, currentTab.url)
                                        }
                                    )
                                }
                            }
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
        val resolvedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://www.google.com/search?q=" + Uri.encode(url)
        }

        if (openMode == OpenMode.FLOATING) {
            startOverlayService(FloatBrowserService.ACTION_SHOW, resolvedUrl)
            Toast.makeText(this, "已弹射为独立悬浮网页", Toast.LENGTH_SHORT).show()
        } else {
            val newTab = BrowserTab(title = title, url = resolvedUrl)
            tabs.add(newTab)
            activeTabId = newTab.id
        }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("floatbro_prefs", Context.MODE_PRIVATE)
        val modeStr = sp.getString("pref_open_mode", OpenMode.FLOATING.name)
        openMode = try {
            OpenMode.valueOf(modeStr ?: OpenMode.FLOATING.name)
        } catch (_: Exception) {
            OpenMode.FLOATING
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

    private fun startOverlayService(action: String, url: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }

        val intent = Intent(this, FloatBrowserService::class.java).apply {
            this.action = action
            if (url != null) {
                putExtra(FloatBrowserService.EXTRA_URL, url)
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
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onTearOffToFloating: (BrowserTab) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Home Tab (Fixed, Not Tearable)
        val isHomeActive = activeTabId == "home"
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            color = if (isHomeActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .height(34.dp)
                .clickable { onSelectTab("home") }
                .padding(end = 4.dp)
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

        // Opened Tabs (Supports Drag-out Tear-Off)
        tabs.forEach { tab ->
            var offsetY by remember { mutableFloatStateOf(0f) }
            val isActive = tab.id == activeTabId

            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                color = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .height(34.dp)
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .padding(end = 4.dp)
                    .pointerInput(tab.id) {
                        detectDragGestures(
                            onDragEnd = {
                                if (offsetY > 48f || offsetY < -48f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTearOffToFloating(tab)
                                }
                                offsetY = 0f
                            },
                            onDragCancel = { offsetY = 0f },
                            onDrag = { _, dragAmount ->
                                offsetY += dragAmount.y
                            }
                        )
                    }
                    .clickable { onSelectTab(tab.id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCloseTab(tab.id) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭标签",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // New Tab Button
        IconButton(
            onClick = onNewTab,
            modifier = Modifier
                .size(34.dp)
                .padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "新建标签",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun BrowserHomeView(
    hasPermission: Boolean,
    openMode: OpenMode,
    onRequestPermission: () -> Unit,
    onOpenUrl: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Hero Brand Logo (Dual-color rings, pure minimal)
        Icon(
            painter = painterResource(id = R.drawable.ic_bubble_logo),
            contentDescription = "FloatBro",
            tint = Color.Unspecified,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Clean Search Bar Capsule
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

        Spacer(modifier = Modifier.height(24.dp))

        // Speed Dials (Clean direct row, no redundant label above)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickDialItem("Android", R.drawable.ic_brand_android) {
                onOpenUrl("https://developer.android.com", "Android Docs")
            }
            QuickDialItem("百科", R.drawable.ic_brand_wikipedia) {
                onOpenUrl("https://wikipedia.org", "维基百科")
            }
            QuickDialItem("B站", R.drawable.ic_brand_bilibili) {
                onOpenUrl("https://bilibili.com", "哔哩哔哩")
            }
            QuickDialItem("GitHub", R.drawable.ic_brand_github) {
                onOpenUrl("https://github.com/floatbro", "GitHub · FloatBro")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Overlay Permission Warning Banner (Only visible when permission is missing)
        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚠️ 悬浮窗权限未开启，无法唤起浮窗",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = onRequestPermission) {
                        Text("去授权", fontSize = 12.sp)
                    }
                }
            }
        }
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
    onTearOff: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<EBWebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mini Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webViewInstance?.let { if (it.canGoBack()) it.goBack() } },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { webViewInstance?.let { if (it.canGoForward()) it.goForward() } },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(horizontal = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
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

        // Web Content
        AndroidView(
            factory = { ctx ->
                EBWebView(ctx).apply {
                    loadUrl(tab.url)
                    webViewInstance = this
                }
            },
            update = { wv ->
                webViewInstance = wv
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
