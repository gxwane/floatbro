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

import android.webkit.WebViewClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit Tests verifying that FloatWebViewClient correctly distinguishes
 * harmless navigation aborts (like 301/302 redirects, e.g. acfun.cn) from real network errors.
 */
class FloatWebViewClientErrorTest {

    @Test
    fun `isAbortedError identifies ERR_ABORTED in description regardless of case`() {
        // Typical Chromium description for redirected or cancelled requests
        assertTrue(FloatWebViewClient.isAbortedError(-1, "net::ERR_ABORTED"))
        assertTrue(FloatWebViewClient.isAbortedError(-1, "net::err_aborted"))
        assertTrue(FloatWebViewClient.isAbortedError(-1, "Request aborted by user"))
        assertTrue(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_UNKNOWN, "ABORTED"))
    }

    @Test
    fun `isAbortedError returns false for actual network failures`() {
        // Real DNS lookup failure (e.g. offline or domain doesn't exist)
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED"))

        // Connection refused
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_CONNECT, "net::ERR_CONNECTION_REFUSED"))

        // Connection timeout
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_TIMEOUT, "net::ERR_TIMED_OUT"))

        // SSL handshake failure
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, "net::ERR_SSL_PROTOCOL_ERROR"))

        // Generic error without aborted keyword
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_UNKNOWN, "net::ERR_UNKNOWN_ERROR"))

        // Null or empty description
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_UNKNOWN, null))
        assertFalse(FloatWebViewClient.isAbortedError(WebViewClient.ERROR_UNKNOWN, ""))
    }
}
