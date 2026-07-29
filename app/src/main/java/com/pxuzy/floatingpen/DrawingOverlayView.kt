package com.pxuzy.floatingpen

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.text.InputType
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private val onSessionChanged: () -> Unit = {},
    private val onSelectionChanged: (toolId: String, color: Int) -> Unit = { _, _ -> },
    private val onTextInputModeChanged: (Boolean) -> Unit = {},
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
        onSessionChanged: () -> Unit = {},
        onSelectionChanged: (toolId: String, color: Int) -> Unit = { _, _ -> },
        onTextInputModeChanged: (Boolean) -> Unit = {},
        onExit: () -> Unit,
    ) : this(
        context = context,
        toolId = PenSettings.normalizeTool(toolId),
        initialColor = styles[PenSettings.normalizeTool(toolId)]?.color ?: PenSettings.DEFAULT_COLOR_ARGB,
        strokeWidthDp = styles[PenSettings.normalizeTool(toolId)]?.widthDp ?: PenSettings.DEFAULT_WIDTH_DP,
        arrowScale = arrowScale,
        toolbarToolIds = toolbarToolIds,
        onSelectionChanged = onSelectionChanged,
        onTextInputModeChanged = onTextInputModeChanged,
        onExit = onExit,
        drawingSession = drawingSession,
        onSessionChanged = onSessionChanged,
    ) {
        toolStyles.putAll(styles.mapKeys { PenSettings.normalizeTool(it.key) })
        applyCurrentToolStyle()
    }

    private val density = resources.displayMetrics.density
    private val sampleDistanceSquared = (1.5f * density) * (1.5f * density)
    private var elements: MutableList<DrawingElement> = drawingSession.currentLayer.elements
    private var sx = 0f; private var sy = 0f; private var cx = 0f; private var cy = 0f
    private var isDrawing = false
    private var sessionDirty = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeToolType = MotionEvent.TOOL_TYPE_UNKNOWN
    private var currentToolId = toolId
    private var configuredToolIds = normalizeToolbarToolIds(toolbarToolIds)
    private var currentColor = normalizeLegacyColor(initialColor)
    private lateinit var canvasView: View
    private lateinit var toolbarPopupHost: FrameLayout
    private var colorPanel: View? = null
    private var moreToolsPanel: View? = null
    private var canvasPanel: View? = null
    private var colorManageMode = false
    private var restoreClearBar: View? = null
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
            finishTextInputMode()
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
    private val restoreClearTimeout = Handler(Looper.getMainLooper())

    init {
        drawPaint.color = currentColor
        setBackgroundColor(Color.argb(10, 0, 0, 0))

        canvasView = object : View(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.x; val y = event.y
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!drawingSession.currentLayer.visible) {
                            drawingSession.setLayerVisible(drawingSession.currentLayer.id, true)
                            sessionDirty = true
                        }
                        dismissRestoreClearBar()
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
                            if (e is CoreDrawingElement.Stroke) {
                                val last = e.points.lastOrNull()
                                val dx = last?.first?.minus(cx) ?: Float.MAX_VALUE
                                val dy = last?.second?.minus(cy) ?: Float.MAX_VALUE
                                if (dx * dx + dy * dy >= sampleDistanceSquared) {
                                    e.points.add(Pair(cx, cy))
                                }
                            }
                        }
                        postInvalidateOnAnimation(); return true
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
                            sessionDirty = true
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
                                "circle" -> circleElement(sx, sy, x, y, selectedColor, drawPaint.strokeWidth)
                                else -> null
                            }
                            if (el != null && (x != sx || y != sy)) elements.add(el)
                        }
                        isDrawing = false
                        sessionDirty = true
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        invalidate(); return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (currentToolId == "pen" && isDrawing && elements.lastOrNull() is CoreDrawingElement.Stroke) {
                            elements.removeAt(elements.lastIndex)
                        }
                        isDrawing = false
                        sessionDirty = true
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        invalidate(); return true
                    }
                }
                return super.onTouchEvent(event)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (sessionDirty) {
                    sessionDirty = false
                    onSessionChanged()
                }
                val visibleLayers = drawingSession.visibleLayersBottomToTop()
                if (visibleLayers.all { it.elements.isEmpty() } && !isDrawing)
                    canvas.drawText("手指滑动开始画线", width / 2f, height / 2f - 120.dpf, hintPaint)
                visibleLayers.forEach { layer ->
                    layer.elements.forEach { drawElement(canvas, it) }
                }
                if (isDrawing && currentToolId != "pen") {
                    val pe = when (currentToolId) {
                        "line" -> CoreDrawingElement.Line(Pair(sx, sy), Pair(cx, cy), selectedColor, drawPaint.strokeWidth)
                        "arrow" -> CoreDrawingElement.Arrow(
                            Pair(sx, sy), Pair(cx, cy), selectedColor, drawPaint.strokeWidth,
                            resolveArrowHeadLengthDp(drawPaint.strokeWidth / density, arrowScale)
                        )
                        "rect" -> CoreDrawingElement.Rect(Pair(sx, sy), Pair(cx, cy), selectedColor, drawPaint.strokeWidth)
                        "circle" -> circleElement(sx, sy, cx, cy, selectedColor, drawPaint.strokeWidth)
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
        toolbarPopupHost = FrameLayout(context).apply { tag = "toolbar-popup-host" }
        toolbarPopupHost.addView(buildToolbar())
        addView(toolbarPopupHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val selectedColor: Int
        get() = currentColor

    private fun circleElement(
        centerX: Float,
        centerY: Float,
        endX: Float,
        endY: Float,
        color: Int,
        width: Float,
    ): CoreDrawingElement.Circle = CoreDrawingElement.Circle(
        center = centerX to centerY,
        radius = max(abs(endX - centerX), abs(endY - centerY)),
        color = color,
        width = width,
    )

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
            is CoreDrawingElement.Circle -> canvas.drawCircle(el.center.first, el.center.second, el.radius, drawPaint)
        }
    }

    override fun onDetachedFromWindow() {
        dismissRestoreClearBar()
        super.onDetachedFromWindow()
    }

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
                setColor(Color.argb(236, 12, 16, 21))
                cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
                setStroke(1.dpf.toInt(), Color.argb(88, 255, 255, 255))
            }
            elevation = 8f
        }

        val dragHandle = ToolIconView(context, "drag").apply {
            tag = "toolbar-drag-handle"
            contentDescription = "拖动工具栏"
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
        }
        bar.addView(dragHandle)
        bar.addView(createColorDot())

        val toolContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            configuredToolIds.take(4).forEach { toolId -> addView(createToolIcon(toolId)) }
            if (configuredToolIds.size > 4) {
                addView(createActionBtn("more", ::toggleMoreTools).apply { tag = "more-tools" })
            }
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
                drawingSession.discardRecoverableClear()
                dismissRestoreClearBar()
                onSessionChanged()
                canvasView.invalidate()
            }
        }.apply { tag = "undo" })
        bar.addView(createActionBtn("canvas", ::toggleCanvasPanel).apply {
            tag = "canvas-selector"
            contentDescription = "选择画板和图层"
        })
        bar.addView(createActionBtn("exit", action = {
            finishTextInputMode()
            onExit()
        }).apply { tag = "exit" })

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
        val resolvedWidth = (drawPaint.strokeWidth / density).toInt()
        val wrapper = LinearLayout(context).apply {
            tag = "color"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "当前颜色 ${colorLabel(currentColor)}，线宽 ${resolvedWidth}dp"
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginEnd = 4.dp }
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
            text = if (compactLayout) "" else "${colorLabel(currentColor)} · ${resolvedWidth}dp"
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
        colorPanel?.let { toolbarPopupHost.removeView(it); colorPanel = null }
        moreToolsPanel?.let { toolbarPopupHost.removeView(it); moreToolsPanel = null }
        canvasPanel?.let { toolbarPopupHost.removeView(it); canvasPanel = null }
        toolbarPopupHost.findViewWithTag<View>("monochrome-toolbar")?.let(toolbarPopupHost::removeView)
        toolbarPopupHost.addView(buildToolbar())
        when {
            restoreColorPanel -> {
                val panel = buildColorPanel() as LinearLayout
                colorPanel = panel
                toolbarPopupHost.addView(panel)
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

    private fun actionButtonSize(): Int = 48.dp

    private fun positionPopupAboveToolbar(popup: View) {
        val toolbar = toolbarPopupHost.findViewWithTag<View>("monochrome-toolbar") ?: return
        val navigationInset = if (Build.VERSION.SDK_INT >= 30) {
            rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
        } else {
            0
        }
        val fallbackWidth = (windowWidthDp * density).toInt()
        val hostWidth = toolbarPopupHost.width.takeIf { it > 0 } ?: width.takeIf { it > 0 } ?: fallbackWidth
        val hostHeight = toolbarPopupHost.height.takeIf { it > 0 } ?: height
        val gap = 8.dp
        val availableWidth = (hostWidth - 16.dp).coerceAtLeast(1)
        val availableHeight = (hostHeight - gap * 2 - navigationInset).coerceAtLeast(1)
        val params = (popup.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)

        // Measure against the actual host before calculating the anchor point.
        // Estimating a fixed height here is what allowed the action area to be
        // positioned outside the visible overlay on small/rotated windows.
        val widthSpec = if (params.width > 0) {
            View.MeasureSpec.makeMeasureSpec(params.width.coerceAtMost(availableWidth), View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST)
        }
        popup.measure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST),
        )
        val measuredWidth = popup.measuredWidth.coerceIn(1, availableWidth)
        val measuredHeight = popup.measuredHeight.coerceIn(1, availableHeight)
        params.width = measuredWidth
        params.height = measuredHeight

        val toolbarTop = toolbar.top
        val toolbarBottom = toolbar.bottom.takeIf { it > toolbarTop }
            ?: (toolbarTop + (toolbar.measuredHeight.takeIf { it > 0 } ?: 56.dp))
        val aboveTop = toolbarTop - measuredHeight - gap
        val belowTop = toolbarBottom + gap
        val maxTop = (hostHeight - measuredHeight - gap - navigationInset).coerceAtLeast(gap)
        val placeAbove = toolbarTop >= gap && aboveTop >= gap
        val placeBelow = belowTop <= maxTop
        when {
            placeAbove -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = aboveTop.coerceIn(gap, maxTop)
                params.bottomMargin = 0
            }
            placeBelow -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = belowTop.coerceIn(gap, maxTop)
                params.bottomMargin = 0
            }
            else -> {
                // If neither side has enough room, keep the panel inside the
                // safe rectangle and let its internal ScrollView handle it.
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = aboveTop.coerceIn(gap, maxTop)
                params.bottomMargin = 0
            }
        }
        popup.layoutParams = params
    }

    private fun toggleMoreTools() {
        finishTextInputMode()
        moreToolsPanel?.let {
            toolbarPopupHost.removeView(it)
            moreToolsPanel = null
            return
        }
        colorPanel?.let {
            toolbarPopupHost.removeView(it)
            colorPanel = null
        }
        canvasPanel?.let { toolbarPopupHost.removeView(it); canvasPanel = null }
        val panel = LinearLayout(context).apply {
            tag = "more-tools-panel"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18.dp, 14.dp, 18.dp, 14.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 12, 16, 21))
                cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
                setStroke(1.dpf.toInt(), Color.argb(82, 255, 255, 255))
            }
            val overflowToolIds = configuredToolIds.drop(4)
            addView(TextView(context).apply {
                text = "更多形状"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 32.dp))
            if (overflowToolIds.isEmpty()) {
                addView(TextView(context).apply {
                    text = "暂无更多工具"
                    textSize = 12f
                    setTextColor(Color.parseColor("#91A0B2"))
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 40.dp))
            } else {
                val toolRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                overflowToolIds.forEach { toolId ->
                    toolRow.addView(createToolIcon(toolId).apply {
                        setOnClickListener {
                            selectTool(toolId)
                            moreToolsPanel?.let(toolbarPopupHost::removeView)
                            moreToolsPanel = null
                        }
                    })
                }
                addView(toolRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 48.dp))
            }
        }
        moreToolsPanel = panel
        toolbarPopupHost.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        positionPopupAboveToolbar(panel)
        panel.post { positionPopupAboveToolbar(panel) }
    }

    private fun toggleCanvasPanel() {
        finishTextInputMode()
        canvasPanel?.let { toolbarPopupHost.removeView(it); canvasPanel = null; return }
        colorPanel?.let { toolbarPopupHost.removeView(it); colorPanel = null }
        moreToolsPanel?.let { toolbarPopupHost.removeView(it); moreToolsPanel = null }
        val availablePanelWidth = (((windowWidthDp.takeIf { it > 0 } ?: 360) * density).toInt() - 16.dp).coerceAtLeast(1)
        val panelWidth = minOf(360.dp, availablePanelWidth)
        val panel = LinearLayout(context).apply {
            tag = "canvas-panel"
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 12, 16, 21)); cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
                setStroke(1.dpf.toInt(), Color.argb(82, 255, 255, 255))
            }
            layoutParams = FrameLayout.LayoutParams(panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        val boardSection = LinearLayout(context).apply {
            tag = "canvas-board-section"
            orientation = LinearLayout.VERTICAL
        }
        boardSection.addView(canvasSectionHeader("画板", "canvas-add-board") {
            drawingSession.createBoard()
            elements = drawingSession.currentLayer.elements
            onSessionChanged()
            rebuildCanvasPanel()
        })
        val boardListContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        drawingSession.boards.forEach { board ->
            val boardRow = LinearLayout(context).apply {
                tag = if (board.id == drawingSession.currentBoard.id) "board-selected:${board.id}" else "board:${board.id}"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6.dp, 0, 2.dp, 0)
                background = canvasRowBackground(board.id == drawingSession.currentBoard.id)
                addView(FloatInkIconView(context, "canvas").apply {
                    layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 8.dp }
                })
                addView(TextView(context).apply {
                    text = board.name
                    textSize = 13f
                    setTextColor(if (board.id == drawingSession.currentBoard.id) Color.WHITE else Color.parseColor("#D2D8E0"))
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, 40.dp, 1f)
                })
                addView(canvasOverflowButton("canvas-menu-board:${board.id}") {
                    showCanvasTargetMenu(false, board.id)
                })
                setOnClickListener {
                    discardActiveGesture(); drawingSession.selectBoard(board.id)
                    dismissRestoreClearBar(discardSnapshot = false)
                    elements = drawingSession.currentLayer.elements
                    onSessionChanged()
                    toolbarPopupHost.removeView(panel); canvasPanel = null; canvasView.invalidate()
                }
            }
            boardListContent.addView(boardRow)
        }
        val layerSection = LinearLayout(context).apply {
            tag = "canvas-layer-section"
            orientation = LinearLayout.VERTICAL
        }
        layerSection.addView(canvasSectionHeader("图层 · ${drawingSession.currentBoard.name}", "canvas-add-layer") {
            drawingSession.createLayer()
            elements = drawingSession.currentLayer.elements
            onSessionChanged()
            rebuildCanvasPanel()
        })
        val layerListContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        drawingSession.currentBoard.layers.forEach { layer ->
            val layerRow = LinearLayout(context).apply {
                tag = "layer:${layer.id}"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6.dp, 0, 2.dp, 0)
                val markerColor = layer.elements.lastOrNull()?.drawColor ?: currentColor
                addView(View(context).apply {
                    tag = "layer-color:${layer.id}"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(markerColor)
                        setStroke(1.dpf.toInt(), Color.argb(150, 255, 255, 255))
                    }
                }, LinearLayout.LayoutParams(18.dp, 18.dp).apply { marginEnd = 8.dp })
                background = canvasRowBackground(layer.id == drawingSession.currentLayer.id)
                addView(FloatInkIconView(context, "layer").apply {
                    layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 6.dp }
                })
                addView(TextView(context).apply {
                    text = layer.name
                    textSize = 13f
                    setTextColor(
                        when {
                            layer.id == drawingSession.currentLayer.id -> Color.WHITE
                            layer.visible -> Color.parseColor("#D2D8E0")
                            else -> Color.parseColor("#718096")
                        },
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, 40.dp, 1f)
                })
                addView(FloatInkIconView(context, if (layer.visible) "eye" else "eye-off").apply {
                    tag = "layer-visibility:${layer.id}"
                    contentDescription = if (layer.visible) "图层可见" else "图层隐藏"
                    layoutParams = LinearLayout.LayoutParams(36.dp, 40.dp)
                    setIconColor(Color.parseColor("#B7C0CC"))
                    setOnClickListener {
                        drawingSession.setLayerVisible(layer.id, !layer.visible)
                        onSessionChanged()
                        rebuildCanvasPanel(); canvasView.invalidate()
                    }
                })
                addView(canvasOverflowButton("canvas-menu-layer:${layer.id}") {
                    showCanvasTargetMenu(true, layer.id)
                })
                setOnClickListener {
                    discardActiveGesture(); drawingSession.selectLayer(layer.id)
                    dismissRestoreClearBar(discardSnapshot = false)
                    elements = drawingSession.currentLayer.elements
                    onSessionChanged()
                    rebuildCanvasPanel(); canvasView.invalidate()
                }
                setOnLongClickListener {
                    drawingSession.moveLayer(layer.id, 0)
                    onSessionChanged()
                    rebuildCanvasPanel()
                    canvasView.invalidate()
                    true
                }
            }
            layerListContent.addView(layerRow)
        }
        val maxPanelHeight = ((resources.configuration.screenHeightDp.takeIf { it > 0 } ?: 640) * 0.55f).toInt().dp
        val sectionListHeight = ((maxPanelHeight - 88.dp) / 2).coerceIn(72.dp, 180.dp)
        boardSection.addView(ScrollView(context).apply {
            tag = "canvas-board-scroll"
            isVerticalScrollBarEnabled = true
            addView(boardListContent, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sectionListHeight))
        layerSection.addView(ScrollView(context).apply {
            tag = "canvas-layer-scroll"
            isVerticalScrollBarEnabled = true
            addView(layerListContent, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sectionListHeight))
        panel.addView(boardSection, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(layerSection, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        canvasPanel = panel
        toolbarPopupHost.addView(panel)
        positionPopupAboveToolbar(panel)
        panel.post { positionPopupAboveToolbar(panel) }
    }

    private fun canvasSectionHeader(title: String, addTag: String, onAdd: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6.dp, 0, 0, 0)
            addView(TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(Color.parseColor("#91A0B2"))
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, 36.dp, 1f)
            })
            addView(FloatInkIconView(context, "add").apply {
                tag = addTag
                contentDescription = if (addTag == "canvas-add-board") "新建画板" else "新建图层"
                background = GradientDrawable().apply {
                    setColor(Color.argb(42, 255, 255, 255))
                    cornerRadius = 6.dpf
                }
                layoutParams = LinearLayout.LayoutParams(36.dp, 32.dp).apply { marginEnd = 2.dp }
                setOnClickListener { onAdd() }
            })
        }

    private fun canvasOverflowButton(buttonTag: String, onClick: () -> Unit): View =
        FloatInkIconView(context, "more").apply {
            tag = buttonTag
            contentDescription = "更多操作"
            setIconColor(Color.parseColor("#D2D8E0"))
            layoutParams = LinearLayout.LayoutParams(36.dp, 40.dp)
            setOnClickListener { onClick() }
        }

    private fun canvasRowBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) Color.argb(40, 255, 255, 255) else Color.TRANSPARENT)
        cornerRadius = 8.dpf
        if (selected) setStroke(1.dpf.toInt(), Color.argb(92, 255, 255, 255))
    }

    private fun rebuildCanvasPanel() {
        canvasPanel?.let { toolbarPopupHost.removeView(it) }
        canvasPanel = null
        toggleCanvasPanel()
    }

    private fun showCanvasTargetMenu(layer: Boolean, targetId: String) {
        val title = if (layer) "图层操作" else "画板操作"
        val targetName = if (layer) {
            drawingSession.currentBoard.layers.firstOrNull { it.id == targetId }?.name
        } else {
            drawingSession.boards.firstOrNull { it.id == targetId }?.name
        } ?: return
        val items = if (layer) {
            arrayOf(
                if (drawingSession.currentBoard.layers.first { it.id == targetId }.visible) "隐藏图层" else "显示图层",
                "重命名",
                "清空当前图层",
                "删除图层",
            )
        } else {
            arrayOf("重命名", "删除画板")
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(targetName)
            .setItems(items) { _, which ->
                if (layer) {
                    when (which) {
                        0 -> {
                            val current = drawingSession.currentBoard.layers.first { it.id == targetId }
                            drawingSession.setLayerVisible(targetId, !current.visible)
                            onSessionChanged()
                            rebuildCanvasPanel()
                            canvasView.invalidate()
                        }
                        1 -> renameCanvasTarget(true, targetId)
                        2 -> clearLayerRecoverably(targetId)
                        3 -> confirmCanvasDelete(true, targetId)
                    }
                } else {
                    when (which) {
                        0 -> renameCanvasTarget(false, targetId)
                        1 -> confirmCanvasDelete(false, targetId)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun clearLayerRecoverably(targetId: String) {
        if (targetId != drawingSession.currentLayer.id) {
            drawingSession.selectLayer(targetId)
            dismissRestoreClearBar(discardSnapshot = false)
            elements = drawingSession.currentLayer.elements
        }
        discardActiveGesture()
        if (!drawingSession.clearCurrentLayerRecoverably()) return
        onSessionChanged()
        canvasPanel?.let { toolbarPopupHost.removeView(it); canvasPanel = null }
        canvasView.invalidate()
        showRestoreClearBar()
    }

    private fun showRestoreClearBar() {
        dismissRestoreClearBar(discardSnapshot = false)
        val bar = LinearLayout(context).apply {
            tag = "restore-clear-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp, 0, 8.dp, 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(242, 29, 38, 48))
                cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
                setStroke(1.dpf.toInt(), Color.argb(120, 140, 180, 220))
            }
            addView(TextView(context).apply {
                text = "已清空当前图层"
                textSize = 13f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, 48.dp, 1f))
            addView(TextView(context).apply {
                tag = "restore-clear-action"
                text = "恢复"
                textSize = 13f
                gravity = Gravity.CENTER
                contentDescription = "恢复已清空的当前图层"
                setTextColor(Color.parseColor("#9FD2FF"))
                setOnClickListener {
                    if (drawingSession.restoreClearedCurrentLayer()) {
                        onSessionChanged()
                        canvasView.invalidate()
                    }
                    dismissRestoreClearBar(discardSnapshot = false)
                }
            }, LinearLayout.LayoutParams(56.dp, 48.dp))
        }
        restoreClearBar = bar
        toolbarPopupHost.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 48.dp))
        positionPopupAboveToolbar(bar)
        restoreClearTimeout.removeCallbacksAndMessages(null)
        restoreClearTimeout.postDelayed({ dismissRestoreClearBar() }, RESTORE_CLEAR_TIMEOUT_MS)
    }

    private fun dismissRestoreClearBar(discardSnapshot: Boolean = true) {
        if (discardSnapshot) drawingSession.discardRecoverableClear()
        restoreClearTimeout.removeCallbacksAndMessages(null)
        restoreClearBar?.let(toolbarPopupHost::removeView)
        restoreClearBar = null
    }

    private fun renameCanvasTarget(layer: Boolean, targetId: String) {
        val target = if (layer) {
            drawingSession.currentBoard.layers.firstOrNull { it.id == targetId }?.name
        } else {
            drawingSession.boards.firstOrNull { it.id == targetId }?.name
        } ?: return
        val input = EditText(context).apply { setText(target); selectAll() }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (layer) "重命名图层" else "重命名画板")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                if (layer) drawingSession.renameLayer(targetId, input.text.toString())
                else drawingSession.renameBoard(targetId, input.text.toString())
                onSessionChanged()
                rebuildCanvasPanel()
            }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun confirmCanvasDelete(layer: Boolean, targetId: String) {
        val name = if (layer) {
            drawingSession.currentBoard.layers.firstOrNull { it.id == targetId }?.name
        } else {
            drawingSession.boards.firstOrNull { it.id == targetId }?.name
        } ?: return
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (layer) "删除图层" else "删除画板")
            .setMessage("确定删除“$name”吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (layer) drawingSession.deleteLayer(targetId)
                else drawingSession.deleteBoard(targetId)
                dismissRestoreClearBar(discardSnapshot = false)
                elements = drawingSession.currentLayer.elements
                onSessionChanged()
                rebuildCanvasPanel()
                canvasView.invalidate()
            }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun toggleColorPanel() {
        if (colorPanel != null) {
            finishTextInputMode()
            toolbarPopupHost.removeView(colorPanel)
            colorPanel = null
            return
        }
        moreToolsPanel?.let {
            toolbarPopupHost.removeView(it)
            moreToolsPanel = null
        }
        canvasPanel?.let { toolbarPopupHost.removeView(it); canvasPanel = null }
        val panel = buildColorPanel()
        colorPanel = panel
        toolbarPopupHost.addView(panel)
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
                setColor(Color.argb(236, 12, 16, 21)); cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
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
                row.addView(colorSwatch(
                    color,
                    "palette-color:${rowIndex * 4 + index}",
                    showDelete = colorManageMode && !PenSettings.isDefaultColor(color),
                ))
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
        PenSettings.load(context).recentColors.take(6).forEachIndexed { index, color ->
            recentRow.addView(colorSwatch(color, "recent-color:$index"))
        }
        recentRow.addView(TextView(context).apply {
            tag = "manage-custom-colors"
            text = if (colorManageMode) "完成" else "管理"
            textSize = 12f
            gravity = Gravity.CENTER
            contentDescription = if (colorManageMode) "完成管理颜色" else "管理自定义颜色"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.argb(35, 255, 255, 255)); cornerRadius = 6.dpf }
            layoutParams = LinearLayout.LayoutParams(52.dp, 34.dp).apply { marginStart = 6.dp }
            setOnClickListener {
                colorManageMode = !colorManageMode
                rebuildColorPanel()
            }
        })
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

    private fun rebuildColorPanel() {
        val previous = colorPanel ?: return
        toolbarPopupHost.removeView(previous)
        val replacement = buildColorPanel()
        colorPanel = replacement
        toolbarPopupHost.addView(replacement)
        positionPopupAboveToolbar(replacement)
        replacement.post { positionPopupAboveToolbar(replacement) }
    }

    private fun colorSwatch(color: Int, viewTag: String, showDelete: Boolean = false) = FrameLayout(context).apply {
        tag = viewTag
        contentDescription = when {
            showDelete -> "删除自定义颜色 #%08X".format(java.util.Locale.US, color)
            !PenSettings.isDefaultColor(color) -> "自定义颜色"
            else -> "默认颜色"
        }
        layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginEnd = 2.dp }
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
                if (color == currentColor) setStroke(2.dpf.toInt(), Color.WHITE)
            }
        }, FrameLayout.LayoutParams(30.dp, 30.dp, Gravity.CENTER))
        if (showDelete) {
            addView(TextView(context).apply {
                tag = "delete-color:$color"
                text = "×"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                contentDescription = "删除自定义颜色 #%08X".format(java.util.Locale.US, color)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#35404C"))
                    setStroke(1.dpf.toInt(), Color.parseColor("#A0AAB5"))
                }
                setOnClickListener { confirmDeleteCustomColor(color) }
            }, FrameLayout.LayoutParams(20.dp, 20.dp, Gravity.TOP or Gravity.END))
        }
        setOnClickListener { applyColor(color) }
    }

    private fun confirmDeleteCustomColor(color: Int) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("删除自定义颜色？")
            .setMessage("删除后不会影响已经画出的笔迹。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (!PenSettings.deleteCustomColorAndReplaceStyles(context, color)) return@setPositiveButton
                val wasCurrentColor = currentColor == color
                applyExternalSettings(PenSettings.load(context))
                if (wasCurrentColor) applyColor(PenSettings.DEFAULT_PALETTE.first())
                else rebuildColorPanel()
            }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
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
        val rgbInput = RgbColorInputView(context).apply {
            tag = "custom-rgb-input"
            color = currentColor
            setOnInputActivatedListener { input ->
                onTextInputModeChanged(true)
                input.post {
                    input.requestFocus()
                    if (input.hasWindowFocus()) {
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                            .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
        controls.addView(rgbInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dp })
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(TextView(context).apply {
            tag = "cancel-custom-color"; text = "取消"; gravity = Gravity.CENTER; textSize = 13f
            setTextColor(Color.WHITE); background = GradientDrawable().apply { setColor(Color.argb(35, 255, 255, 255)); cornerRadius = 6.dpf }
            setOnClickListener {
                finishTextInputMode()
                panel.removeView(controls)
                positionPopupAboveToolbar(panel)
            }
        }, LinearLayout.LayoutParams(0, 40.dp, 1f).apply { marginEnd = 6.dp })
        actions.addView(TextView(context).apply {
            tag = "save-custom-color"; text = "保存"; gravity = Gravity.CENTER; textSize = 13f
            setTextColor(Color.BLACK); background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 6.dpf }
            setOnClickListener {
                val selected = if (rgbInput.fromUserInput) rgbInput.parsedColor() else Color.HSVToColor(hsv)
                if (selected != null) {
                    finishTextInputMode()
                    applyColor(selected)
                }
            }
        }, LinearLayout.LayoutParams(0, 40.dp, 1f))
        controls.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40.dp).apply { topMargin = 8.dp })
        panel.addView(controls)
        positionPopupAboveToolbar(panel)
    }

    private fun finishTextInputMode() {
        val focused = findFocus()
        focused?.let { view ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
        onTextInputModeChanged(false)
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
        colorPanel?.let(toolbarPopupHost::removeView)
        colorPanel = null
    }

    private fun refreshColorControl() {
        val toolbar = toolbarPopupHost.findViewWithTag<LinearLayout>("monochrome-toolbar") ?: return
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
                "circle" -> canvas.drawCircle(cx, cy, r, paint)
                "more" -> {
                    val oldStyle = paint.style
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx - r * 0.7f, cy, r * 0.18f, paint)
                    canvas.drawCircle(cx, cy, r * 0.18f, paint)
                    canvas.drawCircle(cx + r * 0.7f, cy, r * 0.18f, paint)
                    paint.style = oldStyle
                }
                "canvas" -> {
                    canvas.drawRect(cx - r, cy - r * 0.7f, cx + r, cy + r * 0.7f, paint)
                    canvas.drawLine(cx - r * 0.65f, cy - r * 0.25f, cx + r * 0.65f, cy - r * 0.25f, paint)
                    canvas.drawLine(cx - r * 0.65f, cy + r * 0.25f, cx + r * 0.65f, cy + r * 0.25f, paint)
                }
                "layer" -> {
                    canvas.drawRoundRect(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.05f, r * 0.12f, r * 0.12f, paint)
                    canvas.drawRoundRect(cx - r * 0.75f, cy - r * 0.05f, cx + r * 0.75f, cy + r * 0.5f, r * 0.12f, r * 0.12f, paint)
                    canvas.drawRoundRect(cx - r * 0.5f, cy + r * 0.45f, cx + r * 0.5f, cy + r, r * 0.12f, r * 0.12f, paint)
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
        private const val RESTORE_CLEAR_TIMEOUT_MS = 6_000L

        val PALETTE_COLORS = PenSettings.DEFAULT_PALETTE

        fun resolveArrowHeadLengthDp(strokeWidthDp: Float, arrowScale: Float): Float =
            ArrowGeometry.headLengthDp(strokeWidthDp, arrowScale)

        private fun normalizeLegacyColor(value: Int): Int =
            if (value in DrawingElement.colorValues.indices) DrawingElement.colorValues[value] else value
    }
}
