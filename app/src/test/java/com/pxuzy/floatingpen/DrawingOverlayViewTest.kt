package com.pxuzy.floatingpen

import android.app.Application
import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.pxuzy.floatingpen.core.DrawingElement as CoreDrawingElement
import com.pxuzy.floatingpen.core.DrawingSession

@RunWith(RobolectricTestRunner::class)
class DrawingOverlayViewTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `drawing session survives overlay recreation while keeping elements`() {
        val session = DrawingSession()
        val first = DrawingOverlayView(context, "pen", 0, drawingSession = session) {}
        drawGesture(first.getChildAt(0), 10f, 10f, 30f, 30f)

        val reopened = DrawingOverlayView(context, "pen", 0, drawingSession = session) {}

        assertEquals(1, reopened.elementsForTest().size)
        assertEquals(1, session.currentLayer.elements.size)
    }

    @Test
    fun `canvas panel lists current boards and layers and switches selection`() {
        val session = DrawingSession()
        val firstBoard = session.currentBoard
        val secondBoard = session.createBoard()
        val secondLayer = session.createLayer("重点")
        val view = DrawingOverlayView(context, "pen", 0, drawingSession = session) {}
        val toolbar = view.getChildAt(1) as LinearLayout

        toolbar.findByTag("canvas-selector").performClick()
        assertNotNull(view.findByTag("canvas-panel"))
        view.findByTag("board:${firstBoard.id}").performClick()
        assertEquals(firstBoard.id, session.currentBoard.id)

        (view.getChildAt(1) as LinearLayout).findByTag("canvas-selector").performClick()
        view.findByTag("board:${secondBoard.id}").performClick()
        assertEquals(secondBoard.id, session.currentBoard.id)
        assertEquals(secondLayer.id, session.currentLayer.id)
    }

    @Test
    fun `overlay is created with canvas and one toolbar`() {
        val view = DrawingOverlayView(context, "pen", 0) {}

        assertEquals(2, view.childCount)
        assertTrue(view.getChildAt(1) is LinearLayout)
        assertEquals("monochrome-toolbar", view.getChildAt(1).tag)
        val toolbar = view.getChildAt(1) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, toolbar.orientation)
        assertEquals("toolbar-drag-handle", toolbar.findByTag("toolbar-drag-handle").tag)
        assertEquals("toolbar-tool-scroll", toolbar.findByTag("toolbar-tool-scroll").tag)
        assertEquals("undo", toolbar.findByTag("undo").tag)
        assertEquals("clear", toolbar.findByTag("clear").tag)
        assertEquals("exit", toolbar.findByTag("exit").tag)
        assertEquals("更多工具", toolbar.findByTag("more-tools").contentDescription)
        assertTrue(view.textLabels().none { it.contains("✏") })
    }

    @Test
    fun `more tools panel opens and closes without rebuilding toolbar`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout

        toolbar.findByTag("more-tools").performClick()
        assertEquals("more-tools-panel", view.findByTag("more-tools-panel").tag)
        assertTrue(view.getChildAt(1) === toolbar)

        toolbar.findByTag("more-tools").performClick()
        assertEquals(2, view.childCount)
    }

    @Test
    fun `toolbar follows configured order and refreshes live`() {
        val view = DrawingOverlayView(
            context,
            "pen",
            PenSettings.load(context).toolStyles,
            toolbarToolIds = listOf("arrow", "pen"),
        ) {}
        var toolbar = view.getChildAt(1) as LinearLayout
        assertEquals("tool:arrow", toolbar.findByTag("tool:arrow").tag)
        assertEquals("tool:pen", toolbar.findByTag("tool:pen").tag)

        PenSettings.saveToolbarLayout(context, listOf("rect", "line"), setOf("rect", "line"))
        view.applyExternalSettings(PenSettings.load(context))
        toolbar = view.getChildAt(1) as LinearLayout
        assertEquals("tool:pen", toolbar.findByTag("tool:pen").tag)
        assertEquals("tool:rect", toolbar.findByTag("tool:rect").tag)
        assertEquals("tool:line", toolbar.findByTag("tool:line").tag)
    }

    @Test
    fun `toolbar rebuilds for narrow and wide window configurations`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val narrow = Configuration(context.resources.configuration).apply { screenWidthDp = 320 }
        view.applyWindowConfiguration(narrow)
        var toolbar = view.getChildAt(1) as LinearLayout
        assertTrue(toolbar.layoutParams.width <= 304.dp)
        assertEquals("toolbar-tool-scroll", toolbar.findByTag("toolbar-tool-scroll").tag)
        assertEquals("exit", toolbar.findByTag("exit").tag)

        val wide = Configuration(context.resources.configuration).apply { screenWidthDp = 700 }
        view.applyWindowConfiguration(wide)
        toolbar = view.getChildAt(1) as LinearLayout
        assertEquals(520.dp, toolbar.layoutParams.width)
        assertEquals("exit", toolbar.findByTag("exit").tag)
    }

    @Test
    fun `popup panels stay above toolbar and never coexist`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        view.applyWindowConfiguration(Configuration(context.resources.configuration).apply { screenWidthDp = 220 })
        view.measure(
            View.MeasureSpec.makeMeasureSpec(220.dp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640.dp, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 220.dp, 640.dp)
        val toolbar = view.getChildAt(1) as LinearLayout
        assertTrue(toolbar.layoutParams.width <= 204.dp)

        toolbar.findByTag("color").performClick()
        val colorPanel = view.findByTag("color-panel")
        val colorParams = colorPanel.layoutParams as FrameLayout.LayoutParams
        assertTrue(colorParams.bottomMargin > toolbar.height)

        view.applyWindowConfiguration(Configuration(context.resources.configuration).apply { screenWidthDp = 320 })
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val restoredColorPanel = view.findByTag("color-panel")
        assertEquals("color-panel", restoredColorPanel.tag)
        assertTrue((restoredColorPanel.layoutParams as FrameLayout.LayoutParams).bottomMargin > view.getChildAt(1).height)

        (view.getChildAt(1) as LinearLayout).findByTag("more-tools").performClick()
        assertTrue(view.findByTag("more-tools-panel") != colorPanel)
        assertTrue(runCatching { view.findByTag("color-panel") }.isFailure)

        (view.getChildAt(1) as LinearLayout).findByTag("color").performClick()
        assertTrue(runCatching { view.findByTag("more-tools-panel") }.isFailure)
        assertTrue(view.findByTag("color-panel") != colorPanel)
    }

    @Test
    fun `color palette frame stays compact while swatches keep accessible touch targets`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        view.applyWindowConfiguration(Configuration(context.resources.configuration).apply { screenWidthDp = 320 })
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320.dp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640.dp, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320.dp, 640.dp)

        (view.getChildAt(1) as LinearLayout).findByTag("color").performClick()
        val colorPanel = view.findByTag("color-panel")
        colorPanel.measure(
            View.MeasureSpec.makeMeasureSpec(320.dp, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(640.dp, View.MeasureSpec.AT_MOST),
        )
        val firstSwatch = view.findByTag("palette-color:0")

        assertTrue(colorPanel.layoutParams.width <= 220.dp)
        assertTrue(colorPanel.measuredWidth <= 220.dp)
        assertEquals(48.dp, firstSwatch.layoutParams.width)
        assertEquals(48.dp, firstSwatch.layoutParams.height)
    }

    @Test
    fun `custom color editor expands within the available window`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        view.applyWindowConfiguration(Configuration(context.resources.configuration).apply { screenWidthDp = 320 })
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320.dp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640.dp, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320.dp, 640.dp)

        (view.getChildAt(1) as LinearLayout).findByTag("color").performClick()
        view.findByTag("custom-color").performClick()
        val colorPanel = view.findByTag("color-panel")
        val controls = view.findByTag("hsv-controls")

        assertEquals(280.dp, colorPanel.layoutParams.width)
        assertTrue(colorPanel.layoutParams.width <= 304.dp)
        assertTrue(controls.layoutParams.width <= 260.dp)
    }

    @Test
    fun `drag handle switches toolbar to bounded top-left positioning`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320.dp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640.dp, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320.dp, 640.dp)
        val toolbar = view.getChildAt(1) as LinearLayout
        val handle = toolbar.findByTag("toolbar-drag-handle")
        handle.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        handle.dispatchTouchEvent(MotionEvent.obtain(0, 16, MotionEvent.ACTION_MOVE, 46f, 54f, 0))
        val params = toolbar.layoutParams as FrameLayout.LayoutParams
        assertEquals(Gravity.TOP or Gravity.START, params.gravity)
        assertTrue(params.leftMargin >= 0)
        assertTrue(params.topMargin >= 0)
    }

    @Test
    fun `switching toolbar tools loads each independent color and width`() {
        val styles = mapOf(
            "pen" to ToolStyle(DrawingElement.colorValues[0], 4f),
            "arrow" to ToolStyle(DrawingElement.colorValues[3], 15f),
        )
        val view = DrawingOverlayView(context, "pen", styles, arrowScale = 3f) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val canvas = view.getChildAt(0)

        drawGesture(canvas, 10f, 10f, 30f, 30f)
        toolbar.findByTag("tool:arrow").performClick()
        drawGesture(canvas, 40f, 40f, 80f, 80f)

        val pen = view.elementsForTest()[0]
        val arrow = view.elementsForTest()[1] as CoreDrawingElement.Arrow
        assertEquals(DrawingElement.colorValues[0], pen.drawColor)
        assertEquals(4f * context.resources.displayMetrics.density, pen.drawWidth)
        assertEquals(DrawingElement.colorValues[3], arrow.drawColor)
        assertEquals(15f * context.resources.displayMetrics.density, arrow.drawWidth)
        assertEquals(45f, arrow.headLengthDp)
    }

    @Test
    fun `external settings update active tool for the next stroke`() {
        val view = DrawingOverlayView(context, "pen", 0, strokeWidthDp = 4f) {}
        val updated = PenSettings.load(context).copy(
            toolStyles = mapOf("pen" to ToolStyle(DrawingElement.colorValues[2], 13f)),
        )

        view.applyExternalSettings(updated)
        drawGesture(view.getChildAt(0), 10f, 10f, 30f, 30f)

        val stroke = view.elementsForTest().single() as CoreDrawingElement.Stroke
        assertEquals(DrawingElement.colorValues[2], stroke.drawColor)
        assertEquals(13f * context.resources.displayMetrics.density, stroke.drawWidth)
    }

    @Test
    fun `overlay color change updates only current tool style`() {
        val styles = mapOf(
            "pen" to ToolStyle(DrawingElement.colorValues[0], 4f),
            "line" to ToolStyle(DrawingElement.colorValues[2], 9f),
        )
        val view = DrawingOverlayView(context, "line", styles) {}
        val toolbar = view.getChildAt(1) as LinearLayout

        toolbar.findByTag("color").performClick()
        view.findByTag("palette-color:1").performClick()

        val values = PenSettings.load(context)
        assertEquals(DrawingElement.colorValues[1], values.styleFor("line").color)
        assertEquals(DrawingElement.colorValues[0], values.styleFor("pen").color)
    }

    @Test
    fun `each toolbar tool switches drawing behavior`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val canvas = view.getChildAt(0)
        val expected = listOf(
            "pen" to CoreDrawingElement.Stroke::class.java,
            "line" to CoreDrawingElement.Line::class.java,
            "arrow" to CoreDrawingElement.Arrow::class.java,
            "rect" to CoreDrawingElement.Rect::class.java,
        )

        expected.forEachIndexed { index, (tool, elementType) ->
            toolbar.findByTag("tool:$tool").performClick()
            drawGesture(canvas, index * 20f + 10f, 10f, index * 20f + 40f, 40f)
            assertEquals(tool, view.currentToolForTest())
            assertTrue(elementType.isInstance(view.elementsForTest().last()))
        }
    }

    @Test
    fun `color button opens palette and selected swatch applies globally`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val canvas = view.getChildAt(0)

        toolbar.findByTag("color").performClick()
        view.findByTag("palette-color:1").performClick()
        drawGesture(canvas, 10f, 10f, 30f, 30f)

        assertEquals(DrawingElement.colorValues[1], view.currentColorForTest())
        assertEquals(DrawingElement.colorValues[1], view.elementsForTest().single().drawColor)
    }

    @Test
    fun `selected tool indicator uses current pen color and updates immediately`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val pen = toolbar.findByTag("tool:pen")

        assertEquals(DrawingElement.colorValues[0], pen.getTag(R.id.tag_selected_color))

        toolbar.findByTag("color").performClick()
        view.findByTag("palette-color:1").performClick()

        assertEquals(DrawingElement.colorValues[1], pen.getTag(R.id.tag_selected_color))
    }

    @Test
    fun `rendering old elements cannot overwrite selected color`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val canvas = view.getChildAt(0)

        drawGesture(canvas, 10f, 10f, 30f, 30f)
        toolbar.findByTag("color").performClick()
        view.findByTag("palette-color:1").performClick()
        canvas.draw(android.graphics.Canvas())
        drawGesture(canvas, 40f, 40f, 60f, 60f)

        assertEquals(DrawingElement.colorValues[0], view.elementsForTest()[0].drawColor)
        assertEquals(DrawingElement.colorValues[1], view.elementsForTest()[1].drawColor)
    }

    @Test
    fun `tool and color changes are reported to service`() {
        val changes = mutableListOf<Pair<String, Int>>()
        val view = DrawingOverlayView(
            context,
            "pen",
            0,
            onSelectionChanged = { tool, color -> changes += tool to color },
            onExit = {}
        )
        val toolbar = view.getChildAt(1) as LinearLayout

        toolbar.findByTag("tool:arrow").performClick()
        toolbar.findByTag("color").performClick()
        view.findByTag("palette-color:1").performClick()

        assertEquals(listOf("arrow" to DrawingElement.colorValues[0], "arrow" to DrawingElement.colorValues[1]), changes)
    }

    @Test
    fun `arrow head is filled and capped at compact size`() {
        assertEquals(12f, DrawingOverlayView.resolveArrowHeadLengthDp(6f, 2f))
        assertEquals(24f, DrawingOverlayView.resolveArrowHeadLengthDp(6f, 4f))
    }

    @Test
    fun `new arrow captures configured head size`() {
        val view = DrawingOverlayView(context, "arrow", 0, strokeWidthDp = 8f, arrowScale = 3f) {}
        drawGesture(view.getChildAt(0), 10f, 10f, 80f, 80f)

        val arrow = view.elementsForTest().single() as CoreDrawingElement.Arrow
        assertEquals(24f, arrow.headLengthDp)
    }

    @Test
    fun `compact toolbar hides color text to preserve exit button`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val colorControl = toolbar.findByTag("color") as LinearLayout

        assertEquals("", (colorControl.getChildAt(1) as TextView).text.toString())
    }

    @Test
    fun `compact toolbar fits a 320dp screen with exit visible`() {
        val density = context.resources.displayMetrics.density
        context.resources.displayMetrics.widthPixels = (320 * density).toInt()
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1)

        toolbar.measure(
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST),
        )

        assertTrue(toolbar.measuredWidth <= (320 * density).toInt())
        assertTrue(view.findByTag("exit").visibility == View.VISIBLE)
    }

    @Test
    fun `cancelled pen gesture leaves no undo entry`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val canvas = view.getChildAt(0)

        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_CANCEL, 10f, 10f, 0))

        assertTrue(view.elementsForTest().isEmpty())
    }

    @Test
    fun `second pointer cancels active stroke instead of jumping between fingers`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val canvas = view.getChildAt(0)

        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0 },
            MotionEvent.PointerProperties().apply { id = 1 },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = 10f; y = 10f },
            MotionEvent.PointerCoords().apply { x = 200f; y = 200f },
        )
        val pointerDown = MotionEvent.obtain(
            0, 10, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        canvas.dispatchTouchEvent(pointerDown)

        assertTrue(view.elementsForTest().isEmpty())
        pointerDown.recycle()
    }

    @Test
    fun `finger palm contact does not cancel an active stylus stroke`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val canvas = view.getChildAt(0)
        val stylusDown = multiPointerEvent(
            MotionEvent.ACTION_DOWN,
            listOf(PointerSpec(0, 10f, 10f, MotionEvent.TOOL_TYPE_STYLUS)),
        )
        canvas.dispatchTouchEvent(stylusDown)
        val palmDown = multiPointerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(
                PointerSpec(0, 20f, 20f, MotionEvent.TOOL_TYPE_STYLUS),
                PointerSpec(1, 200f, 200f, MotionEvent.TOOL_TYPE_FINGER),
            ),
        )
        canvas.dispatchTouchEvent(palmDown)
        val stylusMove = multiPointerEvent(
            MotionEvent.ACTION_MOVE,
            listOf(
                PointerSpec(0, 40f, 40f, MotionEvent.TOOL_TYPE_STYLUS),
                PointerSpec(1, 210f, 210f, MotionEvent.TOOL_TYPE_FINGER),
            ),
        )
        canvas.dispatchTouchEvent(stylusMove)

        val stroke = view.elementsForTest().single() as CoreDrawingElement.Stroke
        assertTrue(stroke.points.size >= 2)
        stylusDown.recycle(); palmDown.recycle(); stylusMove.recycle()
    }

    @Test
    fun `stylus pointer up finalizes before later finger up`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val canvas = view.getChildAt(0)
        canvas.dispatchTouchEvent(multiPointerEvent(
            MotionEvent.ACTION_DOWN,
            listOf(PointerSpec(0, 10f, 10f, MotionEvent.TOOL_TYPE_STYLUS)),
        ))
        canvas.dispatchTouchEvent(multiPointerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(
                PointerSpec(0, 20f, 20f, MotionEvent.TOOL_TYPE_STYLUS),
                PointerSpec(1, 200f, 200f, MotionEvent.TOOL_TYPE_FINGER),
            ),
        ))
        val stylusUp = multiPointerEvent(
            MotionEvent.ACTION_POINTER_UP,
            listOf(
                PointerSpec(0, 40f, 40f, MotionEvent.TOOL_TYPE_STYLUS),
                PointerSpec(1, 210f, 210f, MotionEvent.TOOL_TYPE_FINGER),
            ),
        )
        canvas.dispatchTouchEvent(stylusUp)
        canvas.dispatchTouchEvent(multiPointerEvent(
            MotionEvent.ACTION_UP,
            listOf(PointerSpec(1, 220f, 220f, MotionEvent.TOOL_TYPE_FINGER)),
        ))

        val stroke = view.elementsForTest().single() as CoreDrawingElement.Stroke
        assertEquals(Pair(40f, 40f), stroke.points.last())
        stylusUp.recycle()
    }

    @Test
    fun `moves after second pointer do not corrupt the previous completed stroke`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val canvas = view.getChildAt(0)

        drawGesture(canvas, 5f, 5f, 20f, 20f)
        val completedStroke = view.elementsForTest().single() as CoreDrawingElement.Stroke
        val completedPointCount = completedStroke.points.size

        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 30, MotionEvent.ACTION_DOWN, 30f, 30f, 0))
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0 },
            MotionEvent.PointerProperties().apply { id = 1 },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = 30f; y = 30f },
            MotionEvent.PointerCoords().apply { x = 200f; y = 200f },
        )
        val pointerDown = MotionEvent.obtain(
            0, 40, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        canvas.dispatchTouchEvent(pointerDown)
        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_MOVE, 220f, 220f, 0))

        assertEquals(1, view.elementsForTest().size)
        assertEquals(completedPointCount, completedStroke.points.size)
        pointerDown.recycle()
    }

    @Test
    fun `pen tap creates a visible two point stroke`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val canvas = view.getChildAt(0)

        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 10f, 10f, 0))

        val stroke = view.elementsForTest().single() as CoreDrawingElement.Stroke
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun `configured stroke width is applied to new elements`() {
        val view = DrawingOverlayView(context, "pen", 0, strokeWidthDp = 12f) {}
        val canvas = view.getChildAt(0)

        drawGesture(canvas, 10f, 10f, 30f, 30f)

        val stroke = view.elementsForTest().single() as CoreDrawingElement.Stroke
        assertEquals(12f * context.resources.displayMetrics.density, stroke.drawWidth)
    }

    @Test
    fun `undo clear and exit actions work`() {
        var exits = 0
        val view = DrawingOverlayView(context, "pen", 0) { exits++ }
        val toolbar = view.getChildAt(1) as LinearLayout
        val canvas = view.getChildAt(0)

        drawGesture(canvas, 10f, 10f, 20f, 20f)
        drawGesture(canvas, 30f, 30f, 40f, 40f)
        toolbar.findByTag("undo").performClick()
        assertEquals(1, view.elementsForTest().size)

        toolbar.findByTag("clear").performClick()
        assertTrue(view.elementsForTest().isEmpty())

        toolbar.findByTag("exit").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun `undo during an active stroke does not modify the previous stroke`() {
        val view = DrawingOverlayView(context, "pen", 0) {}
        val toolbar = view.getChildAt(1) as LinearLayout
        val canvas = view.getChildAt(0)

        drawGesture(canvas, 5f, 5f, 20f, 20f)
        val previous = view.elementsForTest().single() as CoreDrawingElement.Stroke
        val previousPointCount = previous.points.size
        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 30, MotionEvent.ACTION_DOWN, 30f, 30f, 0))
        toolbar.findByTag("undo").performClick()
        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, 200f, 200f, 0))
        canvas.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 220f, 220f, 0))

        assertEquals(1, view.elementsForTest().size)
        assertEquals(previousPointCount, previous.points.size)
    }

    @Test
    fun `zero length shape gestures are ignored`() {
        val view = DrawingOverlayView(context, "arrow", 0) {}
        drawGesture(view.getChildAt(0), 40f, 40f, 40f, 40f)

        assertTrue(view.elementsForTest().isEmpty())
    }

    private fun drawGesture(view: View, x1: Float, y1: Float, x2: Float, y2: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x1, y1, 0)
        val move = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, x2, y2, 0)
        val up = MotionEvent.obtain(0, 20, MotionEvent.ACTION_UP, x2, y2, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(move)
        view.dispatchTouchEvent(up)
        down.recycle(); move.recycle(); up.recycle()
    }

    private data class PointerSpec(val id: Int, val x: Float, val y: Float, val toolType: Int)

    private fun multiPointerEvent(action: Int, points: List<PointerSpec>): MotionEvent {
        val properties = points.map { point ->
            MotionEvent.PointerProperties().apply { id = point.id; toolType = point.toolType }
        }.toTypedArray()
        val coordinates = points.map { point ->
            MotionEvent.PointerCoords().apply { x = point.x; y = point.y }
        }.toTypedArray()
        return MotionEvent.obtain(0, 10, action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun DrawingOverlayView.elementsForTest(): List<DrawingElement> =
        javaClass.getDeclaredField("elements").run {
            isAccessible = true
            get(this@elementsForTest) as List<DrawingElement>
        }

    private fun DrawingOverlayView.currentToolForTest(): String =
        javaClass.getDeclaredField("currentToolId").run {
            isAccessible = true
            get(this@currentToolForTest) as String
        }

    private fun DrawingOverlayView.currentColorForTest(): Int =
        javaClass.getDeclaredField("currentColor").run {
            isAccessible = true
            getInt(this@currentColorForTest)
        }

    private fun ViewGroup.findByTag(tag: String): View {
        if (this.tag == tag) return this
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.tag == tag) return child
            if (child is ViewGroup) {
                runCatching { child.findByTag(tag) }.getOrNull()?.let { return it }
            }
        }
        error("Missing view tag: $tag")
    }

    private fun ViewGroup.textLabels(): List<String> {
        val labels = mutableListOf<String>()
        for (index in 0 until childCount) {
            when (val child = getChildAt(index)) {
                is TextView -> labels += child.text.toString()
                is ViewGroup -> labels += child.textLabels()
            }
        }
        return labels
    }
}