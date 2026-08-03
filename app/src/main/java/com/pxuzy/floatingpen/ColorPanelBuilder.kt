package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Builds the color picker UI without owning the active drawing style. */
class ColorPanelBuilder(
    private val context: Context,
    private val density: Float,
    private val currentColor: () -> Int,
    private val onColorSelected: (Int) -> Unit,
) {
    private val Int.dp: Int get() = (this * density).toInt()
    private val Float.dpf: Float get() = this * density

    fun build(): View {
        val panel = LinearLayout(context).apply {
            tag = "color-panel"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(236, 12, 16, 21))
                cornerRadius = FloatInkTheme.PANEL_RADIUS_DP.dpf
                setStroke(1.dpf.toInt(), FloatInkTheme.overlayStroke)
            }
        }
        PenSettings.DEFAULT_PALETTE.chunked(4).forEachIndexed { rowIndex, colors ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (rowIndex > 0) {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = 6.dp }
                }
            }
            colors.forEachIndexed { index, color ->
                row.addView(colorSwatch(color, "palette-color:${rowIndex * 4 + index}"))
            }
            panel.addView(row)
        }
        val recentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 8.dp }
        }
        PenSettings.load(context).recentColors.take(6).forEachIndexed { index, color ->
            recentRow.addView(colorSwatch(color, "recent-color:$index"))
        }
        panel.addView(recentRow)
        return panel
    }

    private fun colorSwatch(color: Int, viewTag: String) = FrameLayout(context).apply {
        tag = viewTag
        contentDescription = if (PenSettings.isDefaultColor(color)) "默认颜色" else "最近使用颜色"
        layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginEnd = 2.dp }
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == currentColor()) setStroke(2.dpf.toInt(), Color.WHITE)
            }
        }, FrameLayout.LayoutParams(30.dp, 30.dp, Gravity.CENTER))
        setOnClickListener { onColorSelected(color) }
    }
}
