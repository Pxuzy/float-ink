package com.pxuzy.floatingpen.core

sealed class DrawingElement {
    abstract val drawColor: Int
    abstract val drawWidth: Float

    data class Stroke(
        val points: MutableList<Pair<Float, Float>>,
        val color: Int,
        val width: Float,
    ) : DrawingElement() {
        override val drawColor: Int get() = color
        override val drawWidth: Float get() = width
    }

    data class Line(
        val start: Pair<Float, Float>,
        val end: Pair<Float, Float>,
        val color: Int,
        val width: Float,
    ) : DrawingElement() {
        override val drawColor: Int get() = color
        override val drawWidth: Float get() = width
    }

    data class Arrow(
        val start: Pair<Float, Float>,
        val end: Pair<Float, Float>,
        val color: Int,
        val width: Float,
        val headLengthDp: Float = 20f,
    ) : DrawingElement() {
        override val drawColor: Int get() = color
        override val drawWidth: Float get() = width
    }

    data class Rect(
        val start: Pair<Float, Float>,
        val end: Pair<Float, Float>,
        val color: Int,
        val width: Float,
    ) : DrawingElement() {
        override val drawColor: Int get() = color
        override val drawWidth: Float get() = width
    }

    data class Circle(
        val center: Pair<Float, Float>,
        val radius: Float,
        val color: Int,
        val width: Float,
    ) : DrawingElement() {
        override val drawColor: Int get() = color
        override val drawWidth: Float get() = width
    }

    companion object {
        val colorNames = listOf("红色", "蓝色", "绿色", "黑色", "琥珀色")
        val colorValues = listOf(
            0xFFF44336.toInt(),
            0xFF2196F3.toInt(),
            0xFF4CAF50.toInt(),
            0xFF212121.toInt(),
            0xFFFFC107.toInt(),
        )
        val tools = listOf(
            ToolDef("pen", "画笔", intArrayOf(0xFF3B82F6.toInt(), 0xFF2563EB.toInt())),
            ToolDef("line", "直线", intArrayOf(0xFF10B981.toInt(), 0xFF059669.toInt())),
            ToolDef("arrow", "箭头", intArrayOf(0xFFF59E0B.toInt(), 0xFFD97706.toInt())),
            ToolDef("rect", "矩形", intArrayOf(0xFF8B5CF6.toInt(), 0xFF7C3AED.toInt())),
            ToolDef("circle", "圆形", intArrayOf(0xFF06B6D4.toInt(), 0xFF0891B2.toInt())),
            ToolDef("eraser", "橡皮擦", intArrayOf(0xFF94A3B8.toInt(), 0xFF64748B.toInt())),
        )
        val toolNames: Map<String, String> = tools.associate { it.id to it.label }
        const val ARROW_HEAD_ANGLE_RAD = 0.436332
        const val ERASER_RADIUS_DP = 18f
    }
}

data class ToolDef(val id: String, val label: String, val gradientColors: IntArray) {
    override fun equals(other: Any?): Boolean = other is ToolDef && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
