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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowsingHistoryManagerTest {

    private lateinit var historyManager: BrowsingHistoryManager

    @Before
    fun setUp() {
        historyManager = BrowsingHistoryManager()
        historyManager.clearHistory()
    }

    @Test
    fun testRecordVisitDeduplicatesAndPrepends() {
        historyManager.recordVisit("https://cn.bing.com", "必应搜索")
        historyManager.recordVisit("https://m.bilibili.com", "哔哩哔哩")
        historyManager.recordVisit("https://cn.bing.com", "必应搜索 - AI 检索")

        val history = historyManager.getHistory()
        assertEquals(2, history.size)
        // Most recent first
        assertEquals("https://cn.bing.com", history[0].url)
        assertEquals("必应搜索 - AI 检索", history[0].title)
        assertEquals("https://m.bilibili.com", history[1].url)
    }

    @Test
    fun testIgnoresBlankOrInternalUrls() {
        historyManager.recordVisit("", "")
        historyManager.recordVisit("about:blank", "Blank")
        historyManager.recordVisit("about:home", "Home")

        assertTrue(historyManager.getHistory().isEmpty())
    }

    @Test
    fun testSerializationRoundTrip() {
        val original = listOf(
            HistoryItem(id = "1", url = "https://zhihu.com", title = "知乎", timestamp = 1000L),
            HistoryItem(id = "2", url = "https://baidu.com", title = "百度", timestamp = 2000L)
        )
        val json = historyManager.serializeToJson(original)
        val deserialized = historyManager.deserializeFromJson(json)

        assertEquals(2, deserialized.size)
        assertEquals("https://zhihu.com", deserialized[0].url)
        assertEquals("知乎", deserialized[0].title)
        assertEquals(1000L, deserialized[0].timestamp)
        assertEquals("https://baidu.com", deserialized[1].url)
        assertEquals("百度", deserialized[1].title)
    }

    @Test
    fun testRemoveHistorySingleItem() {
        historyManager.recordVisit("https://zhihu.com", "知乎")
        historyManager.recordVisit("https://baidu.com", "百度")
        val history = historyManager.getHistory()
        assertEquals(2, history.size)

        val idToRemove = history[0].id
        historyManager.removeHistory(idToRemove)

        val remaining = historyManager.getHistory()
        assertEquals(1, remaining.size)
        assertEquals("https://zhihu.com", remaining[0].url)
    }

    @Test
    fun testClearHistoryEmptiesList() {
        historyManager.recordVisit("https://baidu.com", "百度")
        assertEquals(1, historyManager.getHistory().size)

        historyManager.clearHistory()
        assertTrue(historyManager.getHistory().isEmpty())
    }
}
