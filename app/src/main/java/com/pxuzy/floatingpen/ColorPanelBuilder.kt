package com.pxuzy.floatingpen

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/** Builds the color picker UI without owning the active drawing style. */
class ColorPanelBuilder(
    private val context: Context,
    private val density: Float,
    private val currentColor: () -> Int,
    private val onColorSelected: (Int) -> Unit,
    private val currentWidthDp: () -> Float,
    private val onWidthSelected: (Float) -> Unit,
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
        panel.addView(widthRow())
        return panel
    }

    /** One low-interference width slider row; the caller persists and applies the value. */
    private fun widthRow(): View {
        val minWidthDp = PenSettings.MIN_WIDTH_DP
        val maxSteps = PenSettings.MAX_WIDTH_DP - PenSettings.MIN_WIDTH_DP
        val initialWidthDp = currentWidthDp().coerceIn(minWidthDp.toFloat(), PenSettings.MAX_WIDTH_DP.toFloat())
        lateinit var widthLabel: TextView
        lateinit var widthSeek: SeekBar
        fun updateWidth(progress: Int) {
            val widthDp = (progress + minWidthDp).toFloat()
            widthLabel.text = "${widthDp.toInt()} dp"
            widthSeek.contentDescription = "线宽 ${widthDp.toInt()}dp，拖动调节"
            onWidthSelected(widthDp)
        }
        return LinearLayout(context).apply {
            tag = "panel-width-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 48.dp
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp,
            ).apply { topMargin = 8.dp }
            addView(TextView(context).apply {
                text = "线宽"
                textSize = 13f
                setTextColor(FloatInkTheme.textSecondary)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 8.dp })
            widthSeek = SeekBar(context).apply {
                tag = "panel-width-seek"
                max = maxSteps
                progress = (initialWidthDp - minWidthDp).roundToInt().coerceIn(0, maxSteps)
                contentDescription = "线宽 ${initialWidthDp.toInt()}dp，拖动调节"
                progressTintList = ColorStateList.valueOf(Color.parseColor("#E6FFFFFF"))
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateWidth(progress)
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
            addView(widthSeek, LinearLayout.LayoutParams(0, 48.dp, 1f))
            widthLabel = TextView(context).apply {
                tag = "panel-width-label"
                text = "${initialWidthDp.toInt()} dp"
                textSize = 13f
                gravity = Gravity.END
                setTextColor(FloatInkTheme.textPrimary)
            }
            addView(widthLabel, LinearLayout.LayoutParams(48.dp, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
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
