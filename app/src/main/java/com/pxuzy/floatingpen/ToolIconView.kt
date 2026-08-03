package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Lightweight toolbar icon view owned by the drawing toolbar, not by its state machine. */
class ToolIconView(context: Context, private val icon: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arcBounds = RectF()
    private val tablerUndoIcon = if (icon == "undo") {
        context.getDrawable(R.drawable.ic_tabler_arrow_back_up)
    } else {
        null
    }

    init {
        val tablerName = when (icon) {
            "drag" -> "grip-vertical"
            "pen" -> "pen"
            "undo" -> "arrow-back-up"
            else -> null
        }
        if (tablerName != null) {
            setTag(R.id.tag_icon_family, "tabler")
            setTag(R.id.tag_icon_name, tablerName)
        }
    }

    fun setIconColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.24f
        when (icon) {
            "drag" -> drawDrag(canvas, cx, cy, r)
            "pen" -> drawPen(canvas, cx, cy, r)
            "line" -> canvas.drawLine(cx - r, cy + r, cx + r, cy - r, paint)
            "arrow" -> drawArrow(canvas, cx, cy, r)
            "rect" -> canvas.drawRoundRect(cx - r, cy - r * 0.75f, cx + r, cy + r * 0.75f, r * 0.16f, r * 0.16f, paint)
            "circle" -> canvas.drawCircle(cx, cy, r, paint)
            "guide" -> drawGuide(canvas, cx, cy, r)
            "more" -> drawMore(canvas, cx, cy, r)
            "canvas" -> drawCanvas(canvas, cx, cy, r)
            "layer" -> drawLayer(canvas, cx, cy, r)
            "undo" -> drawTablerUndoIcon(canvas, tablerUndoIcon, cx, cy)
            "redo" -> drawRedo(canvas, cx, cy, r)
            "clear" -> drawClear(canvas, cx, cy, r)
            "exit" -> {
                canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
                canvas.drawLine(cx + r, cy - r, cx - r, cy + r, paint)
            }
        }
    }

    private fun drawDrag(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        for (row in -1..1) {
            for (column in 0..1) {
                canvas.drawCircle(cx + (column - 0.5f) * r * 0.9f, cy + row * r * 0.72f, r * 0.14f, paint)
            }
        }
        paint.style = oldStyle
    }

    private fun drawPen(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.save()
        canvas.rotate(-45f, cx, cy)
        canvas.drawRoundRect(cx - r * 0.28f, cy - r, cx + r * 0.28f, cy + r * 0.62f, r * 0.18f, r * 0.18f, paint)
        canvas.drawLine(cx - r * 0.28f, cy + r * 0.62f, cx, cy + r, paint)
        canvas.drawLine(cx + r * 0.28f, cy + r * 0.62f, cx, cy + r, paint)
        canvas.restore()
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawLine(cx - r, cy + r, cx + r, cy - r, paint)
        canvas.drawLine(cx + r, cy - r, cx + r * 0.2f, cy - r, paint)
        canvas.drawLine(cx + r, cy - r, cx + r, cy - r * 0.2f, paint)
    }

    private fun drawGuide(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawLine(cx - r, cy, cx + r, cy, paint)
        canvas.drawCircle(cx - r * 0.45f, cy, r * 0.18f, paint)
        canvas.drawCircle(cx + r * 0.45f, cy, r * 0.18f, paint)
    }

    private fun drawMore(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx - r * 0.7f, cy, r * 0.18f, paint)
        canvas.drawCircle(cx, cy, r * 0.18f, paint)
        canvas.drawCircle(cx + r * 0.7f, cy, r * 0.18f, paint)
        paint.style = oldStyle
    }

    private fun drawCanvas(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawRoundRect(cx - r, cy - r * 0.65f, cx + r, cy + r * 0.72f, r * 0.12f, r * 0.12f, paint)
        canvas.drawLine(cx - r * 0.55f, cy + r * 0.72f, cx - r * 0.78f, cy + r, paint)
        canvas.drawLine(cx + r * 0.55f, cy + r * 0.72f, cx + r * 0.78f, cy + r, paint)
    }

    private fun drawLayer(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawLine(cx, cy - r, cx + r, cy - r * 0.42f, paint)
        canvas.drawLine(cx + r, cy - r * 0.42f, cx, cy + r * 0.12f, paint)
        canvas.drawLine(cx, cy + r * 0.12f, cx - r, cy - r * 0.42f, paint)
        canvas.drawLine(cx - r, cy - r * 0.42f, cx, cy - r, paint)
        canvas.drawLine(cx - r, cy, cx, cy + r * 0.55f, paint)
        canvas.drawLine(cx, cy + r * 0.55f, cx + r, cy, paint)
    }

    private fun drawRedo(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        arcBounds.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(arcBounds, 145f, -250f, false, paint)
        canvas.drawLine(cx + r, cy, cx + r * 0.35f, cy - r * 0.55f, paint)
        canvas.drawLine(cx + r, cy, cx + r * 0.2f, cy + r * 0.1f, paint)
    }

    private fun drawClear(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawRoundRect(cx - r * 0.62f, cy - r * 0.42f, cx + r * 0.62f, cy + r, r * 0.12f, r * 0.12f, paint)
        canvas.drawLine(cx - r * 0.85f, cy - r * 0.62f, cx + r * 0.85f, cy - r * 0.62f, paint)
        canvas.drawLine(cx - r * 0.3f, cy - r * 0.88f, cx + r * 0.3f, cy - r * 0.88f, paint)
    }

    private fun drawTablerUndoIcon(canvas: Canvas, drawable: android.graphics.drawable.Drawable?, cx: Float, cy: Float) {
        if (drawable == null) return
        val half = (minOf(width, height) * 0.66f).toInt() / 2
        drawable.setTint(paint.color)
        drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        drawable.draw(canvas)
    }
}
