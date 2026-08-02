package com.pxuzy.floatingpen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FibonacciRetracementTest {
    @Test
    fun `default levels calculate from two endpoints`() {
        val levels = FibonacciRetracement.levels(FibonacciPoint(10f, 100f), FibonacciPoint(300f, 500f))

        assertEquals(7, levels.size)
        assertEquals(100f, levels.first().y)
        assertEquals(347.2f, levels[4].y, 0.01f)
        assertEquals(500f, levels.last().y)
        assertEquals("61.8%", levels[4].label)
        assertTrue(levels[4].emphasized)
    }

    @Test
    fun `reversed endpoints preserve direction and level order`() {
        val levels = FibonacciRetracement.levels(FibonacciPoint(0f, 500f), FibonacciPoint(0f, 100f))

        assertEquals(500f, levels.first().y)
        assertEquals(252.8f, levels[4].y, 0.01f)
        assertEquals(100f, levels.last().y)
    }
}
