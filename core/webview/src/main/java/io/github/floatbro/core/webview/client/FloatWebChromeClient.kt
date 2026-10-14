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
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

class FloatWebChromeClient(
    private val onProgressChangedCallback: ((progress: Int) -> Unit)? = null,
    private val onTitleReceivedCallback: ((title: String) -> Unit)? = null,
    private val onIconReceivedCallback: ((icon: Bitmap) -> Unit)? = null,
) : WebChromeClient() {
    var onShowCustomViewCallback: ((View, CustomViewCallback) -> Unit)? = null
    var onHideCustomViewCallback: (() -> Unit)? = null

    override fun onShowCustomView(
        view: View?,
        callback: CustomViewCallback?,
    ) {
        super.onShowCustomView(view, callback)
        if (view != null && callback != null) {
            timber.log.Timber.d("onShowCustomView triggered for HTML5 video")
            onShowCustomViewCallback?.invoke(view, callback)
        }
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        timber.log.Timber.d("onHideCustomView triggered")
        onHideCustomViewCallback?.invoke()
    }

    override fun onProgressChanged(
        view: WebView?,
        newProgress: Int,
    ) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback?.invoke(newProgress)
    }

    override fun onReceivedTitle(
        view: WebView?,
        title: String?,
    ) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleReceivedCallback?.invoke(it) }
    }

    override fun onReceivedIcon(
        view: WebView?,
        icon: Bitmap?,
    ) {
        super.onReceivedIcon(view, icon)
        icon?.let { onIconReceivedCallback?.invoke(it) }
    }

    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
        consoleMessage?.let {
            timber.log.Timber.d("WebConsole [${it.messageLevel()}]: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
        }
        return super.onConsoleMessage(consoleMessage)
    }

    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: android.webkit.JsResult?,
    ): Boolean {
        timber.log.Timber.i("JsAlert intercepted safely: $message")
        result?.confirm()
        return true
    }

    override fun onJsConfirm(
        view: WebView?,
        url: String?,
        message: String?,
        result: android.webkit.JsResult?,
    ): Boolean {
        timber.log.Timber.i("JsConfirm intercepted safely: $message")
        result?.confirm()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: android.webkit.JsPromptResult?,
    ): Boolean {
        timber.log.Timber.i("JsPrompt intercepted safely: $message")
        result?.confirm(defaultValue ?: "")
        return true
    }
}
