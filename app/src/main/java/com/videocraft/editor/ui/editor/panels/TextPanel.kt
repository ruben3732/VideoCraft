package com.videocraft.editor.ui.editor.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.videocraft.editor.R
import com.videocraft.editor.data.model.*
import com.videocraft.editor.ui.editor.VideoEditorViewModel

/**
 * Panel for adding/editing text overlays with font, color, style, and emoji options.
 */
class TextPanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var currentOverlay: TextOverlay? = null

    private val popularEmojis = listOf(
        "🔥", "💥", "✨", "🎉", "💯", "🚀", "❤️", "😍",
        "🤣", "😂", "👏", "🎵", "💪", "⭐", "🌟", "💫",
        "🏆", "👑", "🎯", "💎", "🌈", "⚡", "🦋", "🍀"
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_text, this, true)
        orientation = VERTICAL

        val etText = findViewById<TextInputEditText>(R.id.et_text_input)
        val chipGroupFonts = findViewById<ChipGroup>(R.id.chip_group_fonts)
        val chipGroupStyles = findViewById<ChipGroup>(R.id.chip_group_text_styles)
        val chipGroupEmojis = findViewById<ChipGroup>(R.id.chip_group_emojis)
        val btnAddText = findViewById<MaterialButton>(R.id.btn_add_text)
        val btnBold = findViewById<ToggleButton>(R.id.btn_bold)
        val btnItalic = findViewById<ToggleButton>(R.id.btn_italic)
        val seekbarFontSize = findViewById<SeekBar>(R.id.seekbar_font_size)
        val tvFontSizeLabel = findViewById<TextView>(R.id.tv_font_size)
        val btnColorWhite = findViewById<View>(R.id.btn_color_white)
        val btnColorBlack = findViewById<View>(R.id.btn_color_black)
        val btnColorYellow = findViewById<View>(R.id.btn_color_yellow)
        val btnColorRed = findViewById<View>(R.id.btn_color_red)
        val btnColorBlue = findViewById<View>(R.id.btn_color_blue)
        val btnColorGreen = findViewById<View>(R.id.btn_color_green)

        // Font chips
        AVAILABLE_FONTS.forEach { fontInfo ->
            val chip = Chip(context).apply {
                text = fontInfo.displayName
                isCheckable = true
                setOnClickListener {
                    currentOverlay?.let { overlay ->
                        val updated = overlay.copy(fontFamily = fontInfo.id)
                        currentOverlay = updated
                        viewModel.updateTextOverlay(updated)
                    }
                }
            }
            chipGroupFonts.addView(chip)
        }

        // Style chips
        TextStyle.values().forEach { style ->
            val chip = Chip(context).apply {
                text = style.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                isCheckable = true
                setOnClickListener {
                    currentOverlay?.let { overlay ->
                        val updated = overlay.copy(style = style)
                        currentOverlay = updated
                        viewModel.updateTextOverlay(updated)
                    }
                }
            }
            chipGroupStyles.addView(chip)
        }

        // Emoji chips
        popularEmojis.forEach { emoji ->
            val chip = Chip(context).apply {
                text = emoji
                setOnClickListener {
                    etText.text?.append(emoji)
                }
            }
            chipGroupEmojis.addView(chip)
        }

        // Font size
        seekbarFontSize.max = 100
        seekbarFontSize.progress = 30  // 40sp default
        seekbarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 20f + progress * 2f  // 20-220sp range
                tvFontSizeLabel.text = "${size.toInt()}sp"
                if (fromUser) currentOverlay?.let { o ->
                    val updated = o.copy(fontSize = size)
                    currentOverlay = updated
                    viewModel.updateTextOverlay(updated)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Bold / Italic
        btnBold.setOnCheckedChangeListener { _, isChecked ->
            currentOverlay?.let { o ->
                val updated = o.copy(isBold = isChecked)
                currentOverlay = updated
                viewModel.updateTextOverlay(updated)
            }
        }
        btnItalic.setOnCheckedChangeListener { _, isChecked ->
            currentOverlay?.let { o ->
                val updated = o.copy(isItalic = isChecked)
                currentOverlay = updated
                viewModel.updateTextOverlay(updated)
            }
        }

        // Color buttons
        fun setColor(color: Int) {
            currentOverlay?.let { o ->
                val updated = o.copy(color = color)
                currentOverlay = updated
                viewModel.updateTextOverlay(updated)
            }
        }
        btnColorWhite.setOnClickListener { setColor(Color.WHITE) }
        btnColorBlack.setOnClickListener { setColor(Color.BLACK) }
        btnColorYellow.setOnClickListener { setColor(Color.YELLOW) }
        btnColorRed.setOnClickListener { setColor(Color.RED) }
        btnColorBlue.setOnClickListener { setColor(Color.BLUE) }
        btnColorGreen.setOnClickListener { setColor(Color.GREEN) }

        // Add text button
        btnAddText.setOnClickListener {
            val text = etText.text?.toString()?.ifBlank { "Your Text" } ?: "Your Text"
            viewModel.addTextOverlay(text)
            // Track the new overlay
            currentOverlay = viewModel.project.value?.textOverlays?.lastOrNull()
        }

        // Text watcher to update live
        etText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentOverlay?.let { o ->
                    val updated = o.copy(text = s?.toString() ?: "")
                    currentOverlay = updated
                    viewModel.updateTextOverlay(updated)
                }
            }
        })

        // Observe selected overlay
        viewModel.selectedOverlayId.observeForever { id ->
            currentOverlay = viewModel.project.value?.textOverlays?.find { it.id == id }
            currentOverlay?.let { o ->
                etText.setText(o.text)
                btnBold.isChecked = o.isBold
                btnItalic.isChecked = o.isItalic
            }
        }
    }
}
