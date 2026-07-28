package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Shared monochrome icon language for FloatInk overlay and launcher controls. */
class FloatInkIconView(
    context: Context,
    private val icon: String,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val bounds = RectF()

    fun setIconColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.24f
        path.rewind()
        when (icon) {
            "canvas" -> {
                canvas.drawRoundRect(cx - r, cy - r * .72f, cx + r, cy + r * .62f, r * .1f, r * .1f, paint)
                canvas.drawLine(cx - r * .55f, cy + r * .72f, cx - r * .78f, cy + r, paint)
                canvas.drawLine(cx + r * .55f, cy + r * .72f, cx + r * .78f, cy + r, paint)
                canvas.drawLine(cx - r * .28f, cy - r, cx + r * .28f, cy - r, paint)
            }
            "layer" -> {
                path.moveTo(cx, cy - r)
                path.lineTo(cx + r, cy - r * .42f)
                path.lineTo(cx, cy + r * .12f)
                path.lineTo(cx - r, cy - r * .42f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - r, cy, cx, cy + r * .56f, paint)
                canvas.drawLine(cx, cy + r * .56f, cx + r, cy, paint)
                canvas.drawLine(cx - r, cy + r * .46f, cx, cy + r, paint)
                canvas.drawLine(cx, cy + r, cx + r, cy + r * .46f, paint)
            }
            "add" -> {
                canvas.drawLine(cx - r * .75f, cy, cx + r * .75f, cy, paint)
                canvas.drawLine(cx, cy - r * .75f, cx, cy + r * .75f, paint)
            }
            "more" -> fill(canvas) {
                canvas.drawCircle(cx, cy - r * .72f, r * .15f, paint)
                canvas.drawCircle(cx, cy, r * .15f, paint)
                canvas.drawCircle(cx, cy + r * .72f, r * .15f, paint)
            }
            "eye", "eye-off" -> {
                path.moveTo(cx - r, cy)
                path.quadTo(cx, cy - r * .85f, cx + r, cy)
                path.quadTo(cx, cy + r * .85f, cx - r, cy)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawCircle(cx, cy, r * .3f, paint)
                if (icon == "eye-off") canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
            }
            "edit" -> {
                canvas.save(); canvas.rotate(-45f, cx, cy)
                canvas.drawRoundRect(cx - r * .22f, cy - r, cx + r * .22f, cy + r * .55f, r * .12f, r * .12f, paint)
                canvas.drawLine(cx - r * .22f, cy + r * .55f, cx, cy + r, paint)
                canvas.drawLine(cx + r * .22f, cy + r * .55f, cx, cy + r, paint)
                canvas.restore()
            }
            "delete", "trash" -> {
                canvas.drawRoundRect(cx - r * .58f, cy - r * .38f, cx + r * .58f, cy + r, r * .1f, r * .1f, paint)
                canvas.drawLine(cx - r * .78f, cy - r * .62f, cx + r * .78f, cy - r * .62f, paint)
                canvas.drawLine(cx - r * .28f, cy - r * .9f, cx + r * .28f, cy - r * .9f, paint)
                canvas.drawLine(cx - r * .22f, cy, cx - r * .22f, cy + r * .62f, paint)
                canvas.drawLine(cx + r * .22f, cy, cx + r * .22f, cy + r * .62f, paint)
            }
            "history" -> {
                bounds.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(bounds, -70f, 300f, false, paint)
                canvas.drawLine(cx - r * .92f, cy - r * .28f, cx - r * .92f, cy - r * .88f, paint)
                canvas.drawLine(cx - r * .92f, cy - r * .28f, cx - r * .34f, cy - r * .34f, paint)
                canvas.drawLine(cx, cy, cx, cy - r * .52f, paint)
                canvas.drawLine(cx, cy, cx + r * .42f, cy + r * .22f, paint)
            }
            "import" -> {
                canvas.drawRoundRect(cx - r, cy - r * .68f, cx + r, cy + r, r * .1f, r * .1f, paint)
                canvas.drawLine(cx, cy - r, cx, cy + r * .28f, paint)
                canvas.drawLine(cx - r * .42f, cy - r * .12f, cx, cy + r * .3f, paint)
                canvas.drawLine(cx + r * .42f, cy - r * .12f, cx, cy + r * .3f, paint)
            }
        }
    }

    private inline fun fill(canvas: Canvas, block: () -> Unit) {
        val old = paint.style
        paint.style = Paint.Style.FILL
        block()
        paint.style = old
    }
}
