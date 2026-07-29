package com.pxuzy.floatingpen

import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RgbColorInputViewTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `channels use integer numeric keyboard and expose stable tags`() {
        val view = RgbColorInputView(context)

        listOf("rgb-r", "rgb-g", "rgb-b").forEach { tag ->
            val input = view.findByTag(tag) as EditText
            assertEquals(InputType.TYPE_CLASS_NUMBER, input.inputType)
            assertEquals(48.dp, input.layoutParams.height)
        }
    }

    @Test
    fun `zero and 255 channel boundaries parse as opaque color`() {
        val view = RgbColorInputView(context)
        (view.findByTag("rgb-r") as EditText).setText("0")
        (view.findByTag("rgb-g") as EditText).setText("255")
        (view.findByTag("rgb-b") as EditText).setText("0")

        assertEquals(Color.rgb(0, 255, 0), view.parsedColor())
        assertTrue((view.findByTag("rgb-error") as TextView).text.isEmpty())
    }

    @Test
    fun `rejects empty non numeric and out of range channels`() {
        val view = RgbColorInputView(context)
        (view.findByTag("rgb-r") as EditText).setText("256")
        (view.findByTag("rgb-g") as EditText).setText("")
        (view.findByTag("rgb-b") as EditText).setText("12")

        assertNull(view.parsedColor())
        val error = view.findByTag("rgb-error") as TextView
        assertTrue(error.text.contains("0"))
        assertTrue(error.text.contains("255"))
    }

    @Test
    fun `channels clamp typed values above 255 immediately`() {
        val view = RgbColorInputView(context)
        val red = view.findByTag("rgb-r") as EditText
        val green = view.findByTag("rgb-g") as EditText

        red.setText("256")
        green.setText("999")
        (view.findByTag("rgb-b") as EditText).setText("25")

        assertEquals("255", red.text.toString())
        assertEquals("255", green.text.toString())
        assertEquals(Color.rgb(255, 255, 25), view.parsedColor())
    }

    @Test
    fun `channels keep valid boundary and partial values unchanged`() {
        val view = RgbColorInputView(context)
        val red = view.findByTag("rgb-r") as EditText
        val green = view.findByTag("rgb-g") as EditText
        val blue = view.findByTag("rgb-b") as EditText

        red.setText("0")
        green.setText("25")
        blue.setText("255")

        assertEquals("0", red.text.toString())
        assertEquals("25", green.text.toString())
        assertEquals("255", blue.text.toString())
    }

    @Test
    fun `setting color synchronizes every channel`() {
        val view = RgbColorInputView(context)

        view.color = Color.argb(100, 12, 34, 56)

        assertEquals("12", (view.findByTag("rgb-r") as EditText).text.toString())
        assertEquals("34", (view.findByTag("rgb-g") as EditText).text.toString())
        assertEquals("56", (view.findByTag("rgb-b") as EditText).text.toString())
    }

    @Test
    fun `channel click notifies the host that RGB input is active`() {
        val view = RgbColorInputView(context)
        var activated: EditText? = null
        view.setOnInputActivatedListener { activated = it }
        val red = view.findByTag("rgb-r") as EditText

        assertTrue(red.callOnClick())

        assertEquals(red, activated)
    }

    @Test
    fun `programmatic color sync is clean but channel edits are tracked`() {
        val view = RgbColorInputView(context)
        view.color = Color.rgb(10, 20, 30)
        assertTrue(!view.fromUserInput)

        (view.findByTag("rgb-r") as EditText).setText("11")

        assertTrue(view.fromUserInput)
    }

    private fun ViewGroup.findByTag(tag: String): View {
        if (this.tag == tag) return this
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == tag) return child
            if (child is ViewGroup) runCatching { child.findByTag(tag) }.getOrNull()?.let { return it }
        }
        error("Missing view tag: $tag")
    }
}
