package com.videocraft.editor.ui.editor.panels

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.videocraft.editor.R
import com.videocraft.editor.ui.editor.VideoEditorViewModel

class AudioPanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    private val onAddAudio: (() -> Unit)? = null,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_audio, this, true)
        orientation = VERTICAL

        val seekbarVolume = findViewById<SeekBar>(R.id.seekbar_volume)
        val tvVolumeLevel = findViewById<TextView>(R.id.tv_volume_level)
        val switchMute = findViewById<SwitchMaterial>(R.id.switch_mute)
        val btnAddAudio = findViewById<android.widget.Button>(R.id.btn_add_audio_track)
        val seekbarFadeIn = findViewById<SeekBar>(R.id.seekbar_fade_in)
        val seekbarFadeOut = findViewById<SeekBar>(R.id.seekbar_fade_out)

        seekbarVolume.max = 200  // 0-200% volume
        seekbarVolume.progress = 100
        seekbarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val vol = progress / 100f
                tvVolumeLevel.text = "${progress}%"
                if (fromUser) {
                    viewModel.selectedClipId.value?.let { id ->
                        viewModel.setClipVolume(id, vol)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        switchMute.setOnCheckedChangeListener { _, isChecked ->
            viewModel.selectedClipId.value?.let { id ->
                viewModel.muteClip(id, isChecked)
            }
        }

        btnAddAudio.setOnClickListener { onAddAudio?.invoke() }
    }
}

class VolumePanel @JvmOverloads constructor(
    context: Context,
    private val viewModel: VideoEditorViewModel,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_volume, this, true)
        orientation = VERTICAL

        val seekbarVideoVol = findViewById<SeekBar>(R.id.seekbar_video_volume)
        val seekbarBgMusicVol = findViewById<SeekBar>(R.id.seekbar_bg_music_volume)
        val tvVideoVol = findViewById<TextView>(R.id.tv_video_volume_label)
        val tvBgVol = findViewById<TextView>(R.id.tv_bg_music_volume_label)

        // Video volume
        seekbarVideoVol.max = 100
        seekbarVideoVol.progress = 100
        seekbarVideoVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVideoVol.text = "Video: ${progress}%"
                if (fromUser) {
                    viewModel.selectedClipId.value?.let { id ->
                        viewModel.setClipVolume(id, progress / 100f)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // BG music volume
        seekbarBgMusicVol.max = 100
        seekbarBgMusicVol.progress = 80
        seekbarBgMusicVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBgVol.text = "Music: ${progress}%"
                // Apply to all audio tracks
                val project = viewModel.project.value ?: return
                project.audioTracks.forEach { track ->
                    track.volume = progress / 100f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}
