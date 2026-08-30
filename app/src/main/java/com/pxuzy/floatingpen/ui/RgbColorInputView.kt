package com.pxuzy.floatingpen

import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class RgbColorInputView(context: Context) : LinearLayout(context) {
    enum class Channel { RED, GREEN, BLUE }

    private val inputs = linkedMapOf<Channel, EditText>()
    private var onInputActivated: ((EditText) -> Unit)? = null
    private var synchronizing = false
    private var normalizingInput = false
    var fromUserInput: Boolean = false
        private set
    private val errorText = TextView(context).apply {
        tag = "rgb-error"
        textSize = 12f
        setTextColor(Color.parseColor("#FFB4AB"))
        visibility = GONE
    }

    var color: Int
        get() = parsedColor() ?: Color.BLACK
        set(value) {
            synchronizing = true
            inputs.getValue(Channel.RED).setText(Color.red(value).toString())
            inputs.getValue(Channel.GREEN).setText(Color.green(value).toString())
            inputs.getValue(Channel.BLUE).setText(Color.blue(value).toString())
            synchronizing = false
            fromUserInput = false
            clearError()
        }

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addChannel(Channel.RED, "R", "rgb-r")
            addChannel(Channel.GREEN, "G", "rgb-g")
            addChannel(Channel.BLUE, "B", "rgb-b")
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(errorText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 4.dp
        })
    }

    fun parsedColor(): Int? {
        val values = Channel.entries.map { channel ->
            inputs.getValue(channel).text.toString().toIntOrNull()?.takeIf { it in 0..255 }
        }
        if (values.any { it == null }) {
            errorText.text = "R、G、B 均需输入 0–255 的整数"
            errorText.visibility = VISIBLE
            return null
        }
        clearError()
        return Color.rgb(values[0]!!, values[1]!!, values[2]!!)
    }

    fun focusChannel(channel: Channel): EditText = inputs.getValue(channel).also { it.requestFocus() }

    fun input(channel: Channel): EditText = inputs.getValue(channel)

    fun setOnInputActivatedListener(listener: (EditText) -> Unit) {
        onInputActivated = listener
    }

    private fun LinearLayout.addChannel(channel: Channel, label: String, inputTag: String) {
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#B8C2CF"))
                gravity = Gravity.CENTER
            }, LayoutParams(24.dp, 48.dp))
            val input = EditText(context).apply {
                tag = inputTag
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(3))
                setSingleLine(true)
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#718096"))
                hint = "0"
                contentDescription = "$label 通道，0 到 255"
                setOnClickListener { onInputActivated?.invoke(this) }
                setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onInputActivated?.invoke(this) }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                        if (!synchronizing) fromUserInput = true
                    }
                    override fun afterTextChanged(text: Editable?) {
                        if (synchronizing || normalizingInput || text.isNullOrEmpty()) return
                        val value = text.toString().toIntOrNull() ?: return
                        if (value <= MAX_CHANNEL_VALUE) return
                        normalizingInput = true
                        setText(MAX_CHANNEL_VALUE.toString())
                        setSelection(length())
                        normalizingInput = false
                    }
                })
            }
            inputs[channel] = input
            addView(input, LayoutParams(0, 48.dp, 1f))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            if (channel != Channel.BLUE) marginEnd = 8.dp
        })
    }

    private fun clearError() {
        errorText.text = ""
        errorText.visibility = GONE
    }

    private companion object {
        const val MAX_CHANNEL_VALUE = 255
    }
}
