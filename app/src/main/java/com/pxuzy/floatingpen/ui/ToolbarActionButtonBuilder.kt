package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout

/** Builds fixed toolbar action buttons without owning their actions. */
class ToolbarActionButtonBuilder(
    private val context: Context,
    private val density: Float,
    private val compactLayout: () -> Boolean,
    private val toolbarButtonSizeDp: () -> Int,
) {
    private val Int.dp: Int get() = (this * density).toInt()
    private val Float.dpf: Float get() = this * density

    fun build(icon: String, action: () -> Unit): View = ToolIconView(context, icon).apply {
        contentDescription = when (icon) {
            "undo" -> "撤销"
            "clear" -> "清空"
            "more" -> "更多工具"
            "canvas" -> "选择画板和图层"
            else -> "退出"
        }
        val sizeDp = toolbarButtonSizeDp()
        val horizontalPadding = (sizeDp * if (compactLayout()) 0.08f else 0.12f).dp.toInt()
        val verticalPadding = (sizeDp * if (compactLayout()) 0.06f else 0.08f).dp.toInt()
        setMinimumWidth(0)
        setMinimumHeight(0)
        minimumWidth = 0
        minimumHeight = 0
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        setOnClickListener { action() }
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 6.dpf
        }
        layoutParams = LinearLayout.LayoutParams(sizeDp.dp, sizeDp.dp).apply { marginStart = 2.dp }
    }
}
