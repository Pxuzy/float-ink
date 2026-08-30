package com.pxuzy.floatingpen

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Launcher editor for the order and visibility of the currently implemented tools. */
class ToolbarLayoutEditorView(
    context: Context,
    initialOrder: List<String>,
    initialEnabled: Set<String>,
    private val onChanged: (order: List<String>, enabled: Set<String>) -> Unit,
) : LinearLayout(context) {
    private val order = initialOrder.filter { it in PenSettings.TOOL_IDS }.toMutableList()
    private val enabled = initialEnabled.intersect(order.toSet()).toMutableSet()

    init {
        orientation = VERTICAL
        setPadding(0, 4.dp, 0, 4.dp)
        setOnDragListener { _, event -> handleDrag(event) }
        rebuildRows()
    }

    fun currentOrder(): List<String> = order.toList()
    fun currentEnabled(): Set<String> = enabled.toSet()

    private fun rebuildRows() {
        removeAllViews()
        order.forEach { toolId -> addView(createRow(toolId)) }
    }

    private fun createRow(toolId: String): View {
        val label = DrawingElement.toolNames[toolId] ?: toolId
        val row = LinearLayout(context).apply {
            tag = "toolbar-tool:$toolId"
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp, 4.dp, 8.dp, 4.dp)
            background = roundedBackground(Color.parseColor("#151C25"), 10f)
            isLongClickable = true
            setOnLongClickListener {
                val clip = ClipData.newPlainText("toolbar-tool", toolId)
                startDragAndDrop(clip, DragShadowBuilder(this), this, 0)
                true
            }
        }
        row.addView(TextView(context).apply {
            text = "☰"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#91A0B2"))
            contentDescription = "拖动排序 $label"
        }, LayoutParams(44.dp, 48.dp))
        row.addView(TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(0, 48.dp, 1f))
        row.addView(CheckBox(context).apply {
            tag = "toolbar-enabled:$toolId"
            contentDescription = "在悬浮工具栏显示$label"
            isChecked = toolId in enabled
            minWidth = 48.dp
            minHeight = 48.dp
            setOnCheckedChangeListener { _, checked ->
                if (!checked && enabled.size <= 1) {
                    isChecked = true
                    return@setOnCheckedChangeListener
                }
                if (checked) enabled += toolId else enabled -= toolId
                notifyChanged()
            }
        }, LayoutParams(56.dp, 48.dp))
        return row
    }

    private fun handleDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return event.clipDescription?.hasMimeType("text/plain") == true
            DragEvent.ACTION_DRAG_ENTERED -> {
                (event.localState as? View)?.alpha = 0.55f
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                (event.localState as? View)?.alpha = 1f
                return true
            }
            DragEvent.ACTION_DROP -> {
                val dragged = event.localState as? View ?: return false
                val targetIndex = (0 until childCount).firstOrNull { index ->
                    val child = getChildAt(index)
                    event.y >= child.top && event.y <= child.bottom
                } ?: return false
                val fromIndex = indexOfChild(dragged)
                if (fromIndex >= 0 && fromIndex != targetIndex) {
                    removeViewAt(fromIndex)
                    addView(dragged, targetIndex.coerceIn(0, childCount))
                    order.clear()
                    for (index in 0 until childCount) {
                        order += getChildAt(index).tag.toString().removePrefix("toolbar-tool:")
                    }
                    notifyChanged()
                }
                dragged.alpha = 1f
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                (event.localState as? View)?.alpha = 1f
                return true
            }
        }
        return true
    }

    private fun notifyChanged() = onChanged(currentOrder(), currentEnabled())

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.dpf
        setStroke(1.dp, Color.parseColor("#263241"))
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Int.dpf: Float get() = this * resources.displayMetrics.density
    private val Float.dpf: Float get() = this * resources.displayMetrics.density
}
