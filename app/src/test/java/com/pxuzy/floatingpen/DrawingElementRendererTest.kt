package com.pxuzy.floatingpen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pxuzy.floatingpen.core.DrawingElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DrawingElementRendererTest {
    @Test
    fun `renderer draws every persisted element type`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val renderer = DrawingElementRenderer(density = 1f, paint = paint)
        val elements = listOf(
            DrawingElement.Stroke(mutableListOf(10f to 10f, 30f to 30f), Color.RED, 3f),
            DrawingElement.Line(40f to 10f, 60f to 30f, Color.GREEN, 3f),
            DrawingElement.Arrow(70f to 10f, 90f to 30f, Color.BLUE, 3f, 12f),
            DrawingElement.Rect(100f to 10f, 120f to 30f, Color.YELLOW, 3f),
            DrawingElement.Circle(150f to 20f, 10f, Color.MAGENTA, 3f),
        )

        elements.forEach { renderer.draw(Canvas(bitmap), it) }

        assertTrue(bitmapHasVisiblePixels(bitmap))
        assertEquals(Paint.Style.STROKE, paint.style)
    }

    private fun bitmapHasVisiblePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 0 }
    }
}
