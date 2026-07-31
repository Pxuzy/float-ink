package com.pxuzy.floatingpen

import android.graphics.Color

object FloatInkTheme {
    val background: Int = Color.parseColor("#0B0F14")
    val surface: Int = Color.parseColor("#121820")
    val surfaceRaised: Int = Color.parseColor("#18212B")
    val surfaceActive: Int = Color.parseColor("#263A50")
    val textPrimary: Int = Color.parseColor("#F7F9FB")
    val textSecondary: Int = Color.parseColor("#91A0B2")
    val textMuted: Int = Color.parseColor("#718096")
    val border: Int = Color.parseColor("#263241")
    val borderStrong: Int = Color.parseColor("#55FFFFFF")

    // Overlay surfaces stay neutral and translucent so the drawing remains visible underneath.
    val overlayBar: Int = Color.argb(236, 12, 16, 21)
    val overlayPanel: Int = Color.argb(238, 12, 16, 21)
    val overlayStroke: Int = Color.argb(88, 255, 255, 255)
    val overlaySelected: Int = Color.argb(34, 255, 255, 255)

    const val CONTROL_RADIUS_DP = 6f
    const val PANEL_RADIUS_DP = 8f
}
