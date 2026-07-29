package com.pxuzy.floatingpen

import org.junit.Assert.assertTrue
import org.junit.Test

class UndoIconGeometryTest {
    @Test
    fun `undo glyph is an open counterclockwise arc with a left upper arrowhead`() {
        val geometry = UndoIconGeometry.forRadius(12f)

        assertTrue(geometry.sweepDegrees < 0f)
        assertTrue(geometry.sweepDegrees > -220f)
        assertTrue(geometry.arrowTipX < 0f)
        assertTrue(geometry.arrowTipY < 0f)
        assertTrue(geometry.arrowWingUpperY < geometry.arrowTipY)
        assertTrue(geometry.arrowWingLowerY > geometry.arrowTipY)
    }
}
