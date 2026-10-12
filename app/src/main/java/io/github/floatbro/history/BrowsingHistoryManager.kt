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

package io.github.floatbro.history

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import java.util.regex.Pattern

/**
 * Single entry representing a visited website history record.
 */
data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Centralized manager for persistent browsing history across in-app tabs and floating windows.
 */
class BrowsingHistoryManager(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val inMemoryHistory = mutableListOf<HistoryItem>()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun recordVisit(url: String, title: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || trimmedUrl == "about:blank" || trimmedUrl == "about:home") {
            return
        }

        val displayTitle = title.trim().ifBlank {
            try {
                java.net.URI(trimmedUrl).host ?: trimmedUrl
            } catch (_: Exception) {
                trimmedUrl
            }
        }

        // Deduplicate existing entry with the same URL
        inMemoryHistory.removeAll { it.url == trimmedUrl }

        // Prepend as most recent
        val item = HistoryItem(
            id = UUID.randomUUID().toString(),
            url = trimmedUrl,
            title = displayTitle,
            timestamp = System.currentTimeMillis()
        )
        inMemoryHistory.add(0, item)

        // Cap to max entries
        while (inMemoryHistory.size > MAX_HISTORY_ITEMS) {
            inMemoryHistory.removeAt(inMemoryHistory.lastIndex)
        }

        saveToDisk()
    }

    @Synchronized
    fun getHistory(): List<HistoryItem> {
        return inMemoryHistory.toList()
    }

    @Synchronized
    fun removeHistory(id: String) {
        val removed = inMemoryHistory.removeAll { it.id == id }
        if (removed) {
            saveToDisk()
        }
    }

    @Synchronized
    fun clearHistory() {
        inMemoryHistory.clear()
        saveToDisk()
    }

    fun serializeToJson(items: List<HistoryItem>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { index, item ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"").append(escapeJson(item.id)).append("\",")
            sb.append("\"url\":\"").append(escapeJson(item.url)).append("\",")
            sb.append("\"title\":\"").append(escapeJson(item.title)).append("\",")
            sb.append("\"timestamp\":").append(item.timestamp)
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    fun deserializeFromJson(json: String): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        if (json.isBlank() || !json.trim().startsWith("[")) return list
        val pattern = Pattern.compile("\\{\"id\":\"(.*?)\",\"url\":\"(.*?)\",\"title\":\"(.*?)\",\"timestamp\":(\\d+)\\}")
        val matcher = pattern.matcher(json)
        while (matcher.find()) {
            val id = unescapeJson(matcher.group(1) ?: "")
            val url = unescapeJson(matcher.group(2) ?: "")
            val title = unescapeJson(matcher.group(3) ?: "")
            val timestamp = matcher.group(4)?.toLongOrNull() ?: System.currentTimeMillis()
            list.add(HistoryItem(id, url, title, timestamp))
        }
        return list
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun unescapeJson(str: String): String {
        return str.replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
    }

    @Synchronized
    private fun loadFromDisk() {
        if (prefs == null) return
        val json = prefs.getString(KEY_HISTORY_JSON, null) ?: return
        val list = deserializeFromJson(json)
        inMemoryHistory.clear()
        inMemoryHistory.addAll(list)
    }

    @Synchronized
    private fun saveToDisk() {
        if (prefs == null) return
        val json = serializeToJson(inMemoryHistory)
        prefs.edit().putString(KEY_HISTORY_JSON, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "floatbro_browsing_history"
        private const val KEY_HISTORY_JSON = "history_json"
        private const val MAX_HISTORY_ITEMS = 50

        @Volatile
        private var instance: BrowsingHistoryManager? = null

        fun getInstance(context: Context): BrowsingHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: BrowsingHistoryManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
