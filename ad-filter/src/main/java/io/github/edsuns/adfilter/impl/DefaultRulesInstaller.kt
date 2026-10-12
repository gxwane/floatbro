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
import timber.log.Timber
import java.io.InputStream
import java.util.zip.CRC32

/**
 * Manages extraction, C++ BloomFilter compilation, and automatic invalidation
 * of built-in offline default mobile ad-blocking rules from assets.
 */
internal class DefaultRulesInstaller(
    private val context: Context,
    private val binaryDataStore: BinaryDataStore,
    private val filterDataLoader: FilterDataLoader
) {

    fun ensureInstalledAndLoaded() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedCrc = prefs.getLong(KEY_RULES_CRC, -1L)
            val currentCrc = calculateAssetCrc()
            val hasBinary = binaryDataStore.hasData(ID_BUILTIN)

            if (!hasBinary || cachedCrc != currentCrc) {
                Timber.i("Installing/updating built-in default adblock rules (CRC: %d)...", currentCrc)
                val client = AdBlockClient(ID_BUILTIN)
                val rawBytes = openAsset().use { it.readBytes() }
                client.loadBasicData(rawBytes, true)
                val processedData = client.getProcessedData()
                binaryDataStore.saveData(ID_BUILTIN, processedData)
                prefs.edit().putLong(KEY_RULES_CRC, currentCrc).apply()
                Timber.i("Successfully compiled built-in rules: %d rules loaded", client.getFiltersCount())
            }

            filterDataLoader.load(ID_BUILTIN)
            Timber.i("Built-in adblock rules loaded into detector")
        } catch (e: Exception) {
            Timber.e(e, "Failed to install/load built-in default rules")
        }
    }

    private fun openAsset(): InputStream {
        return context.assets.open(ASSET_PATH)
    }

    private fun calculateAssetCrc(): Long {
        val crc = CRC32()
        openAsset().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        return crc.value
    }

    companion object {
        const val ID_BUILTIN = "builtin_default"
        const val ASSET_PATH = "adfilter/default_rules.txt"
        private const val PREFS_NAME = "adfilter_builtin_prefs"
        private const val KEY_RULES_CRC = "builtin_rules_crc"
    }
}
