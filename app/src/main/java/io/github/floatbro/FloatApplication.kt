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

package io.github.floatbro

import android.app.Application
import android.os.StrictMode
import io.github.edsuns.adfilter.AdFilter
import io.github.floatbro.core.common.diagnostics.LocalDiagnostics
import io.github.floatbro.diagnostics.DiagnosticTimberTree
import io.github.floatbro.diagnostics.FloatBroCrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class FloatApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize unified local diagnostics subsystem
        val diagnostics =
            LocalDiagnostics.getInstance(this).apply {
                initialize()
            }

        // 2. Install unhandled crash supervisor with Direct I/O emergency flush
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(FloatBroCrashHandler(diagnostics, defaultHandler))

        // 3. Plant diagnostic timber tree (handles both logcat in debug and breadcrumb aggregation)
        Timber.plant(DiagnosticTimberTree(diagnostics))

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build(),
            )
        }

        // Initialize high-performance C++ BloomFilter AdFilter engine
        val filter = AdFilter.create(this)
        applicationScope.launch(Dispatchers.IO) {
            filter.setEnabled(true)
        }
    }
}
