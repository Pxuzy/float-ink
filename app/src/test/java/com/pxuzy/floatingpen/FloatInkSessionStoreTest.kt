package com.pxuzy.floatingpen

import android.content.Context
import com.pxuzy.floatingpen.core.DrawingElement
import com.pxuzy.floatingpen.core.DrawingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FloatInkSessionStoreTest {
    private val context = org.robolectric.RuntimeEnvironment.getApplication()

    @Test
    fun `session round trip preserves boards layers elements and selection`() {
        val session = DrawingSession()
        val firstBoard = session.currentBoard
        session.addElement(DrawingElement.Line(0f to 1f, 10f to 11f, 0xFF112233.toInt(), 4f))
        val secondBoard = session.createBoard("讲解板")
        val secondLayer = session.createLayer("重点")
        session.addElement(DrawingElement.Arrow(1f to 2f, 20f to 30f, 0xFF445566.toInt(), 6f, 18f))

        val encoded = FloatInkSessionCodec.encode(session, "session-test")
        val restored = FloatInkSessionCodec.decode(encoded)

        assertEquals("session-test", restored.sessionId)
        assertEquals(2, restored.session.boards.size)
        assertEquals(firstBoard.name, restored.session.boards[0].name)
        assertEquals(1, restored.session.boards[0].layers[0].elements.size)
        assertEquals(secondBoard.name, restored.session.currentBoard.name)
        assertEquals(secondLayer.name, restored.session.currentLayer.name)
        assertTrue(restored.session.currentLayer.elements.single() is DrawingElement.Arrow)
    }

    @Test
    fun `store writes atomically and reads a floatink file`() {
        val file = File(context.cacheDir, "test.floatink")
        val session = DrawingSession()
        session.addElement(DrawingElement.Stroke(mutableListOf(1f to 2f, 3f to 4f), 7, 8f))

        FloatInkSessionStore.save(file, session, "atomic-test")
        val restored = FloatInkSessionStore.load(file)

        assertEquals("atomic-test", restored.sessionId)
        assertEquals(1, restored.session.currentLayer.elements.size)
        assertTrue(!File(file.path + ".tmp").exists())
    }
}
