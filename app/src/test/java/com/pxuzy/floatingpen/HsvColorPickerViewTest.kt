package com.pxuzy.floatingpen

import android.graphics.Color
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HsvColorPickerViewTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `setting color preserves hsv and alpha`() {
        val picker = HsvColorPickerView(context)
        picker.color = Color.argb(128, 255, 0, 0)

        val hsv = picker.hsv()
        assertEquals(0f, hsv[0], 0.01f)
        assertEquals(1f, hsv[1], 0.01f)
        assertEquals(1f, hsv[2], 0.01f)
        assertEquals(128, Color.alpha(picker.color))
    }

    @Test
    fun `palette touch clamps saturation and value`() {
        val picker = HsvColorPickerView(context)
        picker.measure(280 shl 2 or 0, 236 shl 2 or 0)
        picker.layout(0, 0, 280, 236)

        picker.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, -100f, -100f))
        picker.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 500f, 500f))
        picker.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 500f, 500f))

        val hsv = picker.hsv()
        assertTrue(hsv[1] in 0f..1f)
        assertTrue(hsv[2] in 0f..1f)
    }

    @Test
    fun `valid palette drag emits a user color`() {
        val picker = HsvColorPickerView(context)
        picker.measure(280 shl 2, 236 shl 2)
        picker.layout(0, 0, 280, 236)
        var changed: Int? = null
        picker.setOnColorChangedListener { color, fromUser ->
            if (fromUser) changed = color
        }

        picker.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        picker.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 20f, 20f))

        assertTrue(changed != null)
    }

    private fun event(action: Int, x: Float, y: Float) =
        MotionEvent.obtain(0, 0, action, x, y, 0)
}
