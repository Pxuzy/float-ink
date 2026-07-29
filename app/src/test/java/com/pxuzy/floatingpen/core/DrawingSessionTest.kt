package com.pxuzy.floatingpen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingSessionTest {

    @Test
    fun `new session starts with one board and one default layer`() {
        val session = DrawingSession()

        assertEquals("画板 1", session.currentBoard.name)
        assertEquals("默认图层", session.currentLayer.name)
        assertEquals(1, session.boards.size)
        assertEquals(1, session.currentBoard.layers.size)
    }

    @Test
    fun `new board starts with its own default layer and becomes current`() {
        val session = DrawingSession()
        val board = session.createBoard()

        assertEquals("画板 2", board.name)
        assertEquals(board.id, session.currentBoard.id)
        assertEquals("默认图层", session.currentLayer.name)
        assertEquals(1, board.layers.size)
    }

    @Test
    fun `selecting board exposes only that board layers`() {
        val session = DrawingSession()
        val firstBoardId = session.currentBoard.id
        val secondBoard = session.createBoard()
        session.createLayer("重点标记")

        session.selectBoard(firstBoardId)

        assertEquals(firstBoardId, session.currentBoard.id)
        assertEquals(1, session.currentBoard.layers.size)
        assertEquals(secondBoard.id, session.boards[1].id)
        assertEquals(2, session.boards[1].layers.size)
    }

    @Test
    fun `new layer is inserted above current and becomes current`() {
        val session = DrawingSession()
        val defaultLayerId = session.currentLayer.id

        val layer = session.createLayer()

        assertEquals("图层 1", layer.name)
        assertEquals(layer.id, session.currentLayer.id)
        assertEquals(listOf(layer.id, defaultLayerId), session.currentBoard.layers.map { it.id })
    }

    @Test
    fun `selecting hidden layer makes it visible and current`() {
        val session = DrawingSession()
        val layer = session.createLayer("标记")
        session.setLayerVisible(layer.id, false)

        session.selectLayer(layer.id)

        assertEquals(layer.id, session.currentLayer.id)
        assertTrue(session.currentLayer.visible)
    }

    @Test
    fun `undo and clear affect only current layer`() {
        val session = DrawingSession()
        val firstLayer = session.currentLayer
        val firstElement = DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f)
        session.addElement(firstElement)
        val secondLayer = session.createLayer("重点")
        val secondElement = DrawingElement.Rect(1f to 1f, 20f to 20f, 2, 3f)
        session.addElement(secondElement)

        session.undo()
        assertTrue(secondLayer.elements.isEmpty())
        assertEquals(listOf(firstElement), firstLayer.elements)

        session.addElement(secondElement)
        session.clearCurrentLayer()
        assertTrue(secondLayer.elements.isEmpty())
        assertEquals(listOf(firstElement), firstLayer.elements)
    }

    @Test
    fun `recoverable clear restores only the active layer and a new element expires restore`() {
        val session = DrawingSession()
        val bottom = session.currentLayer
        val bottomElement = DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f)
        session.addElement(bottomElement)
        val top = session.createLayer("重点")
        val topElement = DrawingElement.Rect(1f to 1f, 20f to 20f, 2, 3f)
        session.addElement(topElement)

        assertTrue(session.clearCurrentLayerRecoverably())
        assertTrue(top.elements.isEmpty())
        assertEquals(listOf(bottomElement), bottom.elements)
        assertTrue(session.restoreClearedCurrentLayer())
        assertEquals(listOf(topElement), top.elements)

        assertTrue(session.clearCurrentLayerRecoverably())
        session.addElement(DrawingElement.Circle(5f to 5f, 4f, 3, 2f))
        assertFalse(session.restoreClearedCurrentLayer())
        assertEquals(1, top.elements.size)
    }

    @Test
    fun `switching layer expires recoverable clear snapshot`() {
        val session = DrawingSession()
        session.addElement(DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f))
        val other = session.createLayer("其他")

        session.selectLayer(session.currentBoard.layers.last().id)
        assertTrue(session.clearCurrentLayerRecoverably())
        session.selectLayer(other.id)

        assertFalse(session.restoreClearedCurrentLayer())
    }

    @Test
    fun `structural session changes expire recoverable clear snapshot`() {
        val session = DrawingSession()
        val first = session.currentLayer
        session.addElement(DrawingElement.Line(0f to 0f, 10f to 10f, 1, 2f))
        val second = session.createLayer("第二层")
        session.addElement(DrawingElement.Rect(1f to 1f, 20f to 20f, 2, 3f))

        assertTrue(session.clearCurrentLayerRecoverably())
        assertTrue(session.deleteLayer(second.id))
        assertFalse(session.restoreClearedCurrentLayer())

        session.selectLayer(first.id)
        assertTrue(session.clearCurrentLayerRecoverably())
        session.clear()
        assertFalse(session.restoreClearedCurrentLayer())

        session.addElement(DrawingElement.Circle(5f to 5f, 4f, 3, 2f))
        assertTrue(session.clearCurrentLayerRecoverably())
        session.replaceFrom(DrawingSession())
        assertFalse(session.restoreClearedCurrentLayer())
    }

    @Test
    fun `deleting current layer selects adjacent layer and keeps one layer`() {
        val session = DrawingSession()
        val defaultLayerId = session.currentLayer.id
        val second = session.createLayer("第二层")
        val third = session.createLayer("第三层")

        session.deleteLayer(third.id)
        assertEquals(second.id, session.currentLayer.id)

        session.deleteLayer(second.id)
        assertEquals(defaultLayerId, session.currentLayer.id)
        assertEquals(1, session.currentBoard.layers.size)

        session.deleteLayer(defaultLayerId)
        assertEquals(1, session.currentBoard.layers.size)
    }

    @Test
    fun `deleting current board selects previous board and keeps one board`() {
        val session = DrawingSession()
        val firstBoardId = session.currentBoard.id
        val second = session.createBoard()
        val third = session.createBoard()

        session.deleteBoard(second.id)
        assertEquals(third.id, session.currentBoard.id)

        session.deleteBoard(third.id)
        assertEquals(firstBoardId, session.currentBoard.id)
        assertEquals(1, session.boards.size)

        session.deleteBoard(firstBoardId)
        assertEquals(1, session.boards.size)
        assertNotNull(session.currentBoard)
    }

    @Test
    fun `reordering layers changes display order without changing elements`() {
        val session = DrawingSession()
        val bottom = session.currentLayer
        val element = DrawingElement.Stroke(mutableListOf(0f to 0f, 1f to 1f), 1, 2f)
        session.addElement(element)
        val top = session.createLayer("顶部")

        session.moveLayer(top.id, 1)

        assertEquals(listOf(bottom.id, top.id), session.currentBoard.layers.map { it.id })
        assertEquals(listOf(element), bottom.elements)
    }

    @Test
    fun `visible layers are exposed in bottom to top render order`() {
        val session = DrawingSession()
        val bottom = session.currentLayer
        val top = session.createLayer("顶部")
        val middle = session.createLayer("中间")
        session.setLayerVisible(middle.id, false)

        assertEquals(listOf(bottom.id, top.id), session.visibleLayersBottomToTop().map { it.id })
    }

    @Test
    fun `renaming board and layer trims names and rejects blank names`() {
        val session = DrawingSession()
        val boardId = session.currentBoard.id
        val layerId = session.currentLayer.id

        session.renameBoard(boardId, "  讲解板  ")
        session.renameLayer(layerId, "  重点  ")

        assertEquals("讲解板", session.currentBoard.name)
        assertEquals("重点", session.currentLayer.name)
        assertFalse(session.renameBoard(boardId, "   "))
        assertFalse(session.renameLayer(layerId, "   "))
    }
}
