package com.videocraft.editor.ui.editor.timeline

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.videocraft.editor.R
import com.videocraft.editor.data.model.VideoClip
import com.videocraft.editor.data.model.VideoProject
import kotlinx.coroutines.*

/**
 * Custom timeline view that shows:
 * - Video track with thumbnails
 * - Audio track waveform
 * - Text/image overlay tracks
 * - Playhead
 * - Keyframe markers
 * - Supports pinch-to-zoom and drag-to-scroll
 */
class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Listeners ─────────────────────────────────────────────────────────────

    private var onSeekListener: ((Long) -> Unit)? = null
    private var onClipClickListener: ((String) -> Unit)? = null
    private var onClipLongClickListener: ((String) -> Unit)? = null

    fun setOnSeekListener(l: (Long) -> Unit) { onSeekListener = l }
    fun setOnClipClickListener(l: (String) -> Unit) { onClipClickListener = l }
    fun setOnClipLongClickListener(l: (String) -> Unit) { onClipLongClickListener = l }

    // ── State ─────────────────────────────────────────────────────────────────

    private var project: VideoProject? = null
    private var playheadPositionMs: Long = 0
    private var scrollOffsetPx: Float = 0f
    private var scaleFactor: Float = 1f    // pixels per second
    private val BASE_PX_PER_SEC = 100f

    private val thumbnailCache = HashMap<String, Bitmap>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Track geometry ────────────────────────────────────────────────────────

    private val TRACK_HEIGHT_DP = 56f
    private val TRACK_GAP_DP = 8f
    private val TRACK_PADDING_TOP_DP = 40f   // for time ruler
    private val PLAYHEAD_WIDTH_DP = 2f

    private val trackHeight get() = TRACK_HEIGHT_DP * density
    private val trackGap get() = TRACK_GAP_DP * density
    private val trackPaddingTop get() = TRACK_PADDING_TOP_DP * density
    private val density get() = resources.displayMetrics.density

    // ── Paints ────────────────────────────────────────────────────────────────

    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6200EE")
    }
    private val clipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3700B3")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081")
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 10f * resources.displayMetrics.density
    }
    private val rulerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        strokeWidth = 1f
    }
    private val overlayTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#03DAC5")
        alpha = 200
    }
    private val audioTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAB40")
        alpha = 200
    }
    private val keyframePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#1A1A2E")
    }

    // ── Gesture detectors ────────────────────────────────────────────────────

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            scrollOffsetPx = (scrollOffsetPx + distanceX).coerceAtLeast(0f)
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val timeMs = pxToMs(e.x + scrollOffsetPx)
            onSeekListener?.invoke(timeMs)

            // Detect if a clip was tapped
            val clipId = hitTestClip(e.x + scrollOffsetPx, e.y)
            if (clipId != null) onClipClickListener?.invoke(clipId)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val clipId = hitTestClip(e.x + scrollOffsetPx, e.y)
            if (clipId != null) onClipLongClickListener?.invoke(clipId)
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.1f, 5f)
                invalidate()
                return true
            }
        })

    // ── Public API ────────────────────────────────────────────────────────────

    fun setProject(project: VideoProject) {
        this.project = project
        loadThumbnails()
        invalidate()
    }

    fun setPlayheadPosition(posMs: Long) {
        playheadPositionMs = posMs
        invalidate()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawTimeRuler(canvas)

        val proj = project ?: return

        // Video clips (row 0)
        val videoTrackY = trackPaddingTop
        proj.videoTracks.forEach { clip ->
            drawVideoClip(canvas, clip, videoTrackY)
        }

        // Audio clips (row 1)
        val audioTrackY = videoTrackY + trackHeight + trackGap
        proj.audioTracks.forEach { clip ->
            val startX = msToScreenX(clip.startTimeMs)
            val endX = msToScreenX(clip.endTimeMs)
            val rect = RectF(startX, audioTrackY, endX, audioTrackY + trackHeight)
            canvas.drawRoundRect(rect, 8f, 8f, audioTrackPaint)
        }

        // Text overlay tracks (rows 2+)
        proj.textOverlays.forEachIndexed { i, overlay ->
            val trackY = audioTrackY + trackHeight + trackGap + i * (trackHeight / 2 + trackGap / 2)
            val startX = msToScreenX(overlay.startTimeMs)
            val endX = msToScreenX(overlay.endTimeMs)
            val rect = RectF(startX, trackY, endX, trackY + trackHeight / 2)
            val paint = Paint(overlayTrackPaint).apply { color = Color.parseColor("#BB86FC") }
            canvas.drawRoundRect(rect, 4f, 4f, paint)
        }

        // Image overlay tracks
        proj.imageOverlays.forEachIndexed { i, overlay ->
            val trackY = audioTrackY + trackHeight + trackGap +
                    proj.textOverlays.size * (trackHeight / 2 + trackGap / 2) +
                    i * (trackHeight / 2 + trackGap / 2)
            val startX = msToScreenX(overlay.startTimeMs)
            val endX = msToScreenX(overlay.endTimeMs)
            val rect = RectF(startX, trackY, endX, trackY + trackHeight / 2)
            canvas.drawRoundRect(rect, 4f, 4f, overlayTrackPaint)
        }

        drawPlayhead(canvas)
    }

    private fun drawVideoClip(canvas: Canvas, clip: VideoClip, trackY: Float) {
        val startX = msToScreenX(clip.startTimeMs)
        val endX = msToScreenX(clip.endTimeMs)
        if (endX < 0 || startX > width) return

        val rect = RectF(startX, trackY, endX, trackY + trackHeight)
        canvas.drawRoundRect(rect, 8f, 8f, clipPaint)

        // Draw thumbnails if cached
        val thumb = thumbnailCache[clip.id]
        if (thumb != null) {
            val thumbWidth = trackHeight.toInt()
            var x = startX
            while (x < endX) {
                val destRect = RectF(x, trackY, (x + thumbWidth).coerceAtMost(endX), trackY + trackHeight)
                canvas.drawBitmap(thumb, null, destRect, null)
                x += thumbWidth
            }
        }

        canvas.drawRoundRect(rect, 8f, 8f, clipBorderPaint)

        // Keyframe diamonds
        clip.keyframes.forEach { kf ->
            val kfX = msToScreenX(kf.timeMs)
            val cy = trackY + trackHeight / 2
            val path = Path().apply {
                moveTo(kfX, cy - 8f)
                lineTo(kfX + 6f, cy)
                lineTo(kfX, cy + 8f)
                lineTo(kfX - 6f, cy)
                close()
            }
            canvas.drawPath(path, keyframePaint)
        }
    }

    private fun drawTimeRuler(canvas: Canvas) {
        val pxPerSec = BASE_PX_PER_SEC * scaleFactor
        val startSec = (scrollOffsetPx / pxPerSec).toLong()
        val endSec = ((scrollOffsetPx + width) / pxPerSec).toLong() + 1
        val stepSec = when {
            pxPerSec > 200 -> 1L
            pxPerSec > 50 -> 5L
            pxPerSec > 20 -> 10L
            else -> 30L
        }

        for (s in startSec..endSec step stepSec) {
            val x = msToPx(s * 1000) - scrollOffsetPx
            canvas.drawLine(x, 0f, x, trackPaddingTop, rulerLinePaint)
            val label = formatTime(s * 1000)
            canvas.drawText(label, x + 4f, trackPaddingTop - 6f, rulerPaint)
        }
    }

    private fun drawPlayhead(canvas: Canvas) {
        val x = msToScreenX(playheadPositionMs)
        canvas.drawLine(x, 0f, x, height.toFloat(), playheadPaint)
        // Triangle at top
        val path = Path().apply {
            moveTo(x - 8f, 0f)
            lineTo(x + 8f, 0f)
            lineTo(x, 16f)
            close()
        }
        canvas.drawPath(path, playheadPaint)
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    private fun msToPx(ms: Long): Float = ms / 1000f * BASE_PX_PER_SEC * scaleFactor
    private fun msToScreenX(ms: Long): Float = msToPx(ms) - scrollOffsetPx
    private fun pxToMs(px: Float): Long = (px / (BASE_PX_PER_SEC * scaleFactor) * 1000).toLong()

    private fun hitTestClip(absoluteX: Float, y: Float): String? {
        val proj = project ?: return null
        val timeMs = pxToMs(absoluteX)
        val trackY = trackPaddingTop
        if (y >= trackY && y <= trackY + trackHeight) {
            return proj.videoTracks.find { it.startTimeMs <= timeMs && it.endTimeMs >= timeMs }?.id
        }
        return null
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "${s / 60}:${String.format("%02d", s % 60)}"
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // ── Thumbnails ────────────────────────────────────────────────────────────

    private fun loadThumbnails() {
        val proj = project ?: return
        proj.videoTracks.forEach { clip ->
            if (!thumbnailCache.containsKey(clip.id)) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, clip.uri)
                        val bmp = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        retriever.release()
                        if (bmp != null) {
                            val scaled = Bitmap.createScaledBitmap(bmp,
                                (trackHeight * density).toInt(),
                                (trackHeight * density).toInt(), true)
                            thumbnailCache[clip.id] = scaled
                            withContext(Dispatchers.Main) { invalidate() }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }
}
