package com.pxuzy.floatingpen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.pxuzy.floatingpen.core.FibonacciPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FibonacciGuideRendererTest {
    @Test
    fun `render state exposes all levels and endpoint handles`() {
        val state = FibonacciRenderState(
            start = FibonacciPoint(40f, 80f),
            end = FibonacciPoint(240f, 680f),
            moveHandle = FibonacciPoint(140f, 380f),
            selected = true,
            handlesVisible = true,
            creating = false,
        )

        assertEquals(FibonacciPoint(40f, 80f), state.start)
        assertEquals(FibonacciPoint(240f, 680f), state.end)
        assertEquals(true, state.handlesVisible)
    }

    @Test
    fun `reused renderer keeps its output across repeated draws`() {
        val bitmap = Bitmap.createBitmap(320, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = FibonacciGuideRenderer(density = 1f)
        val state = FibonacciRenderState(
            start = FibonacciPoint(40f, 80f),
            end = FibonacciPoint(240f, 680f),
            moveHandle = FibonacciPoint(140f, 380f),
            selected = true,
            handlesVisible = true,
            creating = false,
        )

        renderer.draw(canvas, state)
        val firstPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(firstPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        bitmap.eraseColor(Color.TRANSPARENT)
        renderer.draw(canvas, state)
        val secondPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(secondPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        assertEquals(firstPixels.toList(), secondPixels.toList())
    }
}