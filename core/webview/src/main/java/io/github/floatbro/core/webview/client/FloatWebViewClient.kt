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
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.browser.BrowserPreferences
import io.github.floatbro.browser.UserAgentMode
import io.github.floatbro.core.webview.bridge.InputFocusBridge
import timber.log.Timber

class FloatWebViewClient(
    private val onPageStartedCallback: ((url: String) -> Unit)? = null,
    private val onPageFinishedCallback: ((url: String) -> Unit)? = null,
    private val onTitleUpdated: ((title: String) -> Unit)? = null,
    private val onPageCommitVisibleCallback: ((url: String) -> Unit)? = null
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

        // Block rogue subresources on non-standard high ports (e.g. ad networks on :37068, :33473, :8321)
        val port = request.url.port
        val isNonStandardPort = port > 0 && port != 80 && port != 443 && port != 8080 && port != 8443
        val isLocalhost = host == "localhost" || host == "127.0.0.1" ||
            (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")))
        if (isNonStandardPort && !isLocalhost && !request.isForMainFrame) {
            Timber.i("Blocked high-port rogue subresource: %s (port=%d)", request.url, port)
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        // Whitelist & adblock toggle check based on main document host
        if (request.isForMainFrame) {
            AdFilter.get().setMainFrameUrl(view, request.url.toString())
        }

        val mainFrameUrl = if (request.isForMainFrame) {
            request.url.toString()
        } else {
            AdFilter.get().getMainFrameUrl(view)
                ?: request.requestHeaders?.get("Referer")
                ?: request.requestHeaders?.get("referer")
        }
        val mainFrameHost = try {
            mainFrameUrl?.let { android.net.Uri.parse(it).host?.lowercase() } ?: ""
        } catch (_: Exception) { "" }

        val prefs = BrowserPreferences.getInstance(view.context)
        if (prefs.isAdBlockEnabled && (mainFrameHost.isEmpty() || prefs.isAdBlockEnabledForHost(mainFrameHost))) {
            val result = AdFilter.get().shouldIntercept(view, request)
            if (result.shouldBlock) {
                Timber.v("AdBlock blocked URL: %s, rule: %s", request.url, result.rule)
                return result.resourceResponse ?: WebResourceResponse("text/plain", "UTF-8", null)
            }
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
        // 1. Inject Stealth polyfill for mobile anti-anti-adblock & anti-crawler protection
        injectStealthPolyfill(view)

        if (!url.isNullOrEmpty()) {
            val host = try { android.net.Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
            val prefs = BrowserPreferences.getInstance(view.context)
            if (prefs.isAdBlockEnabled && (host.isEmpty() || prefs.isAdBlockEnabledForHost(host))) {
                // Inject AdFilter CSS :has() element hiding rules & scriptlets
                AdFilter.get().performScript(view, url)
            }
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

    companion object {
        fun isAbortedError(errorCode: Int, description: String?): Boolean {
            val desc = description ?: ""
            return desc.contains("ERR_ABORTED", ignoreCase = true) ||
                   (errorCode == WebViewClient.ERROR_UNKNOWN && desc.contains("ABORTED", ignoreCase = true))
        }

        const val STEALTH_POLYFILL_JS = """
            (function() {
                try {
                    // 1. Normalize navigator.platform to prevent "Linux armv8l" from triggering PC crawler detection
                    if (!navigator.platform || /linux/i.test(navigator.platform)) {
                        Object.defineProperty(navigator, 'platform', { get: function() { return 'Android'; }, configurable: true });
                    }
                    // 2. Normalize navigator.userAgent: strip "Linux; " keyword to bypass naive anti-adblock / anti-crawler regexes
                    if (navigator.userAgent && /linux/i.test(navigator.userAgent)) {
                        var cleanedUa = navigator.userAgent.replace(/Linux;\s*/gi, '');
                        Object.defineProperty(navigator, 'userAgent', { get: function() { return cleanedUa; }, configurable: true });
                    }
                    // 3. Normalize screen.width in floating window mode so responsive window width doesn't trigger PC DevTools detector
                    if (window.innerWidth <= 768 && window.screen.width > 1024) {
                        Object.defineProperty(screen, 'width', { get: function() { return window.innerWidth; }, configurable: true });
                        Object.defineProperty(screen, 'availWidth', { get: function() { return window.innerWidth; }, configurable: true });
                    }
                    // 4. Pirated novel site instant decrypter (skip artificial 10s countdown if present)
                    if (typeof c !== 'undefined' && typeof php_decrypt_js === 'function') {
                        var contentEl = document.getElementById('chaptercontent');
                        if (contentEl && (!contentEl.innerHTML || contentEl.innerHTML.indexOf('章节内容加载失败') !== -1 || contentEl.innerHTML.length < 50)) {
                            contentEl.innerHTML = php_decrypt_js(c);
                        }
                    }
                    // 5. Purge rogue ad banners and link containers
                    var cleanNovelAds = function() {
                        var moreEl = document.getElementById('morecontent');
                        if (moreEl) moreEl.remove();
                        var selectors = [
                            'a[href*=".top"]', 'a[href*=".vip"]', 'a[href*="fbp9k"]', 'a[href*="/tg"]',
                            'div[style*="z-index: 2147483647"]', 'div[style*="z-index: 9999"]',
                            'div[id^="ad_"]', 'div[class*="ad_"]'
                        ];
                        for (var s = 0; s < selectors.length; s++) {
                            var els = document.querySelectorAll(selectors[s]);
                            for (var i = 0; i < els.length; i++) {
                                var el = els[i];
                                var container = el.closest('div') || el;
                                if (container && container !== document.body && container.id !== 'chaptercontent') {
                                    container.remove();
                                }
                            }
                        }
                    };
                    cleanNovelAds();
                    if (document.readyState !== 'complete') {
                        document.addEventListener('DOMContentLoaded', cleanNovelAds);
                        window.addEventListener('load', cleanNovelAds);
                    }
                } catch (_) {}
            })();
        """
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        super.onPageCommitVisible(view, url)
        Timber.d("onPageCommitVisible: %s", url)
        InputFocusBridge.inject(view)
        onPageCommitVisibleCallback?.invoke(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            val failingUrl = request.url.toString()
            val desc = error.description?.toString() ?: ""
            val code = error.errorCode
            if (isAbortedError(code, desc)) {
                Timber.d("Ignoring harmless main frame abort/redirect error: %s (error=%s, code=%d)", failingUrl, desc, code)
                return
            }
            Timber.w("Main frame failed loading: %s, error: %s (code=%d)", failingUrl, desc, code)
            val errorHtml = generateErrorHtml(failingUrl, desc)
            view.loadDataWithBaseURL(failingUrl, errorHtml, "text/html", "UTF-8", failingUrl)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        @Suppress("DEPRECATION")
        super.onReceivedError(view, errorCode, description, failingUrl)
        val url = failingUrl ?: view.url ?: return
        val desc = description ?: ""
        if (isAbortedError(errorCode, desc)) {
            Timber.d("Ignoring harmless main frame abort/redirect error: %s (error=%s, code=%d)", url, desc, errorCode)
            return
        }
        Timber.w("Main frame failed loading: %s, error: %s (code=%d)", url, desc, errorCode)
        val errorHtml = generateErrorHtml(url, desc)
        view.loadDataWithBaseURL(url, errorHtml, "text/html", "UTF-8", url)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Timber.e("WebView render process gone (didCrash=%b)", detail.didCrash())
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        return true
    }

    private fun generateErrorHtml(url: String, description: String): String {
        val safeUrl = url.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
        val safeDesc = description.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

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

    private fun injectStealthPolyfill(view: WebView) {
        val prefs = BrowserPreferences.getInstance(view.context)
        // Only inject mobile polyfill when in default mobile mode to avoid desktop fingerprint mismatch
        if (prefs.userAgentMode == UserAgentMode.DEFAULT) {
            view.evaluateJavascript(STEALTH_POLYFILL_JS, null)
        }
    }
}
