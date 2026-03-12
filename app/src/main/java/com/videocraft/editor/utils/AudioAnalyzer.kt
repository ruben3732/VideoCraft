package com.videocraft.editor.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.videocraft.editor.data.model.SilenceSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * Analyzes audio from a video file to detect silence and low-energy segments.
 * Used by the AI Edit feature to auto-remove pauses.
 */
object AudioAnalyzer {

    private const val SILENCE_THRESHOLD_RMS = 300     // RMS amplitude below this = silence
    private const val MIN_SILENCE_DURATION_MS = 400    // Ignore pauses shorter than this
    private const val ANALYSIS_CHUNK_MS = 50           // Analyze in 50ms windows

    /**
     * Detects silent/pause segments in the audio track of a video file.
     * @return List of silence segments with start/end times in milliseconds
     */
    suspend fun detectSilence(
        context: Context,
        videoPath: String,
        silenceThreshold: Int = SILENCE_THRESHOLD_RMS,
        minSilenceDurationMs: Long = MIN_SILENCE_DURATION_MS.toLong()
    ): List<SilenceSegment> = withContext(Dispatchers.IO) {
        val segments = mutableListOf<SilenceSegment>()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(videoPath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) return@withContext segments

            extractor.selectTrack(audioTrackIndex)

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            val pcmData = mutableListOf<Short>()

            // Decode audio to PCM
            while (!isEOS) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer: ByteBuffer = codec.getInputBuffer(inputIndex)!!
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex)!!
                    while (outputBuffer.hasRemaining()) {
                        pcmData.add(outputBuffer.short)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            codec.stop()
            codec.release()

            // Analyze PCM for silence
            val samplesPerChunk = (sampleRate * ANALYSIS_CHUNK_MS / 1000) * channelCount
            var currentSilenceStart: Long? = null
            var chunkIndex = 0

            while (chunkIndex * samplesPerChunk < pcmData.size) {
                val startSample = chunkIndex * samplesPerChunk
                val endSample = minOf(startSample + samplesPerChunk, pcmData.size)
                val chunkTimeMs = chunkIndex.toLong() * ANALYSIS_CHUNK_MS

                val rms = computeRms(pcmData, startSample, endSample)

                if (rms < silenceThreshold) {
                    if (currentSilenceStart == null) {
                        currentSilenceStart = chunkTimeMs
                    }
                } else {
                    if (currentSilenceStart != null) {
                        val silenceDuration = chunkTimeMs - currentSilenceStart!!
                        if (silenceDuration >= minSilenceDurationMs) {
                            segments.add(
                                SilenceSegment(
                                    startMs = currentSilenceStart!!,
                                    endMs = chunkTimeMs
                                )
                            )
                        }
                        currentSilenceStart = null
                    }
                }
                chunkIndex++
            }

            // Handle trailing silence
            if (currentSilenceStart != null) {
                val totalDurationMs = pcmData.size.toLong() * 1000 / (sampleRate * channelCount)
                val silenceDuration = totalDurationMs - currentSilenceStart!!
                if (silenceDuration >= minSilenceDurationMs) {
                    segments.add(SilenceSegment(currentSilenceStart!!, totalDurationMs))
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }

        segments
    }

    private fun computeRms(samples: List<Short>, start: Int, end: Int): Double {
        if (start >= end) return 0.0
        var sumSquares = 0.0
        for (i in start until end) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / (end - start))
    }

    /**
     * Converts detected silences to cut ranges, keeping short gaps to avoid choppy cuts.
     * Adds a small buffer around cuts to avoid abrupt transitions.
     */
    fun silencesToCutRanges(
        silences: List<SilenceSegment>,
        bufferMs: Long = 100
    ): List<LongRange> {
        return silences.map { silence ->
            val cutStart = (silence.startMs + bufferMs).coerceAtLeast(0)
            val cutEnd = (silence.endMs - bufferMs).coerceAtLeast(cutStart)
            cutStart..cutEnd
        }.filter { it.last > it.first }
    }
}
