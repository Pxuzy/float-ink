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

    private companion object {
        /** Pressure of 0 keeps MIN_PRESSURE_FACTOR of the base width. */
        const val MIN_PRESSURE_FACTOR = 0.3f
    }

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
        if (stroke.pressures.size != stroke.points.size) {
            // Legacy or pressure-less stroke: draw with the fixed width.
            canvas.drawPath(buildSmoothStrokePath(stroke.points), paint)
            return
        }
        val baseWidth = paint.strokeWidth
        val previousStyle = paint.style
        paint.style = Paint.Style.STROKE
        try {
            drawPressureSegments(canvas, stroke)
        } finally {
            paint.strokeWidth = baseWidth
            paint.style = previousStyle
        }
    }

    /**
     * Draws each smoothed segment with a width derived from its control
     * point's pressure. Segments match the smoothing scheme: for samples
     * p0..pn, segment i spans mid(i-1)→mid(i) with control point p[i], and the
     * final segment is a straight line to p[n].
     */
    private fun drawPressureSegments(canvas: Canvas, stroke: DrawingElement.Stroke) {
        val points = stroke.points
        val pressures = stroke.pressures
        val baseWidth = stroke.width
        strokePath.rewind()
        strokePath.moveTo(points[0].first, points[0].second)
        if (points.size == 2) {
            paint.strokeWidth = pressureWidth(baseWidth, pressures[0])
            strokePath.lineTo(points[1].first, points[1].second)
            canvas.drawPath(strokePath, paint)
            return
        }
        var prevMidX = points[0].first
        var prevMidY = points[0].second
        for (index in 1 until points.size - 1) {
            val current = points[index]
            val next = points[index + 1]
            val midX = (current.first + next.first) / 2f
            val midY = (current.second + next.second) / 2f
            strokePath.rewind()
            strokePath.moveTo(prevMidX, prevMidY)
            strokePath.quadTo(current.first, current.second, midX, midY)
            paint.strokeWidth = pressureWidth(baseWidth, pressures[index])
            canvas.drawPath(strokePath, paint)
            prevMidX = midX
            prevMidY = midY
        }
        val last = points.last()
        strokePath.rewind()
        strokePath.moveTo(prevMidX, prevMidY)
        strokePath.lineTo(last.first, last.second)
        paint.strokeWidth = pressureWidth(baseWidth, pressures.last())
        canvas.drawPath(strokePath, paint)
    }

    /** Maps pressure [0,1] onto a stroke width between MIN_FACTOR and 100% of base. */
    internal fun pressureWidth(baseWidth: Float, pressure: Float): Float {
        val normalized = pressure.coerceIn(0f, 1f)
        return baseWidth * (MIN_PRESSURE_FACTOR + (1f - MIN_PRESSURE_FACTOR) * normalized)
    }

    /**
     * Builds the stroke path with midpoint quadratic smoothing: instead of
     * connecting samples with straight lines (visible corners on fast strokes),
     * each sample becomes a quadTo control point and the curve passes through
     * the midpoints between consecutive samples. Classic smoothing used by
     * mainstream drawing apps; endpoints stay exact.
     */
    internal fun buildSmoothStrokePath(points: List<Pair<Float, Float>>): Path {
        strokePath.rewind()
        if (points.size < 2) return strokePath
        strokePath.moveTo(points[0].first, points[0].second)
        if (points.size == 2) {
            strokePath.lineTo(points[1].first, points[1].second)
            return strokePath
        }
        for (index in 1 until points.size - 1) {
            val current = points[index]
            val next = points[index + 1]
            strokePath.quadTo(
                current.first, current.second,
                (current.first + next.first) / 2f,
                (current.second + next.second) / 2f,
            )
        }
        val last = points.last()
        strokePath.lineTo(last.first, last.second)
        return strokePath
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
