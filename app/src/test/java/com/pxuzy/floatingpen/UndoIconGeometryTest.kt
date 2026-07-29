package com.pxuzy.floatingpen

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
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

    @Test
    fun `undo arrowhead tip connects to the left upper arc endpoint`() {
        val radius = 12f
        val geometry = UndoIconGeometry.forRadius(radius)
        val radians = Math.toRadians(geometry.startDegrees.toDouble())
        val arcEndpointX = radius * cos(radians).toFloat()
        val arcEndpointY = radius * sin(radians).toFloat()

        assertEquals(arcEndpointX, geometry.arrowTipX, 0.01f)
        assertEquals(arcEndpointY, geometry.arrowTipY, 0.01f)
    }
}
