package com.pxuzy.floatingpen

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `application uses the adaptive launcher icon resources`() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(R.mipmap.ic_launcher, info.icon)
    }

    @Test
    fun `bottom navigation opens home pen and settings pages`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        assertNotNull(root.findByTag("nav-home"))
        assertNotNull(root.findByTag("nav-pen"))
        assertNotNull(root.findByTag("nav-settings"))

        root.findByTag("nav-pen").performClick()
        assertNotNull(root.findByTag("setting-tool:pen"))
        assertNotNull(root.findByTag("default-drawing-section"))
        assertNotNull(root.findByTag("primary-tools"))
        assertNotNull(root.findByTag("more-tools-section"))
        assertNotNull(root.findByTag("setting-tool:circle"))
        assertNotNull(root.findByTag("global-color:0"))
        assertNotNull(root.findByTag("global-width"))
        assertNotNull(root.findByTag("apply-global-style"))
        assertNotNull(root.findByTag("tool-color:0"))
        assertNotNull(root.findByTag("tool-width"))
        assertNotNull(root.findByTag("tool-preview"))
        assertTrue(root.findByTagOrNull("setting-arrow-scale") == null)

        root.findByTag("setting-tool:arrow").performClick()
        assertNotNull(root.findByTag("setting-arrow-scale"))
        assertNotNull(root.findByTag("setting-arrow-scale-label"))
        assertNotNull(root.findByTag("setting-arrow-preview"))

        root.findByTag("nav-settings").performClick()
        assertNotNull(root.findByTag("setting-bubble-opacity"))
        assertNotNull(root.findByTag("setting-auto-hide"))
        assertNotNull(root.findByTag("setting-auto-hide-delay"))
        assertNotNull(root.findByTag("settings-bubble-section"))
        assertNotNull(root.findByTag("settings-auto-hide-section"))
        assertNotNull(root.findByTag("settings-live-copy"))
        assertNotNull(root.findByTag("toolbar-layout-section"))
        assertNotNull(root.findByTag("toolbar-tool:pen"))
    }

    @Test
    fun `pen page keeps global and tool styles isolated until apply all`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        root.findByTag("nav-pen").performClick()
        root.findByTag("setting-tool:arrow").performClick()
        root.findByTag("tool-color:2").performClick()
        (root.findByTag("tool-width") as SeekBar).setProgress(10, true)
        root.findByTag("global-color:1").performClick()
        (root.findByTag("global-width") as SeekBar).setProgress(4, true)

        var values = PenSettings.load(activity)
        assertEquals(DrawingElement.colorValues[2], values.styleFor("arrow").color)
        assertEquals(12f, values.styleFor("arrow").widthDp)
        assertEquals(DrawingElement.colorValues[1], values.globalColor)
        assertEquals(6f, values.globalWidthDp)

        root.findByTag("apply-global-style").performClick()
        values = PenSettings.load(activity)
        PenSettings.TOOL_IDS.forEach { tool ->
            assertEquals(DrawingElement.colorValues[1], values.styleFor(tool).color)
            assertEquals(6f, values.styleFor(tool).widthDp)
        }
        assertEquals("6 dp", (root.findByTag("tool-width-label") as TextView).text.toString())
    }

    @Test
    fun `switching tools reloads each independent style and arrow controls`() {
        PenSettings.saveToolStyle(context, "pen", DrawingElement.colorValues[0], 4f)
        PenSettings.saveToolStyle(context, "arrow", DrawingElement.colorValues[3], 15f)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        root.findByTag("setting-tool:arrow").performClick()
        assertEquals("15 dp", (root.findByTag("tool-width-label") as TextView).text.toString())
        assertNotNull(root.findByTag("setting-arrow-scale"))

        root.findByTag("setting-tool:pen").performClick()
        assertEquals("4 dp", (root.findByTag("tool-width-label") as TextView).text.toString())
        assertTrue(root.findByTagOrNull("setting-arrow-scale") == null)
    }

    @Test
    fun `circle loads its own width and preview updates while slider changes`() {
        PenSettings.saveToolStyle(context, "circle", DrawingElement.colorValues[2], 9f)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        root.findByTag("setting-tool:circle").performClick()
        assertEquals("9 dp", (root.findByTag("tool-width-label") as TextView).text.toString())
        assertEquals("circle", root.findByTag("tool-preview").getTag(R.id.tag_preview_tool))
        assertEquals(9f, root.findByTag("tool-preview").getTag(R.id.tag_preview_width_dp))

        (root.findByTag("tool-width") as SeekBar).setProgress(14, true)

        assertEquals("16 dp", (root.findByTag("tool-width-label") as TextView).text.toString())
        assertEquals(16f, PenSettings.load(activity).styleFor("circle").widthDp)
        assertEquals(16f, root.findByTag("tool-preview").getTag(R.id.tag_preview_width_dp))
    }

    @Test
    fun `arrow scale slider persists tenths and updates label`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        root.findByTag("setting-tool:arrow").performClick()
        (root.findByTag("setting-arrow-scale") as SeekBar).setProgress(170, true)

        assertEquals(2.7f, PenSettings.load(activity).arrowScale)
        assertEquals("2.7×", (root.findByTag("setting-arrow-scale-label") as TextView).text.toString())
    }

    @Test
    fun `all sliders expose exact ranges and endpoint labels`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        val globalWidth = root.findByTag("global-width") as SeekBar
        assertEquals(22, globalWidth.max)
        globalWidth.setProgress(0, true)
        assertEquals("2 dp", (root.findByTag("global-width-label") as TextView).text.toString())
        globalWidth.setProgress(globalWidth.max, true)
        assertEquals("24 dp", (root.findByTag("global-width-label") as TextView).text.toString())

        val width = root.findByTag("tool-width") as SeekBar
        assertEquals(22, width.max)
        width.setProgress(0, true)
        assertEquals("2 dp", (root.findByTag("tool-width-label") as TextView).text.toString())
        width.setProgress(width.max, true)
        assertEquals("24 dp", (root.findByTag("tool-width-label") as TextView).text.toString())

        root.findByTag("setting-tool:arrow").performClick()
        val arrow = root.findByTag("setting-arrow-scale") as SeekBar
        assertEquals(300, arrow.max)
        arrow.setProgress(0, true)
        assertEquals("1.0×", (root.findByTag("setting-arrow-scale-label") as TextView).text.toString())
        arrow.setProgress(arrow.max, true)
        assertEquals("4.0×", (root.findByTag("setting-arrow-scale-label") as TextView).text.toString())

        root.findByTag("nav-settings").performClick()
        val opacity = root.findByTag("setting-bubble-opacity") as SeekBar
        assertEquals(65, opacity.max)
        opacity.setProgress(0, true)
        assertEquals("35%", (root.findByTag("setting-opacity-label") as TextView).text.toString())
        opacity.setProgress(opacity.max, true)
        assertEquals("100%", (root.findByTag("setting-opacity-label") as TextView).text.toString())

        val delay = root.findByTag("setting-auto-hide-delay") as SeekBar
        assertEquals(9, delay.max)
        delay.setProgress(0, true)
        assertEquals("0.5 秒", (root.findByTag("setting-delay-label") as TextView).text.toString())
        delay.setProgress(delay.max, true)
        assertEquals("5.0 秒", (root.findByTag("setting-delay-label") as TextView).text.toString())
    }

    @Test
    fun `arrow scale label accepts precise custom value`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        root.findByTag("setting-tool:arrow").performClick()
        root.findByTag("setting-arrow-scale-label").performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val input = dialog.findViewById<EditText>(android.R.id.edit)
        input.setText("2.35")
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(2.35f, PenSettings.load(activity).arrowScale)
        assertEquals("2.35×", (root.findByTag("setting-arrow-scale-label") as TextView).text.toString())
        assertEquals(135, (root.findByTag("setting-arrow-scale") as SeekBar).progress)
    }

    @Test
    fun `phone pen page wraps eight colors into two rows`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        root.findByTag("nav-pen").performClick()
        val colorGrid = root.findByTag("tool-color-grid") as ViewGroup
        val colorRow = colorGrid.getChildAt(0) as ViewGroup

        assertEquals(1, colorGrid.childCount)
        assertEquals(DrawingOverlayView.PALETTE_COLORS.size + 2, colorRow.childCount)
    }

    @Test
    fun `pen color palette uses expandable horizontal slots`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        val palette = root.findByTag("tool-color-grid") as ViewGroup
        val row = palette.getChildAt(0) as ViewGroup
        assertEquals(DrawingOverlayView.PALETTE_COLORS.size + 2, row.childCount)
        assertTrue((0 until DrawingOverlayView.PALETTE_COLORS.size).all { row.getChildAt(it).layoutParams.width == 48.dp })
    }

    @Test
    fun `reopening pen page does not retain detached color buttons`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        root.findByTag("nav-pen").performClick()
        root.findByTag("nav-home").performClick()
        root.findByTag("nav-pen").performClick()
        root.findByTag("tool-color:0").performClick()

        val buttons = activity.privateField("colorButtons") as List<*>
        assertEquals(DrawingOverlayView.PALETTE_COLORS.size, buttons.size)
    }

    @Test
    fun `preset color selection refreshes palette without rebuilding the page`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()
        val paletteBefore = root.findByTag("tool-color-grid")

        root.findByTag("tool-color:1").performClick()

        assertSame(paletteBefore, root.findByTag("tool-color-grid"))
    }

    @Test
    fun `main navigation and tool selectors keep accessible touch targets`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        assertEquals("首页", root.findByTag("nav-home").contentDescription)
        root.findByTag("nav-pen").performClick()
        assertEquals(48.dp, root.findByTag("setting-tool:pen").layoutParams.height)
        assertEquals(48.dp, root.findByTag("tool-color:0").layoutParams.width)
    }

    @Test
    fun `history section uses compact rows and secondary action bar`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-settings").performClick()

        assertNull(root.findByTagOrNull("history-location"))
        val actions = root.findByTag("history-actions") as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, actions.orientation)
        assertTrue(root.findByTag("history-import") is LinearLayout)
        assertTrue(root.findByTag("history-trash") is LinearLayout)
        assertTrue(root.findByTag("history-empty-icon") is FloatInkIconView)
    }

    @Test
    fun `opacity slider dynamically renders preview alpha and label`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-settings").performClick()
        val opacity = root.findByTag("setting-bubble-opacity") as SeekBar
        val preview = root.findByTag("setting-opacity-preview")
        opacity.setProgress(0, true)
        assertEquals("35%", (root.findByTag("setting-opacity-label") as TextView).text.toString())
        assertEquals(0.35f, preview.alpha, 0.001f)
        opacity.setProgress(opacity.max, true)
        assertEquals("100%", (root.findByTag("setting-opacity-label") as TextView).text.toString())
        assertEquals(1f, preview.alpha, 0.001f)
    }

    @Test
    fun `auto hide toggle enables and disables delay control`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        root.findByTag("nav-settings").performClick()
        val toggle = root.findByTag("setting-auto-hide") as CheckBox
        val delay = root.findByTag("setting-auto-hide-delay") as SeekBar

        toggle.isChecked = false
        assertTrue(!delay.isEnabled)
        toggle.isChecked = true
        assertTrue(delay.isEnabled)
    }

    @Test
    fun `custom color add action is present in both pen palettes`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()
        assertNotNull(root.findByTag("global-add-color"))
        assertNotNull(root.findByTag("tool-add-color"))
    }

    @Test
    fun `color management reveals deletion only for custom colors`() {
        val custom = 0xFF123456.toInt()
        PenSettings.addCustomColor(context, custom)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()

        assertEquals(null, root.findByTagOrNull("tool-delete-color:$custom"))
        root.findByTag("tool-manage-colors").performClick()

        assertNotNull(root.findByTag("tool-delete-color:$custom"))
        assertEquals(null, root.findByTagOrNull("tool-delete-color:${PenSettings.DEFAULT_PALETTE.first()}"))
    }

    @Test
    fun `custom color action opens hsv picker with precise input fields`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()
        root.findByTag("tool-add-color").performClick()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val content = dialog.findViewById<ViewGroup>(android.R.id.custom)
        assertNotNull(content.findByTagOrNull("custom-color-scroll"))
        assertNotNull(content.findByTagOrNull("custom-color-picker"))
        assertNotNull(content.findByTagOrNull("custom-color-alpha"))
        assertNotNull(content.findByTagOrNull("custom-color-hex"))
        assertNotNull(content.findByTagOrNull("rgb-r"))
        assertNotNull(content.findByTagOrNull("rgb-g"))
        assertNotNull(content.findByTagOrNull("rgb-b"))
        assertEquals(android.text.InputType.TYPE_CLASS_NUMBER, (content.findByTagOrNull("rgb-r") as EditText).inputType)
    }

    @Test
    fun `custom RGB channels save the exact selected color`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findByTag("nav-pen").performClick()
        root.findByTag("tool-add-color").performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val content = dialog.findViewById<ViewGroup>(android.R.id.custom)
        (content.findByTagOrNull("rgb-r") as EditText).apply { requestFocus(); callOnClick(); setText("12") }
        (content.findByTagOrNull("rgb-g") as EditText).setText("34")
        (content.findByTagOrNull("rgb-b") as EditText).setText("56")
        assertTrue((content.findByTagOrNull("custom-color-rgb") as RgbColorInputView).fromUserInput)

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()

        val expected = android.graphics.Color.rgb(12, 34, 56)
        val actual = PenSettings.customColors(activity)
        assertTrue("expected=$expected customColors=$actual active=${PenSettings.load(activity).color}", actual.contains(expected))
    }

    @Test
    fun `service start request records grace timestamp before asynchronous startup`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.callPrivate("startOverlayService")

        val prefs = activity.getSharedPreferences(OverlayService.PREF_NAME, Application.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, false))
        assertTrue(prefs.getLong(OverlayService.PREF_KEY_SERVICE_STARTED_AT, 0L) > 0L)
    }

    @Test
    fun `resume reloads pen settings changed by the overlay`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        PenSettings.saveTool(activity, "rect")
        PenSettings.saveColor(activity, DrawingElement.colorValues[2])

        controller.pause().resume()

        assertEquals("rect", activity.privateField("selectedTool"))
        assertEquals(DrawingElement.colorValues[2], activity.privateField("selectedColor"))
    }

    private fun ViewGroup.findByTagOrNull(tag: String): View? {
        if (this.tag == tag) return this
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == tag) return child
            if (child is ViewGroup) child.findByTagOrNull(tag)?.let { return it }
        }
        return null
    }

    private fun ViewGroup.findByTag(tag: String): View =
        findByTagOrNull(tag) ?: error("Missing view tag: $tag")

    private fun MainActivity.privateField(name: String): Any? =
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateField)
        }

    private fun MainActivity.callPrivate(name: String) {
        javaClass.getDeclaredMethod(name).run {
            isAccessible = true
            invoke(this@callPrivate)
        }
    }
}
