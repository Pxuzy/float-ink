package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.FibonacciPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FibonacciOverlayControllerTest {
    @Test
    fun `drag creates selected guide with persistent endpoints`() {
        val controller = FibonacciOverlayController(400f, 800f, 48f)

        controller.begin(40f, 80f)
        controller.move(240f, 680f)
        controller.end()

        val state = controller.renderState()!!
        assertTrue(state.selected)
        assertTrue(state.handlesVisible)
        assertPointEquals(FibonacciPoint(40f, 80f), state.start)
        assertPointEquals(FibonacciPoint(240f, 680f), state.end)
    }

    @Test
    fun `dragging selected endpoint changes only that endpoint`() {
        val controller = FibonacciOverlayController(400f, 800f, 48f)
        controller.begin(40f, 80f)
        controller.move(240f, 680f)
        controller.end()

        controller.begin(240f, 680f)
        controller.move(300f, 600f)
        controller.end()

        val state = controller.renderState()!!
        assertPointEquals(FibonacciPoint(40f, 80f), state.start)
        assertPointEquals(FibonacciPoint(300f, 600f), state.end)
    }

    @Test
    fun `dragging move handle translates both endpoints without changing range`() {
        val controller = FibonacciOverlayController(400f, 800f, 48f)
        controller.begin(40f, 80f)
        controller.move(240f, 680f)
        controller.end()

        controller.begin(140f, 380f)
        controller.move(180f, 460f)
        controller.end()

        val state = controller.renderState()!!
        assertPointEquals(FibonacciPoint(80f, 160f), state.start)
        assertPointEquals(FibonacciPoint(280f, 760f), state.end)
        assertPointEquals(FibonacciPoint(180f, 460f), state.moveHandle)
    }

    @Test
    fun `moving near viewport edge preserves range instead of stretching it`() {
        val controller = FibonacciOverlayController(400f, 800f, 48f)
        controller.begin(40f, 80f)
        controller.move(240f, 680f)
        controller.end()

        controller.begin(140f, 380f)
        controller.move(300f, 780f)
        controller.end()

        val state = controller.renderState()!!
        assertEquals(600f, state.end.y - state.start.y, 0.01f)
        assertEquals(800f, state.end.y, 0.01f)
    }

    @Test
    fun `resize preserves relative endpoint positions`() {
        val controller = FibonacciOverlayController(400f, 800f, 48f)
        controller.begin(100f, 200f)
        controller.move(300f, 600f)
        controller.end()

        controller.resize(800f, 400f)

        val state = controller.renderState()!!
        assertPointEquals(FibonacciPoint(200f, 100f), state.start)
        assertPointEquals(FibonacciPoint(600f, 300f), state.end)
    }

    @Test
    fun `empty canvas tap creates a replacement guide`() {
        val controller = FibonacciOverlayController(400f, 800f, 48f)
        controller.begin(40f, 80f)
        controller.move(240f, 680f)
        controller.end()

        controller.begin(100f, 200f)
        controller.move(300f, 600f)
        controller.end()

        val state = controller.renderState()!!
        assertPointEquals(FibonacciPoint(100f, 200f), state.start)
        assertPointEquals(FibonacciPoint(300f, 600f), state.end)
    }

    private fun assertPointEquals(expected: FibonacciPoint, actual: FibonacciPoint) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }
}
