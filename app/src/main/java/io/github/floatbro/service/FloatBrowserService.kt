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
    }

    private lateinit var overlayManager: FloatingWindowManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = FloatingWindowManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        Timber.d("FloatBrowserService created and initialized FloatingWindowManager")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        val url = intent?.getStringExtra(EXTRA_URL) ?: intent?.dataString

        Timber.d("onStartCommand: action=$action, url=$url")

        when (action) {
            ACTION_MINIMIZE -> {
                overlayManager.minimizeToBubble()
            }
            ACTION_CLOSE -> {
                stopSelf()
            }
            ACTION_SHOW, Intent.ACTION_VIEW -> {
                overlayManager.showWindow(url)
            }
            else -> {
                if (overlayManager.currentMode == OverlayMode.HIDDEN) {
                    overlayManager.showWindow(url)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
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

        val minimizeIntent = Intent(this, FloatBrowserService::class.java).apply {
            action = ACTION_MINIMIZE
        }
        val minimizePendingIntent = PendingIntent.getService(
            this,
            2,
            minimizeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(this, FloatBrowserService::class.java).apply {
            action = ACTION_CLOSE
        }
        val closePendingIntent = PendingIntent.getService(
            this,
            3,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_service_running))
            .setContentText(getString(R.string.tap_to_open_bubble))
            .setSmallIcon(R.drawable.ic_bubble_logo)
            .setContentIntent(showPendingIntent)
            .addAction(R.drawable.ic_bubble_logo, "还原浮窗", showPendingIntent)
            .addAction(R.drawable.ic_minimize, "最小化", minimizePendingIntent)
            .addAction(R.drawable.ic_close, "关闭", closePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
