package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SelectionMenuView(
    context: Context,
    private val onStartDrawing: (toolId: String, colorIndex: Int) -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private var selectedColorIndex = 0
    private var selectedToolIndex = 0
    private val toolButtons = mutableListOf<View>()

    private val tools = DrawingElement.tools

    init {
        setBackgroundColor(Color.parseColor("#80000000"))
        setOnClickListener { onDismiss() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20.dp, 24.dp, 20.dp, 24.dp)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1A1A2E"))
                cornerRadius = 24.dpf
            }
            elevation = 16f
            setOnClickListener { }

            // Drag handle
            addView(TextView(context).apply {
                text = "───"; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#555555"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12.dp }
            })

            // Title
            addView(TextView(context).apply {
                text = "选择工具"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; setTextColor(Color.parseColor("#F0F0F0"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20.dp }
            })

            // Tools grid
            addView(createToolGrid())

            // Separator
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin = 16.dp; bottomMargin = 16.dp }
                setBackgroundColor(Color.parseColor("#22FFFFFF"))
            })

            // Colors + Bottom buttons
            addView(createColorAndButtons())
        }

        addView(panel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun createToolGrid(): View {
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        for (row in tools.chunked(2)) {
            val rowLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for ((i, tool) in row.withIndex()) {
                val globalIndex = tools.indexOf(tool)
                rowLayout.addView(createToolButton(tool, globalIndex))
            }
            grid.addView(rowLayout)
        }
        return grid
    }

    private fun createToolButton(tool: ToolDef, index: Int): View {
        val item = LinearLayout(context).apply {
            tag = "menu-tool:${tool.id}"
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(16.dp, 10.dp, 16.dp, 10.dp)
            setOnClickListener {
                selectedToolIndex = index
                updateToolSelection()
            }
        }
        val iconBg = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(52.dp, 52.dp).apply { bottomMargin = 6.dp; rightMargin = 12.dp }
            background = GradientDrawable().apply {
                colors = tool.gradientColors; cornerRadius = 14.dpf; gradientType = GradientDrawable.LINEAR_GRADIENT
            }
            addView(ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_edit); setColorFilter(Color.WHITE); scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams(26.dp, 26.dp, Gravity.CENTER)
            })
        }
        item.addView(iconBg)
        item.addView(TextView(context).apply {
            text = tool.label; textSize = 12f; setTextColor(Color.parseColor("#AAAAAA")); gravity = Gravity.CENTER
        })
        toolButtons += item
        updateToolButton(item, index == selectedToolIndex)
        return item
    }

    private fun updateToolSelection() {
        toolButtons.forEachIndexed { index, button -> updateToolButton(button, index == selectedToolIndex) }
    }

    private fun updateToolButton(button: View, selected: Boolean) {
        button.alpha = if (selected) 1f else 0.55f
    }

    private fun createColorAndButtons(): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        strip.addView(TextView(context).apply {
            text = "颜色"; textSize = 12f; setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 12.dp }
        })

        DrawingElement.colorValues.forEachIndexed { idx, c ->
            val size = 32.dp
            val circle = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 8.dp }
                background = GradientDrawable().apply { setColor(c); cornerRadius = (size / 2).toFloat() }
                tag = idx
                setOnClickListener { selectedColorIndex = idx; updateStrip(strip) }
            }
            strip.addView(circle)
        }
        container.addView(strip)
        updateStrip(strip)

        // Button row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 16.dp, 0, 0)
        }
        btnRow.addView(TextView(context).apply {
            text = "关闭"; textSize = 14f; setTextColor(Color.parseColor("#888888"))
            setPadding(24.dp, 10.dp, 24.dp, 10.dp); gravity = Gravity.CENTER
            setOnClickListener { onDismiss() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 12.dp }
        })
        btnRow.addView(TextView(context).apply {
            text = "开始绘制"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(28.dp, 10.dp, 28.dp, 10.dp); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                colors = intArrayOf(0xFF60A5FA.toInt(), 0xFF3B82F6.toInt()); cornerRadius = 20.dpf; gradientType = GradientDrawable.LINEAR_GRADIENT
            }
            setOnClickListener { onStartDrawing(tools[selectedToolIndex].id, selectedColorIndex) }
        })
        container.addView(btnRow)
        return container
    }

    private fun updateStrip(strip: View) {
        val g = strip as ViewGroup
        for (i in 0 until g.childCount) {
            val child = g.getChildAt(i)
            val idx = child.tag as? Int ?: continue
            child.alpha = if (idx == selectedColorIndex) 1f else 0.3f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
