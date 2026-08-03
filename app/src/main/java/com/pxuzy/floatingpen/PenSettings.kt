package com.pxuzy.floatingpen

import android.content.Context

object PenSettings {
    const val PREF_NAME = "pen_settings"
    const val KEY_TOOL = "default_tool"
    const val KEY_COLOR = "default_color"
    const val KEY_COLOR_ARGB = "default_color_argb"
    const val KEY_WIDTH_DP = "default_width_dp"
    const val KEY_GLOBAL_COLOR_ARGB = "global_color_argb"
    const val KEY_GLOBAL_WIDTH_DP = "global_width_dp"
    const val KEY_RECENT_COLORS = "recent_colors"
    const val KEY_CUSTOM_COLORS = "custom_colors"
    const val KEY_BUBBLE_OPACITY = "bubble_opacity"
    const val KEY_AUTO_HIDE = "bubble_auto_hide"
    const val KEY_AUTO_HIDE_DELAY = "bubble_auto_hide_delay"
    const val KEY_ARROW_SCALE = "arrow_scale"
    const val KEY_TOOL_ARROW_SCALE = "tool_arrow_scale"
    const val KEY_TOOLBAR_ORDER = "toolbar_order"
    const val KEY_TOOLBAR_ENABLED = "toolbar_enabled"
    const val KEY_BUBBLE_SIZE_DP = "bubble_size_dp"
    const val KEY_TOOLBAR_BUTTON_SIZE_DP = "toolbar_button_size_dp"
    private const val KEY_BUBBLE_X = "bubble_x"
    private const val KEY_BUBBLE_Y = "bubble_y"
    private const val KEY_BUBBLE_SNAPPED_LEFT = "bubble_snapped_left"
    private const val KEY_STYLE_MIGRATED = "tool_style_migrated_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    const val DEFAULT_TOOL = "pen"
    const val DEFAULT_COLOR = 0
    val DEFAULT_COLOR_ARGB: Int = DrawingElement.colorValues[DEFAULT_COLOR]
    const val DEFAULT_WIDTH_DP = 4f
    const val MIN_WIDTH_DP = 2
    const val MAX_WIDTH_DP = 24
    const val DEFAULT_BUBBLE_OPACITY = 0.66f
    const val DEFAULT_AUTO_HIDE = true
    const val DEFAULT_AUTO_HIDE_DELAY_MS = 1500L
    const val MIN_AUTO_HIDE_DELAY_MS = 500L
    const val MAX_AUTO_HIDE_DELAY_MS = 5000L
    const val DEFAULT_ARROW_SCALE = 3f
    const val MIN_ARROW_SCALE = 1f
    const val MAX_ARROW_SCALE = 4f

    const val DEFAULT_BUBBLE_SIZE_DP = 48
    const val MIN_BUBBLE_SIZE_DP = 36
    const val MAX_BUBBLE_SIZE_DP = 64
    const val DEFAULT_TOOLBAR_BUTTON_SIZE_DP = 36
    const val MIN_TOOLBAR_BUTTON_SIZE_DP = 20
    const val MAX_TOOLBAR_BUTTON_SIZE_DP = 60

    val TOOL_IDS = listOf("pen", "line", "arrow", "rect", "circle", "eraser")
    /** Built-in colors are immutable; user colors live in a separate persisted list. */
    val DEFAULT_PALETTE = listOf(
        0xFFF44336.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFF212121.toInt(),
        0xFFFFC107.toInt(), 0xFFFFFFFF.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
    )

    fun isDefaultColor(color: Int): Boolean = color in DEFAULT_PALETTE

    fun customColors(context: Context): List<Int> = parseColorList(
        prefs(context)
            .getString(KEY_CUSTOM_COLORS, "").orEmpty()
    )

    fun allColors(context: Context): List<Int> = DEFAULT_PALETTE + customColors(context)

    fun addCustomColor(context: Context, color: Int): Boolean {
        if (isDefaultColor(color)) return false
        saveColorList(context, KEY_CUSTOM_COLORS, (listOf(color) + customColors(context).filterNot { it == color }).take(24))
        return true
    }

    fun deleteCustomColor(context: Context, color: Int): Boolean {
        if (isDefaultColor(color)) return false
        val colors = customColors(context)
        if (color !in colors) return false
        saveColorList(context, KEY_CUSTOM_COLORS, colors.filterNot { it == color })
        return true
    }

    fun deleteCustomColorAndReplaceStyles(context: Context, color: Int, fallback: Int = DEFAULT_PALETTE.first()): Boolean {
        if (!deleteCustomColor(context, color)) return false
        migrateLegacyStyleIfNeeded(context)
        val prefs = prefs(context)
        prefs.edit().apply {
            putString(
                KEY_RECENT_COLORS,
                parseColorList(prefs.getString(KEY_RECENT_COLORS, "").orEmpty())
                    .filterNot { it == color }
                    .joinToString(",") { it.toUInt().toString(16) },
            )
            if (prefs.getInt(KEY_GLOBAL_COLOR_ARGB, DEFAULT_COLOR_ARGB) == color) {
                putInt(KEY_GLOBAL_COLOR_ARGB, fallback)
            }
            TOOL_IDS.forEach { toolId ->
                putInt(toolColorKey(toolId), fallback)
            }
        }.apply()
        return true
    }

    /** Parses R,G,B or #RRGGBB/#AARRGGBB input and returns an opaque ARGB color. */
    fun parseRgb(text: String): Int? {
        val value = text.trim().removePrefix("#")
        val channels = value.split(',', ' ', ';').filter { it.isNotBlank() }
        if (channels.size == 3) {
            val rgb = channels.map { it.toIntOrNull()?.takeIf { channel -> channel in 0..255 } }
            if (rgb.all { it != null }) return android.graphics.Color.rgb(rgb[0]!!, rgb[1]!!, rgb[2]!!)
        }
        return when (value.length) {
            6 -> value.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> value.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    data class Values(
        val tool: String,
        val globalColor: Int,
        val globalWidthDp: Float,
        val toolStyles: Map<String, ToolStyle>,
        val recentColors: List<Int>,
        val bubbleOpacity: Float,
        val autoHide: Boolean,
        val autoHideDelayMs: Long,
        val arrowScale: Float,
        val toolbarOrder: List<String>,
        val toolbarEnabled: Set<String>,
        val bubbleSizeDp: Int = DEFAULT_BUBBLE_SIZE_DP,
        val toolbarButtonSizeDp: Int = DEFAULT_TOOLBAR_BUTTON_SIZE_DP,
    ) {
        val color: Int get() = styleFor(tool).color
        val widthDp: Float get() = styleFor(tool).widthDp
        val colorIndex: Int
            get() = DrawingElement.colorValues.indexOf(color).takeIf { it >= 0 } ?: DEFAULT_COLOR

        fun styleFor(toolId: String): ToolStyle =
            toolStyles[normalizeTool(toolId)] ?: ToolStyle(DEFAULT_COLOR_ARGB, DEFAULT_WIDTH_DP)

        fun visibleToolbarToolIds(): List<String> =
            toolbarOrder.filter { it in toolbarEnabled }.ifEmpty { listOf(DEFAULT_TOOL) }
    }

    data class ToolbarLayout(val order: List<String>, val enabled: Set<String>)

    data class BubblePosition(val x: Int, val y: Int, val snappedLeft: Boolean)

    fun loadBubblePosition(context: Context): BubblePosition? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_BUBBLE_X) || !prefs.contains(KEY_BUBBLE_Y)) return null
        return BubblePosition(
            prefs.getInt(KEY_BUBBLE_X, 0),
            prefs.getInt(KEY_BUBBLE_Y, 0),
            prefs.getBoolean(KEY_BUBBLE_SNAPPED_LEFT, false),
        )
    }

    fun saveBubblePosition(context: Context, x: Int, y: Int, snappedLeft: Boolean) {
        prefs(context).edit()
            .putInt(KEY_BUBBLE_X, x)
            .putInt(KEY_BUBBLE_Y, y)
            .putBoolean(KEY_BUBBLE_SNAPPED_LEFT, snappedLeft)
            .apply()
    }

    fun load(context: Context): Values {
        val prefs = prefs(context)
        migrateLegacyStyleIfNeeded(context)
        val tool = normalizeTool(prefs.getString(KEY_TOOL, DEFAULT_TOOL))
        val globalColor = prefs.getInt(KEY_GLOBAL_COLOR_ARGB, DEFAULT_COLOR_ARGB)
        val globalWidth = clampWidth(prefs.getFloat(KEY_GLOBAL_WIDTH_DP, DEFAULT_WIDTH_DP))
        val styles = TOOL_IDS.associateWith { toolId ->
            ToolStyle(
                globalColor,
                clampWidth(prefs.getFloat(toolWidthKey(toolId), globalWidth)),
            )
        }
        val recentColors = parseColorList(prefs.getString(KEY_RECENT_COLORS, "").orEmpty()).take(6)
        val opacity = prefs.getFloat(KEY_BUBBLE_OPACITY, DEFAULT_BUBBLE_OPACITY).coerceIn(0.35f, 1f)
        val autoHide = prefs.getBoolean(KEY_AUTO_HIDE, DEFAULT_AUTO_HIDE)
        val delay = prefs.getLong(KEY_AUTO_HIDE_DELAY, DEFAULT_AUTO_HIDE_DELAY_MS)
            .coerceIn(MIN_AUTO_HIDE_DELAY_MS, MAX_AUTO_HIDE_DELAY_MS)
        val arrowScale = DEFAULT_ARROW_SCALE
        val layout = loadToolbarLayout(context)
        val bubbleSize = prefs.getInt(KEY_BUBBLE_SIZE_DP, DEFAULT_BUBBLE_SIZE_DP)
            .coerceIn(MIN_BUBBLE_SIZE_DP, MAX_BUBBLE_SIZE_DP)
        val toolbarButtonSize = prefs.getInt(KEY_TOOLBAR_BUTTON_SIZE_DP, DEFAULT_TOOLBAR_BUTTON_SIZE_DP)
            .coerceIn(MIN_TOOLBAR_BUTTON_SIZE_DP, MAX_TOOLBAR_BUTTON_SIZE_DP)
        return Values(tool, globalColor, globalWidth, styles, recentColors, opacity, autoHide, delay, arrowScale, layout.order, layout.enabled, bubbleSize, toolbarButtonSize)
    }

    private fun migrateLegacyStyleIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_STYLE_MIGRATED, false)) return
        val legacyIndex = prefs.getInt(KEY_COLOR, DEFAULT_COLOR).coerceIn(DrawingElement.colorValues.indices)
        val legacyColor = if (prefs.contains(KEY_COLOR_ARGB)) {
            prefs.getInt(KEY_COLOR_ARGB, DEFAULT_COLOR_ARGB)
        } else {
            DrawingElement.colorValues[legacyIndex]
        }
        val legacyWidth = clampWidth(prefs.getFloat(KEY_WIDTH_DP, DEFAULT_WIDTH_DP))
        val legacyArrowScale = prefs.getFloat(KEY_ARROW_SCALE, DEFAULT_ARROW_SCALE)
            .coerceIn(MIN_ARROW_SCALE, MAX_ARROW_SCALE)
        prefs.edit().apply {
            if (!prefs.contains(KEY_GLOBAL_COLOR_ARGB)) putInt(KEY_GLOBAL_COLOR_ARGB, legacyColor)
            if (!prefs.contains(KEY_GLOBAL_WIDTH_DP)) putFloat(KEY_GLOBAL_WIDTH_DP, legacyWidth)
            TOOL_IDS.forEach { toolId ->
                if (!prefs.contains(toolColorKey(toolId))) putInt(toolColorKey(toolId), legacyColor)
                if (!prefs.contains(toolWidthKey(toolId))) putFloat(toolWidthKey(toolId), legacyWidth)
            }
            if (!prefs.contains(KEY_TOOL_ARROW_SCALE)) putFloat(KEY_TOOL_ARROW_SCALE, legacyArrowScale)
            putBoolean(KEY_STYLE_MIGRATED, true)
        }.commit()
    }

    fun saveTool(context: Context, tool: String) {
        prefs(context)
            .edit().putString(KEY_TOOL, normalizeTool(tool)).apply()
    }

    fun saveGlobalStyle(context: Context, color: Int, widthDp: Float) {
        migrateLegacyStyleIfNeeded(context)
        saveGlobalColor(context, color)
        prefs(context).edit()
            .putFloat(KEY_GLOBAL_WIDTH_DP, clampWidth(widthDp)).apply()
    }

    /** The ink color is global; keep every tool style synchronized for legacy callers. */
    fun saveGlobalColor(context: Context, color: Int) {
        migrateLegacyStyleIfNeeded(context)
        prefs(context).edit().apply {
            putInt(KEY_GLOBAL_COLOR_ARGB, color)
            TOOL_IDS.forEach { toolId -> putInt(toolColorKey(toolId), color) }
        }.apply()
    }

    fun saveToolStyle(context: Context, tool: String, color: Int, widthDp: Float) {
        migrateLegacyStyleIfNeeded(context)
        val toolId = normalizeTool(tool)
        val prefs = prefs(context)
        prefs.edit()
            .putInt(toolColorKey(toolId), prefs.getInt(KEY_GLOBAL_COLOR_ARGB, DEFAULT_COLOR_ARGB))
            .putFloat(toolWidthKey(toolId), clampWidth(widthDp))
            .apply()
    }

    fun applyGlobalStyleToAllTools(context: Context) {
        val values = load(context)
        prefs(context).edit().apply {
            TOOL_IDS.forEach { toolId ->
                putInt(toolColorKey(toolId), values.globalColor)
                putFloat(toolWidthKey(toolId), values.globalWidthDp)
            }
        }.apply()
    }

    // Compatibility for callers that still edit the legacy global controls.
    fun saveColor(context: Context, color: Int) {
        val values = load(context)
        saveGlobalStyle(context, color, values.globalWidthDp)
    }

    fun saveWidth(context: Context, widthDp: Float) {
        val values = load(context)
        saveGlobalStyle(context, values.globalColor, widthDp)
        saveToolStyle(context, values.tool, values.styleFor(values.tool).color, widthDp)
    }

    fun addRecentColor(context: Context, color: Int) {
        val updated = listOf(color) + load(context).recentColors.filterNot { it == color }
        prefs(context).edit()
            .putString(KEY_RECENT_COLORS, updated.take(6).joinToString(",") { it.toUInt().toString(16) })
            .apply()
    }

    private fun parseColorList(serialized: String): List<Int> = serialized.split(',')
        .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toLongOrNull(16)?.toInt() }
        .distinct()

    private fun saveColorList(context: Context, key: String, colors: List<Int>) {
        prefs(context).edit()
            .putString(key, colors.distinct().joinToString(",") { it.toUInt().toString(16) })
            .apply()
    }

    fun saveBubbleOpacity(context: Context, opacity: Float) {
        prefs(context)
            .edit().putFloat(KEY_BUBBLE_OPACITY, opacity).apply()
    }

    fun saveAutoHide(context: Context, enabled: Boolean) {
        prefs(context)
            .edit().putBoolean(KEY_AUTO_HIDE, enabled).apply()
    }

    fun saveAutoHideDelay(context: Context, delayMs: Long) {
        prefs(context)
            .edit().putLong(KEY_AUTO_HIDE_DELAY, delayMs).apply()
    }

    fun saveArrowScale(context: Context, scale: Float) {
        migrateLegacyStyleIfNeeded(context)
        prefs(context).edit().putFloat(KEY_TOOL_ARROW_SCALE, DEFAULT_ARROW_SCALE).apply()
    }

    fun saveBubbleSize(context: Context, sizeDp: Int) {
        prefs(context).edit()
            .putInt(KEY_BUBBLE_SIZE_DP, sizeDp.coerceIn(MIN_BUBBLE_SIZE_DP, MAX_BUBBLE_SIZE_DP))
            .apply()
    }

    fun saveToolbarButtonSize(context: Context, sizeDp: Int) {
        prefs(context).edit()
            .putInt(KEY_TOOLBAR_BUTTON_SIZE_DP, sizeDp.coerceIn(MIN_TOOLBAR_BUTTON_SIZE_DP, MAX_TOOLBAR_BUTTON_SIZE_DP))
            .apply()
    }

    fun loadToolbarLayout(context: Context): ToolbarLayout {
        val prefs = prefs(context)
        val storedOrder = parseToolIds(prefs.getString(KEY_TOOLBAR_ORDER, "").orEmpty())
        val order = (storedOrder + TOOL_IDS).filter { it in TOOL_IDS }.distinct()
        val storedEnabled = parseToolIds(prefs.getString(KEY_TOOLBAR_ENABLED, "").orEmpty()).toSet()
        val enabled = if (storedEnabled.isEmpty() && !prefs.contains(KEY_TOOLBAR_ENABLED)) {
            order.toSet()
        } else {
            (storedEnabled intersect order.toSet()).ifEmpty { setOf(order.first()) }
        }
        return ToolbarLayout(order, enabled)
    }

    fun saveToolbarLayout(context: Context, order: List<String>, enabled: Set<String>) {
        val normalizedOrder = (order + TOOL_IDS).filter { it in TOOL_IDS }.distinct()
        val normalizedEnabled = (enabled intersect normalizedOrder.toSet()).ifEmpty { setOf(normalizedOrder.first()) }
        prefs(context).edit()
            .putString(KEY_TOOLBAR_ORDER, normalizedOrder.joinToString(","))
            .putString(KEY_TOOLBAR_ENABLED, normalizedEnabled.joinToString(","))
            .apply()
    }

    fun normalizeTool(tool: String?): String = tool?.takeIf(TOOL_IDS::contains) ?: DEFAULT_TOOL
    private fun parseToolIds(serialized: String): List<String> = serialized.split(',').filter { it in TOOL_IDS }.distinct()
    private fun clampWidth(value: Float): Float = value.coerceIn(MIN_WIDTH_DP.toFloat(), MAX_WIDTH_DP.toFloat())
    private fun toolColorKey(tool: String) = "tool_${tool}_color_argb"
    private fun toolWidthKey(tool: String) = "tool_${tool}_width_dp"
}