package com.pxuzy.floatingpen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.pxuzy.floatingpen.core.FibonacciRetracement

/** Draws the temporary Fibonacci overlay independently from regular ink rendering. */
class FibonacciGuideRenderer(private val density: Float) {
    private val primaryColor = 0xFFE0A84B.toInt()
    private val secondaryColor = 0xCCB99A62.toInt()
    private val handleColor = Color.WHITE
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.STROKE
        strokeWidth = density
        strokeCap = Paint.Cap.ROUND
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = handleColor
        style = Paint.Style.FILL
    }

    fun draw(canvas: Canvas, state: FibonacciRenderState) {
        FibonacciRetracement.levels(state.start, state.end).forEach { level ->
            linePaint.color = if (level.emphasized) primaryColor else secondaryColor
            linePaint.strokeWidth = if (level.emphasized) 2f * density else density
            canvas.drawLine(0f, level.y, canvas.width.toFloat(), level.y, linePaint)
            labelPaint.color = if (level.emphasized) Color.WHITE else secondaryColor
            canvas.drawText(level.label, 12f * density, (level.y - 6f * density).coerceAtLeast(16f * density), labelPaint)
        }
        if (state.handlesVisible) drawHandles(canvas, state)
    }

    private fun drawHandles(canvas: Canvas, state: FibonacciRenderState) {
        canvas.drawLine(state.start.x, state.start.y, state.end.x, state.end.y, connectorPaint)
        canvas.drawCircle(state.start.x, state.start.y, 6f * density, handlePaint)
        canvas.drawCircle(state.end.x, state.end.y, 6f * density, handlePaint)
        canvas.drawCircle(state.moveHandle.x, state.moveHandle.y, 8f * density, handlePaint)
    }
}
