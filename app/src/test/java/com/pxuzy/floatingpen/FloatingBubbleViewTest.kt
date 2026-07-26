package com.pxuzy.floatingpen

import android.app.Application
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
        assertEquals(89, android.graphics.Color.alpha(paint.color))
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
    fun `floating pen uses compact 48dp square footprint`() {
        val bubble = bubble({}, {})

        bubble.measure(0, 0)

        val expected = (48 * context.resources.displayMetrics.density).toInt()
        assertEquals(expected, bubble.measuredWidth)
        assertEquals(expected, bubble.measuredHeight)
        assertTrue(bubble.contentDescription.toString().contains("画笔"))
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
}