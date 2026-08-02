package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.FibonacciPoint
import kotlin.math.hypot

/**
 * Owns the temporary two-point Fibonacci overlay independently from drawing ink.
 * Endpoints are stored as viewport fractions so window resizing preserves placement.
 */
class FibonacciOverlayController(
    viewportWidth: Float,
    viewportHeight: Float,
    private val hitRadiusPx: Float,
) {
    private var width = viewportWidth.coerceAtLeast(1f)
    private var height = viewportHeight.coerceAtLeast(1f)
    private var guide: FibonacciGuide? = null
    private var gesture: Gesture = Gesture.Idle
    private var moveStart: FibonacciPoint? = null
    private var guideStartAtMove: FibonacciGuide? = null

    fun begin(x: Float, y: Float): Boolean {
        val existing = guide
        gesture = when {
            existing == null -> {
                guide = FibonacciGuide(pointFraction(x, y), pointFraction(x, y))
                Gesture.Creating
            }
            isHit(existing.end, x, y) -> Gesture.DraggingEnd
            isHit(existing.start, x, y) -> Gesture.DraggingStart
            isHit(existing.moveHandle(), x, y) -> {
                moveStart = FibonacciPoint(x, y)
                guideStartAtMove = existing
                Gesture.Moving
            }
            else -> {
                guide = FibonacciGuide(pointFraction(x, y), pointFraction(x, y))
                Gesture.Creating
            }
        }
        return true
    }

    fun move(x: Float, y: Float): Boolean {
        val activeGuide = guide ?: return false
        val point = pointFraction(x, y)
        guide = when (gesture) {
            Gesture.Creating, Gesture.DraggingEnd -> activeGuide.copy(end = point)
            Gesture.DraggingStart -> activeGuide.copy(start = point)
            Gesture.Moving -> moveGuide(x, y)
            Gesture.Idle -> return false
        }
        return true
    }

    fun end(): Boolean = finishGesture()

    fun cancel(): Boolean = finishGesture()

    fun resize(viewportWidth: Float, viewportHeight: Float) {
        width = viewportWidth.coerceAtLeast(1f)
        height = viewportHeight.coerceAtLeast(1f)
    }

    fun renderState(): FibonacciRenderState? {
        val activeGuide = guide ?: return null
        return FibonacciRenderState(
            start = activeGuide.start.resolve(width, height),
            end = activeGuide.end.resolve(width, height),
            moveHandle = activeGuide.moveHandle().resolve(width, height),
            selected = true,
            handlesVisible = true,
            creating = gesture == Gesture.Creating,
        )
    }

    private fun finishGesture(): Boolean {
        if (gesture == Gesture.Idle) return false
        gesture = Gesture.Idle
        moveStart = null
        guideStartAtMove = null
        return true
    }

    private fun pointFraction(x: Float, y: Float) = FibonacciPointFraction(
        x = (x / width).coerceIn(0f, 1f),
        y = (y / height).coerceIn(0f, 1f),
    )

    private fun isHit(point: FibonacciPointFraction, x: Float, y: Float): Boolean {
        val pixel = point.resolve(width, height)
        return hypot(pixel.x - x, pixel.y - y) <= hitRadiusPx
    }

    private fun moveGuide(x: Float, y: Float): FibonacciGuide {
        val origin = guideStartAtMove ?: return requireNotNull(guide)
        val startTouch = moveStart ?: return origin
        val requestedDx = (x - startTouch.x) / width
        val requestedDy = (y - startTouch.y) / height
        val dx = requestedDx.coerceIn(
            -minOf(origin.start.x, origin.end.x),
            1f - maxOf(origin.start.x, origin.end.x),
        )
        val dy = requestedDy.coerceIn(
            -minOf(origin.start.y, origin.end.y),
            1f - maxOf(origin.start.y, origin.end.y),
        )
        return FibonacciGuide(
            start = FibonacciPointFraction(origin.start.x + dx, origin.start.y + dy),
            end = FibonacciPointFraction(origin.end.x + dx, origin.end.y + dy),
        )
    }

    private sealed interface Gesture {
        data object Idle : Gesture
        data object Creating : Gesture
        data object DraggingStart : Gesture
        data object DraggingEnd : Gesture
        data object Moving : Gesture
    }
}

data class FibonacciGuide(
    val start: FibonacciPointFraction,
    val end: FibonacciPointFraction,
) {
    fun moveHandle() = FibonacciPointFraction(
        x = (start.x + end.x) / 2f,
        y = (start.y + end.y) / 2f,
    )
}

data class FibonacciPointFraction(
    val x: Float,
    val y: Float,
) {
    fun resolve(width: Float, height: Float) = FibonacciPoint(x * width, y * height)
}

data class FibonacciRenderState(
    val start: FibonacciPoint,
    val end: FibonacciPoint,
    val moveHandle: FibonacciPoint,
    val selected: Boolean,
    val handlesVisible: Boolean,
    val creating: Boolean,
)
