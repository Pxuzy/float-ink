package com.pxuzy.floatingpen

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.text.InputType
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.pxuzy.floatingpen.core.DrawingElement as CoreDrawingElement
import com.pxuzy.floatingpen.core.ArrowGeometry
import com.pxuzy.floatingpen.core.DrawingSession
import kotlin.math.*

class DrawingOverlayView(
    context: Context, toolId: String, initialColor: Int,
    strokeWidthDp: Float = PenSettings.DEFAULT_WIDTH_DP,
    private var arrowScale: Float = PenSettings.DEFAULT_ARROW_SCALE,
    toolbarToolIds: List<String> = PenSettings.TOOL_IDS,
    val drawingSession: DrawingSession = DrawingSession(),
    private val onSelectionChanged: (toolId: String, color: Int) -> Unit = { _, _ -> },
    private val onExit: () -> Unit,
) : FrameLayout(context) {

    private val toolStyles = mutableMapOf(
        PenSettings.normalizeTool(toolId) to ToolStyle(normalizeLegacyColor(initialColor), strokeWidthDp)
    )

    constructor(
        context: Context,
        toolId: String,
        styles: Map<String, ToolStyle>,
        arrowScale: Float = PenSettings.DEFAULT_ARROW_SCALE,
        toolbarToolIds: List<String> = PenSettings.TOOL_IDS,
        drawingSession: DrawingSession = DrawingSession(),
        onSelectionChanged: (toolId: String, color: Int) -> Unit = { _, _ -> },
        onExit: () -> Unit,
    ) : this(
        context = context,
        toolId = PenSettings.normalizeTool(toolId),
        initialColor = styles[PenSettings.normalizeTool(toolId)]?.color ?: PenSettings.DEFAULT_COLOR_ARGB,
        strokeWidthDp = styles[PenSettings.normalizeTool(toolId)]?.widthDp ?: PenSettings.DEFAULT_WIDTH_DP,
        arrowScale = arrowScale,
        toolbarToolIds = toolbarToolIds,
        onSelectionChanged = onSelectionChanged,
        onExit = onExit,
        drawingSession = drawingSession,
    ) {
        toolStyles.putAll(styles.mapKeys { PenSettings.normalizeTool(it.key) })
        applyCurrentToolStyle()
    }

    private val density = resources.displayMetrics.density
    private var elements: MutableList<DrawingElement> = drawingSession.currentLayer.elements
    private var sx = 0f; private var sy = 0f; private var cx = 0f; private var cy = 0f
    private var isDrawing = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeToolType = MotionEvent.TOOL_TYPE_UNKNOWN
    private var currentToolId = toolId
    private var configuredToolIds = normalizeToolbarToolIds(toolbarToolIds)
    private var currentColor = normalizeLegacyColor(initialColor)
    private lateinit var canvasView: View
    private var colorPanel: View? = null
    private var moreToolsPanel: View? = null
    private var canvasPanel: View? = null
    private val toolButtons = mutableMapOf<String, View>()
    private var windowWidthDp = resources.configuration.screenWidthDp
    private var compactLayout = isCompactWidth()

    private fun isCompactWidth(): Boolean =
        resources.configuration.screenWidthDp in 1..399

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyWindowConfiguration(newConfig)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        colorPanel?.let(::positionPopupAboveToolbar)
        moreToolsPanel?.let(::positionPopupAboveToolbar)
        canvasPanel?.let(::positionPopupAboveToolbar)
    }

    internal fun applyWindowConfiguration(newConfig: Configuration) {
        val previousWidthDp = windowWidthDp
        windowWidthDp = newConfig.screenWidthDp
        val nextCompact = newConfig.screenWidthDp in 1..399
        if (nextCompact != compactLayout || newConfig.screenWidthDp != previousWidthDp) {
            compactLayout = nextCompact
            rebuildToolbar()
        }
    }

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        isAntiAlias = true; strokeWidth = strokeWidthDp.dpf
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF"); textSize = 14.dpf; textAlign = Paint.Align.CENTER
    }
    private val strokePath = Path()
    private val arrowHeadPath = Path()

    init {
        drawPaint.color = currentColor
        setBackgroundColor(Color.argb(10, 0, 0, 0))

        canvasView = object : View(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.x; val y = event.y
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePointerId = event.getPointerId(0)
                        activeToolType = event.getToolType(0)
                        sx = x; sy = y; cx = x; cy = y; isDrawing = true
                        if (currentToolId == "pen") elements.add(
                            CoreDrawingElement.Stroke(mutableListOf(Pair(x, y)), selectedColor, drawPaint.strokeWidth))
                        invalidate(); return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDrawing) return true
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex < 0) return true
                        cx = event.getX(pointerIndex); cy = event.getY(pointerIndex)
                        if (currentToolId == "pen" && elements.isNotEmpty()) {
                            val e = elements.last()
                            if (e is CoreDrawingElement.Stroke) e.points.add(Pair(cx, cy))
                        }
                        invalidate(); return true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        val newPointerType = event.getToolType(event.actionIndex)
                        if (activeToolType == MotionEvent.TOOL_TYPE_STYLUS && newPointerType == MotionEvent.TOOL_TYPE_FINGER) {
                            return true
                        }
                        if (currentToolId == "pen" && isDrawing && elements.lastOrNull() is CoreDrawingElement.Stroke) {
                            elements.removeAt(elements.lastIndex)
                        }
                        isDrawing = false
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        // A stylus can be lifted before a palm/finger contact. If the
                        // active pointer is not finalized here, the later finger UP
                        // would append its unrelated coordinate to this stroke.
                        if (event.getPointerId(event.actionIndex) == activePointerId) {
                            val activeX = event.getX(event.actionIndex)
                            val activeY = event.getY(event.actionIndex)
                            if (currentToolId == "pen" && isDrawing) {
                                val stroke = elements.lastOrNull() as? CoreDrawingElement.Stroke
                                if (stroke?.points?.size == 1) {
                                    stroke.points.add(Pair(activeX, activeY))
                                }
                            }
                            isDrawing = false
                            activePointerId = MotionEvent.INVALID_POINTER_ID
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (currentToolId == "pen" && isDrawing) {
                            val stroke = elements.lastOrNull() as? CoreDrawingElement.Stroke
                            if (stroke?.points?.size == 1) stroke.points.add(Pair(x, y))
                        } else if (isDrawing) {
                            val el = when (currentToolId) {
                                "line" -> CoreDrawingElement.Line(Pair(sx, sy), Pair(x, y), selectedColor, drawPaint.strokeWidth)
                                "arrow" -> CoreDrawingElement.Arrow(
                                    Pair(sx, sy), Pair(x, y), selectedColor, drawPaint.strokeWidth,
                                    resolveArrowHeadLengthDp(drawPaint.strokeWidth / density, arrowScale)
                                )
                                "rect" -> CoreDrawingElement.Rect(Pair(sx, sy), Pair(x, y), selectedColor, drawPaint.strokeWidth)
                                else -> null
                            }
                            if (el != null && (x != sx || y != sy)) elements.add(el)
                        }
                        isDrawing = false
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        invalidate(); return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (currentToolId == "pen" && isDrawing && elements.lastOrNull() is CoreDrawingElement.Stroke) {
                            elements.removeAt(elements.lastIndex)
                        }
                        isDrawing = false
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        invalidate(); return true
                    }
                }
                return super.onTouchEvent(event)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (elements.isEmpty() && !isDrawing)
                    canvas.drawText("手指滑动开始画线", width / 2f, height / 2f - 120.dpf, hintPaint)
                elements.forEach { drawElement(canvas, it) }
                if (isDrawing && currentToolId != "pen") {
                    val pe = when (currentToolId) {
                        "line" -> CoreDrawingElement.Line(Pair(sx, sy), Pair(cx, cy), selectedColor, drawPaint.strokeWidth)
                        "arrow" -> CoreDrawingElement.Arrow(
                            Pair(sx, sy), Pair(cx, cy), selectedColor, drawPaint.strokeWidth,
                            resolveArrowHeadLengthDp(drawPaint.strokeWidth / density, arrowScale)
                        )
                        "rect" -> CoreDrawingElement.Rect(Pair(sx, sy), Pair(cx, cy), selectedColor, drawPaint.strokeWidth)
                        else -> null
                    }
                    if (pe != null) drawElement(canvas, pe)
                }
                // drawElement() reuses drawPaint for historical elements. Restore
                // the active tool style so a redraw cannot affect the next stroke.
                val currentStyle = toolStyles[currentToolId]
                drawPaint.color = currentColor
                drawPaint.strokeWidth = currentStyle?.widthDp?.dpf ?: drawPaint.strokeWidth
                drawPaint.style = Paint.Style.STROKE
            }
        }
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addView(buildToolbar())
    }

    private val selectedColor: Int
        get() = currentColor

    private fun drawElement(canvas: Canvas, el: DrawingElement) {
        drawPaint.color = el.drawColor; drawPaint.strokeWidth = el.drawWidth
        when (el) {
            is CoreDrawingElement.Stroke -> {
                if (el.points.size >= 2) {
                    strokePath.rewind()
                    strokePath.moveTo(el.points[0].first, el.points[0].second)
                    for (i in 1 until el.points.size) {
                        strokePath.lineTo(el.points[i].first, el.points[i].second)
                    }
                    canvas.drawPath(strokePath, drawPaint)
                }
            }
            is CoreDrawingElement.Line -> canvas.drawLine(el.start.first, el.start.second, el.end.first, el.end.second, drawPaint)
            is CoreDrawingElement.Arrow -> {
                val headLength = el.headLengthDp.dpf
                val base = ArrowGeometry.headBasePoint(
                    el.start.first, el.start.second, el.end.first, el.end.second, headLength
                )
                // Stop the shaft at the triangle base so the round stroke cap cannot blunt the tip.
                canvas.drawLine(el.start.first, el.start.second, base.first, base.second, drawPaint)
                val angle = atan2((el.end.second - el.start.second).toDouble(), (el.end.first - el.start.first).toDouble())
                val halfWidth = headLength * 0.45f
                val perpendicularX = (-sin(angle) * halfWidth).toFloat()
                val perpendicularY = (cos(angle) * halfWidth).toFloat()
                arrowHeadPath.rewind()
                arrowHeadPath.moveTo(el.end.first, el.end.second)
                arrowHeadPath.lineTo(base.first + perpendicularX, base.second + perpendicularY)
                arrowHeadPath.lineTo(base.first - perpendicularX, base.second - perpendicularY)
                arrowHeadPath.close()
                val previousStyle = drawPaint.style
                drawPaint.style = Paint.Style.FILL
                canvas.drawPath(arrowHeadPath, drawPaint)
                drawPaint.style = previousStyle
            }
            is CoreDrawingElement.Rect -> {
                val l = minOf(el.start.first, el.end.first); val t = minOf(el.start.second, el.end.second)
                val r = maxOf(el.start.first, el.end.first); val b = maxOf(el.start.second, el.end.second)
                canvas.drawRect(l, t, r, b, drawPaint)
            }
        }
    }

    // ===== Pill 悬浮工具栏 =====

    private fun buildToolbar(): View {
        val screenWidth = windowWidthDp
            .takeIf { it > 0 }
            ?.let { (it * density).toInt() }
            ?: resources.displayMetrics.widthPixels
        val availableWidth = (screenWidth - 16.dp).coerceAtLeast(1)
        val maxWidth = availableWidth.coerceAtMost(520.dp)
        val bar = LinearLayout(context).apply {
            tag = "monochrome-toolbar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val horizontalPadding = if (compactLayout) 6.dp else 10.dp
            setPadding(horizontalPadding, 6.dp, horizontalPadding, 6.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 10, 13, 17))
                cornerRadius = 12.dpf
                setStroke(1.dpf.toInt(), Color.argb(88, 255, 255, 255))
            }
            elevation = 8f
        }

        val dragHandle = ToolIconView(context, "drag").apply {
            tag = "toolbar-drag-handle"
            contentDescription = "拖动工具栏"
            layoutParams = LinearLayout.LayoutParams(30.dp, 40.dp)
        }
        bar.addView(dragHandle)
        bar.addView(createActionBtn("canvas", ::toggleCanvasPanel).apply {
            tag = "canvas-selector"
            contentDescription = "选择画板和图层"
        })
        bar.addView(createColorDot())

        val toolContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            configuredToolIds.take(4).forEach { toolId -> addView(createToolIcon(toolId)) }
            addView(createActionBtn("more", ::toggleMoreTools).apply { tag = "more-tools" })
        }
        val toolScroll = HorizontalScrollView(context).apply {
            tag = "toolbar-tool-scroll"
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(toolContent, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 48.dp))
        }
        bar.addView(toolScroll, LinearLayout.LayoutParams(0, 52.dp, 1f))

        bar.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1.dpf.toInt(), 24.dp).apply { marginStart = 4.dp; marginEnd = 2.dp }
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
        })
        bar.addView(createActionBtn("undo") {
            if (isDrawing) {
                discardActiveGesture()
                canvasView.invalidate()
            } else if (elements.isNotEmpty()) {
                elements.removeAt(elements.lastIndex)
                canvasView.invalidate()
            }
        }.apply { tag = "undo" })
        bar.addView(createActionBtn("clear") {
            discardActiveGesture()
            elements.clear()
            canvasView.invalidate()
        }.apply { tag = "clear" })
        bar.addView(createActionBtn("exit", action = onExit).apply { tag = "exit" })

        installToolbarDragHandle(dragHandle, bar)
        bar.layoutParams = LayoutParams(maxWidth, 56.dp, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = 20.dp
        }
        return bar
    }

    private fun installToolbarDragHandle(handle: View, toolbar: View) {
        var startRawX = 0f
        var startRawY = 0f
        var startLeft = 0
        var startTop = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val params = toolbar.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener false
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startLeft = toolbar.left
                    startTop = toolbar.top
                    params.gravity = Gravity.TOP or Gravity.START
                    params.leftMargin = startLeft
                    params.topMargin = startTop
                    params.rightMargin = 0
                    params.bottomMargin = 0
                    toolbar.layoutParams = params
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = toolbar.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener false
                    val maxLeft = (width - toolbar.width).coerceAtLeast(0)
                    val maxTop = (height - toolbar.height).coerceAtLeast(0)
                    params.leftMargin = (startLeft + event.rawX - startRawX).toInt().coerceIn(0, maxLeft)
                    params.topMargin = (startTop + event.rawY - startRawY).toInt().coerceIn(0, maxTop)
                    toolbar.layoutParams = params
                    colorPanel?.let(::positionPopupAboveToolbar)
                    moreToolsPanel?.let(::positionPopupAboveToolbar)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun createColorDot(): View {
        val size = if (compactLayout) 24.dp else 28.dp
        val wrapper = LinearLayout(context).apply {
            tag = "color"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, if (compactLayout) 36.dp else 40.dp).apply { marginEnd = 4.dp }
            setOnClickListener { toggleColorPanel() }
        }
        // Colored circle
        wrapper.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 4.dp }
            background = GradientDrawable().apply {
                setColor(currentColor)
                cornerRadius = (size / 2).toFloat()
                setStroke(2.dpf.toInt(), Color.parseColor("#55FFFFFF"))
            }
        })
        // Color name text
        wrapper.addView(TextView(context).apply {
            text = if (compactLayout) "" else colorLabel(currentColor)
            textSize = if (compactLayout) 10f else 11f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER_VERTICAL
        })
        // Separator
        wrapper.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1.dpf.toInt(), 24.dp).apply { marginStart = 8.dp }
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        })
        return wrapper
    }

    private fun createToolIcon(toolId: String): View {
        val size = if (compactLayout) 36.dp else 40.dp
        return ToolIconView(context, toolId).apply {
            tag = "tool:$toolId"
            contentDescription = DrawingElement.toolNames[toolId]
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 2.dp }
            setOnClickListener { selectTool(toolId) }
            toolButtons[toolId] = this
            updateToolButton(this, toolId == currentToolId)
        }
    }

    private fun selectTool(toolId: String) {
        val normalized = PenSettings.normalizeTool(toolId)
        if (normalized == currentToolId) return
        discardActiveGesture()
        currentToolId = normalized
        applyCurrentToolStyle()
        onSelectionChanged(currentToolId, currentColor)
        refreshColorControl()
        refreshToolIndicators()
        canvasView.invalidate()
    }

    private fun discardActiveGesture() {
        if (currentToolId == "pen" && isDrawing && elements.lastOrNull() is CoreDrawingElement.Stroke) {
            elements.removeAt(elements.lastIndex)
        }
        isDrawing = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeToolType = MotionEvent.TOOL_TYPE_UNKNOWN
    }

    private fun applyCurrentToolStyle() {
        val style = toolStyles[currentToolId] ?: PenSettings.load(context).styleFor(currentToolId)
        toolStyles[currentToolId] = style
        currentColor = style.color
        drawPaint.color = style.color
        drawPaint.strokeWidth = style.widthDp.dpf
    }

    internal fun applyExternalSettings(settings: PenSettings.Values) {
        toolStyles.putAll(settings.toolStyles)
        arrowScale = settings.arrowScale
        val nextToolbarIds = normalizeToolbarToolIds(settings.visibleToolbarToolIds())
        if (nextToolbarIds != configuredToolIds) {
            configuredToolIds = nextToolbarIds
            rebuildToolbar()
        }
        applyCurrentToolStyle()
        refreshColorControl()
        refreshToolIndicators()
        canvasView.invalidate()
    }

    private fun rebuildToolbar() {
        if (childCount < 2) return
        val restoreColorPanel = colorPanel != null
        val restoreHsvControls = colorPanel?.findViewWithTag<View>("hsv-controls") != null
        val restoreMorePanel = moreToolsPanel != null
        colorPanel?.let { removeView(it); colorPanel = null }
        moreToolsPanel?.let { removeView(it); moreToolsPanel = null }
        removeViewAt(1)
        addView(buildToolbar(), 1)
        when {
            restoreColorPanel -> {
                val panel = buildColorPanel() as LinearLayout
                colorPanel = panel
                addView(panel)
                if (restoreHsvControls) showHsvControls(panel)
                positionPopupAboveToolbar(panel)
                panel.post { positionPopupAboveToolbar(panel) }
            }
            restoreMorePanel -> {
                toggleMoreTools()
            }
        }
    }

    private fun normalizeToolbarToolIds(ids: List<String>): List<String> {
        val normalized = ids.map(PenSettings::normalizeTool).distinct()
        return if (currentToolId in normalized) normalized else listOf(currentToolId) + normalized
    }

    private fun refreshToolIndicators() {
        toolButtons.forEach { (id, button) -> updateToolButton(button, id == currentToolId) }
    }

    private fun updateToolButton(button: View, isActive: Boolean) {
        val iconColor = Color.WHITE
        (button as? ToolIconView)?.setIconColor(iconColor)
        button.setTag(R.id.tag_selected_color, if (isActive) selectedColor else null)
        button.background = GradientDrawable().apply {
            if (isActive) {
                setStroke(2.dpf.toInt(), Color.WHITE)
                setColor(Color.argb(34, 255, 255, 255))
            } else {
                setColor(Color.TRANSPARENT)
            }
            cornerRadius = 8.dpf
        }
    }

    private fun createActionBtn(icon: String, action: () -> Unit): View {
        return ToolIconView(context, icon).apply {
            contentDescription = when (icon) {
                "undo" -> "撤销"
                "clear" -> "清空"
                "more" -> "更多工具"
                "canvas" -> "选择画板和图层"
                else -> "退出"
            }
            val horizontalPadding = if (compactLayout) 6.dp else 10.dp
            setPadding(horizontalPadding, 6.dp, horizontalPadding, 6.dp)
            setOnClickListener { action() }
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 6.dpf
            }
                layoutParams = LinearLayout.LayoutParams(actionButtonSize(), actionButtonSize()).apply { marginStart = 2.dp }
        }
    }

    private fun actionButtonSize(): Int = when {
        windowWidthDp in 1..179 -> 32.dp
        compactLayout -> 40.dp
        else -> 44.dp
    }

    private fun positionPopupAboveToolbar(popup: View) {
        val toolbar = findViewWithTag<View>("monochrome-toolbar") ?: return
        val toolbarParams = toolbar.layoutParams as? FrameLayout.LayoutParams ?: return
        val toolbarHeight = toolbar.height.takeIf { it > 0 }
            ?: toolbar.measuredHeight.takeIf { it > 0 }
            ?: 112.dp
        val navigationInset = if (Build.VERSION.SDK_INT >= 30) {
            rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
        } else {
            0
        }
        val fallbackWidth = (windowWidthDp * density).toInt()
        val availableWidth = ((width.takeIf { it > 0 } ?: fallbackWidth) - 16.dp).coerceAtLeast(1)
        val params = (popup.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        if (params.width <= 0) {
            if (popup.measuredWidth == 0) {
                val desiredWidth = if (popup.tag == "color-panel") COLOR_PANEL_COMPACT_WIDTH_DP.dp else 220.dp
                params.width = desiredWidth.coerceAtMost(availableWidth)
            } else if (popup.measuredWidth > availableWidth) {
                params.width = availableWidth
            }
        } else if (params.width > availableWidth) {
            params.width = availableWidth
        }
        val toolbarIsDragged = toolbarParams.gravity and Gravity.TOP == Gravity.TOP
        if (toolbarIsDragged) {
            val popupHeight = popup.height.takeIf { it > 0 }
                ?: popup.measuredHeight.takeIf { it > 0 }
                ?: if (popup.tag == "color-panel") 180.dp else 80.dp
            val gap = 8.dp
            val topAbove = toolbar.top - popupHeight - gap
            val topBelow = toolbar.bottom + gap
            val maxTop = (height - popupHeight - gap).coerceAtLeast(gap)
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.topMargin = if (topAbove >= gap) topAbove else topBelow.coerceAtMost(maxTop)
            params.bottomMargin = 0
        } else {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.bottomMargin = toolbarHeight + toolbarParams.bottomMargin + navigationInset + 8.dp
        }
        popup.layoutParams = params
    }

    private fun toggleMoreTools() {
        moreToolsPanel?.let {
            removeView(it)
            moreToolsPanel = null
            return
        }
        colorPanel?.let {
            removeView(it)
            colorPanel = null
        }
        val panel = LinearLayout(context).apply {
            tag = "more-tools-panel"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18.dp, 14.dp, 18.dp, 14.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 12, 16, 21))
                cornerRadius = 12.dpf
                setStroke(1.dpf.toInt(), Color.argb(82, 255, 255, 255))
            }
            addView(TextView(context).apply {
                text = "更多形状"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 32.dp))
            addView(TextView(context).apply {
                text = "后续新增工具会显示在这里"
                textSize = 12f
                setTextColor(Color.parseColor("#91A0B2"))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 28.dp))
        }
        moreToolsPanel = panel
        addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        positionPopupAboveToolbar(panel)
        panel.post { positionPopupAboveToolbar(panel) }
    }

    private fun toggleCanvasPanel() {
        canvasPanel?.let { removeView(it); canvasPanel = null; return }
        colorPanel?.let { removeView(it); colorPanel = null }
        moreToolsPanel?.let { removeView(it); moreToolsPanel = null }
        val panel = LinearLayout(context).apply {
            tag = "canvas-panel"
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 12, 16, 21)); cornerRadius = 12.dpf
                setStroke(1.dpf.toInt(), Color.argb(82, 255, 255, 255))
            }
        }
        panel.addView(TextView(context).apply {
            text = "画板 / 图层"; textSize = 13f; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(220.dp, 30.dp))
        drawingSession.boards.forEach { board ->
            panel.addView(TextView(context).apply {
                tag = "board:${board.id}"
                text = if (board.id == drawingSession.currentBoard.id) "● ${board.name}" else "○ ${board.name}"
                textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    discardActiveGesture(); drawingSession.selectBoard(board.id)
                    elements = drawingSession.currentLayer.elements
                    removeView(panel); canvasPanel = null; canvasView.invalidate()
                }
            }, LinearLayout.LayoutParams(220.dp, 36.dp))
        }
        panel.addView(TextView(context).apply {
            text = "图层：${drawingSession.currentBoard.name}"; textSize = 12f
            setTextColor(Color.parseColor("#91A0B2"))
        }, LinearLayout.LayoutParams(220.dp, 28.dp))
        drawingSession.currentBoard.layers.forEach { layer ->
            panel.addView(TextView(context).apply {
                tag = "layer:${layer.id}"
                text = if (layer.id == drawingSession.currentLayer.id) "● ${layer.name}" else "○ ${layer.name}"
                textSize = 13f
                setTextColor(if (layer.visible) Color.WHITE else Color.parseColor("#718096"))
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    discardActiveGesture(); drawingSession.selectLayer(layer.id)
                    elements = drawingSession.currentLayer.elements
                    rebuildCanvasPanel(); canvasView.invalidate()
                }
                setOnLongClickListener {
                    drawingSession.moveLayer(layer.id, 0)
                    rebuildCanvasPanel()
                    true
                }
            }, LinearLayout.LayoutParams(220.dp, 36.dp))
        }
        panel.addView(canvasActionRow("board", "新建画板") { drawingSession.createBoard(); rebuildCanvasPanel() })
        panel.addView(canvasActionRow("layer", "新建图层") { drawingSession.createLayer(); elements = drawingSession.currentLayer.elements; rebuildCanvasPanel() })
        panel.addView(canvasActionRow("rename-board", "重命名画板") { renameCanvasTarget(false) })
        panel.addView(canvasActionRow("rename-layer", "重命名图层") { renameCanvasTarget(true) })
        panel.addView(canvasActionRow("delete-board", "删除当前画板") { confirmCanvasDelete(false) })
        panel.addView(canvasActionRow("delete-layer", "删除当前图层") { confirmCanvasDelete(true) })
        canvasPanel = panel
        addView(panel)
        positionPopupAboveToolbar(panel)
        panel.post { positionPopupAboveToolbar(panel) }
    }

    private fun canvasActionRow(tagValue: String, label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            tag = "canvas-action:$tagValue"
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6.dp, 0, 6.dp, 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(220.dp, 34.dp)
        }

    private fun rebuildCanvasPanel() {
        canvasPanel?.let { removeView(it) }
        canvasPanel = null
        toggleCanvasPanel()
    }

    private fun renameCanvasTarget(layer: Boolean) {
        val target = if (layer) drawingSession.currentLayer.name else drawingSession.currentBoard.name
        val input = EditText(context).apply { setText(target); selectAll() }
        AlertDialog.Builder(context)
            .setTitle(if (layer) "重命名图层" else "重命名画板")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                if (layer) drawingSession.renameLayer(drawingSession.currentLayer.id, input.text.toString())
                else drawingSession.renameBoard(drawingSession.currentBoard.id, input.text.toString())
                rebuildCanvasPanel()
            }
            .show()
    }

    private fun confirmCanvasDelete(layer: Boolean) {
        val name = if (layer) drawingSession.currentLayer.name else drawingSession.currentBoard.name
        AlertDialog.Builder(context)
            .setTitle(if (layer) "删除图层" else "删除画板")
            .setMessage("确定删除“$name”吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (layer) drawingSession.deleteLayer(drawingSession.currentLayer.id)
                else drawingSession.deleteBoard(drawingSession.currentBoard.id)
                elements = drawingSession.currentLayer.elements
                rebuildCanvasPanel()
                canvasView.invalidate()
            }
            .show()
    }

    private fun toggleColorPanel() {
        if (colorPanel != null) {
            removeView(colorPanel)
            colorPanel = null
            return
        }
        moreToolsPanel?.let {
            removeView(it)
            moreToolsPanel = null
        }
        val panel = buildColorPanel()
        colorPanel = panel
        addView(panel)
        positionPopupAboveToolbar(panel)
        panel.post { positionPopupAboveToolbar(panel) }
    }

    private fun buildColorPanel(): View {
        val panel = LinearLayout(context).apply {
            tag = "color-panel"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(236, 12, 16, 21)); cornerRadius = 12.dpf
                setStroke(1.dpf.toInt(), Color.argb(82, 255, 255, 255))
            }
        }
        PenSettings.allColors(context).chunked(4).forEachIndexed { rowIndex, colors ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (rowIndex > 0) layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6.dp }
            }
            colors.forEachIndexed { index, color ->
                    row.addView(colorSwatch(color, "palette-color:${rowIndex * 4 + index}", !PenSettings.isDefaultColor(color)))
            }
            panel.addView(row)
        }
        val recentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
        }
        PenSettings.load(context).recentColors.take(3).forEachIndexed { index, color ->
            recentRow.addView(colorSwatch(color, "recent-color:$index"))
        }
        recentRow.addView(TextView(context).apply {
            tag = "custom-color"; text = "+"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.argb(35, 255, 255, 255)); cornerRadius = 6.dpf }
            layoutParams = LinearLayout.LayoutParams(34.dp, 34.dp).apply { marginStart = 6.dp }
            setOnClickListener { showHsvControls(panel) }
        })
        panel.addView(recentRow)
        return panel
    }

    private fun colorSwatch(color: Int, viewTag: String, deletable: Boolean = false) = FrameLayout(context).apply {
        tag = viewTag
        contentDescription = if (deletable) "自定义颜色，长按删除" else "默认颜色"
        layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginEnd = 2.dp }
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
                if (color == currentColor) setStroke(2.dpf.toInt(), Color.WHITE)
            }
        }, FrameLayout.LayoutParams(30.dp, 30.dp, Gravity.CENTER))
        setOnClickListener { applyColor(color) }
        setOnLongClickListener {
            if (deletable) {
                PenSettings.deleteCustomColor(context, color)
                if (currentColor == color) applyColor(PenSettings.DEFAULT_PALETTE.first())
                if (colorPanel != null) toggleColorPanel()
            }
            true
        }
    }

    private fun showHsvControls(panel: LinearLayout) {
        if (panel.findViewWithTag<View>("hsv-controls") != null) return
        val availableWidth = ((width.takeIf { it > 0 } ?: (windowWidthDp * density).toInt()) - 16.dp).coerceAtLeast(1)
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = COLOR_PANEL_EDITOR_WIDTH_DP.dp.coerceAtMost(availableWidth)
            panel.layoutParams = params
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(currentColor, hsv)
        val controls = LinearLayout(context).apply {
            tag = "hsv-controls"; orientation = LinearLayout.VERTICAL
            val available = ((width.takeIf { it > 0 } ?: (windowWidthDp * density).toInt()) - 32.dp).coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(minOf(260.dp, available), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8.dp }
        }
        val preview = View(context).apply {
            tag = "hsv-preview"; background = GradientDrawable().apply { setColor(currentColor); cornerRadius = 4.dpf }
        }
        controls.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20.dp))
        fun slider(label: String, maxValue: Int, progressValue: Int, onChange: (Int) -> Unit) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply { text = label; textSize = 12f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(36.dp, 36.dp))
            row.addView(SeekBar(context).apply {
                max = maxValue; progress = progressValue
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                        onChange(value)
                        preview.background = GradientDrawable().apply { setColor(Color.HSVToColor(hsv)); cornerRadius = 4.dpf }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }, LinearLayout.LayoutParams(0, 36.dp, 1f))
            controls.addView(row)
        }
        slider("色相", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        slider("饱和", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        slider("明度", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }
        val rgbInput = EditText(context).apply {
            tag = "custom-rgb-input"
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "RGB：255,128,0 或 #FF8000"
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        controls.addView(rgbInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40.dp).apply { topMargin = 6.dp })
        controls.addView(TextView(context).apply {
            tag = "apply-rgb-color"; text = "应用 RGB 数字"; gravity = Gravity.CENTER; textSize = 13f
            setTextColor(Color.BLACK); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 6.dpf }
            setOnClickListener { PenSettings.parseRgb(rgbInput.text.toString())?.let(::applyColor) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 36.dp).apply { topMargin = 4.dp })
        controls.addView(TextView(context).apply {
            tag = "apply-custom-color"; text = "应用颜色"; gravity = Gravity.CENTER; textSize = 13f
            setTextColor(Color.BLACK); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 6.dpf }
            setOnClickListener { applyColor(Color.HSVToColor(hsv)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 36.dp).apply { topMargin = 6.dp })
        panel.addView(controls)
        positionPopupAboveToolbar(panel)
    }

    private fun applyColor(color: Int) {
        currentColor = color
        drawPaint.color = color
        toolStyles[currentToolId] = ToolStyle(color, drawPaint.strokeWidth / density)
        PenSettings.saveToolStyle(context, currentToolId, color, drawPaint.strokeWidth / density)
        if (!PenSettings.isDefaultColor(color)) PenSettings.addCustomColor(context, color)
        PenSettings.addRecentColor(context, color)
        onSelectionChanged(currentToolId, color)
        refreshColorControl()
        refreshToolIndicators()
        colorPanel?.let(::removeView)
        colorPanel = null
    }

    private fun refreshColorControl() {
        val toolbar = findViewWithTag<LinearLayout>("monochrome-toolbar") ?: return
        val colorControl = toolbar.findViewWithTag<LinearLayout>("color") ?: return
        colorControl.getChildAt(0).background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(currentColor); setStroke(2.dpf.toInt(), Color.parseColor("#55FFFFFF"))
        }
        (colorControl.getChildAt(1) as? TextView)?.text = if (compactLayout) "" else colorLabel(currentColor)
    }

    private fun colorLabel(color: Int): String {
        val index = DrawingElement.colorValues.indexOf(color)
        return if (index >= 0) DrawingElement.colorNames[index] else "自定义"
    }

    private inner class ToolIconView(context: Context, private val icon: String) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f.dpf
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val arcBounds = RectF()

        fun setIconColor(color: Int) {
            paint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) * 0.24f
            when (icon) {
                "drag" -> {
                    val oldStyle = paint.style
                    paint.style = Paint.Style.FILL
                    for (row in -1..1) {
                        for (column in -1..1) {
                            canvas.drawCircle(cx + column * r * 0.75f, cy + row * r * 0.75f, r * 0.14f, paint)
                        }
                    }
                    paint.style = oldStyle
                }
                "pen" -> {
                    canvas.save()
                    canvas.rotate(-45f, cx, cy)
                    canvas.drawRoundRect(cx - r * 0.28f, cy - r, cx + r * 0.28f, cy + r * 0.62f, r * 0.18f, r * 0.18f, paint)
                    canvas.drawLine(cx - r * 0.28f, cy + r * 0.62f, cx, cy + r, paint)
                    canvas.drawLine(cx + r * 0.28f, cy + r * 0.62f, cx, cy + r, paint)
                    canvas.restore()
                }
                "line" -> canvas.drawLine(cx - r, cy + r, cx + r, cy - r, paint)
                "arrow" -> {
                    canvas.drawLine(cx - r, cy + r, cx + r, cy - r, paint)
                    canvas.drawLine(cx + r, cy - r, cx + r * 0.2f, cy - r, paint)
                    canvas.drawLine(cx + r, cy - r, cx + r, cy - r * 0.2f, paint)
                }
                "rect" -> canvas.drawRoundRect(cx - r, cy - r * 0.75f, cx + r, cy + r * 0.75f, r * 0.16f, r * 0.16f, paint)
                "more" -> {
                    val oldStyle = paint.style
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx - r * 0.7f, cy, r * 0.18f, paint)
                    canvas.drawCircle(cx, cy, r * 0.18f, paint)
                    canvas.drawCircle(cx + r * 0.7f, cy, r * 0.18f, paint)
                    paint.style = oldStyle
                }
                "undo" -> {
                    arcBounds.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawArc(arcBounds, 35f, -250f, false, paint)
                    canvas.drawLine(cx - r, cy, cx - r * 0.35f, cy - r * 0.55f, paint)
                    canvas.drawLine(cx - r, cy, cx - r * 0.2f, cy + r * 0.1f, paint)
                }
                "clear" -> {
                    canvas.drawRoundRect(cx - r * 0.62f, cy - r * 0.42f, cx + r * 0.62f, cy + r, r * 0.12f, r * 0.12f, paint)
                    canvas.drawLine(cx - r * 0.85f, cy - r * 0.62f, cx + r * 0.85f, cy - r * 0.62f, paint)
                    canvas.drawLine(cx - r * 0.3f, cy - r * 0.88f, cx + r * 0.3f, cy - r * 0.88f, paint)
                }
                "exit" -> {
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, paint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = true

    private val Float.dpf: Float get() = this * density
    private val Int.dpf: Float get() = this * density
    private val Int.dp: Int get() = (this * density).toInt()

    companion object {
        private const val COLOR_PANEL_COMPACT_WIDTH_DP = 220
        private const val COLOR_PANEL_EDITOR_WIDTH_DP = 280

        val PALETTE_COLORS = listOf(
            0xFFF44336.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFF212121.toInt(),
            0xFFFFC107.toInt(), 0xFFFFFFFF.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
        )

        fun resolveArrowHeadLengthDp(strokeWidthDp: Float, arrowScale: Float): Float =
            ArrowGeometry.headLengthDp(strokeWidthDp, arrowScale)

        private fun normalizeLegacyColor(value: Int): Int =
            if (value in DrawingElement.colorValues.indices) DrawingElement.colorValues[value] else value
    }
}
