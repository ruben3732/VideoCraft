package com.videocraft.editor.utils

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.videocraft.editor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object FFmpegUtils {

    /**
     * Export the full video project to a file.
     * Applies: speed, volume, overlays, captions, audio tracks, aspect ratio.
     */
    suspend fun exportProject(
        context: Context,
        project: VideoProject,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (project.videoTracks.isEmpty()) return@withContext false

        val outputDir = context.cacheDir
        val tempFiles = mutableListOf<File>()

        // Step 1: Process each video clip (trim + speed)
        val processedClips = mutableListOf<File>()
        for ((index, clip) in project.videoTracks.withIndex()) {
            val trimmed = File(outputDir, "clip_${index}_trimmed.mp4")
            val inputPath = FileUtils.uriToPath(context, clip.uri) ?: continue

            val success = processClip(
                inputPath = inputPath,
                outputFile = trimmed,
                trimStartMs = clip.trimStartMs,
                trimEndMs = if (clip.trimEndMs < 0) -1 else clip.trimEndMs,
                speed = clip.speed,
                volume = if (clip.isMuted) 0f else clip.volume
            )
            if (success) {
                processedClips.add(trimmed)
                tempFiles.add(trimmed)
            }
            onProgress((index + 1).toFloat() / project.videoTracks.size * 0.4f)
        }

        if (processedClips.isEmpty()) return@withContext false

        // Step 2: Concatenate clips
        val concatenated = File(outputDir, "concatenated.mp4")
        tempFiles.add(concatenated)
        val concatSuccess = if (processedClips.size == 1) {
            processedClips[0].copyTo(concatenated, overwrite = true); true
        } else {
            concatenateVideos(processedClips, concatenated)
        }
        if (!concatSuccess) return@withContext false
        onProgress(0.5f)

        // Step 3: Scale to target aspect ratio
        val scaled = File(outputDir, "scaled.mp4")
        tempFiles.add(scaled)
        val (w, h) = project.aspectRatio.let {
            if (it == AspectRatio.RATIO_ORIGINAL) Pair(project.outputWidth, project.outputHeight)
            else computeDimensions(project.outputWidth, project.outputHeight, it)
        }
        scaleVideo(concatenated, scaled, w, h)
        onProgress(0.6f)

        // Step 4: Add text overlays and captions via drawtext filter
        val withText = File(outputDir, "with_text.mp4")
        tempFiles.add(withText)
        applyTextAndCaptions(scaled, withText, project.textOverlays, project.captions)
        onProgress(0.75f)

        // Step 5: Mix in extra audio tracks
        val withAudio = File(outputDir, "with_audio.mp4")
        tempFiles.add(withAudio)
        if (project.audioTracks.isNotEmpty()) {
            mixAudio(context, withText, project.audioTracks, withAudio)
        } else {
            withText.copyTo(withAudio, overwrite = true)
        }
        onProgress(0.9f)

        // Step 6: Copy to final output
        withAudio.copyTo(outputFile, overwrite = true)
        onProgress(1.0f)

        // Cleanup temp
        tempFiles.forEach { it.deleteOnExit() }

        true
    }

    // ── Process single clip (trim + speed + volume) ──────────────────────────

    private suspend fun processClip(
        inputPath: String,
        outputFile: File,
        trimStartMs: Long,
        trimEndMs: Long,
        speed: Float,
        volume: Float
    ): Boolean {
        val ss = trimStartMs / 1000.0
        val durationArg = if (trimEndMs > 0) {
            "-t ${(trimEndMs - trimStartMs) / 1000.0}"
        } else ""

        // Speed: video PTS filter + audio atempo (supports 0.5..2.0; chain for outside range)
        val videoFilter = buildSpeedFilter(speed)
        val audioFilter = buildAudioSpeedFilter(speed, volume)

        val cmd = "-y -ss $ss $durationArg -i \"$inputPath\" " +
                "-filter:v \"$videoFilter\" " +
                "-filter:a \"$audioFilter\" " +
                "-c:v libx264 -preset fast -crf 23 " +
                "-c:a aac -b:a 128k " +
                "\"${outputFile.absolutePath}\""

        return executeFFmpeg(cmd)
    }

    private fun buildSpeedFilter(speed: Float): String {
        return if (speed == 1f) "null" else "setpts=${1.0f / speed}*PTS"
    }

    private fun buildAudioSpeedFilter(speed: Float, volume: Float): String {
        val tempoFilters = mutableListOf<String>()
        var remaining = speed.toDouble()
        while (remaining > 2.0) { tempoFilters.add("atempo=2.0"); remaining /= 2.0 }
        while (remaining < 0.5) { tempoFilters.add("atempo=0.5"); remaining *= 2.0 }
        tempoFilters.add("atempo=$remaining")
        if (volume != 1f) tempoFilters.add("volume=$volume")
        return tempoFilters.joinToString(",")
    }

    // ── Concatenate ──────────────────────────────────────────────────────────

    private suspend fun concatenateVideos(clips: List<File>, output: File): Boolean {
        val listFile = File(output.parentFile, "concat_list.txt")
        listFile.writeText(clips.joinToString("\n") { "file '${it.absolutePath}'" })
        val cmd = "-y -f concat -safe 0 -i \"${listFile.absolutePath}\" " +
                "-c copy \"${output.absolutePath}\""
        val result = executeFFmpeg(cmd)
        listFile.delete()
        return result
    }

    // ── Scale / Aspect Ratio ──────────────────────────────────────────────────

    private suspend fun scaleVideo(input: File, output: File, width: Int, height: Int): Boolean {
        val cmd = "-y -i \"${input.absolutePath}\" " +
                "-vf \"scale=$width:$height:force_original_aspect_ratio=decrease,pad=$width:$height:(ow-iw)/2:(oh-ih)/2:black\" " +
                "-c:v libx264 -preset fast -crf 23 -c:a copy " +
                "\"${output.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Text overlays & captions via drawtext ─────────────────────────────────

    private suspend fun applyTextAndCaptions(
        input: File,
        output: File,
        textOverlays: List<TextOverlay>,
        captions: List<Caption>
    ): Boolean {
        if (textOverlays.isEmpty() && captions.isEmpty()) {
            input.copyTo(output, overwrite = true)
            return true
        }

        val filters = mutableListOf<String>()

        // Text overlays
        for (overlay in textOverlays) {
            val escaped = overlay.text.replace("'", "\\'").replace(":", "\\:")
            val fontColor = colorToHex(overlay.color)
            val startS = overlay.startTimeMs / 1000.0
            val endS = overlay.endTimeMs / 1000.0

            // Approximate normalized position to pixels (assume 1080x1920)
            val xPx = "(W*${overlay.x}-tw/2)"
            val yPx = "(H*${overlay.y}-th/2)"

            var drawtextFilter = "drawtext=text='$escaped'" +
                    ":fontsize=${overlay.fontSize.toInt()}" +
                    ":fontcolor=$fontColor" +
                    ":x=$xPx:y=$yPx" +
                    ":enable='between(t,$startS,$endS)'"

            if (overlay.style == TextStyle.SHADOW) {
                drawtextFilter += ":shadowx=3:shadowy=3:shadowcolor=black@0.5"
            }
            if (overlay.style == TextStyle.OUTLINE) {
                drawtextFilter += ":borderw=2:bordercolor=black"
            }
            filters.add(drawtextFilter)
        }

        // Captions (bottom third)
        for (caption in captions) {
            val escaped = caption.text.replace("'", "\\'").replace(":", "\\:")
            val fontColor = colorToHex(caption.color)
            val startS = caption.startTimeMs / 1000.0
            val endS = caption.endTimeMs / 1000.0
            val drawtextFilter = "drawtext=text='$escaped'" +
                    ":fontsize=${caption.fontSize.toInt()}" +
                    ":fontcolor=$fontColor" +
                    ":x=(W-tw)/2:y=(H*0.85-th/2)" +
                    ":box=1:boxcolor=black@0.6:boxborderw=8" +
                    ":enable='between(t,$startS,$endS)'"
            filters.add(drawtextFilter)
        }

        val filterChain = filters.joinToString(",")
        val cmd = "-y -i \"${input.absolutePath}\" " +
                "-vf \"$filterChain\" " +
                "-c:v libx264 -preset fast -crf 23 -c:a copy " +
                "\"${output.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Mix audio tracks ──────────────────────────────────────────────────────

    private suspend fun mixAudio(
        context: Context,
        videoFile: File,
        audioTracks: List<AudioClip>,
        output: File
    ): Boolean {
        val inputArgs = StringBuilder("-y -i \"${videoFile.absolutePath}\" ")
        val filterParts = mutableListOf<String>()

        for ((i, track) in audioTracks.withIndex()) {
            val path = FileUtils.uriToPath(context, track.uri) ?: continue
            val delay = track.startTimeMs
            inputArgs.append("-i \"$path\" ")
            // adelay in ms per channel
            filterParts.add("[${i + 1}:a]adelay=${delay}|${delay},volume=${track.volume}[a${i + 1}]")
        }

        if (filterParts.isEmpty()) {
            videoFile.copyTo(output, overwrite = true)
            return true
        }

        val mergeInputs = "[0:a]" + filterParts.indices.joinToString("") { "[a${it + 1}]" }
        val filter = filterParts.joinToString(";") + ";${mergeInputs}amix=inputs=${filterParts.size + 1}:duration=longest[aout]"

        val cmd = "${inputArgs}-filter_complex \"$filter\" " +
                "-map 0:v -map \"[aout]\" " +
                "-c:v copy -c:a aac -b:a 192k \"${output.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Trim only (for quick preview) ────────────────────────────────────────

    suspend fun trimVideo(
        inputPath: String,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): Boolean {
        val ss = startMs / 1000.0
        val t = (endMs - startMs) / 1000.0
        val cmd = "-y -ss $ss -t $t -i \"$inputPath\" -c copy \"${outputFile.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Extract audio for analysis ────────────────────────────────────────────

    suspend fun extractAudio(inputPath: String, outputFile: File): Boolean {
        val cmd = "-y -i \"$inputPath\" -vn -ar 16000 -ac 1 -c:a pcm_s16le \"${outputFile.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Cut/remove segments (filler removal) ─────────────────────────────────

    suspend fun removeSegments(
        inputPath: String,
        outputFile: File,
        segmentsToRemove: List<LongRange>
    ): Boolean {
        if (segmentsToRemove.isEmpty()) {
            val cmd = "-y -i \"$inputPath\" -c copy \"${outputFile.absolutePath}\""
            return executeFFmpeg(cmd)
        }

        // Build select filter to keep everything except removed segments
        val keepConditions = segmentsToRemove.mapIndexed { i, range ->
            val start = range.first / 1000.0
            val end = range.last / 1000.0
            "not(between(t,$start,$end))"
        }.joinToString("*")

        val cmd = "-y -i \"$inputPath\" " +
                "-vf \"select='$keepConditions',setpts=N/FRAME_RATE/TB\" " +
                "-af \"aselect='$keepConditions',asetpts=N/SR/TB\" " +
                "-c:v libx264 -preset fast -crf 23 -c:a aac -b:a 128k " +
                "\"${outputFile.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Generate thumbnail ────────────────────────────────────────────────────

    suspend fun generateThumbnail(inputPath: String, outputFile: File, timeMs: Long = 0): Boolean {
        val ss = timeMs / 1000.0
        val cmd = "-y -ss $ss -i \"$inputPath\" -vframes 1 -q:v 2 \"${outputFile.absolutePath}\""
        return executeFFmpeg(cmd)
    }

    // ── Get video duration (ms) ───────────────────────────────────────────────

    fun getVideoDuration(path: String): Long {
        var duration = 0L
        val probe = FFmpegKitConfig.parseFFprobe(
            "ffprobe -v quiet -print_format json -show_format \"$path\""
        )
        // Simple fallback: use MediaMetadataRetriever
        return duration
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun computeDimensions(maxW: Int, maxH: Int, ratio: AspectRatio): Pair<Int, Int> {
        val r = ratio.ratioWidth.toFloat() / ratio.ratioHeight.toFloat()
        val w = if (r >= 1f) maxW else (maxH * r).toInt().roundToEven()
        val h = if (r < 1f) maxH else (maxW / r).toInt().roundToEven()
        return Pair(w, h)
    }

    private fun Int.roundToEven() = if (this % 2 == 0) this else this + 1

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private suspend fun executeFFmpeg(command: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val session = FFmpegKit.executeAsync(command) { session ->
                if (cont.isActive) {
                    cont.resume(ReturnCode.isSuccess(session.returnCode))
                }
            }
            cont.invokeOnCancellation { session.cancel() }
        }
}
