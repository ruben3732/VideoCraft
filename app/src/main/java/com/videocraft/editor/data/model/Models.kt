package com.videocraft.editor.data.model

import android.graphics.Color
import android.net.Uri
import java.util.UUID

// ─── Media Types ─────────────────────────────────────────────────────────────

enum class MediaType { VIDEO, IMAGE, AUDIO, GIF }

enum class AspectRatio(
    val ratioWidth: Int,
    val ratioHeight: Int,
    val displayName: String,
    val platform: String
) {
    RATIO_9_16(9, 16, "9:16", "Reels / TikTok / Shorts"),
    RATIO_16_9(16, 9, "16:9", "YouTube / Landscape"),
    RATIO_1_1(1, 1, "1:1", "Instagram Square"),
    RATIO_4_5(4, 5, "4:5", "Instagram Feed"),
    RATIO_4_3(4, 3, "4:3", "Traditional"),
    RATIO_21_9(21, 9, "21:9", "Cinematic"),
    RATIO_ORIGINAL(-1, -1, "Original", "Native Ratio");

    fun toFloat(): Float = if (ratioWidth < 0) -1f else ratioWidth.toFloat() / ratioHeight.toFloat()
}

// ─── Keyframe ─────────────────────────────────────────────────────────────────

enum class Interpolation { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, HOLD }

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val x: Float? = null,          // normalized 0..1
    val y: Float? = null,          // normalized 0..1
    val scale: Float? = null,
    val rotation: Float? = null,
    val opacity: Float? = null,
    val interpolation: Interpolation = Interpolation.LINEAR
)

// ─── Text Overlay ─────────────────────────────────────────────────────────────

enum class TextAlignment { LEFT, CENTER, RIGHT }
enum class TextStyle { PLAIN, SHADOW, OUTLINE, NEON, BUBBLE, GRADIENT }

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "Tap to edit",
    var fontFamily: String = "roboto",
    var fontSize: Float = 40f,
    var color: Int = Color.WHITE,
    var backgroundColor: Int = Color.TRANSPARENT,
    var startTimeMs: Long = 0,
    var endTimeMs: Long = 3000,
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var hasBackground: Boolean = false,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var alignment: TextAlignment = TextAlignment.CENTER,
    var style: TextStyle = TextStyle.PLAIN,
    var emoji: String = "",
    val keyframes: MutableList<Keyframe> = mutableListOf()
)

// ─── Image Overlay ────────────────────────────────────────────────────────────

data class ImageOverlay(
    val id: String = UUID.randomUUID().toString(),
    var uri: Uri = Uri.EMPTY,
    var startTimeMs: Long = 0,
    var endTimeMs: Long = 3000,
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    val keyframes: MutableList<Keyframe> = mutableListOf()
)

// ─── Video Clip ───────────────────────────────────────────────────────────────

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    var uri: Uri = Uri.EMPTY,
    var startTimeMs: Long = 0,      // position on timeline
    var endTimeMs: Long = 0,        // position on timeline
    var trimStartMs: Long = 0,      // in-point inside original file
    var trimEndMs: Long = -1,       // out-point (-1 = full length)
    var speed: Float = 1.0f,        // 0.25 … 4.0
    var volume: Float = 1.0f,       // 0.0 … 1.0
    var isMuted: Boolean = false,
    val keyframes: MutableList<Keyframe> = mutableListOf()
)

// ─── Audio Clip ───────────────────────────────────────────────────────────────

data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    var uri: Uri = Uri.EMPTY,
    var startTimeMs: Long = 0,
    var endTimeMs: Long = 0,
    var trimStartMs: Long = 0,
    var volume: Float = 1.0f,
    var isFadeIn: Boolean = false,
    var isFadeOut: Boolean = false,
    var label: String = "Audio"
)

// ─── Caption ──────────────────────────────────────────────────────────────────

enum class CaptionLanguage { ENGLISH, HINGLISH, HINDI }
enum class CaptionStyle {
    STANDARD, BOLD_KARAOKE, WORD_BY_WORD, ANIMATED, HIGHLIGHT, GRADIENT, NEON
}

data class Caption(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var startTimeMs: Long = 0,
    var endTimeMs: Long = 2000,
    var fontFamily: String = "roboto_bold",
    var fontSize: Float = 32f,
    var color: Int = Color.WHITE,
    var backgroundColor: Int = Color.parseColor("#CC000000"),
    var style: CaptionStyle = CaptionStyle.STANDARD,
    var emoji: String = "",
    var isEdited: Boolean = false,
    var language: CaptionLanguage = CaptionLanguage.ENGLISH
)

// ─── Video Project ────────────────────────────────────────────────────────────

data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Untitled Project",
    val videoTracks: MutableList<VideoClip> = mutableListOf(),
    val audioTracks: MutableList<AudioClip> = mutableListOf(),
    val textOverlays: MutableList<TextOverlay> = mutableListOf(),
    val imageOverlays: MutableList<ImageOverlay> = mutableListOf(),
    val captions: MutableList<Caption> = mutableListOf(),
    var aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    var totalDurationMs: Long = 0,
    var outputWidth: Int = 1080,
    var outputHeight: Int = 1920,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun recalcDuration() {
        totalDurationMs = videoTracks.maxOfOrNull { it.endTimeMs } ?: 0L
    }
}

// ─── Stock Media (API responses) ──────────────────────────────────────────────

enum class StockType { PHOTO, VIDEO, GIF }

data class StockMediaItem(
    val id: String,
    val type: StockType,
    val thumbnailUrl: String,
    val previewUrl: String,
    val downloadUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,        // seconds, 0 for images
    val attribution: String = "",
    val tags: String = ""
)

// ─── AI Edit Result ───────────────────────────────────────────────────────────

data class SilenceSegment(
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long = endMs - startMs
)

data class FillerWord(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1f
)

data class AIEditSuggestion(
    val silenceSegments: List<SilenceSegment>,
    val fillerWords: List<FillerWord>,
    val suggestedCuts: List<LongRange>,        // ms ranges to cut
    val suggestedBrollKeywords: List<String>,  // for stock search
    val totalSavedMs: Long
)

// ─── Font Info ────────────────────────────────────────────────────────────────

data class FontInfo(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val category: FontCategory
)

enum class FontCategory {
    SANS_SERIF, SERIF, HANDWRITING, DISPLAY, MONOSPACE, IMPACT, CREATIVE
}

// Pre-defined font list (using Google Fonts bundled in assets)
val AVAILABLE_FONTS = listOf(
    FontInfo("roboto", "Roboto", "fonts/Roboto-Regular.ttf", FontCategory.SANS_SERIF),
    FontInfo("roboto_bold", "Roboto Bold", "fonts/Roboto-Bold.ttf", FontCategory.SANS_SERIF),
    FontInfo("montserrat", "Montserrat", "fonts/Montserrat-Regular.ttf", FontCategory.SANS_SERIF),
    FontInfo("montserrat_bold", "Montserrat Bold", "fonts/Montserrat-Bold.ttf", FontCategory.SANS_SERIF),
    FontInfo("pacifico", "Pacifico", "fonts/Pacifico-Regular.ttf", FontCategory.HANDWRITING),
    FontInfo("lobster", "Lobster", "fonts/Lobster-Regular.ttf", FontCategory.DISPLAY),
    FontInfo("dancing_script", "Dancing Script", "fonts/DancingScript-Regular.ttf", FontCategory.HANDWRITING),
    FontInfo("oswald", "Oswald", "fonts/Oswald-Regular.ttf", FontCategory.DISPLAY),
    FontInfo("playfair", "Playfair Display", "fonts/PlayfairDisplay-Regular.ttf", FontCategory.SERIF),
    FontInfo("raleway", "Raleway", "fonts/Raleway-Regular.ttf", FontCategory.SANS_SERIF),
    FontInfo("bebas_neue", "Bebas Neue", "fonts/BebasNeue-Regular.ttf", FontCategory.IMPACT),
    FontInfo("comfortaa", "Comfortaa", "fonts/Comfortaa-Regular.ttf", FontCategory.CREATIVE),
    FontInfo("righteous", "Righteous", "fonts/Righteous-Regular.ttf", FontCategory.DISPLAY),
    FontInfo("titillium", "Titillium Web", "fonts/TitilliumWeb-Regular.ttf", FontCategory.SANS_SERIF),
    FontInfo("ubuntu", "Ubuntu", "fonts/Ubuntu-Regular.ttf", FontCategory.SANS_SERIF),
    FontInfo("special_elite", "Special Elite", "fonts/SpecialElite-Regular.ttf", FontCategory.CREATIVE)
)
