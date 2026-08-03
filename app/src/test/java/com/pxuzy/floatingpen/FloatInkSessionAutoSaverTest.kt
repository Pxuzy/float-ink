package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.DrawingElement
import com.pxuzy.floatingpen.core.DrawingSession
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
}
