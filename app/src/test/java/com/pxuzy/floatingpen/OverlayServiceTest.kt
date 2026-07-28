package com.pxuzy.floatingpen

import android.content.Intent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayServiceTest {
    @Test
    fun `service owns running state after it is created`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()

        assertTrue(
            service.getSharedPreferences(OverlayService.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, false)
        )
        assertTrue(service.privateField("foregroundReady") as Boolean)
        assertTrue(OverlayService.isRunningInProcess())
        controller.destroy()
        assertTrue(!OverlayService.isRunningInProcess())
    }

    @Test
    fun `tapping bubble opens default drawing directly without menu`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        service.onStartCommand(
            Intent(service, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_BUBBLE
            },
            0,
            1
        )

        val bubble = service.privateField("bubbleView") as View
        bubble.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        bubble.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 10f, 10f, 0))

        assertNotNull(service.privateField("drawingView"))
        assertNull(service.privateField("menuView"))
        controller.destroy()
    }

    @Test
    fun `bubble window uses top start coordinates for reliable drag positioning`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()

        val params = service.javaClass.getDeclaredMethod("getBubbleOverlayParams").run {
            isAccessible = true
            invoke(service) as WindowManager.LayoutParams
        }

        assertEquals(Gravity.TOP or Gravity.START, params.gravity)
        controller.destroy()
    }

    @Test
    fun `settings changed action refreshes existing bubble`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(service, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_BUBBLE
        }, 0, 1)
        val bubble = service.privateField("bubbleView") as FloatingBubbleView

        PenSettings.saveBubbleOpacity(service, 0.35f)
        service.onStartCommand(Intent(service, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SETTINGS_CHANGED
        }, 0, 2)

        val paint = bubble.javaClass.getDeclaredField("buttonPaint").run {
            isAccessible = true
            get(bubble) as android.graphics.Paint
        }
        assertEquals(89, android.graphics.Color.alpha(paint.color))
        controller.destroy()
    }

    @Test
    fun `settings changed action refreshes an open drawing view`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(service, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_DRAWING
        }, 0, 1)
        val drawing = service.privateField("drawingView") as DrawingOverlayView

        PenSettings.saveToolStyle(service, "pen", DrawingElement.colorValues[2], 13f)
        service.onStartCommand(Intent(service, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SETTINGS_CHANGED
        }, 0, 2)

        val paint = drawing.javaClass.getDeclaredField("drawPaint").run {
            isAccessible = true
            get(drawing) as android.graphics.Paint
        }
        assertEquals(DrawingElement.colorValues[2], paint.color)
        assertEquals(13f * service.resources.displayMetrics.density, paint.strokeWidth)
        controller.destroy()
    }

    @Test
    fun `drawing tool changes persist as the next global default`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(service, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_DRAWING
        }, 0, 1)

        val drawing = service.privateField("drawingView") as DrawingOverlayView
        drawing.findViewWithTag<View>("tool:arrow").performClick()

        assertEquals("arrow", PenSettings.load(service).tool)
        controller.destroy()
    }

    @Test
    fun `drawing text input mode toggles focus without touch passthrough`() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(service, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_DRAWING
        }, 0, 1)

        val params = service.privateField("drawingOverlayParams") as WindowManager.LayoutParams
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)

        service.callTextInputMode(true)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL == 0)

        service.callTextInputMode(false)
        service.callTextInputMode(false)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        controller.destroy()
    }

    private fun OverlayService.privateField(name: String): Any? =
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateField)
        }

    private fun OverlayService.callTextInputMode(enabled: Boolean) {
        javaClass.getDeclaredMethod("setDrawingTextInputMode", Boolean::class.javaPrimitiveType).run {
            isAccessible = true
            invoke(this@callTextInputMode, enabled)
        }
    }
}