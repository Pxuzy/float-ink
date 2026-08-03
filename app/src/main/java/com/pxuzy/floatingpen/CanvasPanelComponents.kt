package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Builds shared board/layer panel controls without owning session state. */
class CanvasPanelComponents(
    private val context: Context,
    private val density: Float,
) {
    private val Int.dp: Int get() = (this * density).toInt()
    private val Float.dpf: Float get() = this * density

    fun sectionHeader(title: String, addTag: String, onAdd: () -> Unit): View =
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

    fun overflowButton(buttonTag: String, onClick: () -> Unit): View =
        FloatInkIconView(context, "more").apply {
            tag = buttonTag
            contentDescription = "更多操作"
            setIconColor(Color.parseColor("#D2D8E0"))
            layoutParams = LinearLayout.LayoutParams(36.dp, 40.dp)
            setOnClickListener { onClick() }
        }

    fun rowBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) Color.argb(40, 255, 255, 255) else Color.TRANSPARENT)
        cornerRadius = 8.dpf
        if (selected) setStroke(1.dpf.toInt(), Color.argb(92, 255, 255, 255))
    }
}
