package com.pxuzy.floatingpen

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.pxuzy.floatingpen.core.ArrowGeometry
import com.pxuzy.floatingpen.core.DrawingElement
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Renders persisted drawing elements without owning gesture or session state. */
class DrawingElementRenderer(
    private val density: Float,
    private val paint: Paint,
) {
    private val strokePath = Path()
    private val arrowHeadPath = Path()

    fun draw(canvas: Canvas, element: DrawingElement) {
        paint.color = element.drawColor
        paint.strokeWidth = element.drawWidth
        when (element) {
            is DrawingElement.Stroke -> drawStroke(canvas, element)
            is DrawingElement.Line -> canvas.drawLine(
                element.start.first,
                element.start.second,
                element.end.first,
                element.end.second,
                paint,
            )
            is DrawingElement.Arrow -> drawArrow(canvas, element)
            is DrawingElement.Rect -> canvas.drawRect(
                minOf(element.start.first, element.end.first),
                minOf(element.start.second, element.end.second),
                maxOf(element.start.first, element.end.first),
                maxOf(element.start.second, element.end.second),
                paint,
            )
            is DrawingElement.Circle -> canvas.drawCircle(
                element.center.first,
                element.center.second,
                element.radius,
                paint,
            )
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: DrawingElement.Stroke) {
        if (stroke.points.size < 2) return
        strokePath.rewind()
        strokePath.moveTo(stroke.points[0].first, stroke.points[0].second)
        for (index in 1 until stroke.points.size) {
            strokePath.lineTo(stroke.points[index].first, stroke.points[index].second)
        }
        canvas.drawPath(strokePath, paint)
    }

    private fun drawArrow(canvas: Canvas, arrow: DrawingElement.Arrow) {
        val headLength = arrow.headLengthDp * density
        val base = ArrowGeometry.headBasePoint(
            arrow.start.first,
            arrow.start.second,
            arrow.end.first,
            arrow.end.second,
            headLength,
        )
        canvas.drawLine(arrow.start.first, arrow.start.second, base.first, base.second, paint)
        val angle = atan2(
            (arrow.end.second - arrow.start.second).toDouble(),
            (arrow.end.first - arrow.start.first).toDouble(),
        )
        val halfWidth = headLength * 0.45f
        val perpendicularX = (-sin(angle) * halfWidth).toFloat()
        val perpendicularY = (cos(angle) * halfWidth).toFloat()
        arrowHeadPath.rewind()
        arrowHeadPath.moveTo(arrow.end.first, arrow.end.second)
        arrowHeadPath.lineTo(base.first + perpendicularX, base.second + perpendicularY)
        arrowHeadPath.lineTo(base.first - perpendicularX, base.second - perpendicularY)
        arrowHeadPath.close()
        val previousStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(arrowHeadPath, paint)
        paint.style = previousStyle
    }
}
