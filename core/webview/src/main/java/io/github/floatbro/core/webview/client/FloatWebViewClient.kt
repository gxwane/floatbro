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
        val result = AdFilter.get().shouldIntercept(view, request)
        if (result.shouldBlock) {
            Timber.v("AdBlock blocked URL: %s, rule: %s", request.url, result.rule)
            return result.resourceResponse ?: WebResourceResponse("text/plain", "UTF-8", null)
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
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
            onPageFinishedCallback?.invoke(url)
            view.title?.let { onTitleUpdated?.invoke(it) }
        }
    }
}
