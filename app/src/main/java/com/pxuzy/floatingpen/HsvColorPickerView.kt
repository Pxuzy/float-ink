package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** A dependency-free HSV saturation/value and hue picker for the launcher UI. */
class HsvColorPickerView(context: Context) : View(context) {
    private val palettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val hsv = floatArrayOf(0f, 1f, 1f)
    private var alphaValue = 255
    private var listener: ((color: Int, fromUser: Boolean) -> Unit)? = null
    private var draggingPalette = false
    private var draggingHue = false

    var color: Int
        get() = Color.HSVToColor(alphaValue, hsv)
        set(value) {
            Color.colorToHSV(value, hsv)
            alphaValue = Color.alpha(value)
            invalidate()
        }

    var alpha: Int
        get() = alphaValue
        set(value) {
            alphaValue = value.coerceIn(0, 255)
            invalidate()
        }

    fun hsv(): FloatArray = hsv.copyOf()

    fun setOnColorChangedListener(listener: (color: Int, fromUser: Boolean) -> Unit) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredWidth = (280f * density).roundToInt()
        val desiredHeight = (236f * density).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val horizontalPadding = 4f * density
        val gap = 10f * density
        val hueHeight = 24f * density
        val paletteSize = minOf(width.toFloat() - horizontalPadding * 2, height - hueHeight - gap - 4f * density)
        if (paletteSize <= 0f) return

        val left = horizontalPadding
        val top = 2f * density
        drawSaturationValue(canvas, left, top, paletteSize)
        drawHue(canvas, left, top + paletteSize + gap, paletteSize, hueHeight)

        val selectorX = left + hsv[1] * paletteSize
        val selectorY = top + (1f - hsv[2]) * paletteSize
        canvas.drawCircle(selectorX, selectorY, 7f * density, selectorPaint)
        canvas.drawCircle(selectorX, selectorY, 9f * density, selectorPaint.apply { alpha = 80 })
        selectorPaint.alpha = 255

        val hueX = left + (hsv[0] / 360f).coerceIn(0f, 1f) * paletteSize
        canvas.drawRoundRect(
            hueX - 5f * density,
            top + paletteSize + gap - 3f * density,
            hueX + 5f * density,
            top + paletteSize + gap + hueHeight + 3f * density,
            5f * density,
            5f * density,
            selectorPaint,
        )
    }

    private fun drawSaturationValue(canvas: Canvas, left: Float, top: Float, size: Float) {
        palettePaint.shader = LinearGradient(
            left,
            top,
            left + size,
            top,
            Color.WHITE,
            Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, top, left + size, top + size, palettePaint)
        palettePaint.shader = LinearGradient(
            left,
            top,
            left,
            top + size,
            0x00000000,
            0xFF000000.toInt(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(left, top, left + size, top + size, palettePaint)
        palettePaint.shader = null
    }

    private fun drawHue(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val colors = IntArray(7) { index -> Color.HSVToColor(floatArrayOf(index * 60f, 1f, 1f)) }
        huePaint.shader = LinearGradient(left, top, left + width, top, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, left + width, top + height, height / 2f, height / 2f, huePaint)
        huePaint.shader = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        val horizontalPadding = 4f * density
        val gap = 10f * density
        val hueHeight = 24f * density
        val paletteSize = minOf(width.toFloat() - horizontalPadding * 2, height - hueHeight - gap - 4f * density)
        val left = horizontalPadding
        val paletteTop = 2f * density
        val hueTop = paletteTop + paletteSize + gap
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingPalette = event.y in paletteTop..(paletteTop + paletteSize) && event.x in left..(left + paletteSize)
                draggingHue = event.y in hueTop..(hueTop + hueHeight) && event.x in left..(left + paletteSize)
                if (!draggingPalette && !draggingHue) return false
                updateFromTouch(event.x, event.y, left, paletteTop, paletteSize, hueTop, hueHeight)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!draggingPalette && !draggingHue) return false
                updateFromTouch(event.x, event.y, left, paletteTop, paletteSize, hueTop, hueHeight)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = draggingPalette || draggingHue
                if (wasDragging) updateFromTouch(event.x, event.y, left, paletteTop, paletteSize, hueTop, hueHeight)
                draggingPalette = false
                draggingHue = false
                return wasDragging
            }
        }
        return true
    }

    private fun updateFromTouch(
        x: Float,
        y: Float,
        left: Float,
        paletteTop: Float,
        paletteSize: Float,
        hueTop: Float,
        hueHeight: Float,
    ) {
        if (draggingPalette) {
            hsv[1] = ((x - left) / paletteSize).coerceIn(0f, 1f)
            hsv[2] = (1f - (y - paletteTop) / paletteSize).coerceIn(0f, 1f)
        } else if (draggingHue) {
            hsv[0] = (((x - left) / paletteSize) * 360f).coerceIn(0f, 360f)
        }
        invalidate()
        listener?.invoke(color, true)
    }
}
