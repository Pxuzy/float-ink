package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.DrawingElement
import kotlin.math.abs
import kotlin.math.hypot

/** Tests whether a local eraser touch overlaps a persisted drawing element. */
object DrawingElementHitTester {
    fun hits(element: DrawingElement, x: Float, y: Float, eraserRadius: Float): Boolean = when (element) {
        is DrawingElement.Stroke -> element.points.zipWithNext().any { (start, end) ->
            distanceToSegment(x, y, start.first, start.second, end.first, end.second) <= eraserRadius + element.width / 2f
        } || element.points.any { point -> hypot(x - point.first, y - point.second) <= eraserRadius + element.width / 2f }
        is DrawingElement.Line -> distanceToSegment(x, y, element.start.first, element.start.second, element.end.first, element.end.second) <= eraserRadius + element.width / 2f
        is DrawingElement.Arrow -> distanceToSegment(x, y, element.start.first, element.start.second, element.end.first, element.end.second) <= eraserRadius + element.width / 2f
        is DrawingElement.Rect -> {
            val left = minOf(element.start.first, element.end.first)
            val right = maxOf(element.start.first, element.end.first)
            val top = minOf(element.start.second, element.end.second)
            val bottom = maxOf(element.start.second, element.end.second)
            listOf(
                distanceToSegment(x, y, left, top, right, top),
                distanceToSegment(x, y, right, top, right, bottom),
                distanceToSegment(x, y, right, bottom, left, bottom),
                distanceToSegment(x, y, left, bottom, left, top),
            ).any { it <= eraserRadius + element.width / 2f }
        }
        is DrawingElement.Circle -> abs(hypot(x - element.center.first, y - element.center.second) - element.radius) <= eraserRadius + element.width / 2f
    }

    private fun distanceToSegment(x: Float, y: Float, startX: Float, startY: Float, endX: Float, endY: Float): Float {
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(x - startX, y - startY)
        val ratio = (((x - startX) * dx + (y - startY) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(x - (startX + ratio * dx), y - (startY + ratio * dy))
    }
}
