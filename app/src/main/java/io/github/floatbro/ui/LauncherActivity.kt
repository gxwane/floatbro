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
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import io.github.floatbro.R
import io.github.floatbro.core.webview.EBWebView
import io.github.floatbro.service.FloatBrowserService
import io.github.floatbro.ui.theme.FloatBroTheme
import java.util.UUID

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
    }

    private var hasOverlayPermission by mutableStateOf(false)
    private var openMode by mutableStateOf(OpenMode.TAB)
    private val tabs = mutableStateListOf<BrowserTab>()
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
                // BackHandler for dedicated Settings secondary page
                BackHandler(enabled = activeTabId == "settings") {
                    activeTabId = previousTabId
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(), // Safe inset padding avoiding status bar and camera hole
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. Tab Strip (Sole Top Bar when browsing)
                        if (activeTabId != "settings") {
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
                                onOpenSettings = {
                                    previousTabId = activeTabId
                                    activeTabId = "settings"
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
                                        }
                                    )
                                }
                                else -> {
                                    val currentTab = tabs.find { it.id == activeTabId }
                                    if (currentTab != null) {
                                        InAppWebViewPane(
                                            tab = currentTab,
                                            onTearOff = {
                                                tabs.removeAll { it.id == currentTab.id }
                                                activeTabId = "home"
                                                startOverlayService(FloatBrowserService.ACTION_SHOW, currentTab.url)
                                                Toast.makeText(this@LauncherActivity, "网页已撕裂转化为全局悬浮窗", Toast.LENGTH_SHORT).show()
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
        val resolvedUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("www.") -> "https://$url"
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://cn.bing.com/search?q=" + Uri.encode(url)
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
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

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
            .height(44.dp)
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

            // Web Tabs
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    color = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .height(36.dp)
                        .clickable { onSelectTab(tab.id) }
                        .padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
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
                    .size(36.dp)
                    .padding(bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建标签页",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Settings Button pinned at top-right
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(36.dp)
                .padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Standard Secondary Page Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Settings Preferences Group List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Group 1: 浏览与交互偏好
            Text(
                text = "浏览与交互偏好",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "默认打开网页方式",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "选择点击常用导航或搜索链接时，默认采用悬浮窗弹出或在应用内打开",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Group 2: 悬浮窗高级特性
            Text(
                text = "悬浮窗高级特性",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统悬浮窗权限",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (hasOverlayPermission) "悬浮权限已正常开启" else "尚未授权，点击跳转系统设置授权",
                                fontSize = 12.sp,
                                color = if (hasOverlayPermission) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                        if (!hasOverlayPermission) {
                            Button(onClick = onRequestOverlayPermission) {
                                Text("去授权", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "贴边吸附交互逻辑",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• 靠边停放：拖到屏幕边缘松手，窗口自动贴边吸附停靠。\n• 收拢为悬浮球：推入边缘超过 40% 面积时自动折叠为悬浮球。\n• 拖拽收回：拖动悬浮窗向下滑动至屏幕底部，自动收回为主应用标签页。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group 3: 隐私与清理
            Text(
                text = "隐私与清理",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClearData() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "清除浏览数据与缓存",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "清除所有网页缓存、Cookies 与临时存储",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group 4: 关于 FloatBro
            Text(
                text = "关于应用",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bubble_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FloatBro 浮窗浏览器",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "版本 1.0.0-CleanCraft (Tier 3) · 纯粹、极简、高性能",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
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

        // Speed Dials (Android, Bing, Wikipedia, GitHub)
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
    onTearOff: () -> Unit,
    onTitleReceived: (String) -> Unit,
    onFaviconReceived: (Bitmap) -> Unit
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
                    onTitleChangedListener = { title -> onTitleReceived(title) }
                    onReceivedIconListener = { icon -> onFaviconReceived(icon) }
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
