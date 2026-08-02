package com.pxuzy.floatingpen.core

import kotlin.math.abs

/** Independent Fibonacci retracement geometry for the overlay guide. */
data class FibonacciPoint(val x: Float, val y: Float)

data class FibonacciLevel(
    val ratio: Float,
    val y: Float,
    val label: String,
    val emphasized: Boolean,
)

object FibonacciRetracement {
    val DEFAULT_RATIOS = listOf(0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f)

    fun levels(start: FibonacciPoint, end: FibonacciPoint, ratios: List<Float> = DEFAULT_RATIOS): List<FibonacciLevel> {
        val distance = end.y - start.y
        return ratios.map { ratio ->
            FibonacciLevel(
                ratio = ratio,
                y = start.y + distance * ratio,
                label = formatRatio(ratio),
                emphasized = abs(ratio - 0.618f) < 0.0005f,
            )
        }
    }

    fun formatRatio(ratio: Float): String = "${kotlin.math.round(ratio * 1000f).toInt() / 10f}%"
}
