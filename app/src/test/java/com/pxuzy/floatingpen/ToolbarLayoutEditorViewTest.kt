package com.pxuzy.floatingpen

import android.app.Application
import android.widget.CheckBox
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolbarLayoutEditorViewTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `editor renders configured order and enabled state`() {
        val editor = ToolbarLayoutEditorView(
            context,
            listOf("arrow", "pen", "line", "rect"),
            setOf("arrow", "line"),
        ) { _, _ -> }

        assertEquals(listOf("arrow", "pen", "line", "rect"), editor.currentOrder())
        assertEquals(setOf("arrow", "line"), editor.currentEnabled())
        assertEquals("toolbar-tool:arrow", editor.getChildAt(0).tag)
        assertEquals(true, (editor.findViewWithTag<CheckBox>("toolbar-enabled:arrow")).isChecked)
        assertEquals(false, (editor.findViewWithTag<CheckBox>("toolbar-enabled:pen")).isChecked)
    }

    @Test
    fun `editor keeps one tool enabled`() {
        val changes = mutableListOf<Set<String>>()
        val editor = ToolbarLayoutEditorView(context, PenSettings.TOOL_IDS, setOf("pen")) { _, enabled -> changes += enabled }
        val onlyTool = editor.findViewWithTag<CheckBox>("toolbar-enabled:pen")

        onlyTool.isChecked = false

        assertTrue(onlyTool.isChecked)
        assertTrue(changes.isEmpty())
    }
}
