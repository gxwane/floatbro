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

package io.github.floatbro.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.floatbro.BuildConfig
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val downloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

data class UpdateCandidates(
    val currentVersion: String,
    val stable: ReleaseInfo?,
    val prerelease: ReleaseInfo?
)

data class DownloadProgress(
    val downloadId: Long,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Int
)

class AppUpdateManager(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val ioExecutor = Executors.newSingleThreadExecutor()

    val currentVersion: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            "1.0.0"
        }

    fun fetchReleasesAsync(
        repoOwner: String = DEFAULT_REPO_OWNER,
        repoName: String = DEFAULT_REPO_NAME,
        onSuccess: (UpdateCandidates) -> Unit,
        onError: (String) -> Unit
    ) {
        ioExecutor.execute {
            try {
                val candidates = fetchReleasesSync(repoOwner, repoName)
                Handler(Looper.getMainLooper()).post {
                    onSuccess(candidates)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "检查更新失败，请稍后重试")
                }
            }
        }
    }

    fun fetchReleasesSync(
        repoOwner: String = DEFAULT_REPO_OWNER,
        repoName: String = DEFAULT_REPO_NAME
    ): UpdateCandidates {
        val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=15"
        var stableRelease: ReleaseInfo? = null
        var prereleaseRelease: ReleaseInfo? = null

        try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "FloatBro-Android-App")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(responseText)

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val isDraft = item.optBoolean("draft", false)
                    if (isDraft) continue

                    val isPrerelease = item.optBoolean("prerelease", false)
                    val tagName = item.optString("tag_name", "")
                    // 仅当远端版本高于本地当前版本时，才作为升级候选 (对齐 PiliPalaZ)
                    if (!isNewerVersion(tagName, currentVersion)) {
                        continue
                    }

                    val name = item.optString("name", tagName)
                    val body = item.optString("body", "")
                    val publishedAt = item.optString("published_at", "").take(10)

                    val assets = item.optJSONArray("assets") ?: JSONArray()
                    var apkUrl = ""
                    var apkName = ""
                    var apkSize = 0L

                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkName = assetName
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }

                    if (apkUrl.isBlank()) {
                        apkUrl = item.optString("html_url", "")
                        apkName = "FloatBro-$tagName.apk"
                    }

                    val release = ReleaseInfo(
                        tagName = tagName,
                        name = name,
                        body = body,
                        publishedAt = publishedAt,
                        isPrerelease = isPrerelease,
                        downloadUrl = apkUrl,
                        apkFileName = apkName,
                        apkSizeBytes = apkSize
                    )

                    if (isPrerelease && prereleaseRelease == null) {
                        prereleaseRelease = release
                    } else if (!isPrerelease && stableRelease == null) {
                        stableRelease = release
                    }

                    if (stableRelease != null && prereleaseRelease != null) break
                }
            } else if (responseCode == 404) {
                // 仓库尚未发布任何 Release，无可用版本
            } else {
                throw java.io.IOException("检查更新服务响应异常 (HTTP $responseCode)")
            }
        } catch (e: Exception) {
            // 网络异常向上抛出，使 UI 呈现真实错误态与重试按钮
            throw e
        }

        return UpdateCandidates(
            currentVersion = currentVersion,
            stable = stableRelease,
            prerelease = prereleaseRelease
        )
    }

    fun enqueueDownload(release: ReleaseInfo): Long {
        val uri = Uri.parse(release.downloadUrl)
        val updateDir = getUpdateDirectory()
        if (!updateDir.exists()) updateDir.mkdirs()

        val destFile = File(updateDir, release.apkFileName)
        if (destFile.exists()) {
            destFile.delete()
        }

        val request = DownloadManager.Request(uri)
            .setTitle("FloatBro ${release.tagName}")
            .setDescription("正在下载新版本安装包...")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${release.apkFileName}"
            )

        return downloadManager.enqueue(request)
    }

    fun queryProgress(downloadId: Long): DownloadProgress? {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
            DownloadProgress(downloadId, status, downloaded, total, percent)
        }
    }

    fun cancelDownload(downloadId: Long) {
        try {
            downloadManager.remove(downloadId)
        } catch (_: Exception) {}
    }

    fun getDownloadedFile(release: ReleaseInfo): File {
        return File(getUpdateDirectory(), release.apkFileName)
    }

    fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    fun installApk(file: File) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getUpdateDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        return File(base, "updates")
    }

    companion object {
        const val DEFAULT_REPO_OWNER = "gxwane"
        const val DEFAULT_REPO_NAME = "floatbro"

        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            return compareSemVer(remoteTag, currentVersion) > 0
        }

        private fun cleanVersion(tag: String): String {
            return tag.trim().removePrefix("v").removePrefix("V")
        }

        fun compareSemVer(v1: String, v2: String): Int {
            val (core1, pre1) = parseVersion(v1)
            val (core2, pre2) = parseVersion(v2)

            val maxCoreLen = maxOf(core1.size, core2.size)
            for (i in 0 until maxCoreLen) {
                val p1 = core1.getOrElse(i) { 0 }
                val p2 = core2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }

            // Core versions are equal. SemVer rule: normal version > prerelease version
            if (pre1.isNullOrEmpty() && !pre2.isNullOrEmpty()) {
                return 1
            }
            if (!pre1.isNullOrEmpty() && pre2.isNullOrEmpty()) {
                return -1
            }
            if (!pre1.isNullOrEmpty() && !pre2.isNullOrEmpty()) {
                val preParts1 = pre1.split(".")
                val preParts2 = pre2.split(".")
                val maxPreLen = maxOf(preParts1.size, preParts2.size)
                for (i in 0 until maxPreLen) {
                    val part1 = preParts1.getOrElse(i) { "" }
                    val part2 = preParts2.getOrElse(i) { "" }
                    val num1 = part1.toIntOrNull()
                    val num2 = part2.toIntOrNull()
                    if (num1 != null && num2 != null) {
                        if (num1 != num2) return num1.compareTo(num2)
                    } else {
                        val cmp = part1.compareTo(part2)
                        if (cmp != 0) return cmp
                    }
                }
            }

            return 0
        }

        private fun parseVersion(ver: String): Pair<List<Int>, String?> {
            val clean = cleanVersion(ver)
            val dashIndex = clean.indexOf('-')
            val plusIndex = clean.indexOf('+')
            val splitIndex = when {
                dashIndex != -1 && plusIndex != -1 -> minOf(dashIndex, plusIndex)
                dashIndex != -1 -> dashIndex
                plusIndex != -1 -> plusIndex
                else -> -1
            }

            val coreStr = if (splitIndex != -1) clean.substring(0, splitIndex) else clean
            val preStr = if (dashIndex != -1) {
                val end = if (plusIndex != -1 && plusIndex > dashIndex) plusIndex else clean.length
                clean.substring(dashIndex + 1, end)
            } else null

            val coreParts = coreStr.split(".").mapNotNull { it.toIntOrNull() }
            return Pair(coreParts, preStr)
        }
    }
}
