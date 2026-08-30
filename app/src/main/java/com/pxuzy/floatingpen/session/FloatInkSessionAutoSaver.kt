package com.pxuzy.floatingpen

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Debounced auto-saver for a live DrawingSession.
 *
 * Thread-safety model: [markDirty] runs on the main thread and immediately
 * captures a deep copy of the session, so the background encoder can never
 * observe a half-updated stroke (torn snapshot) or a list mutated mid-iteration
 * (ConcurrentModificationException). A single lock serializes file writes so
 * the synchronous fallback in [close] can never interleave with an in-flight
 * background save.
 */
class FloatInkSessionAutoSaver(
    private val session: com.pxuzy.floatingpen.core.DrawingSession,
    private val save: (session: com.pxuzy.floatingpen.core.DrawingSession, sessionId: String) -> Unit,
    private val delayMs: Long = 800L,
    private val sessionId: String = "session-${UUID.randomUUID()}",
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val saveLock = Any()
    private var dirty = false
    private var pendingSnapshot: com.pxuzy.floatingpen.core.DrawingSession? = null

    fun markDirty() {
        pendingSnapshot = session.deepCopy()
        dirty = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ saveNowIfDirty() }, delayMs)
    }

    fun saveNowIfDirty() {
        if (!dirty) return
        dirty = false
        val snapshot = pendingSnapshot ?: return
        pendingSnapshot = null
        executor.execute { synchronized(saveLock) { save(snapshot, sessionId) } }
    }

    fun close(): Boolean {
        handler.removeCallbacksAndMessages(null)
        saveNowIfDirty()
        executor.shutdown()
        if (awaitPendingSaves()) return true
        // Background save exceeded the timeout. Never exit with unsaved work:
        // write the current state synchronously as a last resort. The lock
        // serializes with any still-running background task.
        return synchronized(saveLock) {
            runCatching {
                save(session.deepCopy(), sessionId)
                true
            }.getOrDefault(false)
        }
    }

    private fun awaitPendingSaves(): Boolean {
        return try {
            executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_SECONDS = 3L
    }
}
