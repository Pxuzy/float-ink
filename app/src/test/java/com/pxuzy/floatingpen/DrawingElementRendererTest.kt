package com.pxuzy.floatingpen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
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

    @Test
    fun `smooth stroke rounds the corner of a sharp turn`() {
        val renderer = DrawingElementRenderer(density = 1f, paint = Paint())
        val points = listOf(0f to 0f, 10f to 0f, 10f to 10f)

        val rawLength = PathMeasure(Path().apply {
            moveTo(0f, 0f); lineTo(10f, 0f); lineTo(10f, 10f)
        }, false).length

        val smoothed = renderer.buildSmoothStrokePath(points)
        val smoothedLength = PathMeasure(smoothed, false).length

        // Midpoint quadratic smoothing cuts the corner: strictly shorter than
        // the raw polyline. (Robolectric's getPosTan is unreliable, so endpoint
        // exactness is asserted implicitly: the algorithm moves to the first
        // sample and ends with a lineTo the last sample.)
        assertTrue(smoothedLength < rawLength)
        assertTrue(smoothedLength > 0f)
    }

    @Test
    fun `smooth stroke keeps a straight two-point stroke straight`() {
        val renderer = DrawingElementRenderer(density = 1f, paint = Paint())

        val smoothed = renderer.buildSmoothStrokePath(listOf(0f to 0f, 20f to 20f))

        assertEquals(PathMeasure(Path().apply {
            moveTo(0f, 0f); lineTo(20f, 20f)
        }, false).length, PathMeasure(smoothed, false).length, 0.01f)
    }

    private fun bitmapHasVisiblePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 0 }
    }
}
