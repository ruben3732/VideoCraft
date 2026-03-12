package com.videocraft.editor.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.videocraft.editor.databinding.ActivityHomeBinding
import com.videocraft.editor.ui.editor.VideoEditorActivity
import com.videocraft.editor.utils.FileUtils
import com.videocraft.editor.utils.toast
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val recentVideosAdapter = RecentVideosAdapter { uri -> openEditor(uri) }

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            loadRecentVideos()
        } else {
            toast("Permissions required to access your media library")
        }
    }

    // ── Video picker ──────────────────────────────────────────────────────────

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { openEditor(it) }
    }

    // ── Image picker for project with image ───────────────────────────────────

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { openEditorWithImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        checkPermissions()
    }

    private fun setupViews() {
        // Recent videos grid
        binding.rvRecentVideos.apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 2)
            adapter = recentVideosAdapter
        }

        // New project — pick video
        binding.btnNewProject.setOnClickListener {
            videoPicker.launch("video/*")
        }

        // Import image
        binding.btnImportImage.setOnClickListener {
            imagePicker.launch("image/*")
        }

        // Start with blank project
        binding.btnBlankProject.setOnClickListener {
            val intent = Intent(this, VideoEditorActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            loadRecentVideos()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun loadRecentVideos() {
        lifecycleScope.launch {
            val videos = FileUtils.getDeviceVideos(this@HomeActivity).take(20)
            recentVideosAdapter.submitList(videos)
            binding.tvEmptyState.visibility = if (videos.isEmpty()) android.view.View.VISIBLE
                                              else android.view.View.GONE
        }
    }

    private fun openEditor(videoUri: Uri) {
        val intent = Intent(this, VideoEditorActivity::class.java).apply {
            putExtra(VideoEditorActivity.EXTRA_VIDEO_URI, videoUri.toString())
        }
        startActivity(intent)
    }

    private fun openEditorWithImage(imageUri: Uri) {
        val intent = Intent(this, VideoEditorActivity::class.java).apply {
            putExtra(VideoEditorActivity.EXTRA_IMAGE_URI, imageUri.toString())
        }
        startActivity(intent)
    }
}
