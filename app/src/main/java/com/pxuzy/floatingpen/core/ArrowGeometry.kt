package com.pxuzy.floatingpen.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object ArrowGeometry {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f

    fun headLengthDp(strokeWidthDp: Float, arrowScale: Float): Float =
        (strokeWidthDp * arrowScale.coerceIn(MIN_SCALE, MAX_SCALE)).coerceIn(8f, 96f)

    fun headBasePoint(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        headLengthPx: Float,
    ): Pair<Float, Float> {
        val deltaX = endX - startX
        val deltaY = endY - startY
        val distance = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
        if (distance == 0f) return endX to endY
        val effectiveHeadLength = headLengthPx.coerceAtMost(distance)
        val angle = atan2(deltaY.toDouble(), deltaX.toDouble())
        return (
            endX - effectiveHeadLength * cos(angle).toFloat()
        ) to (
            endY - effectiveHeadLength * sin(angle).toFloat()
        )
    }
}
