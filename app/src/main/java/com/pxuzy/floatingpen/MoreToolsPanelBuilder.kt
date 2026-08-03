package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Builds the auxiliary and overflow tool panel without owning drawing state. */
class MoreToolsPanelBuilder(
    private val context: Context,
    private val density: Float,
    private val actionButtonSize: Int,
    private val overflowToolIds: List<String>,
    private val isGoldenGuideVisible: () -> Boolean,
    private val createToolIcon: (String) -> View,
    private val onFibonacciSelected: () -> Unit,
    private val onGoldenGuideToggled: () -> Unit,
    private val onOverflowToolSelected: (String) -> Unit,
) {
    private val Int.dp: Int get() = (this * density).toInt()
    private val Float.dpf: Float get() = this * density

    fun build(): View = LinearLayout(context).apply {
        tag = "more-tools-panel"
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(18.dp, 14.dp, 18.dp, 14.dp)
        background = GradientDrawable().apply {
            setColor(FloatInkTheme.overlayPanel)
            cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
            setStroke(1.dpf.toInt(), FloatInkTheme.overlayStroke)
        }
        addView(sectionTitle("辅助工具"))
        addView(LinearLayout(context).apply {
            tag = "fibonacci-retracement"
            contentDescription = "斐波那契回撤"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6.dp, 2.dp, 6.dp, 2.dp)
            addView(FloatInkIconView(context, "fibonacci").apply {
                setIconColor(Color.WHITE)
            }, LinearLayout.LayoutParams(actionButtonSize, actionButtonSize))
            addView(TextView(context).apply {
                text = "斐波那契回撤"
                textSize = 13f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, actionButtonSize))
            setOnClickListener { onFibonacciSelected() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, actionButtonSize))
        addView(FloatInkIconView(context, "guide").apply {
            tag = "golden-guide"
            contentDescription = goldenGuideDescription()
            layoutParams = LinearLayout.LayoutParams(actionButtonSize, actionButtonSize)
            setIconColor(Color.WHITE)
            background = guideBackground()
            setOnClickListener {
                onGoldenGuideToggled()
                contentDescription = goldenGuideDescription()
                background = guideBackground()
            }
        })
        addView(sectionTitle("更多形状"))
        if (overflowToolIds.isEmpty()) {
            addView(TextView(context).apply {
                text = "暂无更多工具"
                textSize = 12f
                setTextColor(Color.parseColor("#91A0B2"))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 40.dp))
        } else {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                overflowToolIds.forEach { toolId ->
                    addView(createToolIcon(toolId).apply {
                        setOnClickListener { onOverflowToolSelected(toolId) }
                    })
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, actionButtonSize))
        }
    }

    private fun sectionTitle(title: String): TextView = TextView(context).apply {
        text = title
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 32.dp)
    }

    private fun goldenGuideDescription(): String =
        if (isGoldenGuideVisible()) "隐藏黄金分割线" else "显示黄金分割线"

    private fun guideBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(if (isGoldenGuideVisible()) FloatInkTheme.overlaySelected else Color.TRANSPARENT)
        cornerRadius = 8.dpf
    }
}
