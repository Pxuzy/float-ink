package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Builds the recoverable-clear notice without owning its timeout or session state. */
class RestoreClearBarBuilder(
    private val context: Context,
    private val density: Float,
    private val onRestore: () -> Unit,
) {
    private val Int.dp: Int get() = (this * density).toInt()
    private val Float.dpf: Float get() = this * density

    fun build(): View = LinearLayout(context).apply {
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
            setOnClickListener { onRestore() }
        }, LinearLayout.LayoutParams(56.dp, 48.dp))
    }
}
