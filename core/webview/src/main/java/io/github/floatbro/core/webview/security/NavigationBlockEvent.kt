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

package io.github.floatbro.core.webview.security

/**
 * Event payload dispatched when a rogue navigation or next-page ad hijacking is intercepted.
 */
data class NavigationBlockEvent(
    val originalUrl: String,
    val cleanRedirectUrl: String?,
    val targetHost: String,
    val isAutoForwarded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
