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

package io.github.floatbro.core.webview

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe singleton bridge for seamless state transfer between in-app tabs
 * and system floating windows (TYPE_APPLICATION_OVERLAY).
 *
 * Supports zero-reload EBWebView instance handoff across View hierarchies,
 * as well as Bundle-based fallback state transfer.
 */
object TabOverlayStateBridge {
    private data class StoredState(
        val bundle: Bundle,
        val fallbackUrl: String,
        val fallbackTitle: String,
        val timestamp: Long = SystemClock.uptimeMillis(),
    )

    private val storage = ConcurrentHashMap<String, StoredState>()
    private val webViewRegistry = ConcurrentHashMap<String, EBWebView>()
    private val snapshotRegistry = ConcurrentHashMap<String, android.graphics.Bitmap>()
    private val snapshotTimeoutTasks = ConcurrentHashMap<String, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnables = ConcurrentHashMap<String, Runnable>()
    private val commitListeners = ConcurrentHashMap<String, () -> Unit>()
    private const val TTL_MS = 20_000L // 20s expiration

    /**
     * Stores an active EBWebView instance for zero-reload transfer between Window hierarchies.
     * Automatically detaches external listeners and unlinks from parent container.
     */
    fun putWebView(
        id: String,
        webView: EBWebView,
    ) {
        // 1. Detach external listeners to break implicit activity references
        webView.detachAllListeners()
        // 2. Ensure detached from current parent
        (webView.parent as? ViewGroup)?.removeView(webView)
        webViewRegistry[id] = webView
        Timber.d("TabOverlayStateBridge: Stored active EBWebView instance for session $id")

        // 3. Schedule 15s safety orphan cleanup on the main looper
        val timeoutTask =
            Runnable {
                webViewRegistry.remove(id)?.let { orphan ->
                    Timber.w("TabOverlayStateBridge: Cleaning expired orphaned WebView for $id")
                    try {
                        (orphan.parent as? ViewGroup)?.removeView(orphan)
                        orphan.stopLoading()
                        orphan.loadUrl("about:blank")
                        orphan.clearHistory()
                        orphan.removeAllViews()
                        orphan.destroy()
                    } catch (e: Throwable) {
                        Timber.e(e, "Error destroying orphaned WebView")
                    }
                }
                timeoutRunnables.remove(id)
            }
        timeoutRunnables[id] = timeoutTask
        mainHandler.postDelayed(timeoutTask, 15_000L)
    }

    /**
     * Atomically consumes a stored EBWebView instance for direct attachment to target window.
     */
    fun takeWebView(id: String): EBWebView? {
        timeoutRunnables.remove(id)?.let { mainHandler.removeCallbacks(it) }
        val wv = webViewRegistry.remove(id)
        if (wv != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            Timber.d("TabOverlayStateBridge: Consumed active EBWebView instance for session $id")
        }
        return wv
    }

    fun putState(
        id: String,
        bundle: Bundle,
        fallbackUrl: String,
        fallbackTitle: String,
    ) {
        cleanExpired()
        storage[id] = StoredState(bundle, fallbackUrl, fallbackTitle)
        Timber.d("TabOverlayStateBridge: Stored state for session $id (url: $fallbackUrl)")
    }

    /**
     * Atomically retrieves and removes the stored [Bundle] for one-time consumption.
     */
    fun takeState(id: String): Bundle? {
        cleanExpired()
        val entry = storage.remove(id)
        if (entry != null) {
            Timber.d("TabOverlayStateBridge: Consumed state for session $id")
        }
        return entry?.bundle
    }

    /**
     * Retrieves stored fallback URL if available.
     */
    fun getFallbackUrl(id: String): String? = storage[id]?.fallbackUrl

    /**
     * Stores a transient snapshot Bitmap for cross-window visual continuity.
     * Manages replacement recycling and schedules a 10s watchdog cleanup.
     */
    fun putSnapshot(
        id: String,
        bitmap: android.graphics.Bitmap,
    ) {
        // Discard prior snapshot reference if replaced to allow GC
        snapshotRegistry.remove(id)
        snapshotRegistry[id] = bitmap
        Timber.d("TabOverlayStateBridge: Stored transient snapshot for session $id")

        val timeoutTask =
            Runnable {
                snapshotRegistry.remove(id)?.let {
                    Timber.w("TabOverlayStateBridge: Cleaned expired snapshot reference for $id")
                }
                snapshotTimeoutTasks.remove(id)
            }
        snapshotTimeoutTasks[id] = timeoutTask
        mainHandler.postDelayed(timeoutTask, 10_000L)
    }

    /**
     * Atomically consumes and removes the snapshot for single-use handover.
     */
    fun takeSnapshot(id: String): android.graphics.Bitmap? {
        snapshotTimeoutTasks.remove(id)?.let { mainHandler.removeCallbacks(it) }
        val bmp = snapshotRegistry.remove(id)
        if (bmp != null) {
            Timber.d("TabOverlayStateBridge: Consumed transient snapshot for session $id")
        }
        return bmp
    }

    /**
     * Clears stored state for a session (e.g. on gesture cancel/snapback).
     */
    fun clear(id: String) {
        storage.remove(id)
        commitListeners.remove(id)
        timeoutRunnables.remove(id)?.let { mainHandler.removeCallbacks(it) }
        snapshotTimeoutTasks.remove(id)?.let { mainHandler.removeCallbacks(it) }
        snapshotRegistry.remove(id)
        webViewRegistry.remove(id)?.let { wv ->
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.removeAllViews()
                wv.destroy()
            } catch (e: Throwable) {
                Timber.e(e, "Error clearing WebView in TabOverlayStateBridge")
            }
        }
    }

    /**
     * Registers a one-shot callback for when the target WebView completes its first frame commit.
     */
    fun registerCommitListener(
        id: String,
        callback: () -> Unit,
    ) {
        commitListeners[id] = callback
    }

    /**
     * Notifies that the target window has committed its first visible frame.
     */
    fun notifyCommit(id: String) {
        val listener = commitListeners.remove(id)
        if (listener != null) {
            Timber.d("TabOverlayStateBridge: Notifying first frame commit for session $id")
            listener.invoke()
        }
    }

    fun clearCommitListener(id: String) {
        commitListeners.remove(id)
    }

    private fun cleanExpired() {
        val now = SystemClock.uptimeMillis()
        storage.entries.removeIf { now - it.value.timestamp > TTL_MS }
    }
}
