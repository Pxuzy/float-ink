package com.pxuzy.floatingpen

import android.graphics.Color

object FloatInkTheme {
    // 石墨蓝（Graphite Blue）主题色板
    val background: Int = Color.parseColor("#101214")
    val surface: Int = Color.parseColor("#15181C")
    val surfaceRaised: Int = Color.parseColor("#1C2025")
    val surfaceActive: Int = Color.parseColor("#26384B")
    val textPrimary: Int = Color.parseColor("#E8EDF3")
    val textSecondary: Int = Color.parseColor("#A6AFBB")
    val textMuted: Int = Color.parseColor("#718096")
    val border: Int = Color.parseColor("#30363E")
    val borderStrong: Int = Color.parseColor("#55FFFFFF")

    // 主操作与选中态强调色；onAccent 为深色文字，保证浅蓝背景上的对比度
    val accent: Int = Color.parseColor("#8AB4F8")
    val onAccent: Int = Color.parseColor("#0F1B2C")

    // Overlay surfaces stay neutral and translucent so the drawing remains visible underneath.
    val overlayBar: Int = Color.argb(236, 12, 16, 21)
    val overlayPanel: Int = Color.argb(238, 12, 16, 21)
    val overlayStroke: Int = Color.argb(88, 255, 255, 255)
    val overlaySelected: Int = Color.argb(34, 255, 255, 255)

    const val CONTROL_RADIUS_DP = 6f
    const val PANEL_RADIUS_DP = 8f
}
