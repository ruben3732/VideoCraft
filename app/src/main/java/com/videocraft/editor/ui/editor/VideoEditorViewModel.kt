package com.videocraft.editor.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.videocraft.editor.data.model.*
import com.videocraft.editor.utils.FFmpegUtils
import com.videocraft.editor.utils.FileUtils
import com.videocraft.editor.utils.getVideoDurationMs
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class ExportState {
    object Idle : ExportState()
    data class Progress(val percent: Float) : ExportState()
    data class Success(val outputFile: File) : ExportState()
    data class Error(val message: String) : ExportState()
}

class VideoEditorViewModel(app: Application) : AndroidViewModel(app) {

    // ── Project state ─────────────────────────────────────────────────────────

    private val _project = MutableLiveData(VideoProject())
    val project: LiveData<VideoProject> = _project

    private val _currentPositionMs = MutableLiveData(0L)
    val currentPositionMs: LiveData<Long> = _currentPositionMs

    private val _selectedClipId = MutableLiveData<String?>(null)
    val selectedClipId: LiveData<String?> = _selectedClipId

    private val _selectedOverlayId = MutableLiveData<String?>(null)
    val selectedOverlayId: LiveData<String?> = _selectedOverlayId

    private val _exportState = MutableLiveData<ExportState>(ExportState.Idle)
    val exportState: LiveData<ExportState> = _exportState

    private val _aspectRatio = MutableLiveData(AspectRatio.RATIO_9_16)
    val aspectRatio: LiveData<AspectRatio> = _aspectRatio

    // ── Add media ─────────────────────────────────────────────────────────────

    fun addVideoClip(uri: Uri) {
        val durationMs = uri.getVideoDurationMs(getApplication())
        val project = _project.value ?: return
        val startTime = project.totalDurationMs

        val clip = VideoClip(
            uri = uri,
            startTimeMs = startTime,
            endTimeMs = startTime + durationMs,
            trimEndMs = durationMs
        )
        project.videoTracks.add(clip)
        project.recalcDuration()
        _project.value = project
    }

    fun addImageClip(uri: Uri, durationMs: Long = 3000L) {
        val project = _project.value ?: return
        val startTime = project.totalDurationMs
        // Images become a still frame video-clip internally
        val clip = VideoClip(
            uri = uri,
            startTimeMs = startTime,
            endTimeMs = startTime + durationMs
        )
        project.videoTracks.add(clip)
        project.recalcDuration()
        _project.value = project
    }

    fun addAudioTrack(uri: Uri) {
        val project = _project.value ?: return
        val clip = AudioClip(
            uri = uri,
            startTimeMs = _currentPositionMs.value ?: 0L,
            volume = 1f
        )
        project.audioTracks.add(clip)
        _project.value = project
    }

    // ── Video clip operations ─────────────────────────────────────────────────

    fun setClipSpeed(clipId: String, speed: Float) {
        updateClip(clipId) { it.copy(speed = speed.coerceIn(0.1f, 4f)) }
    }

    fun setClipVolume(clipId: String, volume: Float) {
        updateClip(clipId) { it.copy(volume = volume.coerceIn(0f, 2f)) }
    }

    fun muteClip(clipId: String, mute: Boolean) {
        updateClip(clipId) { it.copy(isMuted = mute) }
    }

    fun trimClip(clipId: String, trimStartMs: Long, trimEndMs: Long) {
        updateClip(clipId) { it.copy(trimStartMs = trimStartMs, trimEndMs = trimEndMs) }
        rebuildTimeline()
    }

    fun splitClip(clipId: String, atPositionMs: Long) {
        val project = _project.value ?: return
        val clipIndex = project.videoTracks.indexOfFirst { it.id == clipId }
        if (clipIndex < 0) return
        val original = project.videoTracks[clipIndex]
        val splitPoint = atPositionMs - original.startTimeMs + original.trimStartMs

        val firstHalf = original.copy(
            id = UUID.randomUUID().toString(),
            trimEndMs = splitPoint,
            endTimeMs = atPositionMs
        )
        val secondHalf = original.copy(
            id = UUID.randomUUID().toString(),
            trimStartMs = splitPoint,
            startTimeMs = atPositionMs
        )
        project.videoTracks[clipIndex] = firstHalf
        project.videoTracks.add(clipIndex + 1, secondHalf)
        _project.value = project
    }

    fun deleteClip(clipId: String) {
        val project = _project.value ?: return
        project.videoTracks.removeAll { it.id == clipId }
        project.recalcDuration()
        _project.value = project
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val project = _project.value ?: return
        val clip = project.videoTracks.removeAt(fromIndex)
        project.videoTracks.add(toIndex, clip)
        rebuildTimeline()
        _project.value = project
    }

    // ── Text overlays ─────────────────────────────────────────────────────────

    fun addTextOverlay(text: String = "Your Text Here") {
        val project = _project.value ?: return
        val overlay = TextOverlay(
            text = text,
            startTimeMs = _currentPositionMs.value ?: 0L,
            endTimeMs = (_currentPositionMs.value ?: 0L) + 3000L
        )
        project.textOverlays.add(overlay)
        _project.value = project
        _selectedOverlayId.value = overlay.id
    }

    fun updateTextOverlay(updated: TextOverlay) {
        val project = _project.value ?: return
        val index = project.textOverlays.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            project.textOverlays[index] = updated
            _project.value = project
        }
    }

    fun deleteTextOverlay(overlayId: String) {
        val project = _project.value ?: return
        project.textOverlays.removeAll { it.id == overlayId }
        _project.value = project
    }

    // ── Image overlays ────────────────────────────────────────────────────────

    fun addImageOverlay(uri: Uri) {
        val project = _project.value ?: return
        val overlay = ImageOverlay(
            uri = uri,
            startTimeMs = _currentPositionMs.value ?: 0L,
            endTimeMs = (_currentPositionMs.value ?: 0L) + 3000L
        )
        project.imageOverlays.add(overlay)
        _project.value = project
        _selectedOverlayId.value = overlay.id
    }

    fun updateImageOverlay(updated: ImageOverlay) {
        val project = _project.value ?: return
        val index = project.imageOverlays.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            project.imageOverlays[index] = updated
            _project.value = project
        }
    }

    fun deleteImageOverlay(overlayId: String) {
        val project = _project.value ?: return
        project.imageOverlays.removeAll { it.id == overlayId }
        _project.value = project
    }

    // ── Captions ──────────────────────────────────────────────────────────────

    fun setCaptions(captions: List<Caption>) {
        val project = _project.value ?: return
        project.captions.clear()
        project.captions.addAll(captions)
        _project.value = project
    }

    // ── Keyframes ────────────────────────────────────────────────────────────

    fun addKeyframeToTextOverlay(overlayId: String, keyframe: Keyframe) {
        val project = _project.value ?: return
        project.textOverlays.find { it.id == overlayId }?.let {
            it.keyframes.removeAll { kf -> kf.timeMs == keyframe.timeMs }
            it.keyframes.add(keyframe)
            it.keyframes.sortBy { kf -> kf.timeMs }
        }
        _project.value = project
    }

    fun addKeyframeToImageOverlay(overlayId: String, keyframe: Keyframe) {
        val project = _project.value ?: return
        project.imageOverlays.find { it.id == overlayId }?.let {
            it.keyframes.removeAll { kf -> kf.timeMs == keyframe.timeMs }
            it.keyframes.add(keyframe)
            it.keyframes.sortBy { kf -> kf.timeMs }
        }
        _project.value = project
    }

    // ── Aspect ratio ──────────────────────────────────────────────────────────

    fun setAspectRatio(ratio: AspectRatio) {
        val project = _project.value ?: return
        project.aspectRatio = ratio
        _aspectRatio.value = ratio
        _project.value = project
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
    }

    fun selectOverlay(overlayId: String?) {
        _selectedOverlayId.value = overlayId
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportVideo() {
        val project = _project.value ?: return
        viewModelScope.launch {
            _exportState.value = ExportState.Progress(0f)
            val outputFile = FileUtils.createTempVideoFile(getApplication(), "videocraft_export")
            val success = FFmpegUtils.exportProject(
                context = getApplication(),
                project = project,
                outputFile = outputFile,
                onProgress = { progress ->
                    _exportState.postValue(ExportState.Progress(progress))
                }
            )
            _exportState.value = if (success) ExportState.Success(outputFile)
                                  else ExportState.Error("Export failed. Please try again.")
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateClip(clipId: String, transform: (VideoClip) -> VideoClip) {
        val project = _project.value ?: return
        val index = project.videoTracks.indexOfFirst { it.id == clipId }
        if (index >= 0) {
            project.videoTracks[index] = transform(project.videoTracks[index])
            _project.value = project
        }
    }

    private fun rebuildTimeline() {
        val project = _project.value ?: return
        var cursor = 0L
        for (clip in project.videoTracks) {
            val duration = clip.trimEndMs - clip.trimStartMs
            clip.startTimeMs = cursor
            clip.endTimeMs = cursor + duration
            cursor += duration
        }
        project.totalDurationMs = cursor
        _project.value = project
    }
}
