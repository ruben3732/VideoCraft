package com.videocraft.editor.ui.aiedit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.videocraft.editor.data.model.AIEditSuggestion
import com.videocraft.editor.data.model.FillerWord
import com.videocraft.editor.data.model.SilenceSegment
import com.videocraft.editor.utils.AudioAnalyzer
import com.videocraft.editor.utils.CaptionProcessor
import com.videocraft.editor.utils.FFmpegUtils
import com.videocraft.editor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AIEditState {
    object Idle : AIEditState()
    data class Analyzing(val message: String) : AIEditState()
    data class Complete(val outputUri: Uri?) : AIEditState()
    data class Error(val message: String) : AIEditState()
}

class AIEditViewModel(app: Application) : AndroidViewModel(app) {

    private val captionProcessor = CaptionProcessor(app)

    private val _analysisState = MutableLiveData<AIEditState>(AIEditState.Idle)
    val analysisState: LiveData<AIEditState> = _analysisState

    private val _suggestion = MutableLiveData<AIEditSuggestion?>(null)
    val suggestion: LiveData<AIEditSuggestion?> = _suggestion

    /**
     * Main AI edit pipeline:
     * 1. Detect silences
     * 2. (Optional) Detect filler words via STT
     * 3. Generate cut list
     * 4. Apply cuts via FFmpeg
     */
    fun analyzeAndEdit(
        videoUri: Uri,
        removeSilence: Boolean,
        removeFillers: Boolean,
        addBroll: Boolean,
        addCaptions: Boolean,
        addSoundFx: Boolean,
        silenceThreshold: Int = 300,
        minSilenceDuration: Long = 400L
    ) {
        viewModelScope.launch {
            try {
                _analysisState.value = AIEditState.Analyzing("Preparing video...")

                val videoPath = FileUtils.uriToPath(getApplication(), videoUri)
                    ?: throw Exception("Cannot access video file")

                // Step 1: Extract audio
                _analysisState.value = AIEditState.Analyzing("Extracting audio...")
                val audioFile = FileUtils.createTempVideoFile(getApplication(), "audio_extract")
                    .also { it.delete() }.let {
                        java.io.File(it.parent, it.nameWithoutExtension + ".wav")
                    }
                FFmpegUtils.extractAudio(videoPath, audioFile)

                // Step 2: Detect silences
                _analysisState.value = AIEditState.Analyzing("Detecting pauses...")
                val silences = if (removeSilence) {
                    AudioAnalyzer.detectSilence(
                        context = getApplication(),
                        videoPath = videoPath,
                        silenceThreshold = silenceThreshold,
                        minSilenceDurationMs = minSilenceDuration
                    )
                } else emptyList()

                // Step 3: Detect filler words (simplified — real impl uses STT)
                _analysisState.value = AIEditState.Analyzing("Scanning for filler words...")
                val fillerWords = if (removeFillers) {
                    detectFillerWordPositions(videoPath)
                } else emptyList()

                // Step 4: Build cut ranges
                val cutRanges = buildCutRanges(silences, fillerWords)

                // Step 5: Estimate saved time
                val totalSavedMs = cutRanges.sumOf { it.last - it.first }

                // Step 6: Generate B-roll keywords from video metadata/title
                val brollKeywords = generateBrollKeywords(videoPath)

                val suggestionResult = AIEditSuggestion(
                    silenceSegments = silences,
                    fillerWords = fillerWords,
                    suggestedCuts = cutRanges,
                    suggestedBrollKeywords = brollKeywords,
                    totalSavedMs = totalSavedMs
                )
                _suggestion.value = suggestionResult

                if (cutRanges.isEmpty()) {
                    _analysisState.value = AIEditState.Analyzing("No cuts needed — your video is clean! ✨")
                    kotlinx.coroutines.delay(2000)
                    _analysisState.value = AIEditState.Idle
                    return@launch
                }

                // Step 7: Apply cuts
                _analysisState.value = AIEditState.Analyzing("Applying ${cutRanges.size} smart cuts...")
                val outputFile = FileUtils.createTempVideoFile(getApplication(), "ai_edit_output")
                val success = FFmpegUtils.removeSegments(videoPath, outputFile, cutRanges)

                if (success) {
                    val savedUri = FileUtils.saveVideoToGallery(getApplication(), outputFile)
                    _analysisState.value = AIEditState.Complete(savedUri)
                } else {
                    _analysisState.value = AIEditState.Error("Failed to apply cuts")
                }

            } catch (e: Exception) {
                _analysisState.value = AIEditState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun detectFillerWordPositions(videoPath: String): List<FillerWord> =
        withContext(Dispatchers.IO) {
            // In a real implementation, this would use speech-to-text with timestamps
            // to find exact positions of filler words in the audio.
            // For demo, return empty list — implement with Whisper API for production.
            emptyList<FillerWord>()
        }

    private fun buildCutRanges(
        silences: List<SilenceSegment>,
        fillerWords: List<FillerWord>
    ): List<LongRange> {
        val ranges = mutableListOf<LongRange>()
        ranges.addAll(AudioAnalyzer.silencesToCutRanges(silences, bufferMs = 80))
        fillerWords.forEach { filler ->
            ranges.add(filler.startMs..filler.endMs)
        }
        return ranges.sortedBy { it.first }.mergeCutRanges()
    }

    private fun List<LongRange>.mergeCutRanges(): List<LongRange> {
        if (isEmpty()) return this
        val sorted = sortedBy { it.first }
        val merged = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val last = merged.last()
            if (current.first <= last.last + 200) {  // merge if within 200ms
                merged[merged.size - 1] = last.first..maxOf(last.last, current.last)
            } else {
                merged.add(current)
            }
        }
        return merged
    }

    private fun generateBrollKeywords(videoPath: String): List<String> {
        // In production: analyze audio/visual content to suggest relevant B-roll
        // For demo: return generic useful keywords
        return listOf("people talking", "office", "technology", "lifestyle", "nature", "city")
    }
}
