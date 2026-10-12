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

package io.github.floatbro.browser

import android.content.Context
import android.content.SharedPreferences

enum class UserAgentMode(val label: String) {
    DEFAULT("默认移动端"),
    DESKTOP("桌面电脑"),
    TABLET("平板/iPad"),
    CUSTOM("自定义");

    companion object {
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val TABLET_UA = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

        fun fromName(name: String?): UserAgentMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class SearchEngine(val label: String, val searchUrlTemplate: String) {
    BING("必应", "https://cn.bing.com/search?q=%s"),
    BAIDU("百度", "https://www.baidu.com/s?wd=%s"),
    GOOGLE("谷歌", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    CUSTOM("自定义", "%s");

    companion object {
        fun fromName(name: String?): SearchEngine {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BING
        }
    }
}

class BrowserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var userAgentMode: UserAgentMode
        get() = UserAgentMode.fromName(prefs.getString(KEY_UA_MODE, UserAgentMode.DEFAULT.name))
        set(value) = prefs.edit().putString(KEY_UA_MODE, value.name).apply()

    var customUserAgent: String
        get() = prefs.getString(KEY_CUSTOM_UA, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_UA, value).apply()

    var searchEngine: SearchEngine
        get() = SearchEngine.fromName(prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.BING.name))
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINE, value.name).apply()

    var customSearchUrl: String
        get() = prefs.getString(KEY_CUSTOM_SEARCH_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_SEARCH_URL, value).apply()

    var isAdBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADBLOCK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ADBLOCK_ENABLED, value).apply()

    var adBlockWhitelist: Set<String>
        get() = prefs.getStringSet(KEY_ADBLOCK_WHITELIST, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ADBLOCK_WHITELIST, value).apply()

    /**
     * Checks if ad blocking is active for the given [host].
     * Returns false if global ad blocking is off, or if [host] / its domain is whitelisted.
     */
    fun isAdBlockEnabledForHost(host: String): Boolean {
        if (!isAdBlockEnabled) return false
        val cleanHost = host.lowercase().trim()
        if (cleanHost.isEmpty()) return true
        val whitelist = adBlockWhitelist
        if (whitelist.contains(cleanHost)) return false
        // Check parent domains (e.g. "piquge.com" matches "m.piquge.com")
        for (whitelisted in whitelist) {
            if (cleanHost.endsWith(".$whitelisted")) {
                return false
            }
        }
        return true
    }

    /**
     * Toggles ad blocking for a specific [host].
     * If currently whitelisted (disabled), removes it from whitelist and returns true (enabled).
     * If not whitelisted, adds it to whitelist and returns false (disabled).
     */
    fun toggleAdBlockForHost(host: String): Boolean {
        val cleanHost = host.lowercase().trim()
        if (cleanHost.isEmpty()) return isAdBlockEnabled
        val currentSet = HashSet(adBlockWhitelist)
        val willBeEnabled: Boolean
        if (currentSet.contains(cleanHost)) {
            currentSet.remove(cleanHost)
            willBeEnabled = true
        } else {
            currentSet.add(cleanHost)
            willBeEnabled = false
        }
        adBlockWhitelist = currentSet
        return willBeEnabled
    }

    fun resolveUserAgent(defaultSystemUa: String? = null): String? {
        return when (userAgentMode) {
            UserAgentMode.DEFAULT -> defaultSystemUa
            UserAgentMode.DESKTOP -> UserAgentMode.DESKTOP_UA
            UserAgentMode.TABLET -> UserAgentMode.TABLET_UA
            UserAgentMode.CUSTOM -> customUserAgent.ifBlank { defaultSystemUa }
        }
    }

    companion object {
        private const val PREFS_NAME = "floatbro_browser_prefs"
        private const val KEY_UA_MODE = "user_agent_mode"
        private const val KEY_CUSTOM_UA = "custom_user_agent"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_CUSTOM_SEARCH_URL = "custom_search_url"
        private const val KEY_ADBLOCK_ENABLED = "adblock_enabled"
        private const val KEY_ADBLOCK_WHITELIST = "adblock_whitelist"

        @Volatile
        private var instance: BrowserPreferences? = null

        fun getInstance(context: Context): BrowserPreferences {
            return instance ?: synchronized(this) {
                instance ?: BrowserPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
