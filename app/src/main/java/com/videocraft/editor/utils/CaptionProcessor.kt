package com.videocraft.editor.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.videocraft.editor.data.model.Caption
import com.videocraft.editor.data.model.CaptionLanguage
import com.videocraft.editor.data.model.CaptionStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class SpeechResult(
    val text: String,
    val confidence: Float,
    val startMs: Long,
    val endMs: Long,
    val isFinal: Boolean
)

/**
 * Handles speech recognition for caption generation.
 * Supports English and Hinglish using Android's built-in SpeechRecognizer.
 */
class CaptionProcessor(private val context: Context) {

    /**
     * Common filler words in English + Hinglish.
     * These are detected and optionally removed during AI Edit.
     */
    val fillerWords = setOf(
        // English fillers
        "um", "uh", "hmm", "like", "you know", "basically", "literally",
        "actually", "honestly", "right", "okay so", "i mean",
        // Hinglish fillers
        "matlab", "toh", "na", "yaar", "arre", "aur", "acha", "haan",
        "woh", "ek dum", "simply", "ekdum", "matlab kya hai",
        "samjhe", "bhai", "sunoo", "dekho", "suniye"
    )

    /**
     * Auto-generate captions from audio using device SpeechRecognizer.
     * Uses 'en-IN' locale for best Hinglish support.
     */
    fun recognizeSpeech(audioFilePath: String, language: CaptionLanguage): Flow<SpeechResult> =
        callbackFlow {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val locale = when (language) {
                CaptionLanguage.HINDI -> "hi-IN"
                CaptionLanguage.HINGLISH -> "en-IN"  // en-IN handles code-mixed speech best
                CaptionLanguage.ENGLISH -> "en-US"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioFilePath)
            }

            var currentStartMs = 0L
            var lastResultMs = 0L

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { currentStartMs = System.currentTimeMillis() }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        trySend(SpeechResult(
                            text = matches[0],
                            confidence = 0.7f,
                            startMs = currentStartMs,
                            endMs = System.currentTimeMillis(),
                            isFinal = false
                        ))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    if (!matches.isNullOrEmpty()) {
                        trySend(SpeechResult(
                            text = matches[0],
                            confidence = scores?.getOrNull(0) ?: 0.8f,
                            startMs = currentStartMs,
                            endMs = System.currentTimeMillis(),
                            isFinal = true
                        ))
                    }
                    close()
                }

                override fun onError(error: Int) {
                    close(Exception("Speech recognition error: $error"))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            awaitClose { recognizer.destroy() }
        }

    /**
     * Convert raw speech results into styled Caption objects.
     */
    fun buildCaptions(
        results: List<SpeechResult>,
        language: CaptionLanguage,
        style: CaptionStyle = CaptionStyle.STANDARD,
        fontFamily: String = "roboto_bold"
    ): List<Caption> {
        return results.filter { it.isFinal && it.text.isNotBlank() }.map { result ->
            val processedText = when (language) {
                CaptionLanguage.HINGLISH -> processHinglish(result.text)
                CaptionLanguage.HINDI -> result.text
                CaptionLanguage.ENGLISH -> result.text
            }
            Caption(
                text = processedText,
                startTimeMs = result.startMs,
                endTimeMs = result.endMs,
                style = style,
                fontFamily = fontFamily,
                language = language
            )
        }
    }

    /**
     * Hinglish post-processing:
     * - Keeps English words as-is
     * - Converts Devanagari (Hindi) to Roman script approximation
     * - Fixes common Hinglish spelling patterns
     */
    private fun processHinglish(text: String): String {
        var processed = text
        // Common Devanagari → Roman transliterations
        val transliterations = mapOf(
            "मैं" to "main", "हूं" to "hoon", "हूँ" to "hoon",
            "आप" to "aap", "तुम" to "tum", "वो" to "wo",
            "यह" to "yeh", "क्या" to "kya", "नहीं" to "nahi",
            "हाँ" to "haan", "ठीक" to "theek", "बहुत" to "bahut",
            "अच्छा" to "acha", "देखो" to "dekho", "सुनो" to "suno",
            "बात" to "baat", "समझे" to "samjhe", "यार" to "yaar",
            "भाई" to "bhai", "दोस्त" to "dost", "काम" to "kaam",
            "लेकिन" to "lekin", "मतलब" to "matlab", "तो" to "toh",
            "जो" to "jo", "कि" to "ki", "और" to "aur",
            "है" to "hai", "हैं" to "hain", "था" to "tha",
            "करना" to "karna", "करते" to "karte", "करो" to "karo",
            "रहा" to "raha", "गया" to "gaya", "आया" to "aaya",
            "सब" to "sab", "कुछ" to "kuch", "कोई" to "koi",
            "जब" to "jab", "तब" to "tab", "अभी" to "abhi",
            "वाला" to "wala", "वाले" to "wale", "वाली" to "wali"
        )
        transliterations.forEach { (hindi, roman) ->
            processed = processed.replace(hindi, roman)
        }
        return processed.trim()
    }

    /**
     * Remove filler words from a caption text.
     */
    fun removeFillersFromText(text: String): String {
        var result = text.lowercase()
        fillerWords.sortedByDescending { it.length }.forEach { filler ->
            result = result.replace(Regex("\\b${Regex.escape(filler)}\\b", RegexOption.IGNORE_CASE), "")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Split a long caption into word-by-word chunks for animated captions.
     */
    fun splitIntoWordCaptions(caption: Caption): List<Caption> {
        val words = caption.text.split(" ").filter { it.isNotBlank() }
        if (words.size <= 1) return listOf(caption)

        val durationPerWord = (caption.endTimeMs - caption.startTimeMs) / words.size
        return words.mapIndexed { i, word ->
            caption.copy(
                text = word,
                startTimeMs = caption.startTimeMs + i * durationPerWord,
                endTimeMs = caption.startTimeMs + (i + 1) * durationPerWord,
                style = CaptionStyle.WORD_BY_WORD
            )
        }
    }

    /**
     * Detect filler words in a list of captions and return their positions.
     */
    fun detectFillers(captions: List<Caption>): List<Caption> {
        return captions.filter { caption ->
            fillerWords.any { filler ->
                caption.text.lowercase().contains(filler)
            }
        }
    }

    companion object {
        /**
         * Check if device supports speech recognition.
         */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
}
