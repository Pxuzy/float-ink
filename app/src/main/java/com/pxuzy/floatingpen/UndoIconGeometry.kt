package com.pxuzy.floatingpen

/** Geometry for a standard counterclockwise undo glyph, independent of Canvas size. */
object UndoIconGeometry {
    data class Values(
        val startDegrees: Float,
        val sweepDegrees: Float,
        val arrowTipX: Float,
        val arrowTipY: Float,
        val arrowWingUpperX: Float,
        val arrowWingUpperY: Float,
        val arrowWingLowerX: Float,
        val arrowWingLowerY: Float,
    )

    fun forRadius(radius: Float): Values = Values(
        startDegrees = 222f,
        sweepDegrees = -188f,
        arrowTipX = -radius * 0.76f,
        arrowTipY = -radius * 0.26f,
        arrowWingUpperX = -radius * 0.28f,
        arrowWingUpperY = -radius * 0.70f,
        arrowWingLowerX = -radius * 0.17f,
        arrowWingLowerY = radius * 0.10f,
    )
}
