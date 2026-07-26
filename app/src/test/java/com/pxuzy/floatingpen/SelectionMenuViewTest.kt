package com.pxuzy.floatingpen

import android.app.Application
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