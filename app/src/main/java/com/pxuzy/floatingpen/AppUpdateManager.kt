package com.pxuzy.floatingpen

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases and hands downloaded APKs to Android's package installer. */
class AppUpdateManager(private val context: Context) {
    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseUrl: String,
    )

    fun check(onResult: (Result<UpdateInfo?>) -> Unit) {
        Thread {
            try {
                val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "FloatInk/${BuildConfig.VERSION_NAME}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("GitHub API HTTP ${connection.responseCode}")
                }
                val update = parseLatestRelease(body)
                val result = if (update != null && isNewer(update.version, BuildConfig.VERSION_NAME)) update else null
                Handler(Looper.getMainLooper()).post { onResult(Result.success(result)) }
            } catch (error: Exception) {
                Handler(Looper.getMainLooper()).post { onResult(Result.failure(error)) }
            }
        }.start()
    }

    fun downloadAndInstall(update: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("浮墨 ${update.version}")
            .setDescription("正在下载更新 APK")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "float-ink-${update.version}.apk")
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
    }

    fun installCompletedDownload(downloadId: Long): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return false
        cursor.use {
            if (!it.moveToFirst() || it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return false
            val path = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val file = Uri.parse(path).path?.let(::File) ?: return false
            if (!file.exists()) return false
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            return true
        }
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/Pxuzy/float-ink/releases/latest"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val PREFS = "app_update"
        private const val KEY_DOWNLOAD_ID = "download_id"

        fun savedDownloadId(context: Context): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DOWNLOAD_ID, -1L)

        fun parseLatestRelease(json: String): UpdateInfo? {
            val tag = json.stringValue("tag_name") ?: return null
            val releaseUrl = json.stringValue("html_url") ?: return null
            val apkUrl = Regex(
                "\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk(?:\\?[^\\\"]*)?)\\\"",
                RegexOption.IGNORE_CASE,
            ).find(json)?.groupValues?.get(1) ?: return null
            return UpdateInfo(tag.removePrefix("v"), apkUrl, releaseUrl)
        }

        fun isNewer(remote: String, local: String): Boolean = compareVersions(remote, local) > 0

        private fun compareVersions(left: String, right: String): Int {
            val a = left.removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = right.removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val result = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
                if (result != 0) return result
            }
            return 0
        }

        private fun String.stringValue(key: String): String? =
            Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(this)?.groupValues?.get(1)

    }
}
