package com.pxuzy.floatingpen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingElementTest {
    @Test
    fun `arrow geometry clamps scale and head size`() {
        assertEquals(8f, ArrowGeometry.headLengthDp(1f, 1f))
        assertEquals(24f, ArrowGeometry.headLengthDp(6f, 4f))
        assertEquals(96f, ArrowGeometry.headLengthDp(100f, 4f))
        assertEquals(6f to 0f, ArrowGeometry.headBasePoint(0f, 0f, 10f, 0f, 4f))
    }

    @Test
    fun `short arrow head base never crosses the start point`() {
        assertEquals(0f to 0f, ArrowGeometry.headBasePoint(0f, 0f, 5f, 0f, 20f))
    }

    @Test
    fun `core drawing elements retain style and geometry without Android types`() {
        val stroke = DrawingElement.Stroke(mutableListOf(1f to 2f, 3f to 4f), 0xFF123456.toInt(), 6f)
        val arrow = DrawingElement.Arrow(0f to 0f, 10f to 10f, 0xFFABCDEF.toInt(), 8f, 16f)

        assertEquals(0xFF123456.toInt(), stroke.drawColor)
        assertEquals(6f, stroke.drawWidth)
        assertEquals(0f to 0f, arrow.start)
        assertEquals(16f, arrow.headLengthDp)
        assertTrue(DrawingElement.tools.any { it.id == "arrow" })
    }

    @Test
    fun `tool definitions compare by stable id`() {
        assertEquals(ToolDef("pen", "其他名称", intArrayOf(1)), ToolDef("pen", "画笔", intArrayOf(2)))
    }
}
