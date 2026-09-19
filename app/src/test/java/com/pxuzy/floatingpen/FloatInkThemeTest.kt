package com.pxuzy.floatingpen

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FloatInkThemeTest {

    @Test
    fun `theme exposes graphite blue palette`() {
        assertEquals(Color.parseColor("#101214"), FloatInkTheme.background)
        assertEquals(Color.parseColor("#15181C"), FloatInkTheme.surface)
        assertEquals(Color.parseColor("#1C2025"), FloatInkTheme.surfaceRaised)
        assertEquals(Color.parseColor("#26384B"), FloatInkTheme.surfaceActive)
        assertEquals(Color.parseColor("#E8EDF3"), FloatInkTheme.textPrimary)
        assertEquals(Color.parseColor("#A6AFBB"), FloatInkTheme.textSecondary)
        assertEquals(Color.parseColor("#718096"), FloatInkTheme.textMuted)
        assertEquals(Color.parseColor("#30363E"), FloatInkTheme.border)
        assertEquals(Color.parseColor("#8AB4F8"), FloatInkTheme.accent)
        assertDarkText(contrastColor = FloatInkTheme.onAccent)
    }

    @Test
    fun `overlay surfaces stay translucent black`() {
        assertEquals(236, Color.alpha(FloatInkTheme.overlayBar))
        assertEquals(12, Color.red(FloatInkTheme.overlayBar))
        assertEquals(16, Color.green(FloatInkTheme.overlayBar))
        assertEquals(21, Color.blue(FloatInkTheme.overlayBar))
        assertEquals(238, Color.alpha(FloatInkTheme.overlayPanel))
        assertEquals(12, Color.red(FloatInkTheme.overlayPanel))
    }

    private fun assertDarkText(contrastColor: Int) {
        val luminance = 0.299f * Color.red(contrastColor) + 0.587f * Color.green(contrastColor) + 0.114f * Color.blue(contrastColor)
        org.junit.Assert.assertTrue("onAccent 应为深色文字，实际亮度 $luminance", luminance < 140f)
        // 与浅蓝 accent 背景保持高对比
        val accentLuminance = 0.299f * Color.red(FloatInkTheme.accent) + 0.587f * Color.green(FloatInkTheme.accent) + 0.114f * Color.blue(FloatInkTheme.accent)
        org.junit.Assert.assertTrue("accent 背景应为浅色", accentLuminance > 140f)
    }
}