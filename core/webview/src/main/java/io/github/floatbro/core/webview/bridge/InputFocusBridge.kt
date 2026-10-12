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

package io.github.floatbro.core.webview.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import timber.log.Timber

/**
 * JavaScript bridge to monitor web form input focus states.
 * Notifies native layer when an editable field gains or loses focus,
 * enabling dynamic WindowManager flag toggling (FLAG_NOT_FOCUSABLE).
 */
class InputFocusBridge(
    private val onFocusChanged: (Boolean) -> Unit
) {
    companion object {
        const val INTERFACE_NAME = "FloatBroFocusBridge"

        const val INJECTION_SCRIPT = """
            (function() {
                if (window.__floatBroFocusInjected) return;
                window.__floatBroFocusInjected = true;

                function isEditableElement(elem) {
                    if (!elem || elem.disabled || elem.readOnly) return false;
                    var tag = (elem.tagName || '').toLowerCase();
                    if (tag === 'input') {
                        var type = (elem.type || 'text').toLowerCase();
                        return type !== 'button' && type !== 'submit' && type !== 'checkbox' && 
                               type !== 'radio' && type !== 'reset' && type !== 'image' && 
                               type !== 'color' && type !== 'range' && type !== 'file';
                    }
                    if (tag === 'textarea' || tag === 'select') return true;
                    if (elem.isContentEditable || (elem.closest && elem.closest('[contenteditable="true"]'))) return true;
                    return false;
                }

                var blurTimeout = null;

                document.addEventListener('focusin', function(e) {
                    if (blurTimeout) {
                        clearTimeout(blurTimeout);
                        blurTimeout = null;
                    }
                    if (isEditableElement(e.target)) {
                        if (window.FloatBroFocusBridge) {
                            window.FloatBroFocusBridge.onInputFocus(true);
                        }
                    }
                }, true);

                document.addEventListener('focusout', function(e) {
                    if (isEditableElement(e.target)) {
                        if (blurTimeout) clearTimeout(blurTimeout);
                        blurTimeout = setTimeout(function() {
                            blurTimeout = null;
                            if (window.FloatBroFocusBridge) {
                                window.FloatBroFocusBridge.onInputFocus(false);
                            }
                        }, 120);
                    }
                }, true);

                document.addEventListener('click', function(e) {
                    if (isEditableElement(e.target) && document.activeElement === e.target) {
                        if (blurTimeout) {
                            clearTimeout(blurTimeout);
                            blurTimeout = null;
                        }
                        if (window.FloatBroFocusBridge) {
                            window.FloatBroFocusBridge.onInputFocus(true);
                        }
                    }
                }, true);
            })();
        """

        fun inject(webView: WebView) {
            webView.evaluateJavascript(INJECTION_SCRIPT, null)
        }
    }

    @JavascriptInterface
    fun onInputFocus(isFocused: Boolean) {
        Timber.d("InputFocusBridge: onInputFocus(%b)", isFocused)
        onFocusChanged(isFocused)
    }
}
