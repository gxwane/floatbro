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
import android.webkit.WebResourceError
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

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            val failingUrl = request.url.toString()
            Timber.w("Main frame failed loading: %s, error: %s (code=%d)", failingUrl, error.description, error.errorCode)
            val errorHtml = generateErrorHtml(failingUrl, error.description.toString())
            view.loadDataWithBaseURL(failingUrl, errorHtml, "text/html", "UTF-8", failingUrl)
        }
    }

    private fun generateErrorHtml(url: String, description: String): String {
        val safeUrl = url.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val safeDesc = description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <title>网页无法打开</title>
            <style>
              body {
                margin: 0; padding: 24px;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                background-color: #F8FAFC; color: #0F172A;
                display: flex; flex-direction: column; align-items: center; justify-content: center;
                min-height: 85vh; text-align: center; box-sizing: border-box;
              }
              @media (prefers-color-scheme: dark) {
                body { background-color: #0F172A; color: #F8FAFC; }
                .card { background-color: #1E293B !important; border-color: #334155 !important; }
                .url { background-color: #0F172A !important; color: #94A3B8 !important; }
                .btn { background-color: #2563EB !important; }
              }
              .card {
                background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 16px;
                padding: 32px 24px; max-width: 360px; width: 100%; box-shadow: 0 4px 12px rgba(0,0,0,0.05);
              }
              .icon { width: 56px; height: 56px; margin: 0 auto 16px auto; }
              h2 { font-size: 18px; margin: 0 0 8px 0; font-weight: 600; }
              p { font-size: 13px; color: #64748B; margin: 0 0 16px 0; line-height: 1.5; }
              .url {
                font-family: monospace; font-size: 11px; color: #475569;
                background: #F1F5F9; padding: 8px 12px; border-radius: 8px;
                word-break: break-all; margin-bottom: 24px;
              }
              .btn {
                background: #2563EB; color: #FFFFFF; border: none; border-radius: 20px;
                padding: 10px 24px; font-size: 14px; font-weight: 500; cursor: pointer;
                box-shadow: 0 2px 6px rgba(37,99,235,0.25);
              }
            </style>
            </head>
            <body>
              <div class="card">
                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                  <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55"></path>
                  <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39"></path>
                  <path d="M10.71 5.05A16 16 0 0 1 22.58 9"></path>
                  <path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88"></path>
                  <path d="M8.53 16.11a6 6 0 0 1 6.95 0"></path>
                  <line x1="12" y1="20" x2="12.01" y2="20"></line>
                </svg>
                <h2>网络连接中断</h2>
                <p>未能加载该页面（$safeDesc），请检查网络连接或稍后重试。</p>
                <div class="url">$safeUrl</div>
                <button class="btn" onclick="location.href='$safeUrl'">重新加载</button>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
