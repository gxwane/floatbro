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

package io.github.floatbro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import io.github.floatbro.R
import io.github.floatbro.overlay.FloatingWindowManager
import io.github.floatbro.overlay.OverlayMode
import timber.log.Timber

/**
 * Foreground Service managing TYPE_APPLICATION_OVERLAY floating windows.
 * Coordinates FloatingWindowManager, intent actions, and notification controls.
 */
class FloatBrowserService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "floatbro_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW = "io.github.floatbro.action.SHOW"
        const val ACTION_MINIMIZE = "io.github.floatbro.action.MINIMIZE"
        const val ACTION_CLOSE = "io.github.floatbro.action.CLOSE"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_INITIAL_X = "extra_initial_x"
        const val EXTRA_INITIAL_Y = "extra_initial_y"
        const val EXTRA_WINDOW_ID = "extra_window_id"
    }

    private lateinit var overlayManager: FloatingWindowManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = FloatingWindowManager(this).apply {
            onAllSessionsClosed = {
                Timber.d("All floating sessions closed, stopping FloatBrowserService")
                ServiceCompat.stopForeground(this@FloatBrowserService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        Timber.d("FloatBrowserService created and initialized FloatingWindowManager")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Ensure foreground notification contract is satisfied upon any startForegroundService invocation
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val action = intent?.action
        val url = intent?.getStringExtra(EXTRA_URL) ?: intent?.dataString
        val initialX = intent?.getIntExtra(EXTRA_INITIAL_X, -1)?.takeIf { it >= 0 }
        val initialY = intent?.getIntExtra(EXTRA_INITIAL_Y, -1)?.takeIf { it >= 0 }
        val windowId = intent?.getStringExtra(EXTRA_WINDOW_ID)

        Timber.d("onStartCommand: action=$action, url=$url, initialX=$initialX, initialY=$initialY, windowId=$windowId")

        when (action) {
            ACTION_MINIMIZE -> {
                if (windowId != null) {
                    overlayManager.sessions[windowId]?.let { overlayManager.minimizeToBubble(it) }
                } else {
                    overlayManager.minimizeAllToBubbles()
                }
            }
            ACTION_CLOSE -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SHOW, Intent.ACTION_VIEW -> {
                if (url == null && windowId == null) {
                    // Pure wake-up from notification click: restore or bring latest active session to front
                    if (overlayManager.hasActiveOverlays) {
                        overlayManager.restoreOrBringToFrontLatest()
                    } else {
                        overlayManager.showWindow(null, initialX, initialY, null)
                    }
                } else {
                    // Explicit navigation intent from LauncherActivity or external app
                    overlayManager.showWindow(url, initialX, initialY, windowId)
                }
            }
            else -> {
                if (!overlayManager.hasActiveOverlays) {
                    overlayManager.showWindow(url, initialX, initialY, windowId)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("FloatBrowserService onConfigurationChanged: orientation=${newConfig.orientation}")
        if (::overlayManager.isInitialized) {
            overlayManager.onConfigurationChanged(newConfig)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Timber.d("FloatBrowserService onTrimMemory: level=$level")
        overlayManager.onTrimMemory(level)
    }

    override fun onDestroy() {
        Timber.d("FloatBrowserService onDestroy called")
        overlayManager.onDestroy()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val showIntent = Intent(this, FloatBrowserService::class.java).apply {
            action = ACTION_SHOW
        }
        val showPendingIntent = PendingIntent.getService(
            this,
            1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_service_running))
            .setSmallIcon(R.drawable.ic_bubble_logo)
            .setContentIntent(showPendingIntent)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
