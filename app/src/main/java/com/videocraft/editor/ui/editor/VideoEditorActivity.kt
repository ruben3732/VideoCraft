package com.videocraft.editor.ui.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videocraft.editor.R
import com.videocraft.editor.data.model.*
import com.videocraft.editor.databinding.ActivityVideoEditorBinding
import com.videocraft.editor.ui.aiedit.AIEditActivity
import com.videocraft.editor.ui.caption.CaptionActivity
import com.videocraft.editor.ui.editor.panels.*
import com.videocraft.editor.ui.stock.StockMediaActivity
import com.videocraft.editor.utils.FileUtils
import com.videocraft.editor.utils.hide
import com.videocraft.editor.utils.show
import com.videocraft.editor.utils.toast
import com.videocraft.editor.utils.toTimeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VideoEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val REQUEST_CAPTIONS = 100
        const val REQUEST_STOCK_MEDIA = 101
    }

    private lateinit var binding: ActivityVideoEditorBinding
    private val viewModel: VideoEditorViewModel by viewModels()
    private var player: ExoPlayer? = null
    private var currentPanel: View? = null

    // ── Media pickers ─────────────────────────────────────────────────────────

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addVideoClip(it) } }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addImageOverlay(it) } }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addAudioTrack(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle initial media from HomeActivity
        intent.getStringExtra(EXTRA_VIDEO_URI)?.let {
            viewModel.addVideoClip(Uri.parse(it))
        }
        intent.getStringExtra(EXTRA_IMAGE_URI)?.let {
            viewModel.addImageClip(Uri.parse(it))
        }

        initPlayer()
        setupToolbar()
        setupBottomToolbar()
        setupTimeline()
        observeViewModel()
    }

    // ── Player ────────────────────────────────────────────────────────────────

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        updatePlayerControls()
                    }
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    viewModel.seekTo(newPosition.positionMs)
                    binding.timelineView.setPlayheadPosition(newPosition.positionMs)
                    updateTimeDisplay()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    binding.btnPlay.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
            })
        }

        // Update position continuously
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                player?.let { p ->
                    if (p.isPlaying) {
                        val pos = p.currentPosition
                        viewModel.seekTo(pos)
                        binding.timelineView.setPlayheadPosition(pos)
                        updateTimeDisplay()
                    }
                }
                delay(16)
            }
        }
    }

    private fun updatePlayerControls() {
        val duration = player?.duration ?: 0L
        binding.seekBarTimeline.max = duration.toInt()
        binding.tvTotalDuration.text = duration.toTimeString()
    }

    private fun updateTimeDisplay() {
        val pos = player?.currentPosition ?: 0L
        binding.tvCurrentTime.text = pos.toTimeString()
        binding.seekBarTimeline.progress = pos.toInt()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { onBackPressed() }
        binding.btnExport.setOnClickListener { exportVideo() }
        binding.btnUndo.setOnClickListener { /* TODO: undo stack */ }
    }

    // ── Bottom toolbar panels ─────────────────────────────────────────────────

    private fun setupBottomToolbar() {
        binding.btnPlay.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        binding.seekBarTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress.toLong())
                    viewModel.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { player?.pause() }
            override fun onStopTrackingTouch(sb: SeekBar?) { player?.play() }
        })

        // Tool tabs
        binding.tabSpeed.setOnClickListener { showPanel(PanelType.SPEED) }
        binding.tabAudio.setOnClickListener { showPanel(PanelType.AUDIO) }
        binding.tabText.setOnClickListener { showPanel(PanelType.TEXT) }
        binding.tabOverlay.setOnClickListener { showPanel(PanelType.OVERLAY) }
        binding.tabAspectRatio.setOnClickListener { showPanel(PanelType.ASPECT_RATIO) }
        binding.tabCaptions.setOnClickListener { openCaptionActivity() }
        binding.tabStock.setOnClickListener { openStockMediaActivity() }
        binding.tabAI.setOnClickListener { openAIEditActivity() }
        binding.tabKeyframe.setOnClickListener { showPanel(PanelType.KEYFRAME) }
        binding.tabVolume.setOnClickListener { showPanel(PanelType.VOLUME) }

        // Add media
        binding.btnAddVideo.setOnClickListener { videoPicker.launch("video/*") }
        binding.btnAddImage.setOnClickListener { imagePicker.launch("image/*") }
        binding.btnAddAudio.setOnClickListener { audioPicker.launch("audio/*") }
    }

    private enum class PanelType {
        SPEED, AUDIO, TEXT, OVERLAY, ASPECT_RATIO, KEYFRAME, VOLUME
    }

    private fun showPanel(type: PanelType) {
        // Remove existing panel
        currentPanel?.let {
            binding.panelContainer.removeView(it)
            currentPanel = null
        }

        val panel = when (type) {
            PanelType.SPEED -> SpeedControlPanel(this, viewModel)
            PanelType.AUDIO -> AudioPanel(this, viewModel) { audioPicker.launch("audio/*") }
            PanelType.TEXT -> TextPanel(this, viewModel)
            PanelType.OVERLAY -> OverlayPanel(this, viewModel) {
                imagePicker.launch("image/*")
            }
            PanelType.ASPECT_RATIO -> AspectRatioPanel(this, viewModel)
            PanelType.KEYFRAME -> KeyframePanel(this, viewModel)
            PanelType.VOLUME -> VolumePanel(this, viewModel)
        }

        binding.panelContainer.addView(panel)
        currentPanel = panel
    }

    // ── Timeline ──────────────────────────────────────────────────────────────

    private fun setupTimeline() {
        binding.timelineView.setOnSeekListener { posMs ->
            player?.seekTo(posMs)
            viewModel.seekTo(posMs)
        }
        binding.timelineView.setOnClipClickListener { clipId ->
            viewModel.selectClip(clipId)
            showPanel(PanelType.SPEED)
        }
        binding.timelineView.setOnClipLongClickListener { clipId ->
            showClipContextMenu(clipId)
        }
    }

    private fun showClipContextMenu(clipId: String) {
        AlertDialog.Builder(this)
            .setTitle("Clip Options")
            .setItems(arrayOf("Split at playhead", "Delete", "Mute / Unmute")) { _, which ->
                when (which) {
                    0 -> viewModel.splitClip(clipId, player?.currentPosition ?: 0L)
                    1 -> viewModel.deleteClip(clipId)
                    2 -> {
                        val clip = viewModel.project.value?.videoTracks?.find { it.id == clipId }
                        clip?.let { viewModel.muteClip(clipId, !it.isMuted) }
                    }
                }
            }
            .show()
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.project.observe(this) { project ->
            rebuildPlayerPlaylist(project)
            binding.timelineView.setProject(project)
        }

        viewModel.exportState.observe(this) { state ->
            when (state) {
                is ExportState.Idle -> binding.exportProgress.hide()
                is ExportState.Progress -> {
                    binding.exportProgress.show()
                    binding.exportProgressBar.progress = (state.percent * 100).toInt()
                    binding.tvExportPercent.text = "${(state.percent * 100).toInt()}%"
                }
                is ExportState.Success -> {
                    binding.exportProgress.hide()
                    showExportSuccessDialog(state.outputFile)
                    viewModel.resetExportState()
                }
                is ExportState.Error -> {
                    binding.exportProgress.hide()
                    toast(state.message)
                    viewModel.resetExportState()
                }
            }
        }

        viewModel.aspectRatio.observe(this) { ratio ->
            applyAspectRatioToPlayer(ratio)
        }
    }

    private fun rebuildPlayerPlaylist(project: VideoProject) {
        if (project.videoTracks.isEmpty()) return
        val items = project.videoTracks.map { clip ->
            MediaItem.fromUri(clip.uri)
        }
        player?.apply {
            setMediaItems(items)
            prepare()
        }
    }

    private fun applyAspectRatioToPlayer(ratio: AspectRatio) {
        val params = binding.playerView.layoutParams
        val displayWidth = resources.displayMetrics.widthPixels
        params.width = displayWidth
        params.height = when (ratio) {
            AspectRatio.RATIO_9_16 -> (displayWidth * 16 / 9)
            AspectRatio.RATIO_16_9 -> (displayWidth * 9 / 16)
            AspectRatio.RATIO_1_1 -> displayWidth
            AspectRatio.RATIO_4_5 -> (displayWidth * 5 / 4)
            AspectRatio.RATIO_4_3 -> (displayWidth * 3 / 4)
            else -> (displayWidth * 16 / 9)
        }.coerceAtMost(resources.displayMetrics.heightPixels / 2)
        binding.playerView.layoutParams = params
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private fun exportVideo() {
        if (viewModel.project.value?.videoTracks.isNullOrEmpty()) {
            toast("Add a video clip first")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Export Video")
            .setMessage("Export your video in full quality (1080p)?")
            .setPositiveButton("Export") { _, _ -> viewModel.exportVideo() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExportSuccessDialog(file: java.io.File) {
        AlertDialog.Builder(this)
            .setTitle("Export Complete!")
            .setMessage("Your video is ready. Save to gallery?")
            .setPositiveButton("Save to Gallery") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val savedUri = FileUtils.saveVideoToGallery(this@VideoEditorActivity, file)
                    if (savedUri != null) {
                        toast("Saved to gallery!", long = true)
                    }
                }
            }
            .setNeutralButton("Share") { _, _ ->
                val uri = FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Video"))
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun openCaptionActivity() {
        val project = viewModel.project.value ?: return
        if (project.videoTracks.isEmpty()) {
            toast("Add a video first to generate captions")
            return
        }
        val videoUri = project.videoTracks.firstOrNull()?.uri?.toString() ?: return
        val intent = Intent(this, CaptionActivity::class.java).apply {
            putExtra(CaptionActivity.EXTRA_VIDEO_URI, videoUri)
        }
        startActivityForResult(intent, REQUEST_CAPTIONS)
    }

    private fun openStockMediaActivity() {
        startActivityForResult(
            Intent(this, StockMediaActivity::class.java),
            REQUEST_STOCK_MEDIA
        )
    }

    private fun openAIEditActivity() {
        val project = viewModel.project.value ?: return
        if (project.videoTracks.isEmpty()) {
            toast("Add a video first")
            return
        }
        val videoUri = project.videoTracks.firstOrNull()?.uri?.toString() ?: return
        val intent = Intent(this, AIEditActivity::class.java).apply {
            putExtra(AIEditActivity.EXTRA_VIDEO_URI, videoUri)
        }
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CAPTIONS -> {
                if (resultCode == RESULT_OK) {
                    val captionsJson = data?.getStringExtra(CaptionActivity.RESULT_CAPTIONS_JSON)
                    // Deserialize and apply captions
                    // (handled in CaptionActivity with Gson)
                }
            }
            REQUEST_STOCK_MEDIA -> {
                if (resultCode == RESULT_OK) {
                    val mediaUrl = data?.getStringExtra(StockMediaActivity.RESULT_MEDIA_URL)
                    val mediaType = data?.getStringExtra(StockMediaActivity.RESULT_MEDIA_TYPE)
                    mediaUrl?.let { url ->
                        when (mediaType) {
                            "VIDEO" -> viewModel.addVideoClip(Uri.parse(url))
                            "PHOTO", "GIF" -> viewModel.addImageOverlay(Uri.parse(url))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
