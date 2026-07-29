package com.pxuzy.floatingpen

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PenSettingsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        context.getSharedPreferences(PenSettings.PREF_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `legacy global style migrates to every tool exactly once`() {
        val prefs = context.getSharedPreferences(PenSettings.PREF_NAME, Application.MODE_PRIVATE)
        prefs.edit()
            .putInt(PenSettings.KEY_COLOR_ARGB, 0xFF123456.toInt())
            .putFloat(PenSettings.KEY_WIDTH_DP, 11f)
            .putFloat(PenSettings.KEY_ARROW_SCALE, 2.35f)
            .commit()

        val migrated = PenSettings.load(context)
        PenSettings.saveToolStyle(context, "pen", 0xFFABCDEF.toInt(), 7f)
        val reloaded = PenSettings.load(context)

        assertEquals(0xFF123456.toInt(), migrated.globalColor)
        assertEquals(11f, migrated.globalWidthDp)
        PenSettings.TOOL_IDS.forEach { tool ->
            assertEquals(0xFF123456.toInt(), migrated.styleFor(tool).color)
            assertEquals(11f, migrated.styleFor(tool).widthDp)
        }
        assertEquals(2.35f, migrated.arrowScale)
        assertEquals(0xFF123456.toInt(), reloaded.styleFor("pen").color)
    }

    @Test
    fun `newly added circle style inherits global defaults after prior migration`() {
        val prefs = context.getSharedPreferences(PenSettings.PREF_NAME, Application.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("tool_style_migrated_v1", true)
            .putInt(PenSettings.KEY_GLOBAL_COLOR_ARGB, 0xFF2468AC.toInt())
            .putFloat(PenSettings.KEY_GLOBAL_WIDTH_DP, 14f)
            .commit()

        val values = PenSettings.load(context)

        assertEquals(0xFF2468AC.toInt(), values.styleFor("circle").color)
        assertEquals(14f, values.styleFor("circle").widthDp)
    }

    @Test
    fun `tool widths persist independently while color stays global`() {
        PenSettings.saveGlobalStyle(context, 0xFF101010.toInt(), 4f)
        PenSettings.saveToolStyle(context, "line", 0xFF202020.toInt(), 8f)
        PenSettings.saveToolStyle(context, "arrow", 0xFF303030.toInt(), 99f)

        val values = PenSettings.load(context)

        assertEquals(0xFF101010.toInt(), values.globalColor)
        assertEquals(4f, values.globalWidthDp)
        assertEquals(0xFF101010.toInt(), values.styleFor("line").color)
        assertEquals(8f, values.styleFor("line").widthDp)
        assertEquals(0xFF101010.toInt(), values.styleFor("arrow").color)
        assertEquals(24f, values.styleFor("arrow").widthDp)
        assertEquals(values.styleFor("pen"), values.styleFor("unknown"))
    }

    @Test
    fun `apply global style updates every tool without changing arrow scale`() {
        PenSettings.saveArrowScale(context, 3.25f)
        PenSettings.saveGlobalStyle(context, 0xFF778899.toInt(), 13f)

        PenSettings.applyGlobalStyleToAllTools(context)

        val values = PenSettings.load(context)
        PenSettings.TOOL_IDS.forEach { tool ->
            assertEquals(0xFF778899.toInt(), values.styleFor(tool).color)
            assertEquals(13f, values.styleFor(tool).widthDp)
        }
        assertEquals(3.25f, values.arrowScale)
    }

    @Test
    fun `global pen and bubble values persist with safe bounds`() {
        PenSettings.saveColor(context, 0xFF7C3AED.toInt())
        PenSettings.saveWidth(context, 99f)
        PenSettings.saveBubbleOpacity(context, 0.2f)
        PenSettings.saveAutoHide(context, false)
        PenSettings.saveAutoHideDelay(context, 9000L)
        PenSettings.saveArrowScale(context, 9f)

        val values = PenSettings.load(context)

        assertEquals(0xFF7C3AED.toInt(), values.color)
        assertEquals(24f, values.widthDp)
        assertEquals(0.35f, values.bubbleOpacity)
        assertFalse(values.autoHide)
        assertEquals(5000L, values.autoHideDelayMs)
        assertEquals(4f, values.arrowScale)
    }

    @Test
    fun `arrow scale defaults to two and preserves precise custom values`() {
        context.getSharedPreferences(PenSettings.PREF_NAME, Application.MODE_PRIVATE).edit().clear().commit()
        assertEquals(2f, PenSettings.load(context).arrowScale)

        PenSettings.saveArrowScale(context, 2.35f)

        assertEquals(2.35f, PenSettings.load(context).arrowScale)
    }

    @Test
    fun `custom colors parse from rgb and can be added or deleted but defaults cannot`() {
        val custom = PenSettings.parseRgb("12, 34, 56")!!
        assertEquals(0xFF0C2238.toInt(), custom)
        assertEquals(0xFFFF8000.toInt(), PenSettings.parseRgb("#FF8000"))
        assertEquals(null, PenSettings.parseRgb("256,0,0"))

        assertTrue(PenSettings.addCustomColor(context, custom))
        assertTrue(custom in PenSettings.allColors(context))
        assertTrue(PenSettings.deleteCustomColor(context, custom))
        assertFalse(custom in PenSettings.allColors(context))
        assertFalse(PenSettings.deleteCustomColor(context, PenSettings.DEFAULT_PALETTE.first()))
        assertTrue(PenSettings.DEFAULT_PALETTE.all { it in PenSettings.allColors(context) })
    }

    @Test
    fun `deleting active custom color replaces every persisted style with safe fallback`() {
        val custom = 0xFF123456.toInt()
        PenSettings.addCustomColor(context, custom)
        PenSettings.addRecentColor(context, custom)
        PenSettings.addRecentColor(context, 0xFF778899.toInt())
        PenSettings.saveGlobalStyle(context, custom, 9f)
        PenSettings.saveToolStyle(context, "pen", custom, 4f)
        PenSettings.saveToolStyle(context, "arrow", 0xFFABCDEF.toInt(), 8f)
        PenSettings.saveToolStyle(context, "rect", custom, 12f)

        assertTrue(PenSettings.deleteCustomColorAndReplaceStyles(context, custom))

        val values = PenSettings.load(context)
        assertTrue(custom !in PenSettings.customColors(context))
        assertTrue(custom !in values.recentColors)
        assertEquals(listOf(0xFF778899.toInt()), values.recentColors)
        assertEquals(PenSettings.DEFAULT_PALETTE.first(), values.globalColor)
        assertEquals(PenSettings.DEFAULT_PALETTE.first(), values.styleFor("pen").color)
        assertEquals(PenSettings.DEFAULT_PALETTE.first(), values.styleFor("rect").color)
        assertEquals(PenSettings.DEFAULT_PALETTE.first(), values.styleFor("arrow").color)
    }

    @Test
    fun `custom colors are unique and newest first`() {
        val first = 0xFF112233.toInt()
        val second = 0xFF445566.toInt()
        PenSettings.addCustomColor(context, first)
        PenSettings.addCustomColor(context, second)
        PenSettings.addCustomColor(context, first)
        assertEquals(listOf(first, second), PenSettings.customColors(context))
    }

    @Test
    fun `rgb accepts hexadecimal with or without hash`() {
        assertEquals(0xFF123456.toInt(), PenSettings.parseRgb("123456"))
        assertEquals(0x80123456.toInt(), PenSettings.parseRgb("#80123456"))
    }

    @Test
    fun `recent colors are unique and newest first`() {
        PenSettings.addRecentColor(context, 0xFF112233.toInt())
        PenSettings.addRecentColor(context, 0xFF445566.toInt())
        PenSettings.addRecentColor(context, 0xFF112233.toInt())

        assertEquals(
            listOf(0xFF112233.toInt(), 0xFF445566.toInt()),
            PenSettings.load(context).recentColors
        )
    }

    @Test
    fun `bubble position persists with snapped edge`() {
        PenSettings.saveBubblePosition(context, 120, 240, true)

        assertEquals(PenSettings.BubblePosition(120, 240, true), PenSettings.loadBubblePosition(context))
    }

    @Test
    fun `toolbar layout defaults to all tools and preserves custom order`() {
        val defaults = PenSettings.loadToolbarLayout(context)
        assertEquals(PenSettings.TOOL_IDS, defaults.order)
        assertEquals(PenSettings.TOOL_IDS.toSet(), defaults.enabled)

        PenSettings.saveToolbarLayout(context, listOf("arrow", "pen", "unknown"), setOf("arrow"))

        val layout = PenSettings.loadToolbarLayout(context)
        assertEquals(listOf("arrow", "pen") + PenSettings.TOOL_IDS.filterNot { it in setOf("arrow", "pen") }, layout.order)
        assertEquals(setOf("arrow"), layout.enabled)
        assertEquals(listOf("arrow"), PenSettings.load(context).visibleToolbarToolIds())
    }

    @Test
    fun `toolbar layout never disables every tool`() {
        PenSettings.saveToolbarLayout(context, PenSettings.TOOL_IDS, emptySet())

        assertEquals(setOf("pen"), PenSettings.loadToolbarLayout(context).enabled)
    }
}