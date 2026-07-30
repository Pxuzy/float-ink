package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

/**
 * 悬浮球 — 参考 EasyFloat / FloatBall 设计
 *
 * 特性：
 * - 拖动后自动贴边（左/右）
 * - 贴边后自动侧边隐藏（只露出 8dp 边缘）
 * - 触摸时滑出 + 放大动画
 * - 点击触发菜单，长按快速切换
 * - 位置按屏幕方向持久化
 */
class FloatingBubbleView(context: Context, private val onTap: () -> Unit, private val onLongPress: () -> Unit) : View(context) {

    companion object {
        private const val BUBBLE_SIZE = 48
        private const val EDGE_MARGIN = 8
        private const val HIDDEN_WIDTH = 8
        private const val STATUS_SAFE = 48
        private const val TAP_THRESHOLD = 8
        private const val LONG_PRESS_MS = 400L
        private const val HIDE_DELAY_MS = 1500L
    }

    private var isHidden = false
    private var isSnappedLeft = false
    private var isDragging = false
    private var isLongPressed = false

    private var winX = 0f; private var winY = 0f
    private var touchDownRawX = 0f; private var touchDownRawY = 0f
    private var touchDownTime = 0L

    private val density = resources.displayMetrics.density
    private val initialSettings = PenSettings.load(context)
    private var bubbleOpacity = initialSettings.bubbleOpacity
    private var autoHideEnabled = initialSettings.autoHide
    private var autoHideDelayMs = initialSettings.autoHideDelayMs
    private var accentColor = initialSettings.color
    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!isDragging && !isLongPressed && isPressed) {
            isLongPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }
    }

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((255 * bubbleOpacity).toInt(), 12, 12, 12); style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(96, 255, 255, 255); strokeWidth = 1.2f * density; style = Paint.Style.STROKE
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 2.4f * density; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor; style = Paint.Style.FILL
    }
    private val accentRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 10, 13, 17); style = Paint.Style.FILL
    }
    private val bubbleBounds = RectF()

    init { contentDescription = "悬浮画笔" }

    /** 实时获取屏幕尺寸 — 每次触摸时刷新，解决旋转后坐标失效 */
    private val screenW: Int get() = resources.displayMetrics.widthPixels
    private val screenH: Int get() = resources.displayMetrics.heightPixels

    // ===== 生命周期 =====

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val lp = layoutParams as WindowManager.LayoutParams
        // OverlayService uses TOP|START, so gravity is intentionally not NO_GRAVITY.
        // A zero position is the uninitialized WindowManager default; place the first
        // bubble on the right instead of mistaking it for a real saved position.
        if (lp.x == 0 && lp.y == 0) {
            val saved = PenSettings.loadBubblePosition(context)
            if (saved != null) {
                lp.x = saved.x
                lp.y = saved.y
                isSnappedLeft = saved.snappedLeft
            } else {
                lp.x = screenW - (BUBBLE_SIZE * density).toInt() - (EDGE_MARGIN * density).toInt()
                lp.y = (STATUS_SAFE * density).toInt()
                isSnappedLeft = false
            }
        } else {
            isSnappedLeft = lp.x < screenW / 2
        }
        clampToScreenBounds(lp)
        savePosition(lp)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        safeUpdateViewLayout(windowManager, lp)
        scheduleHide()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isPressed = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    internal fun resumeAutoHide() {
        scheduleHide()
    }

    internal fun applySettings(settings: PenSettings.Values) {
        bubbleOpacity = settings.bubbleOpacity
        autoHideEnabled = settings.autoHide
        autoHideDelayMs = settings.autoHideDelayMs
        accentColor = settings.color
        accentPaint.color = accentColor
        buttonPaint.color = Color.argb((255 * bubbleOpacity).toInt(), 12, 12, 12)

        if (autoHideEnabled) {
            scheduleHide()
        } else {
            mainHandler.removeCallbacksAndMessages(null)
            if (isHidden) {
                isHidden = false
                if (isAttachedToWindow) {
                    runCatching {
                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        updateBubblePosition(wm, layoutParams as WindowManager.LayoutParams)
                    }
                }
            }
            invalidate()
        }
    }

    internal fun keepInsideCurrentScreen() {
        if (!isAttachedToWindow) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val lp = layoutParams as WindowManager.LayoutParams
        clampToScreenBounds(lp)
        isSnappedLeft = lp.x + width / 2 < screenW / 2
        savePosition(lp)
        safeUpdateViewLayout(wm, lp)
        scheduleHide()
    }

    // ===== 绘制 =====

    override fun onDraw(canvas: Canvas) {
        val offset = if (isHidden) (BUBBLE_SIZE / 2 - HIDDEN_WIDTH).dpf else 0f
        val shiftX = if (isSnappedLeft) -offset else offset

        val cx = width / 2f + shiftX
        val cy = height / 2f
        val half = minOf(width, height) / 2f - 2.dpf
        bubbleBounds.set(cx - half, cy - half, cx + half, cy + half)
        val radius = 10.dpf
        canvas.drawRoundRect(bubbleBounds, radius, radius, buttonPaint)
        canvas.drawRoundRect(bubbleBounds, radius, radius, borderPaint)

        val s = 10.dpf
        canvas.save()
        canvas.rotate(-45f, cx, cy)
        canvas.drawLine(cx, cy - s, cx, cy + s * 0.55f, strokePaint)
        canvas.drawLine(cx - 3.dpf, cy - s, cx + 3.dpf, cy - s, strokePaint)
        canvas.drawLine(cx - 3.dpf, cy - s, cx, cy - s - 4.dpf, strokePaint)
        canvas.drawLine(cx + 3.dpf, cy - s, cx, cy - s - 4.dpf, strokePaint)
        canvas.drawLine(cx - 3.dpf, cy + s * 0.55f, cx + 3.dpf, cy + s * 0.55f, strokePaint)
        canvas.restore()
        canvas.drawCircle(cx + half * 0.58f, cy + half * 0.58f, 5f.dpf, accentRingPaint)
        canvas.drawCircle(cx + half * 0.58f, cy + half * 0.58f, 3.5f.dpf, accentPaint)
    }

    // ===== 触摸事件 =====

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
        val lp = layoutParams as WindowManager.LayoutParams

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isHidden) {
                    isHidden = false
                    updateBubblePosition(wm, lp)
                    invalidate()
                }

                winX = lp.x.toFloat()
                winY = lp.y.toFloat()
                touchDownRawX = event.rawX; touchDownRawY = event.rawY
                touchDownTime = System.currentTimeMillis()
                isDragging = false; isLongPressed = false
                isPressed = true

                animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownRawX
                val dy = event.rawY - touchDownRawY
                val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (distance > TAP_THRESHOLD.dpf && !isDragging) {
                    isDragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                    animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }

                if (isDragging) {
                    lp.x = (winX + dx).toInt()
                    lp.y = (winY + dy).toInt()
                    safeUpdateViewLayout(wm, lp)
                }

                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                mainHandler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                if (!isDragging && !isLongPressed) {
                    isHidden = false
                    updateBubblePosition(wm, lp)
                    performClick()
                } else if (isDragging) {
                    snapToEdgeAndHide(wm, lp)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                mainHandler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                if (isDragging) snapToEdgeAndHide(wm, lp) else scheduleHide()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    // ===== 贴边 & 隐藏 =====

    private fun snapToEdgeAndHide(wm: android.view.WindowManager, lp: WindowManager.LayoutParams) {
        val bSize = (BUBBLE_SIZE * density).toInt()
        val maxX = screenW - bSize
        val maxY = screenH - bSize
        val safeMinY = (STATUS_SAFE * density).toInt()

        isSnappedLeft = lp.x + bSize / 2 < screenW / 2
        lp.x = if (isSnappedLeft) (EDGE_MARGIN * density).toInt() else maxX - (EDGE_MARGIN * density).toInt()
        lp.y = lp.y.coerceIn(safeMinY, maxY.coerceAtLeast(safeMinY))
        savePosition(lp)
        safeUpdateViewLayout(wm, lp)

        scheduleHide()
    }

    private fun updateBubblePosition(wm: android.view.WindowManager, lp: WindowManager.LayoutParams) {
        val bSize = (BUBBLE_SIZE * density).toInt()
        if (isHidden) {
            lp.x = if (isSnappedLeft) (EDGE_MARGIN * density).toInt()
                    else screenW - bSize - (EDGE_MARGIN * density).toInt()
            safeUpdateViewLayout(wm, lp)
        }
    }

    /** Clamp bubble position to valid screen bounds — prevents off-screen after rotation */
    private fun clampToScreenBounds(lp: WindowManager.LayoutParams) {
        val bSize = (BUBBLE_SIZE * density).toInt()
        val maxX = screenW - bSize - (EDGE_MARGIN * density).toInt()
        val maxY = screenH - bSize - (STATUS_SAFE * density).toInt()
        val minX = (EDGE_MARGIN * density).toInt()
        val minY = (STATUS_SAFE * density).toInt()
        lp.x = lp.x.coerceIn(minX, maxX.coerceAtLeast(minX))
        lp.y = lp.y.coerceIn(minY, maxY.coerceAtLeast(minY))
    }

    private fun savePosition(lp: WindowManager.LayoutParams) {
        PenSettings.saveBubblePosition(context, lp.x, lp.y, isSnappedLeft)
    }

    private fun scheduleHide() {
        mainHandler.removeCallbacksAndMessages(null)
        if (!autoHideEnabled) return
        mainHandler.postDelayed({
            if (!isHidden && !isDragging && isAttachedToWindow) {
                isHidden = true
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                    val lp = layoutParams as WindowManager.LayoutParams
                    updateBubblePosition(wm, lp)
                    invalidate()
                } catch (error: Exception) {
                    android.util.Log.w("FloatingBubble", "auto-hide ignored after window removal", error)
                }
            }
        }, autoHideDelayMs)
    }

    private fun safeUpdateViewLayout(wm: android.view.WindowManager, lp: WindowManager.LayoutParams) {
        try {
            lp.gravity = Gravity.TOP or Gravity.START
            layoutParams = lp
            wm.updateViewLayout(this, lp)
            invalidate()
        } catch (error: IllegalArgumentException) {
            android.util.Log.w("FloatingBubble", "window already removed", error)
        } catch (error: SecurityException) {
            android.util.Log.w("FloatingBubble", "overlay permission revoked", error)
        }
    }

    override fun onMeasure(wms: Int, hms: Int) {
        val size = (BUBBLE_SIZE * density).toInt()
        setMeasuredDimension(size, size)
    }

    private val Float.dpf: Float get() = this * density
}
