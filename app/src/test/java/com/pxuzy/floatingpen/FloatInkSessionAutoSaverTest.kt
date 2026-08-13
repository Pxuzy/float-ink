package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.DrawingElement
import com.pxuzy.floatingpen.core.DrawingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class FloatInkSessionAutoSaverTest {
    @Test
    fun `close waits for the last save before callers can clear the session`() {
        val session = DrawingSession().apply {
            addElement(DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f))
        }
        val saveStarted = CountDownLatch(1)
        val allowSaveToFinish = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val saver = FloatInkSessionAutoSaver(
            session = session,
            save = { savedSession, _ ->
                saveStarted.countDown()
                allowSaveToFinish.await(2, TimeUnit.SECONDS)
                assertTrue(savedSession.currentLayer.elements.isNotEmpty())
            },
        )

        saver.markDirty()
        val closeThread = Thread {
            saver.close()
            closeReturned.countDown()
        }
        closeThread.start()

        assertTrue(saveStarted.await(2, TimeUnit.SECONDS))
        assertFalse(closeReturned.await(100, TimeUnit.MILLISECONDS))

        allowSaveToFinish.countDown()
        assertTrue(closeReturned.await(2, TimeUnit.SECONDS))
        closeThread.join(2_000)
        assertFalse(closeThread.isAlive)
    }

    @Test
    fun `background save encodes a snapshot not the live session`() {
        val session = DrawingSession()
        session.addElement(DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f))
        val savedSessions = java.util.concurrent.CopyOnWriteArrayList<DrawingSession>()
        val saver = FloatInkSessionAutoSaver(
            session = session,
            save = { savedSession, _ -> savedSessions += savedSession },
        )

        saver.markDirty()
        // Mutate the live session after the snapshot was captured.
        session.addElement(DrawingElement.Circle(50f to 50f, 10f, 2, 3f))

        saver.saveNowIfDirty()
        // Let the background task run.
        Thread.sleep(500)

        assertTrue(savedSessions.isNotEmpty())
        assertEquals(
            1,
            savedSessions.first().currentLayer.elements.size,
        )
    }

    @Test
    fun `close falls back to a synchronous save when the background task stalls`() {
        val session = DrawingSession().apply {
            addElement(DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f))
        }
        val backgroundCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val saver = FloatInkSessionAutoSaver(
            session = session,
            save = { _, _ ->
                backgroundCalls.incrementAndGet()
                // Stall longer than the 3s close timeout, but not forever:
                // the synchronous fallback waits on the write lock.
                Thread.sleep(4_000)
            },
        )

        saver.markDirty()
        val start = System.currentTimeMillis()
        val saved = saver.close()
        val elapsed = System.currentTimeMillis() - start

        assertTrue("close must report data saved even if the background task is slow", saved)
        assertTrue("close must finish shortly after the stalled background task", elapsed < 10_000)
    }
}
