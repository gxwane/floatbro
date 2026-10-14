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
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.core.webview.EBWebView
import io.github.floatbro.core.webview.client.FloatWebViewClient
import org.json.JSONObject
import timber.log.Timber

/**
 * Controller for the Lightweight Mobile Element Zapper.
 * Pure Android View stack capsule overlay with safe evaluation and lifecycle guard.
 */
class ElementZapperManager(
    private val context: Context,
    private val webView: EBWebView,
    private val container: ViewGroup
) {

    var isZapperActive: Boolean = false
        private set

    private var capsuleView: View? = null

    fun enterZapperMode() {
        if (isZapperActive) return
        val currentUrl = webView.url ?: ""
        if (currentUrl.isEmpty() || currentUrl == "about:blank" || !currentUrl.startsWith("http")) {
            Toast.makeText(context, "当前页面不支持元素标记消除", Toast.LENGTH_SHORT).show()
            return
        }

        isZapperActive = true
        // Freeze native navigation to prevent rogue links or ad scripts from navigating away
        (webView.webViewClient as? FloatWebViewClient)?.isNavigationFrozen = true

        // 1. Inject element_zapper.js
        try {
            val script = context.assets.open("adfilter/element_zapper.js").bufferedReader().use { it.readText() }
            webView.evaluateJavascript(script) { result ->
                Timber.d("ElementZapper script initialized: %s", result)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load and inject element_zapper.js")
            Toast.makeText(context, "加载元素消除器失败", Toast.LENGTH_SHORT).show()
            (webView.webViewClient as? FloatWebViewClient)?.isNavigationFrozen = false
            isZapperActive = false
            return
        }

        // 2. Attach Bottom Capsule View
        showCapsuleView()
        Toast.makeText(context, "已进入标记消除：轻点广告元素高亮选中", Toast.LENGTH_SHORT).show()
    }

    fun exitZapperMode(quietly: Boolean = true) {
        if (!isZapperActive) return
        isZapperActive = false
        (webView.webViewClient as? FloatWebViewClient)?.isNavigationFrozen = false

        try {
            webView.evaluateJavascript("window.__FloatBroZapper ? window.__FloatBroZapper.exit() : '{}'", null)
        } catch (e: Exception) {
            Timber.w(e, "Failed to evaluate exit() on zapper")
        }

        hideCapsuleView()
    }

    private fun showCapsuleView() {
        hideCapsuleView()

        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDarkMode) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val borderColor = if (isDarkMode) Color.parseColor("#475569") else Color.parseColor("#CBD5E1")
        val textColor = if (isDarkMode) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
        val saveColor = if (isDarkMode) Color.parseColor("#34D399") else Color.parseColor("#10B981")
        val closeColor = if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        val dividerColor = if (isDarkMode) Color.parseColor("#334155") else Color.parseColor("#E2E8F0")

        val density = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density + 0.5f).toInt()

        val backgroundDrawable = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(20f).toFloat()
            setStroke(dp(1f), borderColor)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = backgroundDrawable
            elevation = dp(8f).toFloat()
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }

        fun createButton(text: String, color: Int, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 12f
                setTextColor(color)
                isClickable = true
                isFocusable = true
                setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
                setOnClickListener { onClick() }
            }
        }

        fun createDivider(): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(1f), dp(14f)).apply {
                    setMargins(dp(3f), 0, dp(3f), 0)
                }
                setBackgroundColor(dividerColor)
            }
        }

        // Forward references to update button states
        var btnShrink: TextView? = null
        var btnExpand: TextView? = null

        fun updateNavigationButtons(canExpand: Boolean, canShrink: Boolean) {
            btnShrink?.apply {
                isEnabled = canShrink
                alpha = if (canShrink) 1.0f else 0.4f
            }
            btnExpand?.apply {
                isEnabled = canExpand
                alpha = if (canExpand) 1.0f else 0.4f
            }
        }

        // 1. 缩小 (出栈回退到子元素)
        btnShrink = createButton("缩小", textColor) {
            webView.evaluateJavascript("window.__FloatBroZapper ? window.__FloatBroZapper.shrinkChild() : '{}'") { raw ->
                try {
                    val unescaped = unescapeJson(raw)
                    val obj = JSONObject(unescaped)
                    val canExpand = obj.optBoolean("canExpand", true)
                    val canShrink = obj.optBoolean("canShrink", false)
                    updateNavigationButtons(canExpand, canShrink)
                } catch (e: Exception) {
                    Timber.v(e, "shrinkChild response: %s", raw)
                }
            }
        }.apply {
            // Initially at root/leaf target, cannot shrink
            isEnabled = false
            alpha = 0.4f
        }
        layout.addView(btnShrink)

        // 2. 扩大 (入栈选中父容器)
        btnExpand = createButton("扩大", textColor) {
            webView.evaluateJavascript("window.__FloatBroZapper ? window.__FloatBroZapper.expandParent() : '{}'") { raw ->
                try {
                    val unescaped = unescapeJson(raw)
                    val obj = JSONObject(unescaped)
                    if (obj.optString("reason") == "ceiling") {
                        Toast.makeText(context, "已达到最大安全容器层级", Toast.LENGTH_SHORT).show()
                    }
                    val canExpand = obj.optBoolean("canExpand", false)
                    val canShrink = obj.optBoolean("canShrink", true)
                    updateNavigationButtons(canExpand, canShrink)
                } catch (e: Exception) {
                    Timber.v(e, "expandParent response: %s", raw)
                }
            }
        }
        layout.addView(btnExpand)

        layout.addView(createDivider())

        // 3. 临时消除 (即点即走，立即消除并退出胶囊)
        layout.addView(createButton("临时消除", textColor) {
            webView.evaluateJavascript("window.__FloatBroZapper ? window.__FloatBroZapper.hideCurrent() : '{}'") { raw ->
                try {
                    val unescaped = unescapeJson(raw)
                    val obj = JSONObject(unescaped)
                    if (obj.optBoolean("success", false)) {
                        Toast.makeText(context, "已临时消除", Toast.LENGTH_SHORT).show()
                        exitZapperMode(quietly = true)
                    } else {
                        Toast.makeText(context, "请先轻点选中要消除的元素", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse hideCurrent response: %s", raw)
                    Toast.makeText(context, "请先轻点选中要消除的元素", Toast.LENGTH_SHORT).show()
                }
            }
        })

        layout.addView(createDivider())

        // 4. 永久屏蔽 (即刻固化生效 + 持久化保存 + 退出胶囊)
        layout.addView(createButton("永久屏蔽", saveColor) {
            webView.evaluateJavascript("window.__FloatBroZapper ? window.__FloatBroZapper.getCurrentSelector() : '{}'") { raw ->
                try {
                    val unescaped = unescapeJson(raw)
                    val obj = JSONObject(unescaped)
                    val selector = obj.optString("selector", "")
                    if (selector.isBlank()) {
                        Toast.makeText(context, "请先轻点选中要消除的元素", Toast.LENGTH_SHORT).show()
                        return@evaluateJavascript
                    }

                    val host = Uri.parse(webView.url ?: "").host?.lowercase() ?: ""
                    val rule = ElementZapperHelper.buildRule(host, selector)
                    if (rule != null) {
                        // 1. Immediately inject permanent style to current page for 0-latency hiding
                        val permanentCss = "$selector { display: none !important; visibility: hidden !important; }"
                        val injectPermanentJs = """
                            (function() {
                                var s = document.createElement('style');
                                s.setAttribute('data-floatbro-zap-permanent', 'true');
                                s.textContent = ${JSONObject.quote(permanentCss)};
                                (document.head || document.documentElement).appendChild(s);
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(injectPermanentJs, null)

                        // 2. Persist to CustomFilter
                        val customFilter = AdFilter.get(context).customFilter
                        if (!customFilter.contains(rule)) {
                            customFilter.append(rule)
                            customFilter.flush()
                            Toast.makeText(context, "已永久屏蔽", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "规则已存在", Toast.LENGTH_SHORT).show()
                        }
                        exitZapperMode(quietly = true)
                    } else {
                        Toast.makeText(context, "选择器过于宽泛或不安全，无法保存", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse selector response: %s", raw)
                    Toast.makeText(context, "请先轻点选中要消除的元素", Toast.LENGTH_SHORT).show()
                }
            }
        })

        layout.addView(createDivider())

        // 5. 退出 (放弃当前未消除目标的高亮)
        layout.addView(createButton("✕", closeColor) {
            exitZapperMode()
        })

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16f)
        }

        container.addView(layout, params)
        capsuleView = layout
    }

    private fun hideCapsuleView() {
        capsuleView?.let {
            container.removeView(it)
            capsuleView = null
        }
    }

    private fun unescapeJson(raw: String?): String {
        return ElementZapperHelper.unescapeJson(raw)
    }
}
