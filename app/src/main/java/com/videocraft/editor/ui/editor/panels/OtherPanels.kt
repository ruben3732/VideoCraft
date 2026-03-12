package com.videocraft.editor.ui.editor.panels

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.videocraft.editor.R
import com.videocraft.editor.data.model.AspectRatio
import com.videocraft.editor.data.model.ImageOverlay
import com.videocraft.editor.data.model.Keyframe
import com.videocraft.editor.ui.editor.VideoEditorViewModel

// ─── Overlay Panel ────────────────────────────────────────────────────────────

class OverlayPanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    private val onPickImage: (() -> Unit)? = null,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_overlay, this, true)
        orientation = VERTICAL

        val btnPickImage = findViewById<MaterialButton>(R.id.btn_pick_overlay_image)
        val seekbarOpacity = findViewById<SeekBar>(R.id.seekbar_opacity)
        val seekbarScale = findViewById<SeekBar>(R.id.seekbar_overlay_scale)
        val seekbarRotation = findViewById<SeekBar>(R.id.seekbar_overlay_rotation)
        val tvOpacity = findViewById<TextView>(R.id.tv_opacity_label)
        val tvScale = findViewById<TextView>(R.id.tv_scale_label)

        btnPickImage.setOnClickListener { onPickImage?.invoke() }

        seekbarOpacity.max = 100
        seekbarOpacity.progress = 100
        seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacity.text = "Opacity: ${progress}%"
                if (fromUser) updateCurrentOverlay { it.copy(opacity = progress / 100f) }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekbarScale.max = 100
        seekbarScale.progress = 33  // 1.0x
        seekbarScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.1f + (progress / 100f) * 2.9f
                tvScale.text = "Scale: ${String.format("%.1fx", scale)}"
                if (fromUser) updateCurrentOverlay { it.copy(scale = scale) }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekbarRotation.max = 360
        seekbarRotation.progress = 180  // 0 degrees
        seekbarRotation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val degrees = progress - 180f
                if (fromUser) updateCurrentOverlay { it.copy(rotation = degrees) }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateCurrentOverlay(transform: (ImageOverlay) -> ImageOverlay) {
        val id = viewModel.selectedOverlayId.value ?: return
        val overlay = viewModel.project.value?.imageOverlays?.find { it.id == id } ?: return
        viewModel.updateImageOverlay(transform(overlay))
    }
}

// ─── Aspect Ratio Panel ───────────────────────────────────────────────────────

class AspectRatioPanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_aspect_ratio, this, true)
        orientation = VERTICAL

        val ratioButtons = listOf(
            Pair(R.id.btn_ratio_9_16, AspectRatio.RATIO_9_16),
            Pair(R.id.btn_ratio_16_9, AspectRatio.RATIO_16_9),
            Pair(R.id.btn_ratio_1_1, AspectRatio.RATIO_1_1),
            Pair(R.id.btn_ratio_4_5, AspectRatio.RATIO_4_5),
            Pair(R.id.btn_ratio_4_3, AspectRatio.RATIO_4_3),
            Pair(R.id.btn_ratio_21_9, AspectRatio.RATIO_21_9)
        )

        ratioButtons.forEach { (viewId, ratio) ->
            try {
                val btn = findViewById<View>(viewId)
                btn?.setOnClickListener {
                    viewModel.setAspectRatio(ratio)
                    highlightSelected(ratioButtons.map { it.first }, viewId)
                }
            } catch (_: Exception) {}
        }
    }

    private fun highlightSelected(allIds: List<Int>, selectedId: Int) {
        allIds.forEach { id ->
            try {
                findViewById<View>(id)?.isSelected = (id == selectedId)
            } catch (_: Exception) {}
        }
    }
}

// ─── Keyframe Panel ───────────────────────────────────────────────────────────

class KeyframePanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_keyframe, this, true)
        orientation = VERTICAL

        val btnAddKeyframe = findViewById<MaterialButton>(R.id.btn_add_keyframe)
        val tvKeyframeTime = findViewById<TextView>(R.id.tv_keyframe_time)
        val switchKeyframeX = findViewById<Switch>(R.id.switch_kf_x)
        val switchKeyframeY = findViewById<Switch>(R.id.switch_kf_y)
        val switchKeyframeScale = findViewById<Switch>(R.id.switch_kf_scale)
        val switchKeyframeOpacity = findViewById<Switch>(R.id.switch_kf_opacity)

        viewModel.currentPositionMs.observeForever { posMs ->
            tvKeyframeTime.text = "At: ${formatTime(posMs)}"
        }

        btnAddKeyframe.setOnClickListener {
            val posMs = viewModel.currentPositionMs.value ?: return@setOnClickListener
            val overlayId = viewModel.selectedOverlayId.value ?: return@setOnClickListener

            val keyframe = Keyframe(
                timeMs = posMs,
                x = if (switchKeyframeX.isChecked) 0.5f else null,
                y = if (switchKeyframeY.isChecked) 0.5f else null,
                scale = if (switchKeyframeScale.isChecked) 1f else null,
                opacity = if (switchKeyframeOpacity.isChecked) 1f else null
            )

            // Check if it's a text or image overlay
            val project = viewModel.project.value ?: return@setOnClickListener
            if (project.textOverlays.any { it.id == overlayId }) {
                viewModel.addKeyframeToTextOverlay(overlayId, keyframe)
            } else {
                viewModel.addKeyframeToImageOverlay(overlayId, keyframe)
            }
            Toast.makeText(context, "Keyframe added at ${formatTime(posMs)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        return "${m}:${String.format("%02d", s % 60)}.${(ms % 1000) / 10}"
    }
}
