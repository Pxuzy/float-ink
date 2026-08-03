package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Builds and refreshes the toolbar color control without owning drawing state. */
class ColorControlBuilder(
    private val context: Context,
    private val density: Float,
    private val compactLayout: () -> Boolean,
    private val colorLabel: (Int) -> String,
    private val onClick: () -> Unit,
) {
    private val Int.dp: Int get() = (this * density).toInt()
    private val Float.dpf: Float get() = this * density

    fun build(color: Int, widthDp: Int, toolbarButtonSizeDp: Int): LinearLayout {
        val dotSize = (toolbarButtonSizeDp * 0.58f).dpf.toInt().coerceAtLeast(10.dp)
        return LinearLayout(context).apply {
            tag = "color"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = description(color, widthDp)
            layoutParams = LinearLayout.LayoutParams(toolbarButtonSizeDp.dp, toolbarButtonSizeDp.dp).apply { marginEnd = 1.dp }
            addView(View(context).apply {
                tag = "color-dot"
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginEnd = 1.dp }
                background = dotBackground(color, dotSize)
            })
            addView(TextView(context).apply {
                tag = "color-label"
                text = label(color, widthDp)
                textSize = if (compactLayout()) 10f else 11f
                setTextColor(Color.parseColor("#AAFFFFFF"))
                gravity = Gravity.CENTER_VERTICAL
            })
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1.dpf.toInt(), (toolbarButtonSizeDp * 0.65f).dp.toInt().coerceAtLeast(14.dp)).apply { marginStart = 2.dp }
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            })
            setOnClickListener { onClick() }
        }
    }

    fun refresh(control: LinearLayout, color: Int, widthDp: Int) {
        control.contentDescription = description(color, widthDp)
        control.findViewWithTag<View>("color-dot")?.background = dotBackground(
            color,
            control.findViewWithTag<View>("color-dot")?.layoutParams?.width ?: 0,
        )
        (control.findViewWithTag<TextView>("color-label"))?.apply {
            text = label(color, widthDp)
            textSize = if (compactLayout()) 10f else 11f
        }
    }

    private fun label(color: Int, widthDp: Int): String =
        if (compactLayout()) "" else "${colorLabel(color)} · ${widthDp}dp"

    private fun description(color: Int, widthDp: Int): String =
        "当前颜色 ${colorLabel(color)}，线宽 ${widthDp}dp"

    private fun dotBackground(color: Int, size: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = (size / 2).toFloat()
        setStroke(2.dpf.toInt(), Color.parseColor("#55FFFFFF"))
    }
}
