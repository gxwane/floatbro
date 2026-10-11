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

package io.github.floatbro.service.trampoline

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Transparent proxy Activity for handling file pickers and runtime permissions
 * on behalf of FloatBrowserService without breaking overlay architecture.
 */
class TrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Proxy logic will be invoked on demand
        finish()
    }
}
