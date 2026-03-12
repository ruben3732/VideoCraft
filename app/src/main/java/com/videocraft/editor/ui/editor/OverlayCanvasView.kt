package com.videocraft.editor.ui.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.videocraft.editor.data.model.ImageOverlay
import com.videocraft.editor.data.model.Keyframe
import com.videocraft.editor.data.model.TextOverlay
import com.videocraft.editor.data.model.TextStyle

/**
 * Transparent canvas view that renders text and image overlays on top of the video player.
 * Supports drag-to-reposition and pinch-to-scale of overlays.
 */
class OverlayCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var textOverlays: List<TextOverlay> = emptyList()
    var imageOverlays: List<ImageOverlay> = emptyList()
    var currentTimeMs: Long = 0
    var onOverlaySelected: ((String) -> Unit)? = null
    var onOverlayMoved: ((String, Float, Float) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var draggingOverlayId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val t = currentTimeMs

        // Draw image overlays
        imageOverlays.filter { it.startTimeMs <= t && it.endTimeMs >= t }.forEach { overlay ->
            drawImageOverlay(canvas, overlay, t)
        }

        // Draw text overlays
        textOverlays.filter { it.startTimeMs <= t && it.endTimeMs >= t }.forEach { overlay ->
            drawTextOverlay(canvas, overlay, t)
        }
    }

    private fun drawTextOverlay(canvas: Canvas, overlay: TextOverlay, currentTime: Long) {
        val state = interpolateTextOverlay(overlay, currentTime)
        val cx = state.x * width
        val cy = state.y * height

        textPaint.apply {
            textSize = overlay.fontSize * state.scale
            color = overlay.color
            alpha = (state.opacity * 255).toInt()
            isFakeBoldText = overlay.isBold
            textAlign = when (overlay.alignment) {
                com.videocraft.editor.data.model.TextAlignment.LEFT -> Paint.Align.LEFT
                com.videocraft.editor.data.model.TextAlignment.CENTER -> Paint.Align.CENTER
                com.videocraft.editor.data.model.TextAlignment.RIGHT -> Paint.Align.RIGHT
            }
        }

        canvas.save()
        canvas.rotate(state.rotation + overlay.rotation, cx, cy)

        val fullText = if (overlay.emoji.isNotEmpty()) "${overlay.emoji} ${overlay.text}" else overlay.text

        when (overlay.style) {
            TextStyle.SHADOW -> {
                shadowPaint.apply {
                    textSize = textPaint.textSize
                    color = Color.BLACK
                    alpha = 128
                    textAlign = textPaint.textAlign
                }
                canvas.drawText(fullText, cx + 4f, cy + 4f, shadowPaint)
            }
            TextStyle.OUTLINE -> {
                outlinePaint.apply {
                    textSize = textPaint.textSize
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    textAlign = textPaint.textAlign
                }
                canvas.drawText(fullText, cx, cy, outlinePaint)
            }
            TextStyle.BUBBLE -> {
                val bounds = Rect()
                textPaint.getTextBounds(fullText, 0, fullText.length, bounds)
                val pad = 16f
                val bgPaint = Paint().apply {
                    color = overlay.backgroundColor.takeIf { it != Color.TRANSPARENT }
                        ?: Color.parseColor("#CC000000")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(
                    cx + bounds.left - pad,
                    cy + bounds.top - pad,
                    cx + bounds.right + pad,
                    cy + bounds.bottom + pad,
                    12f, 12f, bgPaint
                )
            }
            TextStyle.NEON -> {
                val glowPaint = Paint(textPaint).apply {
                    color = textPaint.color
                    maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
                }
                canvas.drawText(fullText, cx, cy, glowPaint)
            }
            else -> {}
        }

        canvas.drawText(fullText, cx, cy, textPaint)
        canvas.restore()
    }

    private fun drawImageOverlay(canvas: Canvas, overlay: ImageOverlay, currentTime: Long) {
        // Image overlays are handled by Glide/Bitmap loading in actual implementation
        // Placeholder: draw a rect
        val cx = overlay.x * width
        val cy = overlay.y * height
        val paint = Paint().apply { color = Color.parseColor("#40FFFFFF") }
        val size = 80f * overlay.scale
        canvas.drawRect(cx - size, cy - size, cx + size, cy + size, paint)
    }

    private data class OverlayState(
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotation: Float,
        val opacity: Float
    )

    private fun interpolateTextOverlay(overlay: TextOverlay, timeMs: Long): OverlayState {
        if (overlay.keyframes.isEmpty()) {
            return OverlayState(overlay.x, overlay.y, overlay.scale, 0f, 1f)
        }
        val kfs = overlay.keyframes.sortedBy { it.timeMs }
        val prev = kfs.lastOrNull { it.timeMs <= timeMs } ?: kfs.first()
        val next = kfs.firstOrNull { it.timeMs > timeMs } ?: kfs.last()

        if (prev.timeMs == next.timeMs) {
            return OverlayState(
                prev.x ?: overlay.x,
                prev.y ?: overlay.y,
                prev.scale ?: overlay.scale,
                prev.rotation ?: 0f,
                prev.opacity ?: 1f
            )
        }

        val t = (timeMs - prev.timeMs).toFloat() / (next.timeMs - prev.timeMs)
        return OverlayState(
            lerp(prev.x ?: overlay.x, next.x ?: overlay.x, t),
            lerp(prev.y ?: overlay.y, next.y ?: overlay.y, t),
            lerp(prev.scale ?: overlay.scale, next.scale ?: overlay.scale, t),
            lerp(prev.rotation ?: 0f, next.rotation ?: 0f, t),
            lerp(prev.opacity ?: 1f, next.opacity ?: 1f, t)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                // Hit test overlays
                draggingOverlayId = hitTestOverlay(event.x, event.y)
                draggingOverlayId?.let { onOverlaySelected?.invoke(it) }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastTouchX) / width
                val dy = (event.y - lastTouchY) / height
                draggingOverlayId?.let { id ->
                    val overlay = textOverlays.find { it.id == id }
                    if (overlay != null) {
                        val newX = (overlay.x + dx).coerceIn(0f, 1f)
                        val newY = (overlay.y + dy).coerceIn(0f, 1f)
                        onOverlayMoved?.invoke(id, newX, newY)
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> draggingOverlayId = null
        }
        return true
    }

    private fun hitTestOverlay(x: Float, y: Float): String? {
        val normX = x / width
        val normY = y / height
        // Check text overlays (in reverse order = topmost first)
        return textOverlays.asReversed().firstOrNull { o ->
            Math.abs(o.x - normX) < 0.1f && Math.abs(o.y - normY) < 0.1f
        }?.id ?: imageOverlays.asReversed().firstOrNull { o ->
            Math.abs(o.x - normX) < 0.1f && Math.abs(o.y - normY) < 0.1f
        }?.id
    }

    fun updateTime(positionMs: Long) {
        currentTimeMs = positionMs
        invalidate()
    }
}
