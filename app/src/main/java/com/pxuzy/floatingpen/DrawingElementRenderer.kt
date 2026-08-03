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

    fun drawPreview(
        canvas: Canvas,
        toolId: String,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        color: Int,
        strokeWidth: Float,
        arrowHeadLengthDp: Float,
    ) {
        paint.color = color
        paint.strokeWidth = strokeWidth
        when (toolId) {
            "line" -> canvas.drawLine(startX, startY, endX, endY, paint)
            "arrow" -> drawArrow(canvas, startX, startY, endX, endY, arrowHeadLengthDp * density)
            "rect" -> canvas.drawRect(
                minOf(startX, endX),
                minOf(startY, endY),
                maxOf(startX, endX),
                maxOf(startY, endY),
                paint,
            )
            "circle" -> canvas.drawCircle(
                startX,
                startY,
                maxOf(kotlin.math.abs(endX - startX), kotlin.math.abs(endY - startY)),
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

    private fun drawArrow(canvas: Canvas, arrow: DrawingElement.Arrow) = drawArrow(
        canvas,
        arrow.start.first,
        arrow.start.second,
        arrow.end.first,
        arrow.end.second,
        arrow.headLengthDp * density,
    )

    private fun drawArrow(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        headLength: Float,
    ) {
        val base = ArrowGeometry.headBasePoint(startX, startY, endX, endY, headLength)
        canvas.drawLine(startX, startY, base.first, base.second, paint)
        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val halfWidth = headLength * 0.45f
        val perpendicularX = (-sin(angle) * halfWidth).toFloat()
        val perpendicularY = (cos(angle) * halfWidth).toFloat()
        arrowHeadPath.rewind()
        arrowHeadPath.moveTo(endX, endY)
        arrowHeadPath.lineTo(base.first + perpendicularX, base.second + perpendicularY)
        arrowHeadPath.lineTo(base.first - perpendicularX, base.second - perpendicularY)
        arrowHeadPath.close()
        val previousStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(arrowHeadPath, paint)
        paint.style = previousStyle
    }
}
