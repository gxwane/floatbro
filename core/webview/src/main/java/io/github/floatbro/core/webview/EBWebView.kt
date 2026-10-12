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

package io.github.floatbro.core.webview

import android.graphics.Bitmap
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.core.webview.bridge.InputFocusBridge
import io.github.floatbro.core.webview.client.FloatWebChromeClient
import io.github.floatbro.core.webview.client.FloatWebViewClient
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
open class EBWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onProgressChangedListener: ((Int) -> Unit)? = null
    var onTitleChangedListener: ((String) -> Unit)? = null
    var onReceivedIconListener: ((Bitmap) -> Unit)? = null
    var onInputFocusListener: ((Boolean) -> Unit)? = null
    var onPageStartedListener: ((String) -> Unit)? = null
    var onPageFinishedListener: ((String) -> Unit)? = null
    var onPageCommitVisibleListener: ((String) -> Unit)? = null

    private val inputFocusBridge = InputFocusBridge { isFocused ->
        post { onInputFocusListener?.invoke(isFocused) }
    }

    init {
        WebView.setWebContentsDebuggingEnabled(true)
        configureSettings()
        setupClients()

        // Set up AdFilter hooks
        try {
            AdFilter.get().setupWebView(this)
        } catch (e: Exception) {
            Timber.w(e, "AdFilter not initialized yet, skipping setupWebView")
        }
    }

    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Standard modern Chrome Android User-Agent
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
        }

        // Hardware acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Align background color with current theme to eliminate default pure white flash while preserving subpixel text AA
        updateThemeBackgroundColor()

        // Inject JS bridge for soft keyboard focus detection
        addJavascriptInterface(inputFocusBridge, InputFocusBridge.INTERFACE_NAME)
    }

    private fun setupClients() {
        webViewClient = FloatWebViewClient(
            onPageStartedCallback = { url -> onPageStartedListener?.invoke(url) },
            onPageFinishedCallback = { url -> onPageFinishedListener?.invoke(url) },
            onTitleUpdated = { title -> onTitleChangedListener?.invoke(title) },
            onPageCommitVisibleCallback = { url -> onPageCommitVisibleListener?.invoke(url) }
        )

        webChromeClient = FloatWebChromeClient(
            onProgressChangedCallback = { progress -> onProgressChangedListener?.invoke(progress) },
            onTitleReceivedCallback = { title -> onTitleChangedListener?.invoke(title) },
            onIconReceivedCallback = { icon -> onReceivedIconListener?.invoke(icon) }
        )
    }

    fun loadUrlSafely(url: String) {
        val targetUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("www.") -> "https://$url"
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://www.bing.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
        }
        val headers = mapOf(
            "Sec-CH-UA-Mobile" to "?1",
            "Sec-CH-UA-Platform" to "\"Android\""
        )
        loadUrl(targetUrl, headers)
    }

    /**
     * Clears all external callbacks to prevent Activity leaks during instance transfer.
     */
    fun detachAllListeners() {
        onProgressChangedListener = null
        onTitleChangedListener = null
        onReceivedIconListener = null
        onInputFocusListener = null
        onPageStartedListener = null
        onPageFinishedListener = null
        onPageCommitVisibleListener = null
    }

    /**
     * Updates the base Context via MutableContextWrapper without re-creating the WebView.
     */
    fun updateBaseContext(newContext: Context) {
        (context as? android.content.MutableContextWrapper)?.baseContext = newContext
        updateThemeBackgroundColor()
    }

    /**
     * Dynamically synchronizes background color with the current theme surface,
     * ensuring that no 0xFFFFFFFF white flash is exposed during Surface re-attachment,
     * while retaining an opaque canvas to preserve LCD subpixel text anti-aliasing.
     */
    fun updateThemeBackgroundColor() {
        val isDarkMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeBgColor = if (isDarkMode) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
        setBackgroundColor(themeBgColor)
    }
}
