package com.pxuzy.floatingpen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.pxuzy.floatingpen.core.DrawingSession

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: FloatingBubbleView? = null
    private var drawingView: DrawingOverlayView? = null
    private var menuView: SelectionMenuView? = null
    private val drawingSession = DrawingSession()
    private val sessionAutoSaver = FloatInkSessionAutoSaver(
        session = drawingSession,
        save = { session, sessionId ->
            runCatching { FloatInkSessionStore.save(FloatInkStorage.sessionFile(this, sessionId), session, sessionId) }
                .onFailure { android.util.Log.e("OverlayService", "FloatInk 自动保存失败", it) }
        },
    )
    private var foregroundReady = false

    private var pendingTool = "pen"
    private var pendingColor = PenSettings.DEFAULT_COLOR_ARGB
    private var pendingWidthDp = PenSettings.DEFAULT_WIDTH_DP
    private var pendingArrowScale = PenSettings.DEFAULT_ARROW_SCALE
    private var lastTool = "pen"     // for long-press quick open
    private var lastColor = PenSettings.DEFAULT_COLOR_ARGB
    private var menuLock = false     // debounce: prevent rapid double-tap

    // Menu is always non-focusable; drawing temporarily becomes focusable for text input.
    private val fullscreenOverlayParams get() = LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )
    private val drawingOverlayParams = LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )
    private val bubbleOverlayParams get() = LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        try {
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮讲解笔")
                .setContentText("悬浮球已就绪，点击可任意界面标注")
                .setSubText("点击悬浮球直接绘制，进入后可切换工具和颜色")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            foregroundReady = true
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_KEY_SERVICE_RUNNING, true)
                .putLong(PREF_KEY_SERVICE_STARTED_AT, System.currentTimeMillis())
                .apply()
            isProcessRunning = true
        } catch (e: Exception) {
            foregroundReady = false
            isProcessRunning = false
            android.util.Log.e("OverlayService", "startForeground failed — cannot run as foreground service", e)
            // If startForeground fails, service can't function — stop gracefully
            getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit().putBoolean(PREF_KEY_SERVICE_RUNNING, false).apply()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY can cause stale view references after process death
        // Use START_NOT_STICKY to prevent silent restart with broken state
        if (!foregroundReady) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_REMOVE_BUBBLE -> removeBubble()
            ACTION_SHOW_DRAWING -> showDrawing()
            ACTION_HIDE_DRAWING -> hideDrawing()
            ACTION_LOAD_SESSION -> loadSession(intent.getStringExtra(EXTRA_SESSION_FILE))
            ACTION_SETTINGS_CHANGED -> refreshBubbleSettings()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        isProcessRunning = false
        hideDrawing()
        hideMenu()
        removeBubble()
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY_SERVICE_RUNNING, false).apply()
        sessionAutoSaver.close()
        drawingSession.clear()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubbleView?.keepInsideCurrentScreen()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val view = FloatingBubbleView(this,
            onTap = {
                loadDefaultPenSettings()
                showDrawing()
            },
            onLongPress = {
                loadDefaultPenSettings()
                showDrawing()
            }
        )
        if (safeAddView(view, bubbleOverlayParams)) {
            bubbleView = view
        } else {
            stop()
        }
    }

    private fun removeBubble() { safeRemoveView(bubbleView); bubbleView = null }

    private fun loadDefaultPenSettings() {
        val settings = PenSettings.load(this)
        pendingTool = settings.tool
        pendingColor = settings.color
        pendingWidthDp = settings.widthDp
        pendingArrowScale = settings.arrowScale
        lastTool = settings.tool
        lastColor = settings.color
    }
    private fun showMenu() {
        if (menuLock) return
        menuLock = true
        hideMenu()
        val view = SelectionMenuView(this,
            onStartDrawing = { tool, color ->
                pendingTool = tool; pendingColor = color; lastTool = tool; lastColor = color
                hideMenu(); showDrawing()
            },
            onDismiss = { hideMenu() }
        )
        if (!safeAddView(view, fullscreenOverlayParams)) {
            menuLock = false  // allow retry
        } else {
            menuView = view
        }
    }
    private fun hideMenu() { safeRemoveView(menuView); menuView = null; menuLock = false }

    private fun showDrawing() {
        hideDrawing()
        bubbleView?.let { bubble ->
            if (bubble.isAttachedToWindow) safeRemoveView(bubble)
        }
        val settings = PenSettings.load(this)
        pendingTool = settings.tool
        pendingColor = settings.color
        pendingWidthDp = settings.widthDp
        pendingArrowScale = settings.arrowScale
        lastTool = settings.tool
        lastColor = settings.color
        val view = DrawingOverlayView(
            context = this,
            toolId = pendingTool,
            styles = settings.toolStyles,
            arrowScale = pendingArrowScale,
            toolbarToolIds = settings.visibleToolbarToolIds(),
            onExit = { hideDrawing() },
            drawingSession = drawingSession,
            onSessionChanged = { sessionAutoSaver.markDirty() },
            onTextInputModeChanged = ::setDrawingTextInputMode,
            toolbarButtonSizeDp = settings.toolbarButtonSizeDp,
            onSelectionChanged = { tool, color ->
                pendingTool = tool
                pendingColor = color
                lastTool = tool
                lastColor = color
                PenSettings.saveTool(this, tool)
                PenSettings.saveToolStyle(this, tool, color, settings.styleFor(tool).widthDp)
            }
        )
        if (safeAddView(view, drawingOverlayParams)) {
            drawingView = view
        }
    }
    private fun loadSession(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val loaded = FloatInkSessionStore.loadWithBackup(java.io.File(path))
            drawingSession.replaceFrom(loaded.decoded.session)
            if (loaded.recoveredFromBackup) {
                android.util.Log.w("OverlayService", "历史 FloatInk 已从 .bak 恢复")
            }
            sessionAutoSaver.markDirty()
            showDrawing()
        }.onFailure {
            android.util.Log.e("OverlayService", "加载历史 FloatInk 失败", it)
        }
    }

    private fun hideDrawing() {
        setDrawingTextInputMode(false)
        safeRemoveView(drawingView)
        drawingView = null
        bubbleView?.let { bubble ->
            if (!bubble.isAttachedToWindow) safeAddView(bubble, bubbleOverlayParams)
            bubble.resumeAutoHide()
        }
    }

    private fun setDrawingTextInputMode(enabled: Boolean) {
        val view = drawingView ?: return
        val defaultFlags = LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN
        drawingOverlayParams.flags = if (enabled) {
            defaultFlags and LayoutParams.FLAG_NOT_FOCUSABLE.inv() and LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        } else {
            defaultFlags
        }
        @Suppress("DEPRECATION")
        run {
            drawingOverlayParams.softInputMode = if (enabled) {
                LayoutParams.SOFT_INPUT_ADJUST_RESIZE or LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            } else {
                LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
            }
        }
        runCatching { windowManager.updateViewLayout(view, drawingOverlayParams) }
            .onFailure { android.util.Log.e("OverlayService", "update drawing input mode failed", it) }
    }

    private fun refreshBubbleSettings() {
        val settings = PenSettings.load(this)
        bubbleView?.applySettings(settings)
        drawingView?.applyExternalSettings(settings)
    }

    private fun stop() {
        hideDrawing(); hideMenu(); removeBubble()
        // Clear running state so MainActivity shows correct UI
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY_SERVICE_RUNNING, false).apply()
        sessionAutoSaver.close()
        drawingSession.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Safe window operations — prevent crashes from race conditions
    private fun safeAddView(view: android.view.View, params: LayoutParams): Boolean {
        return try {
            windowManager.addView(view, params)
            true
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "addView failed", e)
            false
        }
    }

    private fun safeRemoveView(view: android.view.View?) {
        if (view == null) return
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // View already removed — expected after process restart or race
            android.util.Log.w("OverlayService", "removeView: already removed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "悬浮讲解笔-悬浮球服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "用于保持悬浮球后台运行，点击可进入标注模式"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        @Volatile
        private var isProcessRunning = false

        fun isRunningInProcess(): Boolean = isProcessRunning

        const val PREF_NAME = "floating_pen_prefs"
        const val PREF_KEY_SERVICE_RUNNING = "service_running"
        const val PREF_KEY_SERVICE_STARTED_AT = "service_started_at"
        private const val CHANNEL_ID = "floating_pen_overlay"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW_BUBBLE = "com.pxuzy.floatingpen.SHOW_BUBBLE"
        const val ACTION_REMOVE_BUBBLE = "com.pxuzy.floatingpen.REMOVE_BUBBLE"
        const val ACTION_SHOW_DRAWING = "com.pxuzy.floatingpen.SHOW_DRAWING"
        const val ACTION_HIDE_DRAWING = "com.pxuzy.floatingpen.HIDE_DRAWING"
        const val ACTION_LOAD_SESSION = "com.pxuzy.floatingpen.LOAD_SESSION"
        const val ACTION_SETTINGS_CHANGED = "com.pxuzy.floatingpen.SETTINGS_CHANGED"
        const val ACTION_STOP = "com.pxuzy.floatingpen.STOP"
        const val EXTRA_SESSION_FILE = "extra_session_file"
    }
}
