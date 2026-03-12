package com.videocraft.editor.ui.caption

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.videocraft.editor.R
import com.videocraft.editor.data.model.Caption
import com.videocraft.editor.data.model.CaptionLanguage
import com.videocraft.editor.data.model.CaptionStyle
import com.videocraft.editor.databinding.ActivityCaptionBinding
import com.videocraft.editor.utils.toast

class CaptionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val RESULT_CAPTIONS_JSON = "result_captions_json"
    }

    private lateinit var binding: ActivityCaptionBinding
    private val viewModel: CaptionViewModel by viewModels()
    private lateinit var captionAdapter: CaptionAdapter
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)?.let { Uri.parse(it) }

        initPlayer(videoUri)
        setupViews(videoUri)
        observeViewModel()
    }

    private fun initPlayer(videoUri: Uri?) {
        if (videoUri == null) return
        player = ExoPlayer.Builder(this).build().also {
            binding.captionPlayerView.player = it
            it.setMediaItem(MediaItem.fromUri(videoUri))
            it.prepare()
        }
    }

    private fun setupViews(videoUri: Uri?) {
        binding.btnBack.setOnClickListener { finish() }

        // Language selector
        val languages = CaptionLanguage.values().map { it.name }
        binding.spinnerLanguage.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, languages).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerLanguage.setSelection(CaptionLanguage.HINGLISH.ordinal) // default Hinglish

        // Caption style chips
        setupStyleChips()
        setupFontChips()

        // Generate captions button
        binding.btnGenerateCaptions.setOnClickListener {
            if (videoUri == null) {
                toast("No video loaded")
                return@setOnClickListener
            }
            val language = CaptionLanguage.values()[binding.spinnerLanguage.selectedItemPosition]
            val style = getSelectedStyle()
            val font = getSelectedFont()
            viewModel.generateCaptions(videoUri, language, style, font)
        }

        // Save captions button
        binding.btnSaveCaptions.setOnClickListener {
            val captions = viewModel.captions.value ?: return@setOnClickListener
            val json = Gson().toJson(captions)
            val result = Intent().apply {
                putExtra(RESULT_CAPTIONS_JSON, json)
            }
            setResult(RESULT_OK, result)
            finish()
        }

        // RecyclerView for caption list
        captionAdapter = CaptionAdapter(
            onEdit = { caption ->
                showEditCaptionDialog(caption)
            },
            onDelete = { captionId ->
                viewModel.deleteCaption(captionId)
            },
            onCaptionClick = { caption ->
                player?.seekTo(caption.startTimeMs)
            }
        )
        binding.rvCaptions.apply {
            layoutManager = LinearLayoutManager(this@CaptionActivity)
            adapter = captionAdapter
        }
    }

    private fun setupStyleChips() {
        val styles = mapOf(
            "Standard" to CaptionStyle.STANDARD,
            "Bold" to CaptionStyle.BOLD_KARAOKE,
            "Word-by-Word" to CaptionStyle.WORD_BY_WORD,
            "Animated" to CaptionStyle.ANIMATED,
            "Highlight" to CaptionStyle.HIGHLIGHT,
            "Gradient" to CaptionStyle.GRADIENT,
            "Neon" to CaptionStyle.NEON
        )
        styles.forEach { (label, style) ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = label
                isCheckable = true
                tag = style
            }
            binding.chipGroupCaptionStyles.addView(chip)
        }
    }

    private fun setupFontChips() {
        com.videocraft.editor.data.model.AVAILABLE_FONTS.forEach { font ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = font.displayName
                isCheckable = true
                tag = font.id
            }
            binding.chipGroupCaptionFonts.addView(chip)
        }
    }

    private fun getSelectedStyle(): CaptionStyle {
        for (i in 0 until binding.chipGroupCaptionStyles.childCount) {
            val chip = binding.chipGroupCaptionStyles.getChildAt(i)
                    as? com.google.android.material.chip.Chip ?: continue
            if (chip.isChecked) return chip.tag as? CaptionStyle ?: CaptionStyle.STANDARD
        }
        return CaptionStyle.STANDARD
    }

    private fun getSelectedFont(): String {
        for (i in 0 until binding.chipGroupCaptionFonts.childCount) {
            val chip = binding.chipGroupCaptionFonts.getChildAt(i)
                    as? com.google.android.material.chip.Chip ?: continue
            if (chip.isChecked) return chip.tag as? String ?: "roboto_bold"
        }
        return "roboto_bold"
    }

    private fun showEditCaptionDialog(caption: Caption) {
        val input = android.widget.EditText(this).apply {
            setText(caption.text)
            setPadding(40, 20, 40, 20)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Caption")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val updated = caption.copy(text = input.text.toString(), isEdited = true)
                viewModel.updateCaption(updated)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.captions.observe(this) { captions ->
            captionAdapter.submitList(captions.toList())
            binding.tvCaptionCount.text = "${captions.size} captions generated"
            binding.btnSaveCaptions.isEnabled = captions.isNotEmpty()
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnGenerateCaptions.isEnabled = !loading
            binding.tvLoadingStatus.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.loadingStatus.observe(this) { status ->
            binding.tvLoadingStatus.text = status
        }

        viewModel.errorMessage.observe(this) { error ->
            if (error != null) {
                toast(error)
                viewModel.clearError()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
