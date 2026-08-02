package com.pxuzy.floatingpen

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
            selected = true,
            handlesVisible = true,
            creating = false,
        )

        assertEquals(FibonacciPoint(40f, 80f), state.start)
        assertEquals(FibonacciPoint(240f, 680f), state.end)
        assertEquals(true, state.handlesVisible)
    }
}