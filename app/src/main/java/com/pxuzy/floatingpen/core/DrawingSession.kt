package com.pxuzy.floatingpen.core

import java.util.UUID

/**
 * In-memory drawing state for one running FloatInk session.
 * Persistence is intentionally kept out of this model for the first increment.
 */
class DrawingSession(
    initialBoard: DrawingBoard? = null,
) {
    private var nextBoardNumber = 1
    private var nextLayerNumber = 1

    val boards: MutableList<DrawingBoard> = mutableListOf(
        initialBoard ?: newBoardInternal("画板 1"),
    )

    private var activeBoardId: String = boards.first().id

    init {
        nextBoardNumber = boards.size + 1
    }

    val currentBoard: DrawingBoard
        get() = boards.first { it.id == activeBoardId }

    val currentLayer: DrawingLayer
        get() = currentBoard.layers.first { it.id == currentBoard.activeLayerId }

    /** Layers are stored top-first for the management UI, but Canvas must compose bottom-first. */
    fun visibleLayersBottomToTop(): List<DrawingLayer> =
        currentBoard.layers.asReversed().filter { it.visible }

    fun createBoard(name: String? = null): DrawingBoard {
        val board = newBoardInternal(name ?: "画板 ${nextBoardNumber++}")
        boards += board
        activeBoardId = board.id
        return board
    }

    fun selectBoard(boardId: String): Boolean {
        if (boards.none { it.id == boardId }) return false
        activeBoardId = boardId
        return true
    }

    fun renameBoard(boardId: String, name: String): Boolean {
        val board = boards.find { it.id == boardId } ?: return false
        val normalized = name.trim()
        if (normalized.isEmpty()) return false
        board.name = normalized
        return true
    }

    fun deleteBoard(boardId: String): Boolean {
        if (boards.size <= 1) return false
        val index = boards.indexOfFirst { it.id == boardId }
        if (index < 0) return false
        val wasCurrent = boardId == activeBoardId
        boards.removeAt(index)
        if (wasCurrent) {
            activeBoardId = boards[(index - 1).coerceAtLeast(0).coerceAtMost(boards.lastIndex)].id
        }
        return true
    }

    fun createLayer(name: String? = null): DrawingLayer {
        val board = currentBoard
        val currentIndex = board.layers.indexOfFirst { it.id == board.activeLayerId }
        val layer = DrawingLayer(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "图层 ${nextLayerNumber++}",
        )
        board.layers.add(currentIndex.coerceIn(0, board.layers.size), layer)
        board.activeLayerId = layer.id
        return layer
    }

    fun selectLayer(layerId: String): Boolean {
        val layer = currentBoard.layers.find { it.id == layerId } ?: return false
        layer.visible = true
        currentBoard.activeLayerId = layer.id
        return true
    }

    fun renameLayer(layerId: String, name: String): Boolean {
        val layer = currentBoard.layers.find { it.id == layerId } ?: return false
        val normalized = name.trim()
        if (normalized.isEmpty()) return false
        layer.name = normalized
        return true
    }

    fun setLayerVisible(layerId: String, visible: Boolean): Boolean {
        val layer = currentBoard.layers.find { it.id == layerId } ?: return false
        layer.visible = visible
        return true
    }

    fun deleteLayer(layerId: String): Boolean {
        val board = currentBoard
        if (board.layers.size <= 1) return false
        val index = board.layers.indexOfFirst { it.id == layerId }
        if (index < 0) return false
        val wasCurrent = layerId == board.activeLayerId
        board.layers.removeAt(index)
        if (wasCurrent) {
            board.activeLayerId = board.layers[(index - 1).coerceAtLeast(0).coerceAtMost(board.layers.lastIndex)].id
        }
        return true
    }

    fun moveLayer(layerId: String, targetIndex: Int): Boolean {
        val board = currentBoard
        val sourceIndex = board.layers.indexOfFirst { it.id == layerId }
        if (sourceIndex < 0) return false
        val layer = board.layers.removeAt(sourceIndex)
        board.layers.add(targetIndex.coerceIn(0, board.layers.size), layer)
        return true
    }

    fun addElement(element: DrawingElement) {
        currentLayer.elements += element
    }

    fun undo(): DrawingElement? = currentLayer.elements.removeLastOrNull()

    fun clearCurrentLayer() {
        currentLayer.elements.clear()
    }

    fun clear() {
        boards.clear()
        val board = newBoardInternal("画板 1")
        boards += board
        activeBoardId = board.id
        nextBoardNumber = 2
        nextLayerNumber = 1
    }

    fun replaceFrom(source: DrawingSession) {
        boards.clear()
        boards += source.boards.map { board ->
            board.copy(
                layers = board.layers.map { layer ->
                    layer.copy(elements = layer.elements.toMutableList())
                }.toMutableList(),
            )
        }
        activeBoardId = source.currentBoard.id
        if (boards.none { it.id == activeBoardId }) activeBoardId = boards.first().id
    }

    private fun newBoardInternal(name: String): DrawingBoard {
        val layer = DrawingLayer(name = "默认图层")
        return DrawingBoard(name = name, layers = mutableListOf(layer), activeLayerId = layer.id)
    }
}

data class DrawingLayer(
    var name: String,
    val id: String = UUID.randomUUID().toString(),
    var visible: Boolean = true,
    val elements: MutableList<DrawingElement> = mutableListOf(),
)

data class DrawingBoard(
    var name: String,
    val id: String = UUID.randomUUID().toString(),
    val layers: MutableList<DrawingLayer> = mutableListOf(),
    var activeLayerId: String = "",
)
