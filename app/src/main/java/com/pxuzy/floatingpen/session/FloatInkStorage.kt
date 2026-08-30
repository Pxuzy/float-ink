package com.pxuzy.floatingpen

import android.content.Context
import android.os.Environment
import java.io.File

object FloatInkStorage {
    private const val ROOT_DIR = "FloatInk"
    private const val SESSION_DIR = "sessions"

    fun rootDirectory(context: Context): File {
        val root = File(Environment.getExternalStorageDirectory(), ROOT_DIR)
        if (root.exists() || root.mkdirs()) return root
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fallback = File(downloads, ROOT_DIR)
        if (fallback.exists() || fallback.mkdirs()) return fallback
        return File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT_DIR)
    }

    fun sessionsDirectory(context: Context): File = File(rootDirectory(context), SESSION_DIR).also { it.mkdirs() }

    fun sessionFile(context: Context, sessionId: String): File =
        File(sessionsDirectory(context), "$sessionId.floatink")
}
