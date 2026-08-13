package com.pxuzy.floatingpen

import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectionMenuViewTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `all menu tools can be selected before starting drawing`() {
        val selections = mutableListOf<Pair<String, Int>>()
        val menu = SelectionMenuView(context, { tool, color -> selections += tool to color }, {})

        DrawingElement.tools.forEach { tool ->
            menu.findByTag("menu-tool:${tool.id}").performClick()
            menu.findText("开始绘制").performClick()
        }

        assertEquals(DrawingElement.tools.map { it.id to 0 }, selections)
    }

    @Test
    fun `selected color is used by start drawing button`() {
        val selections = mutableListOf<Pair<String, Int>>()
        val menu = SelectionMenuView(context, { tool, color -> selections += tool to color }, {})

        menu.findByTag(3).performClick()
        menu.findText("开始绘制").performClick()

        assertEquals(listOf("pen" to 3), selections)
    }

    @Test
    fun `close button dismisses menu`() {
        var dismisses = 0
        val menu = SelectionMenuView(context, { _, _ -> }, { dismisses++ })

        menu.findText("关闭").performClick()

        assertEquals(1, dismisses)
    }

    @Test
    fun `tap on scrim outside panel dismisses menu`() {
        var dismisses = 0
        val menu = SelectionMenuView(context, { _, _ -> }, { dismisses++ })
        menu.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        menu.layout(0, 0, 800, 1200)

        // A corner touch misses the centered panel and lands on the scrim.
        menu.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 5f, 5f))
        menu.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 5f, 5f))

        assertEquals(1, dismisses)
    }

    @Test
    fun `tap on panel does not dismiss menu`() {
        var dismisses = 0
        val menu = SelectionMenuView(context, { _, _ -> }, { dismisses++ })
        menu.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        menu.layout(0, 0, 800, 1200)

        // A touch at the center hits the panel, which consumes its own clicks.
        menu.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 400f, 600f))
        menu.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 400f, 600f))

        assertEquals(0, dismisses)
    }

    private fun touch(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x, y, 0)

    private fun ViewGroup.findText(text: String): TextView {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView && child.text.toString() == text) return child
            if (child is ViewGroup) runCatching { child.findText(text) }.getOrNull()?.let { return it }
        }
        error("Missing text: $text")
    }

    private fun ViewGroup.findByTag(tag: Any): View {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == tag) return child
            if (child is ViewGroup) runCatching { child.findByTag(tag) }.getOrNull()?.let { return it }
        }
        error("Missing tag: $tag")
    }
}