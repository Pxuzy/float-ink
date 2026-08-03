package com.pxuzy.floatingpen

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FloatInkSessionAutoSaver(
    private val session: com.pxuzy.floatingpen.core.DrawingSession,
    private val save: (session: com.pxuzy.floatingpen.core.DrawingSession, sessionId: String) -> Unit,
    private val delayMs: Long = 800L,
    private val sessionId: String = "session-${UUID.randomUUID()}",
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var dirty = false

    fun markDirty() {
        dirty = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ saveNowIfDirty() }, delayMs)
    }

    fun saveNowIfDirty() {
        if (!dirty) return
        dirty = false
        executor.execute { save(session, sessionId) }
    }

    fun saveNow() {
        dirty = true
        saveNowIfDirty()
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        saveNowIfDirty()
        executor.shutdown()
        awaitPendingSaves()
    }

    private fun awaitPendingSaves() {
        var interrupted = false
        while (true) {
            try {
                if (executor.awaitTermination(1, TimeUnit.SECONDS)) break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
