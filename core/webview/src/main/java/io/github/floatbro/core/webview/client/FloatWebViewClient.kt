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

package io.github.floatbro.core.webview.client

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.core.webview.bridge.InputFocusBridge
import timber.log.Timber

class FloatWebViewClient(
    private val onPageStartedCallback: ((url: String) -> Unit)? = null,
    private val onPageFinishedCallback: ((url: String) -> Unit)? = null,
    private val onTitleUpdated: ((title: String) -> Unit)? = null
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // Block rogue pseudo-domain app hijacks (e.g. baiduboxapp DNS failure)
        val host = request.url.host?.lowercase()
        if (host != null && (host == "baiduboxapp" || host.contains("baiduboxapp"))) {
            Timber.i("Blocked rogue baiduboxapp subresource request: %s", request.url)
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        val result = AdFilter.get().shouldIntercept(view, request)
        if (result.shouldBlock) {
            Timber.v("AdBlock blocked URL: %s, rule: %s", request.url, result.rule)
            return result.resourceResponse ?: WebResourceResponse("text/plain", "UTF-8", null)
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return handleUrlOverride(view, request.url.toString())
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleUrlOverride(view, url)
    }

    private fun handleUrlOverride(view: WebView, url: String): Boolean {
        val uri = try {
            android.net.Uri.parse(url)
        } catch (_: Exception) {
            return true
        }

        val scheme = uri.scheme?.lowercase() ?: return true
        val host = uri.host?.lowercase()

        // 1. Intercept pseudo-host app hijacks (e.g. https://baiduboxapp//utils?...)
        if (host != null && (host == "baiduboxapp" || host.contains("baiduboxapp"))) {
            Timber.i("Blocked rogue baiduboxapp navigation: %s", url)
            return true
        }

        // 2. Allow standard web navigation
        if (scheme == "http" || scheme == "https") {
            return false
        }

        // 3. Prevent unhandled app schemes (baiduboxapp://, intent://, etc.) from breaking WebView
        Timber.i("Intercepted external app scheme: %s", url)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view.evaluateJavascript("window._w = window; var _w = window;", null)
        if (!url.isNullOrEmpty()) {
            // Inject AdFilter CSS :has() element hiding rules & scriptlets
            AdFilter.get().performScript(view, url)
            onPageStartedCallback?.invoke(url)
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrEmpty()) {
            // Inject input focus detection bridge
            InputFocusBridge.inject(view)

            // Bing mobile viewport resize & anti-collapse fallback (scoped strictly to *.bing.com)
            val isBing = try {
                android.net.Uri.parse(url).host?.endsWith("bing.com") == true
            } catch (_: Exception) {
                false
            }

            if (isBing) {
                val bingFixJs = """
                    (function() {
                        window.dispatchEvent(new Event('resize'));
                        var app = document.getElementById('hp_app');
                        if (app && app.offsetHeight === 0) {
                            app.style.minHeight = '100vh';
                            window.dispatchEvent(new Event('resize'));
                        }
                    })();
                """.trimIndent()
                view.evaluateJavascript(bingFixJs, null)
            }

            onPageFinishedCallback?.invoke(url)
            view.title?.let { onTitleUpdated?.invoke(it) }
        }
    }
}
