package com.videocraft.editor.ui.editor.panels

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.videocraft.editor.R
import com.videocraft.editor.ui.editor.VideoEditorViewModel
import com.videocraft.editor.utils.toSpeedLabel

class SpeedControlPanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val speedPresets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f)

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_speed_control, this, true)
        orientation = VERTICAL

        val tvCurrentSpeed = findViewById<TextView>(R.id.tv_current_speed)
        val seekbarSpeed = findViewById<SeekBar>(R.id.seekbar_speed)
        val chipGroupSpeed = findViewById<ChipGroup>(R.id.chip_group_speed)

        // Preset chips
        speedPresets.forEach { speed ->
            val chip = Chip(context).apply {
                text = speed.toSpeedLabel()
                isCheckable = true
                setOnClickListener {
                    setSpeed(speed, tvCurrentSpeed, seekbarSpeed)
                }
            }
            chipGroupSpeed.addView(chip)
        }

        // SeekBar: maps 0-100 to 0.1x - 4.0x
        seekbarSpeed.max = 100
        seekbarSpeed.progress = 23  // default 1x (approx)
        seekbarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = progressToSpeed(progress)
                    tvCurrentSpeed.text = speed.toSpeedLabel()
                    applySpeed(speed)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setSpeed(speed: Float, tvLabel: TextView, seekBar: SeekBar) {
        tvLabel.text = speed.toSpeedLabel()
        seekBar.progress = speedToProgress(speed)
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        val clipId = viewModel.selectedClipId.value ?: return
        viewModel.setClipSpeed(clipId, speed)
    }

    // Map 0-100 → 0.1x-4.0x with 1x at ~23
    private fun progressToSpeed(progress: Int): Float {
        return when {
            progress <= 23 -> 0.1f + (progress / 23f) * 0.9f
            else -> 1f + ((progress - 23) / 77f) * 3f
        }
    }

    private fun speedToProgress(speed: Float): Int {
        return when {
            speed <= 1f -> ((speed - 0.1f) / 0.9f * 23).toInt()
            else -> (23 + ((speed - 1f) / 3f) * 77).toInt()
        }
    }
}
