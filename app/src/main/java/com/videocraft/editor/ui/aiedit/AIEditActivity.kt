package com.videocraft.editor.ui.aiedit

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.videocraft.editor.R
import com.videocraft.editor.databinding.ActivityAiEditBinding
import com.videocraft.editor.utils.toast

class AIEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
    }

    private lateinit var binding: ActivityAiEditBinding
    private val viewModel: AIEditViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)?.let { Uri.parse(it) }

        setupViews(videoUri)
        observeViewModel()
    }

    private fun setupViews(videoUri: Uri?) {
        binding.btnBack.setOnClickListener { finish() }

        // Main AI Edit button — 1-click magic
        binding.btnAiEdit.setOnClickListener {
            if (videoUri == null) {
                toast("No video loaded")
                return@setOnClickListener
            }
            showAIEditConfirmDialog(videoUri)
        }

        // Individual options
        binding.switchRemoveSilence.isChecked = true
        binding.switchRemoveFillers.isChecked = true
        binding.switchAddBroll.isChecked = false
        binding.switchAddCaptions.isChecked = false
        binding.switchAddSoundFx.isChecked = false

        binding.btnApplySettings.setOnClickListener {
            if (videoUri == null) return@setOnClickListener
            viewModel.analyzeAndEdit(
                videoUri = videoUri,
                removeSilence = binding.switchRemoveSilence.isChecked,
                removeFillers = binding.switchRemoveFillers.isChecked,
                addBroll = binding.switchAddBroll.isChecked,
                addCaptions = binding.switchAddCaptions.isChecked,
                addSoundFx = binding.switchAddSoundFx.isChecked,
                silenceThreshold = binding.sliderSilenceThreshold.value.toInt(),
                minSilenceDuration = (binding.sliderMinPause.value * 1000).toLong()
            )
        }
    }

    private fun showAIEditConfirmDialog(videoUri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("AI Smart Edit")
            .setMessage(
                "AI will automatically:\n\n" +
                "✂️ Remove pauses & silence\n" +
                "🗑️ Cut filler words (um, uh, basically...)\n" +
                "🔥 Tighten the pace\n\n" +
                "This may take a few minutes. Continue?"
            )
            .setPositiveButton("Let's Go!") { _, _ ->
                viewModel.analyzeAndEdit(
                    videoUri = videoUri,
                    removeSilence = true,
                    removeFillers = true,
                    addBroll = binding.switchAddBroll.isChecked,
                    addCaptions = binding.switchAddCaptions.isChecked,
                    addSoundFx = binding.switchAddSoundFx.isChecked,
                    silenceThreshold = 300,
                    minSilenceDuration = 400L
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.analysisState.observe(this) { state ->
            when (state) {
                is AIEditState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "Ready to analyze"
                    binding.btnAiEdit.isEnabled = true
                    binding.btnApplySettings.isEnabled = true
                }
                is AIEditState.Analyzing -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvStatus.text = state.message
                    binding.btnAiEdit.isEnabled = false
                    binding.btnApplySettings.isEnabled = false
                }
                is AIEditState.Complete -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnAiEdit.isEnabled = true
                    binding.btnApplySettings.isEnabled = true
                    showAnalysisResults(state)
                }
                is AIEditState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "Error: ${state.message}"
                    binding.btnAiEdit.isEnabled = true
                    binding.btnApplySettings.isEnabled = true
                    toast(state.message)
                }
            }
        }

        viewModel.suggestion.observe(this) { suggestion ->
            suggestion ?: return@observe

            val savedSecs = suggestion.totalSavedMs / 1000
            binding.tvSavedTime.text = "⏱️ Will save ~${savedSecs}s"
            binding.tvSilenceCount.text = "🔇 ${suggestion.silenceSegments.size} pauses detected"
            binding.tvFillerCount.text = "🗣️ ${suggestion.fillerWords.size} filler words detected"

            if (suggestion.suggestedBrollKeywords.isNotEmpty()) {
                binding.tvBrollKeywords.text = "📸 B-roll ideas: ${suggestion.suggestedBrollKeywords.take(5).joinToString(", ")}"
                binding.tvBrollKeywords.visibility = View.VISIBLE
            }
        }
    }
}
