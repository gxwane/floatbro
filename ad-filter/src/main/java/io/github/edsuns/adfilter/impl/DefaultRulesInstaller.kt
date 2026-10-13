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

package io.github.edsuns.adfilter.impl

import android.content.Context
import io.github.edsuns.adblockclient.AdBlockClient
import io.github.edsuns.adfilter.AdFilter
import timber.log.Timber
import java.io.InputStream
import java.util.zip.CRC32

/**
 * Manages extraction, C++ BloomFilter compilation, and automatic invalidation
 * of modular built-in offline mobile ad-blocking rule packs from assets.
 */
internal class DefaultRulesInstaller(
    private val context: Context,
    private val binaryDataStore: BinaryDataStore,
    private val filterDataLoader: FilterDataLoader
) {

    data class PackInfo(
        val id: String,
        val assetPath: String,
        val prefKey: String
    )

    val packs = listOf(
        PackInfo(AdFilter.PACK_EASYLIST, "adfilter/rules_easylist.txt", "pref_pack_easylist"),
        PackInfo(AdFilter.PACK_CHINA, "adfilter/rules_easylist_china.txt", "pref_pack_china"),
        PackInfo(AdFilter.PACK_PRIVACY, "adfilter/rules_easyprivacy.txt", "pref_pack_privacy")
    )

    fun ensureInstalledAndLoaded() {
        for (pack in packs) {
            ensureInstalled(pack.id)
            if (isPackEnabled(pack.id)) {
                filterDataLoader.load(pack.id)
                Timber.i("Loaded rule pack: %s", pack.id)
            } else {
                filterDataLoader.unload(pack.id)
            }
        }
    }

    fun ensureInstalled(packId: String) {
        val pack = packs.find { it.id == packId } ?: return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val crcKey = "crc_${pack.id}"
            val countKey = "count_${pack.id}"
            val cachedCrc = prefs.getLong(crcKey, -1L)
            val currentCrc = calculateAssetCrc(pack.assetPath)
            val hasBinary = binaryDataStore.hasData(pack.id)

            if (!hasBinary || cachedCrc != currentCrc) {
                Timber.i("Compiling built-in adblock pack %s (CRC: %d)...", pack.id, currentCrc)
                val client = AdBlockClient(pack.id)
                val rawBytes = openAsset(pack.assetPath).use { it.readBytes() }
                client.loadBasicData(rawBytes, true)
                val processedData = client.getProcessedData()
                val count = client.getFiltersCount()
                binaryDataStore.saveData(pack.id, processedData)
                prefs.edit()
                    .putLong(crcKey, currentCrc)
                    .putInt(countKey, count)
                    .apply()
                Timber.i("Successfully compiled %s: %d rules loaded", pack.id, count)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to compile/install rule pack %s", packId)
        }
    }

    fun isPackEnabled(packId: String): Boolean {
        val pack = packs.find { it.id == packId } ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(pack.prefKey, true)
    }

    fun setPackEnabled(packId: String, enabled: Boolean) {
        val pack = packs.find { it.id == packId } ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(pack.prefKey, enabled).apply()
    }

    fun getRuleCount(packId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("count_${packId}", 0)
        if (count > 0) return count
        ensureInstalled(packId)
        return prefs.getInt("count_${packId}", 0)
    }

    private fun openAsset(assetPath: String): InputStream {
        return context.assets.open(assetPath)
    }

    private fun calculateAssetCrc(assetPath: String): Long {
        val crc = CRC32()
        openAsset(assetPath).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        return crc.value
    }

    companion object {
        private const val PREFS_NAME = "adfilter_builtin_prefs"
    }
}
