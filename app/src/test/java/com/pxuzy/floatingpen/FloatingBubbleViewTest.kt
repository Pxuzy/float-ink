package com.pxuzy.floatingpen

import android.app.Application
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FloatingBubbleViewTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `stationary long press triggers quick drawing once`() {
        var taps = 0
        var longPresses = 0
        val bubble = bubble({ taps++ }, { longPresses++ })

        bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(450, java.util.concurrent.TimeUnit.MILLISECONDS)
        bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(1, longPresses)
        assertEquals(0, taps)
    }

    @Test
    fun `short press triggers tap without long press`() {
        var taps = 0
        var longPresses = 0
        val bubble = bubble({ taps++ }, { longPresses++ })

        bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(1, taps)
        assertEquals(0, longPresses)
    }

    @Test
    fun `accessibility click triggers the same tap action`() {
        var taps = 0
        val bubble = bubble({ taps++ }, {})

        assertTrue(bubble.performClick())

        assertEquals(1, taps)
    }

    @Test
    fun `runtime settings update bubble opacity immediately`() {
        val bubble = bubble({}, {})
        val settings = PenSettings.load(context).copy(bubbleOpacity = 0.35f)

        bubble.applySettings(settings)

        val paint = bubble.javaClass.getDeclaredField("buttonPaint").run {
            isAccessible = true
            get(bubble) as android.graphics.Paint
        }
        assertEquals(255, android.graphics.Color.alpha(paint.color))
        assertEquals(0.35f, bubble.alpha, 0.001f)
    }

    @Test
    fun `runtime settings update bubble accent color immediately`() {
        val bubble = bubble({}, {})
        val expected = android.graphics.Color.BLUE

        bubble.applySettings(PenSettings.load(context).copy(toolStyles = mapOf(
            "pen" to ToolStyle(expected, 4f),
        )))

        val accent = bubble.javaClass.getDeclaredField("accentPaint").run {
            isAccessible = true
            get(bubble) as android.graphics.Paint
        }
        assertEquals(expected, accent.color)
    }

    @Test
    fun `settings change forces bubble redraw for immediate rendering`() {
        val bubble = bubble({}, {})
        shadowOf(bubble).clearWasInvalidated()

        bubble.applySettings(PenSettings.load(context).copy(toolStyles = mapOf(
            "pen" to ToolStyle(android.graphics.Color.BLUE, 4f),
        )))

        assertTrue("颜色等非透明度设置也应立即重绘", shadowOf(bubble).wasInvalidated())
    }

    @Test
    fun `runtime settings update auto hide policy immediately`() {
        val bubble = bubble({}, {})
        bubble.applySettings(PenSettings.load(context).copy(autoHide = false, autoHideDelayMs = 5000L))

        assertEquals(false, bubble.javaClass.getDeclaredField("autoHideEnabled").run {
            isAccessible = true
            getBoolean(bubble)
        })
        assertEquals(5000L, bubble.javaClass.getDeclaredField("autoHideDelayMs").run {
            isAccessible = true
            getLong(bubble)
        })
    }

    @Test
    fun `floating bubble keeps the original Lucide pen icon`() {
        val icon = context.getDrawable(R.drawable.ic_lucide_pen_line)
        val bubbleIcon = FloatingBubbleView::class.java.getDeclaredField("penIcon").run {
            isAccessible = true
            get(bubble({}, {}))
        }

        assertTrue(icon != null)
        assertTrue(bubbleIcon != null)
    }

    @Test
    fun `floating pen uses compact 48dp square footprint`() {
        val bubble = bubble({}, {})

        bubble.measure(0, 0)

        val expected = (48 * context.resources.displayMetrics.density).toInt()
        assertEquals(expected, bubble.measuredWidth)
        assertEquals(expected, bubble.measuredHeight)
        assertTrue(bubble.contentDescription.toString().contains("画笔"))
    }

    @Test
    fun `hidden shift leaves only an 8dp sliver inside the window`() {
        val bubble = bubble({}, {})
        bubble.measure(0, 0)
        bubble.layout(0, 0, bubble.measuredWidth, bubble.measuredHeight)
        val density = context.resources.displayMetrics.density

        val shift = bubble.hiddenShiftPx()

        // The bubble must move most of the way out: only HIDDEN_WIDTH (8dp)
        // plus the 2dp draw inset stays inside the window.
        assertEquals((8 + 2) * density, bubble.measuredWidth - shift, 0.5f)
        assertTrue("shift must move the center past the window edge", shift > bubble.measuredWidth / 2f)
    }

    @Test
    fun `uninitialized bubble starts on the right side`() {
        context.getSharedPreferences(PenSettings.PREF_NAME, Application.MODE_PRIVATE).edit()
            .remove("bubble_x")
            .remove("bubble_y")
            .remove("bubble_snapped_left")
            .commit()
        val bubble = bubble({}, {})
        val params = bubble.layoutParams as WindowManager.LayoutParams
        params.x = 0
        params.y = 0

        bubble.layoutParams = params
        bubble.javaClass.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }.invoke(bubble)
        val expectedX = context.resources.displayMetrics.widthPixels -
            (48 * context.resources.displayMetrics.density).toInt() -
            (8 * context.resources.displayMetrics.density).toInt()
        assertEquals(expectedX, (bubble.layoutParams as WindowManager.LayoutParams).x)
    }

    @Test
    fun `drag cancels long press`() {
        var longPresses = 0
        val bubble = bubble({}, { longPresses++ })
        val windowManager = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        windowManager.addView(bubble, bubble.layoutParams)

        try {
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 100f))
            shadowOf(android.os.Looper.getMainLooper()).idleFor(450, java.util.concurrent.TimeUnit.MILLISECONDS)
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 100f))
        } finally {
            windowManager.removeView(bubble)
        }

        assertEquals(0, longPresses)
    }

    @Test
    fun `drag keeps a free position when auto hide is disabled`() {
        val bubble = bubble({}, {})
        bubble.applySettings(PenSettings.load(context).copy(autoHide = false))
        val windowManager = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        windowManager.addView(bubble, bubble.layoutParams)

        try {
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 70f, 90f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 70f, 90f))

            val params = bubble.layoutParams as WindowManager.LayoutParams
            assertEquals(160, params.x)
            assertEquals(180, params.y)
            assertEquals(PenSettings.BubblePosition(160, 180, false), PenSettings.loadBubblePosition(context))
            idleForHide()
            assertEquals(160, params.x)
            assertEquals(false, bubble.isHiddenForTest())
        } finally {
            windowManager.removeView(bubble)
        }
    }

    @Test
    fun `drag keeps a free position away from the edge when auto hide is enabled`() {
        val bubble = bubble({}, {})
        bubble.applySettings(PenSettings.load(context).copy(autoHide = true))
        val windowManager = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        windowManager.addView(bubble, bubble.layoutParams)

        try {
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 70f, 90f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 70f, 90f))

            val params = bubble.layoutParams as WindowManager.LayoutParams
            assertEquals(160, params.x)
            assertEquals(180, params.y)
            assertEquals(PenSettings.BubblePosition(160, 180, false), PenSettings.loadBubblePosition(context))
            idleForHide()
            assertEquals(160, params.x)
            assertEquals(false, bubble.isHiddenForTest())
        } finally {
            windowManager.removeView(bubble)
        }
    }

    @Test
    fun `drag near an edge docks and persists the selected side`() {
        val bubble = bubble({}, {})
        bubble.applySettings(PenSettings.load(context).copy(autoHide = true))
        val windowManager = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        windowManager.addView(bubble, bubble.layoutParams)

        try {
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, -90f, 90f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, -90f, 90f))

            val params = bubble.layoutParams as WindowManager.LayoutParams
            assertEquals((8 * context.resources.displayMetrics.density).toInt(), params.x)
            assertNotNull(PenSettings.loadBubblePosition(context))
            assertTrue(PenSettings.loadBubblePosition(context)!!.snappedLeft)
            assertEquals(false, bubble.isHiddenForTest())
            idleForHide()
            assertEquals(true, bubble.isHiddenForTest())

            bubble.applySettings(PenSettings.load(context).copy(autoHide = false))
            assertEquals(false, bubble.isHiddenForTest())
            idleForHide()
            assertEquals(false, bubble.isHiddenForTest())
        } finally {
            windowManager.removeView(bubble)
        }
    }

    @Test
    fun `disabled edge hiding keeps a near-edge point and enabling it docks after delay`() {
        val bubble = bubble({}, {})
        bubble.applySettings(PenSettings.load(context).copy(autoHide = false))
        val wm = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        wm.addView(bubble, bubble.layoutParams)
        try {
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, -70f, 90f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_UP, -70f, 90f))
            val params = bubble.layoutParams as WindowManager.LayoutParams
            idleForHide()
            assertEquals(20, params.x)
            assertEquals(false, bubble.isHiddenForTest())

            bubble.applySettings(PenSettings.load(context).copy(autoHide = true))
            assertEquals(20, params.x)
            idleForHide()
            assertEquals((8 * context.resources.displayMetrics.density).toInt(), params.x)
            assertEquals(true, bubble.isHiddenForTest())
        } finally {
            wm.removeView(bubble)
        }
    }

    @Test
    fun `cancelled right-edge drag hides and expanding screen restores visibility`() {
        val bubble = bubble({}, {})
        val metrics = context.resources.displayMetrics
        val originalWidth = metrics.widthPixels
        val wm = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        wm.addView(bubble, bubble.layoutParams)
        try {
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, originalWidth.toFloat(), 90f))
            bubble.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, originalWidth.toFloat(), 90f))
            val params = bubble.layoutParams as WindowManager.LayoutParams
            assertEquals(originalWidth - (56 * metrics.density).toInt(), params.x)
            idleForHide()
            assertEquals(true, bubble.isHiddenForTest())
            assertEquals(false, PenSettings.loadBubblePosition(context)!!.snappedLeft)

            metrics.widthPixels = originalWidth * 2
            bubble.keepInsideCurrentScreen()
            idleForHide()
            assertEquals(false, bubble.isHiddenForTest())
            assertEquals(originalWidth - (56 * metrics.density).toInt(), params.x)
        } finally {
            metrics.widthPixels = originalWidth
            wm.removeView(bubble)
        }
    }

    @Test
    fun `saved free position stays visible after recreating the bubble`() {
        PenSettings.saveBubblePosition(context, 160, 180, false)
        val bubble = bubble({}, {}).apply {
            (layoutParams as WindowManager.LayoutParams).apply { x = 0; y = 0 }
        }
        val wm = context.getSystemService(Application.WINDOW_SERVICE) as WindowManager
        wm.addView(bubble, bubble.layoutParams)
        try {
            idleForHide()
            val params = bubble.layoutParams as WindowManager.LayoutParams
            assertEquals(160, params.x)
            assertEquals(180, params.y)
            assertEquals(false, bubble.isHiddenForTest())
        } finally {
            wm.removeView(bubble)
        }
    }

    private fun bubble(onTap: () -> Unit, onLongPress: () -> Unit) =
        FloatingBubbleView(context, onTap, onLongPress).apply {
            layoutParams = WindowManager.LayoutParams().apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }
        }

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0, 0, action, x, y, 0)

    private fun idleForHide() = shadowOf(android.os.Looper.getMainLooper())
        .idleFor(2, java.util.concurrent.TimeUnit.SECONDS)

    private fun FloatingBubbleView.isHiddenForTest(): Boolean =
        javaClass.getDeclaredField("isHidden").run {
            isAccessible = true
            getBoolean(this@isHiddenForTest)
        }
}
