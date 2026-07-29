package com.pxuzy.floatingpen

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val updateManager by lazy { AppUpdateManager(this) }
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE &&
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == AppUpdateManager.savedDownloadId(this@MainActivity)
            ) {
                if (!updateManager.installCompletedDownload(AppUpdateManager.savedDownloadId(this@MainActivity))) {
                    Toast.makeText(this@MainActivity, "更新下载失败，请稍后重试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private lateinit var pageContainer: FrameLayout
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var actionBtn: Button
    private lateinit var widthLabel: TextView
    private lateinit var strokePreview: View
    private lateinit var arrowScaleLabel: TextView
    private lateinit var arrowScaleSeek: SeekBar
    private lateinit var arrowPreview: View
    private val navButtons = mutableMapOf<String, TextView>()
    private val toolButtons = mutableMapOf<String, TextView>()
    private val colorButtons = mutableListOf<View>()
    private var bubbleOpacityPreview: View? = null
    private var currentPage = "home"
    private var selectedTool = PenSettings.DEFAULT_TOOL
    private var selectedGlobalColor = PenSettings.DEFAULT_COLOR_ARGB
    private var selectedGlobalWidthDp = PenSettings.DEFAULT_WIDTH_DP
    private var selectedColor = PenSettings.DEFAULT_COLOR_ARGB
    private var selectedWidthDp = PenSettings.DEFAULT_WIDTH_DP
    private var selectedArrowScale = PenSettings.DEFAULT_ARROW_SCALE
    private val colorManageModes = mutableMapOf<String, Boolean>()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onResume() }
    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onResume() }
    private val historyFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            val temp = java.io.File(cacheDir, "import-${System.currentTimeMillis()}.floatink")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取文件" }
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            FloatInkHistoryRepository(this).import(temp)
            temp.delete()
            showPage("settings")
            Toast.makeText(this, "历史会话导入成功", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(this, "导入失败：${error.localizedMessage ?: "文件无效"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PenSettings.load(this).also {
            selectedTool = it.tool
            selectedGlobalColor = it.globalColor
            selectedGlobalWidthDp = it.globalWidthDp
            selectedColor = it.color
            selectedWidthDp = it.widthDp
            selectedArrowScale = it.arrowScale
        }
        setContentView(buildUi())
        showPage("home")
        ContextCompat.registerReceiver(this, downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver)
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FloatInkTheme.background)
        }
        pageContainer = FrameLayout(this)
        root.addView(pageContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNavigation(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 64.dp))
        return root
    }

    private fun buildBottomNavigation(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(10.dp, 8.dp, 10.dp, 8.dp)
        background = roundedBackground(FloatInkTheme.surface, 0f)
        listOf("home" to "首页", "pen" to "画笔", "settings" to "设置").forEach { (id, label) ->
            addView(TextView(this@MainActivity).apply {
                tag = "nav-$id"
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                minHeight = 48.dp
                setPadding(8.dp, 0, 8.dp, 0)
                contentDescription = label
                setOnClickListener { showPage(id) }
                navButtons[id] = this
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun showPage(page: String) {
        currentPage = page
        pageContainer.removeAllViews()
        pageContainer.addView(
            when (page) {
                "pen" -> buildPenPage()
                "settings" -> buildSettingsPage()
                else -> buildHomePage()
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        updateNavigation()
    }

    private fun buildPage(title: String, subtitle: String, content: LinearLayout.() -> Unit): View {
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 20.dp, 16.dp, 28.dp)
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 26f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle; textSize = 14f; setTextColor(FloatInkTheme.textSecondary)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 4.dp; bottomMargin = 20.dp
                }
            })
            content()
        }
        scroll.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun buildHomePage(): View = buildPage("悬浮讲解笔", "点击启动悬浮球，在任意 App 上方快速绘制") {
        val servicePanel = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp); background = panelBackground()
        }
        statusIcon = ImageView(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginEnd = 12.dp }
        }
        statusText = TextView(this@MainActivity).apply {
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        actionBtn = Button(this@MainActivity).apply {
            textSize = 13f; minHeight = 48.dp; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
            contentDescription = "启动或停止悬浮球"
            background = roundedBackground(selectedColor, 8f)
            setOnClickListener { onActionClick() }
            layoutParams = LinearLayout.LayoutParams(132.dp, 48.dp)
        }
        servicePanel.addView(statusIcon); servicePanel.addView(statusText); servicePanel.addView(actionBtn)
        addView(servicePanel)

        addView(sectionTitle("当前画笔"))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp); background = panelBackground()
            addView(View(this@MainActivity).apply { background = colorCircle(selectedColor) }, LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 12.dp })
            addView(TextView(this@MainActivity).apply {
                text = "${DrawingElement.toolNames[selectedTool] ?: "画笔"}  ·  ${selectedWidthDp.toInt()} dp"
                textSize = 15f; setTextColor(Color.WHITE)
            })
        })
        updateUi()
    }

    private fun buildPenPage(): View {
        toolButtons.clear()
        colorButtons.clear()
        val settings = PenSettings.load(this)
        selectedGlobalColor = settings.globalColor
        selectedGlobalWidthDp = settings.globalWidthDp
        selectedColor = settings.color
        selectedWidthDp = settings.widthDp
        return buildPage("画笔", "先设置默认绘制，再微调当前工具") {
            addView(sectionTitle("默认绘制").apply { tag = "default-drawing-section" })
            addView(buildColorGrid("global") { color ->
                selectedGlobalColor = color
                PenSettings.saveGlobalStyle(this@MainActivity, color, selectedGlobalWidthDp)
                refreshPaletteSelection("global", color)
            })
            addView(buildWidthControl("global", selectedGlobalWidthDp) { width ->
                selectedGlobalWidthDp = width
                PenSettings.saveGlobalStyle(this@MainActivity, selectedGlobalColor, width)
            })
            addView(Button(this@MainActivity).apply {
                tag = "apply-global-style"
                text = "应用到全部工具"
                isAllCaps = false; minHeight = 48.dp
                setTextColor(Color.BLACK)
                background = roundedBackground(Color.WHITE, 7f)
                setOnClickListener {
                    PenSettings.applyGlobalStyleToAllTools(this@MainActivity)
                    notifyOverlaySettingsChanged()
                    showPage("pen")
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dp).apply { topMargin = 8.dp })

            addView(sectionTitle("当前工具"))
            val expandableTools = DrawingElement.tools.drop(4)
            addView(buildToolSelectorRow(DrawingElement.tools.take(4), "primary-tools"))
            if (expandableTools.isNotEmpty()) {
                addView(sectionTitle("更多工具").apply { tag = "more-tools-section" })
                addView(buildToolSelectorRow(expandableTools, "more-tools"))
            }
            addView(buildColorGrid("tool") { color ->
                selectedColor = color
                PenSettings.saveToolStyle(this@MainActivity, selectedTool, color, selectedWidthDp)
                PenSettings.addRecentColor(this@MainActivity, color)
                notifyOverlaySettingsChanged()
                refreshPaletteSelection("tool", color)
                updateToolButtons()
                strokePreview.invalidate()
                if (::arrowScaleLabel.isInitialized) arrowScaleLabel.setTextColor(selectedColor)
                if (::arrowPreview.isInitialized) arrowPreview.invalidate()
            })
            addView(buildWidthControl("tool", selectedWidthDp) { width ->
                selectedWidthDp = width
                PenSettings.saveToolStyle(this@MainActivity, selectedTool, selectedColor, width)
                notifyOverlaySettingsChanged()
                strokePreview.setTag(R.id.tag_preview_width_dp, selectedWidthDp)
                strokePreview.invalidate()
                if (selectedTool == "arrow" && ::arrowPreview.isInitialized) arrowPreview.invalidate()
            })
            strokePreview = ToolPreviewView(this@MainActivity, selectedTool).apply {
                tag = "tool-preview"; background = panelBackground()
                setTag(R.id.tag_preview_tool, selectedTool)
                setTag(R.id.tag_preview_width_dp, selectedWidthDp)
            }
            addView(strokePreview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 72.dp).apply { topMargin = 4.dp })
            if (selectedTool == "arrow") addArrowSettings(this)
            updateToolButtons()
        }
    }

    private fun buildToolSelectorRow(tools: List<ToolDef>, tagValue: String): View {
        val row = LinearLayout(this).apply {
            tag = tagValue
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 2.dp)
        }
        tools.forEach { tool ->
            row.addView(TextView(this).apply {
                tag = "setting-tool:${tool.id}"
                text = tool.label
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(Color.WHITE)
                minHeight = 48.dp
                background = roundedBackground(FloatInkTheme.surfaceRaised, FloatInkTheme.PANEL_RADIUS_DP)
                setOnClickListener { selectTool(tool.id) }
                toolButtons[tool.id] = this
            }, LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginEnd = 6.dp })
        }
        return row
    }

    private fun buildColorGrid(prefix: String, onSelect: (Int) -> Unit): View {
        val selected = if (prefix == "global") selectedGlobalColor else selectedColor
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        }
        val manageMode = colorManageModes[prefix] == true
        PenSettings.allColors(this).forEachIndexed { index, color ->
            val isCustom = !PenSettings.isDefaultColor(color)
            val slot = FrameLayout(this).apply {
                tag = "$prefix-color:$index"
                contentDescription = "${if (prefix == "global") "全局" else "当前工具"}颜色 ${index + 1}"
                setOnClickListener { onSelect(color) }
            }
            val swatch = View(this).apply { background = colorButtonBackground(color, color == selected) }
            if (prefix == "tool") colorButtons += swatch
            slot.addView(swatch, FrameLayout.LayoutParams(34.dp, 34.dp, Gravity.CENTER))
            if (manageMode && isCustom) {
                slot.addView(TextView(this).apply {
                    tag = "$prefix-delete-color:$color"
                    text = "×"
                    gravity = Gravity.CENTER
                    textSize = 14f
                    contentDescription = "删除自定义颜色 #%08X".format(java.util.Locale.US, color)
                    setTextColor(Color.WHITE)
                    background = roundedBackground(Color.parseColor("#35404C"), 10f)
                    setOnClickListener { showDeleteColorDialog(color, prefix) }
                }, FrameLayout.LayoutParams(20.dp, 20.dp, Gravity.TOP or Gravity.END))
            }
            row.addView(slot, LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginEnd = 6.dp })
        }
        row.addView(TextView(this).apply {
            tag = "$prefix-manage-colors"
            text = if (manageMode) "完成" else "管理"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = if (manageMode) "完成管理颜色" else "管理自定义颜色"
            background = roundedBackground(Color.parseColor("#26303B"), 7f)
            setOnClickListener {
                colorManageModes[prefix] = !manageMode
                showPage(currentPage)
            }
        }, LinearLayout.LayoutParams(52.dp, 48.dp).apply { marginStart = 2.dp })
        row.addView(TextView(this).apply {
            tag = "$prefix-add-color"
            text = "+"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = roundedBackground(Color.parseColor("#26303B"), 7f)
            contentDescription = "增加颜色"
            setOnClickListener { showRgbColorDialog(prefix, onSelect) }
        }, LinearLayout.LayoutParams(48.dp, 48.dp).apply { marginStart = 2.dp })
        return HorizontalScrollView(this).apply {
            tag = "$prefix-color-grid"
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 6.dp, 0, 6.dp)
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 56.dp))
        }
    }

    private fun refreshPaletteSelection(prefix: String, selectedColor: Int) {
        val palette = pageContainer.findViewWithTag<ViewGroup>("$prefix-color-grid") ?: return
        val row = palette.getChildAt(0) as? ViewGroup ?: return
        PenSettings.allColors(this).forEachIndexed { index, color ->
            val slot = row.getChildAt(index) as? ViewGroup ?: return@forEachIndexed
            slot.getChildAt(0)?.background = colorButtonBackground(color, color == selectedColor)
        }
    }
    private fun showRgbColorDialog(prefix: String, onSelect: (Int) -> Unit) {
        val initialColor = if (prefix == "global") selectedGlobalColor else selectedColor
        val picker = HsvColorPickerView(this).apply {
            tag = "custom-color-picker"
            color = initialColor
        }
        val preview = TextView(this).apply {
            tag = "custom-color-preview"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
        }
        val alphaSeek = SeekBar(this).apply {
            tag = "custom-color-alpha"
            max = 255
            progress = Color.alpha(initialColor)
        }
        val hexInput = EditText(this).apply {
            tag = "custom-color-hex"
            hint = "HEX：#RRGGBB 或 #AARRGGBB"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7F8A99"))
            setText("#%08X".format(java.util.Locale.US, initialColor))
        }
        val rgbInput = RgbColorInputView(this).apply {
            tag = "custom-color-rgb"
            color = initialColor
        }
        var lastManualInput: View? = null
        hexInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) lastManualInput = hexInput }
        rgbInput.setOnInputActivatedListener { view ->
            lastManualInput = rgbInput
            view.post {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        val pickerHeight = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 180.dp else 236.dp
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 4.dp, 20.dp, 0)
            addView(picker, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, pickerHeight))
            addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40.dp).apply { topMargin = 8.dp })
            addView(TextView(this@MainActivity).apply {
                text = "透明度"; textSize = 12f; setTextColor(Color.parseColor("#AFC2D8"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 28.dp).apply { topMargin = 6.dp })
            addView(alphaSeek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dp))
            addView(hexInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp).apply { topMargin = 4.dp })
            addView(rgbInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp).apply { topMargin = 4.dp })
        }
        fun render(color: Int) {
            val opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
            preview.text = "当前颜色  #%08X".format(java.util.Locale.US, color)
            preview.background = roundedBackground(opaque, 8f)
            if (hexInput.hasFocus().not()) hexInput.setText("#%08X".format(java.util.Locale.US, color))
            if (RgbColorInputView.Channel.entries.none { rgbInput.input(it).hasFocus() }) rgbInput.color = color
        }
        picker.setOnColorChangedListener { color, _ -> render(Color.argb(alphaSeek.progress, Color.red(color), Color.green(color), Color.blue(color))) }
        alphaSeek.setOnSeekBarChangeListener(userSeek { value ->
            picker.alpha = value
            render(picker.color)
        })
        render(initialColor)

        val scrollContent = ScrollView(this).apply {
            tag = "custom-color-scroll"
            isFillViewport = false
            clipToPadding = false
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("自定义颜色")
            .setView(scrollContent)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val explicit = if (rgbInput.fromUserInput) {
                    rgbInput.parsedColor()
                } else {
                    when (lastManualInput) {
                        hexInput -> PenSettings.parseRgb(hexInput.text.toString())
                        rgbInput -> rgbInput.parsedColor()
                        else -> picker.color
                    }
                }
                if (explicit == null) {
                    Toast.makeText(this, "请输入有效的 HEX 或 RGB 颜色", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val savedColor = Color.argb(alphaSeek.progress, Color.red(explicit), Color.green(explicit), Color.blue(explicit))
                PenSettings.addCustomColor(this, savedColor)
                onSelect(savedColor)
                dialog.dismiss()
                showPage(currentPage)
            }
        }
        dialog.show()
    }

    private fun showDeleteColorDialog(color: Int, prefix: String) {
        AlertDialog.Builder(this)
            .setTitle("删除自定义颜色？")
            .setMessage("删除后不会影响已经画出的笔迹。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                PenSettings.deleteCustomColorAndReplaceStyles(this, color)
                val fallback = PenSettings.DEFAULT_PALETTE.first()
                if (selectedColor == color) {
                    selectedColor = fallback
                    selectedWidthDp = PenSettings.load(this).styleFor(selectedTool).widthDp
                }
                if (selectedGlobalColor == color) {
                    selectedGlobalColor = fallback
                }
                notifyOverlaySettingsChanged()
                showPage(currentPage)
            }
            .show()
    }

    private fun buildWidthControl(prefix: String, initial: Float, onChange: (Float) -> Unit): View {
        lateinit var label: TextView
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val header = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(sectionTitle(if (prefix == "global") "全局线宽" else "工具线宽"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            label = TextView(this@MainActivity).apply {
                tag = "$prefix-width-label"; text = "${initial.toInt()} dp"; textSize = 13f
                setTextColor(Color.parseColor("#AEB8C6"))
            }
            header.addView(label)
            addView(header)
            addView(SeekBar(this@MainActivity).apply {
                tag = "$prefix-width"; max = PenSettings.MAX_WIDTH_DP - PenSettings.MIN_WIDTH_DP
                progress = initial.toInt() - PenSettings.MIN_WIDTH_DP
                setOnSeekBarChangeListener(userSeek { value ->
                    val width = (value + PenSettings.MIN_WIDTH_DP).toFloat()
                    label.text = "${width.toInt()} dp"
                    onChange(width)
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp))
        }
    }

    private fun addArrowSettings(parent: LinearLayout) {
        val arrowHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        arrowHeader.addView(sectionTitle("箭头比例"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        arrowScaleLabel = TextView(this).apply {
            tag = "setting-arrow-scale-label"; text = formatArrowScale(selectedArrowScale); textSize = 13f
            setTextColor(selectedColor); setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            background = roundedBackground(Color.parseColor("#20262F"), 7f)
            contentDescription = "箭头比例，点击精确输入"
            setOnClickListener { showArrowScaleInput() }
        }
        arrowHeader.addView(arrowScaleLabel)
        parent.addView(arrowHeader)
        arrowScaleSeek = SeekBar(this).apply {
            tag = "setting-arrow-scale"; max = ARROW_SCALE_STEPS
            progress = arrowScaleToProgress(selectedArrowScale)
            setOnSeekBarChangeListener(userSeek { value -> applyArrowScale(progressToArrowScale(value), false) })
        }
        parent.addView(arrowScaleSeek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp))
        arrowPreview = ToolPreviewView(this, "arrow").apply { tag = "setting-arrow-preview"; background = panelBackground() }
        parent.addView(arrowPreview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 72.dp).apply { topMargin = 4.dp })
    }

    private inner class ToolPreviewView(context: Context, private val tool: String) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private val path = Path()
        override fun onDraw(canvas: Canvas) {
            val left = 24.dp.toFloat(); val right = width - 24.dp.toFloat(); val center = height / 2f
            paint.color = selectedColor; paint.strokeWidth = selectedWidthDp.dp; paint.style = Paint.Style.STROKE
            setTag(R.id.tag_preview_tool, tool)
            setTag(R.id.tag_preview_width_dp, selectedWidthDp)
            when (tool) {
                "pen" -> { path.rewind(); path.moveTo(left, center); path.cubicTo(width * .35f, center - 16.dp, width * .65f, center + 16.dp, right, center); canvas.drawPath(path, paint) }
                "rect" -> canvas.drawRect(left, 16.dp.toFloat(), right, height - 16.dp.toFloat(), paint)
                "circle" -> canvas.drawCircle(width / 2f, center, minOf((right - left) / 2f, height / 2f - 12.dp), paint)
            else -> {
                val head = if (tool == "arrow") DrawingOverlayView.resolveArrowHeadLengthDp(selectedWidthDp, selectedArrowScale).dp else 0f
                canvas.drawLine(left, center, if (tool == "arrow") right - head else right, center, paint)
                if (tool == "arrow") {
                    path.rewind(); path.moveTo(right, center); path.lineTo(right - head, center - head * .45f); path.lineTo(right - head, center + head * .45f); path.close()
                    paint.style = Paint.Style.FILL; canvas.drawPath(path, paint)
                }
            }
        }
    }
}

    private fun buildSettingsPage(): View = buildPage("设置", "调整悬浮按钮的显示行为") {
        val settings = PenSettings.load(this@MainActivity)
        addView(sectionTitle("悬浮按钮").apply { tag = "settings-bubble-section" })
        addView(settingHeader("透明度", "${(settings.bubbleOpacity * 100).toInt()}%", "setting-opacity-label"))
        bubbleOpacityPreview = TextView(this@MainActivity).apply {
            tag = "setting-opacity-preview"
            text = "  悬浮按钮预览  "
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            alpha = settings.bubbleOpacity
            background = roundedBackground(Color.BLACK, 8f)
        }
        addView(bubbleOpacityPreview, LinearLayout.LayoutParams(132.dp, 40.dp).apply { bottomMargin = 4.dp })
        addView(SeekBar(this@MainActivity).apply {
            tag = "setting-bubble-opacity"; max = 65; progress = ((settings.bubbleOpacity - 0.35f) * 100).toInt()
            setOnSeekBarChangeListener(userSeek { value ->
                val opacity = 0.35f + value / 100f
                PenSettings.saveBubbleOpacity(this@MainActivity, opacity)
                notifyOverlaySettingsChanged()
                pageContainer.findViewWithTag<TextView>("setting-opacity-label")?.text = "${(opacity * 100).toInt()}%"
                bubbleOpacityPreview?.alpha = opacity
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp))

        lateinit var delaySeek: SeekBar
        addView(sectionTitle("自动隐藏").apply { tag = "settings-auto-hide-section" })
        addView(CheckBox(this@MainActivity).apply {
            tag = "setting-auto-hide"; text = "自动隐藏到屏幕边缘"; textSize = 15f; setTextColor(Color.WHITE); minHeight = 48.dp
            isChecked = settings.autoHide
            setOnCheckedChangeListener { _, checked ->
                PenSettings.saveAutoHide(this@MainActivity, checked)
                notifyOverlaySettingsChanged()
                delaySeek.isEnabled = checked
            }
        })
        addView(settingHeader("自动隐藏延迟", "${settings.autoHideDelayMs / 1000f} 秒", "setting-delay-label"))
        delaySeek = SeekBar(this@MainActivity).apply {
            tag = "setting-auto-hide-delay"; max = 9
            progress = ((settings.autoHideDelayMs - 500L) / 500L).toInt()
            isEnabled = settings.autoHide
            setOnSeekBarChangeListener(userSeek { value ->
                val delay = 500L + value * 500L
                PenSettings.saveAutoHideDelay(this@MainActivity, delay)
                notifyOverlaySettingsChanged()
                pageContainer.findViewWithTag<TextView>("setting-delay-label")?.text = "${delay / 1000f} 秒"
            })
        }
        addView(delaySeek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp))
        addView(TextView(this@MainActivity).apply {
            tag = "settings-live-copy"
            text = "修改会立即应用到当前悬浮球"; textSize = 12f; setTextColor(Color.parseColor("#7F8A99"))
        })
        addView(buildHistorySection())
        addView(sectionTitle("软件更新").apply { tag = "settings-update-section" })
        addView(TextView(this@MainActivity).apply {
            tag = "settings-update-status"
            text = "当前版本：${BuildConfig.VERSION_NAME}"
            textSize = 12f
            setTextColor(Color.parseColor("#91A0B2"))
        })
        addView(Button(this@MainActivity).apply {
            tag = "settings-check-update"
            text = "检查远程更新"
            isAllCaps = false
            minHeight = 48.dp
            setOnClickListener { checkForUpdate() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp).apply { topMargin = 8.dp })
        addView(sectionTitle("悬浮工具栏").apply { tag = "toolbar-layout-section" })
        addView(TextView(this@MainActivity).apply {
            tag = "toolbar-layout-help"
            text = "长按拖动调整顺序，关闭开关隐藏工具；超过首屏数量的工具会显示在更多工具"
            textSize = 12f
            setTextColor(Color.parseColor("#91A0B2"))
        })
        val toolbarLayout = PenSettings.load(this@MainActivity)
        addView(ToolbarLayoutEditorView(
            this@MainActivity,
            toolbarLayout.toolbarOrder,
            toolbarLayout.toolbarEnabled,
        ) { order, enabled ->
            PenSettings.saveToolbarLayout(this@MainActivity, order, enabled)
            notifyOverlaySettingsChanged()
        })
    }

    private fun buildHistorySection(): View {
        val repository = FloatInkHistoryRepository(this)
        val section = LinearLayout(this).apply {
            tag = "history-section"
            orientation = LinearLayout.VERTICAL
        }
        section.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(FloatInkIconView(this@MainActivity, "history").apply {
                contentDescription = "历史画板"
            }, LinearLayout.LayoutParams(36.dp, 36.dp).apply { marginEnd = 8.dp })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    tag = "history-section-title"
                    text = "历史画板"
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
                addView(TextView(this@MainActivity).apply {
                    tag = "history-section-summary"
                    text = "打开最近会话，更多操作收在行尾菜单"
                    textSize = 12f
                    setTextColor(Color.parseColor("#91A0B2"))
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp))
        val list = LinearLayout(this).apply {
            tag = "history-list"
            orientation = LinearLayout.VERTICAL
        }
        fun refresh() {
            list.removeAllViews()
            val entries = repository.list()
            pageContainer.findViewWithTag<TextView>("history-section-summary")?.text =
                if (entries.isEmpty()) "暂无已保存会话" else "${entries.size} 个已保存会话"
            if (entries.isEmpty()) {
                list.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dp, 8.dp, 12.dp, 8.dp)
                    background = historyRowBackground()
                    addView(FloatInkIconView(this@MainActivity, "canvas").apply {
                        tag = "history-empty-icon"
                        setIconColor(Color.parseColor("#718096"))
                    }, LinearLayout.LayoutParams(40.dp, 48.dp).apply { marginEnd = 8.dp })
                    addView(TextView(this@MainActivity).apply {
                        tag = "history-empty"
                        text = "暂无历史画板\n完成绘制后会自动出现在这里"
                        textSize = 13f
                        setTextColor(Color.parseColor("#91A0B2"))
                        gravity = Gravity.CENTER_VERTICAL
                    }, LinearLayout.LayoutParams(0, 64.dp, 1f))
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8.dp
                })
            }
            entries.forEach { entry ->
                val row = LinearLayout(this).apply {
                    tag = "history:${entry.sessionId}"
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dp, 6.dp, 6.dp, 6.dp)
                    background = historyRowBackground()
                    setOnClickListener { openHistoryEntry(entry) }
                }
                row.addView(FloatInkIconView(this, "canvas").apply {
                    contentDescription = "历史画板"
                }, LinearLayout.LayoutParams(38.dp, 52.dp).apply { marginEnd = 8.dp })
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@MainActivity).apply {
                        text = entry.name
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        maxLines = 1
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = historyEntryMeta(entry)
                        textSize = 11f
                        setTextColor(Color.parseColor("#91A0B2"))
                        maxLines = 1
                    })
                }, LinearLayout.LayoutParams(0, 56.dp, 1f))
                row.addView(FloatInkIconView(this, "more").apply {
                    tag = "history-menu:${entry.sessionId}"
                    contentDescription = "${entry.name}更多操作"
                    setIconColor(Color.parseColor("#D2D8E0"))
                    setOnClickListener { showHistoryEntryMenu(repository, entry, ::refresh) }
                }, LinearLayout.LayoutParams(42.dp, 48.dp))
                list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 8.dp
                })
            }
        }
        section.addView(list)
        refresh()
        section.addView(LinearLayout(this).apply {
            tag = "history-actions"
            orientation = LinearLayout.HORIZONTAL
            addView(historyAction("history-import", "import", "导入会话") {
                historyFilePickerLauncher.launch("application/octet-stream")
            })
            addView(historyAction("history-trash", "trash", "回收站") {
                showHistoryTrashDialog(repository)
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp).apply { topMargin = 2.dp })
        return section
    }

    private fun openHistoryEntry(entry: FloatInkHistoryEntry) {
        if (!isOverlayServiceRunning()) {
            Toast.makeText(this, "请先启动悬浮服务", Toast.LENGTH_SHORT).show()
            return
        }
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_LOAD_SESSION
            putExtra(OverlayService.EXTRA_SESSION_FILE, entry.file.absolutePath)
        })
    }

    private fun showHistoryEntryMenu(
        repository: FloatInkHistoryRepository,
        entry: FloatInkHistoryEntry,
        refresh: () -> Unit,
    ) {
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(arrayOf("重命名", "复制", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        val input = EditText(this).apply { setText(entry.name); selectAll() }
                        AlertDialog.Builder(this)
                            .setTitle("重命名历史会话")
                            .setView(input)
                            .setNegativeButton("取消", null)
                            .setPositiveButton("保存") { _, _ -> repository.rename(entry.sessionId, input.text.toString()); refresh() }
                            .show()
                    }
                    1 -> { repository.copy(entry.sessionId); refresh() }
                    2 -> AlertDialog.Builder(this)
                        .setTitle("删除历史会话")
                        .setMessage("会话将移动到回收站。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ -> repository.delete(entry.sessionId); refresh() }
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun historyAction(tagValue: String, icon: String, label: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            tag = tagValue
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = label
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#171D25"))
                cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#34404E"))
            }
            addView(FloatInkIconView(this@MainActivity, icon).apply {
                setIconColor(Color.parseColor("#D2D8E0"))
            }, LinearLayout.LayoutParams(30.dp, 40.dp))
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#D2D8E0"))
                gravity = Gravity.CENTER_VERTICAL
            })
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply {
                marginEnd = if (tagValue == "history-import") 4.dp else 0
                marginStart = if (tagValue == "history-trash") 4.dp else 0
            }
            setOnClickListener { action() }
        }

    private fun historyRowBackground() = GradientDrawable().apply {
        setColor(Color.parseColor("#141A21"))
        cornerRadius = 10.dp.toFloat()
        setStroke(1.dp, Color.parseColor("#2C3541"))
    }

    private fun historyEntryMeta(entry: FloatInkHistoryEntry): String {
        val ageMs = (System.currentTimeMillis() - entry.modifiedAt).coerceAtLeast(0L)
        val whenText = when {
            ageMs < 60_000L -> "刚刚"
            ageMs < 3_600_000L -> "${ageMs / 60_000L} 分钟前"
            ageMs < 86_400_000L -> "${ageMs / 3_600_000L} 小时前"
            else -> "${ageMs / 86_400_000L} 天前"
        }
        val sizeText = if (entry.sizeBytes < 1024L) "${entry.sizeBytes} B" else "${entry.sizeBytes / 1024L} KB"
        return "$whenText · $sizeText"
    }

    private fun showHistoryTrashDialog(repository: FloatInkHistoryRepository) {
        val trash = repository.listTrash()
        if (trash.isEmpty()) {
            Toast.makeText(this, "回收站为空", Toast.LENGTH_SHORT).show()
            return
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 8.dp, 24.dp, 8.dp)
        }
        trash.forEach { entry ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = entry.name
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f)
                })
                addView(Button(this@MainActivity).apply {
                    text = "恢复"; isAllCaps = false
                    setOnClickListener { repository.restore(entry.sessionId); showPage("settings") }
                })
            })
        }
        AlertDialog.Builder(this)
            .setTitle("回收站")
            .setView(list)
            .setNegativeButton("关闭", null)
            .setNeutralButton("清空回收站") { _, _ ->
                repository.clearTrash()
                Toast.makeText(this, "回收站已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun notifyOverlaySettingsChanged() {
        if (!isOverlayServiceRunning()) return
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SETTINGS_CHANGED
        })
    }

    private fun checkForUpdate() {
        val status = pageContainer.findViewWithTag<TextView>("settings-update-status")
        val button = pageContainer.findViewWithTag<Button>("settings-check-update")
        status?.text = "正在检查 GitHub Releases…"
        button?.isEnabled = false
        updateManager.check { result ->
            button?.isEnabled = true
            result.onSuccess { update ->
                if (update == null) {
                    status?.text = "当前已是最新版本：${BuildConfig.VERSION_NAME}"
                    Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                } else {
                    status?.text = "发现新版本：${update.version}"
                    AlertDialog.Builder(this)
                        .setTitle("发现浮墨新版本")
                        .setMessage("${BuildConfig.VERSION_NAME} → ${update.version}\n将从 GitHub Releases 下载 APK，随后由系统确认安装。")
                        .setPositiveButton("下载更新") { _, _ ->
                            updateManager.downloadAndInstall(update)
                            status?.text = "正在下载 ${update.version}…"
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }.onFailure { error ->
                status?.text = "检查失败：${error.localizedMessage ?: "网络不可用"}"
                Toast.makeText(this, "检查更新失败，请确认网络连接", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun userSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun showArrowScaleInput() {
        val input = EditText(this).apply {
            id = android.R.id.edit
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatArrowScale(selectedArrowScale).removeSuffix("×"))
            selectAll()
            hint = "1.0–4.0"
        }
        AlertDialog.Builder(this)
            .setTitle("自定义箭头比例")
            .setMessage("输入 1.0 到 4.0 之间的比例")
            .setView(input)
            .setPositiveButton("应用") { _, _ ->
                val value = input.text.toString().trim().toFloatOrNull()
                if (value == null || value !in PenSettings.MIN_ARROW_SCALE..PenSettings.MAX_ARROW_SCALE) {
                    Toast.makeText(this, "请输入 1.0–4.0 之间的数值", Toast.LENGTH_SHORT).show()
                } else {
                    applyArrowScale(value, syncSeekBar = true)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyArrowScale(value: Float, syncSeekBar: Boolean) {
        val preciseValue = value.coerceIn(PenSettings.MIN_ARROW_SCALE, PenSettings.MAX_ARROW_SCALE)
        if (syncSeekBar && ::arrowScaleSeek.isInitialized) {
            arrowScaleSeek.progress = arrowScaleToProgress(preciseValue)
        }
        selectedArrowScale = preciseValue
        PenSettings.saveArrowScale(this, selectedArrowScale)
        notifyOverlaySettingsChanged()
        if (::arrowScaleLabel.isInitialized) arrowScaleLabel.text = formatArrowScale(selectedArrowScale)
        if (::arrowPreview.isInitialized) arrowPreview.invalidate()
    }

    private fun formatArrowScale(value: Float): String {
        val hundredths = kotlin.math.round(value * 100f).toInt()
        val text = if (hundredths % 10 == 0) "%.1f".format(java.util.Locale.US, value)
        else "%.2f".format(java.util.Locale.US, value)
        return "$text×"
    }

    private fun arrowScaleToProgress(value: Float): Int =
        kotlin.math.round(
            (value.coerceIn(PenSettings.MIN_ARROW_SCALE, PenSettings.MAX_ARROW_SCALE) - PenSettings.MIN_ARROW_SCALE) * 100f
        ).toInt().coerceIn(0, ARROW_SCALE_STEPS)

    private fun progressToArrowScale(progress: Int): Float =
        PenSettings.MIN_ARROW_SCALE + progress.coerceIn(0, ARROW_SCALE_STEPS) / 100f

    private fun settingHeader(title: String, value: String, valueTag: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(sectionTitle(title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@MainActivity).apply { tag = valueTag; text = value; textSize = 13f; setTextColor(Color.parseColor("#AEB8C6")) })
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(FloatInkTheme.textSecondary)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 20.dp; bottomMargin = 10.dp
        }
    }

    private fun selectTool(tool: String) {
        selectedTool = PenSettings.normalizeTool(tool)
        PenSettings.saveTool(this, selectedTool)
        val style = PenSettings.load(this).styleFor(selectedTool)
        selectedColor = style.color
        selectedWidthDp = style.widthDp
        showPage("pen")
    }

    private fun updateToolButtons() {
        toolButtons.forEach { (tool, button) ->
            button.background = roundedBackground(if (tool == selectedTool) selectedColor else Color.parseColor("#20262F"), 7f)
            button.setTextColor(if (tool == selectedTool && Color.luminance(selectedColor) > 0.65f) Color.BLACK else Color.WHITE)
        }
    }

    private fun selectColor(color: Int) {
        selectedColor = color; PenSettings.saveColor(this, color); PenSettings.addRecentColor(this, color)
        colorButtons.forEachIndexed { index, view ->
            val value = PenSettings.allColors(this)[index]
            view.background = colorButtonBackground(value, value == selectedColor)
        }
        updateToolButtons(); strokePreview.invalidate()
        if (::arrowScaleLabel.isInitialized) arrowScaleLabel.setTextColor(selectedColor)
        if (::arrowPreview.isInitialized) arrowPreview.invalidate()
        updateNavigation()
    }

    private fun updateNavigation() {
        navButtons.forEach { (page, button) ->
            val active = page == currentPage
            button.setTextColor(if (active) FloatInkTheme.textPrimary else FloatInkTheme.textSecondary)
            button.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            button.background = roundedBackground(
                if (active) FloatInkTheme.surfaceActive else Color.TRANSPARENT,
                FloatInkTheme.PANEL_RADIUS_DP
            )
        }
    }

    private fun colorButtonBackground(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
        setStroke(if (selected) 3.dp else 1.dp, if (selected) Color.WHITE else Color.parseColor("#44505E"))
    }
    private fun colorCircle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    private fun panelBackground() = GradientDrawable().apply {
        setColor(FloatInkTheme.surfaceRaised)
        cornerRadius = FloatInkTheme.PANEL_RADIUS_DP * resources.displayMetrics.density
        setStroke(1.dp, FloatInkTheme.border)
    }
    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius.dp }

    override fun onResume() {
        super.onResume()
        PenSettings.load(this).also {
            selectedTool = it.tool
            selectedGlobalColor = it.globalColor
            selectedGlobalWidthDp = it.globalWidthDp
            selectedColor = it.color
            selectedWidthDp = it.widthDp
            selectedArrowScale = it.arrowScale
        }
        if (::pageContainer.isInitialized) showPage(currentPage)
    }

    private fun onActionClick() {
        if (isOverlayServiceRunning()) { stopOverlayService(); return }
        when {
            !Settings.canDrawOverlays(this) -> requestOverlayPermission()
            !hasNotificationPermission() -> requestNotificationPermission()
            else -> startOverlayService()
        }
    }

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("需要在其他 App 上方显示透明画板。点击去授权后，请允许显示悬浮窗。")
            .setPositiveButton("去授权") { _, _ ->
                overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }.setNegativeButton("取消", null).show()
    }

    private fun requestNotificationPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要通知权限")
            .setMessage("后台悬浮服务需要通知权限保持稳定运行。")
            .setPositiveButton("去设置") { _, _ ->
                notificationSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
            }.setNegativeButton("跳过") { _, _ -> startOverlayService() }.show()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun isOverlayServiceRunning(): Boolean {
        val prefs = getSharedPreferences(OverlayService.PREF_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, false)) return false
        if (OverlayService.isRunningInProcess()) return true
        val startedAt = prefs.getLong(OverlayService.PREF_KEY_SERVICE_STARTED_AT, 0L)
        if (System.currentTimeMillis() - startedAt < SERVICE_START_GRACE_MS) return true
        prefs.edit().putBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, false).apply()
        return false
    }

    private fun updateUi() {
        val running = isOverlayServiceRunning()
        val permission = Settings.canDrawOverlays(this)
        when {
            permission && running -> {
                statusIcon.setImageResource(android.R.drawable.presence_online); statusIcon.setColorFilter(Color.parseColor("#4CAF50"))
                statusText.text = "悬浮画笔正在运行"; actionBtn.text = "停止"
            }
            permission -> {
                statusIcon.setImageResource(android.R.drawable.ic_dialog_info); statusIcon.setColorFilter(selectedColor)
                statusText.text = "权限已就绪"; actionBtn.text = "启动"
            }
            else -> {
                statusIcon.setImageResource(android.R.drawable.ic_dialog_alert); statusIcon.setColorFilter(Color.parseColor("#EF4444"))
                statusText.text = "需要悬浮窗权限"; actionBtn.text = "授权"
            }
        }
    }

    private fun startOverlayService() {
        getSharedPreferences(OverlayService.PREF_NAME, MODE_PRIVATE).edit()
            .putBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, true)
            .putLong(OverlayService.PREF_KEY_SERVICE_STARTED_AT, System.currentTimeMillis())
            .apply()
        try {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_SHOW_BUBBLE })
        } catch (error: Exception) {
            getSharedPreferences(OverlayService.PREF_NAME, MODE_PRIVATE).edit().putBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, false).apply()
            Toast.makeText(this, "启动失败：${error.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
        }
        updateUi()
    }

    private fun stopOverlayService() {
        getSharedPreferences(OverlayService.PREF_NAME, MODE_PRIVATE).edit().putBoolean(OverlayService.PREF_KEY_SERVICE_RUNNING, false).apply()
        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        updateUi()
    }

    companion object {
        private const val SERVICE_START_GRACE_MS = 3_000L
        private const val ARROW_SCALE_STEPS = 300
    }
}
