package com.videocraft.editor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.videocraft.editor.R
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── View Extensions ────────────────────────────────────────────────────────────

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
fun View.isVisible() = visibility == View.VISIBLE

fun View.animateIn() {
    val anim = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
    startAnimation(anim)
    show()
}

fun View.animateOut(gone: Boolean = true) {
    val anim = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
    anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
        override fun onAnimationStart(a: android.view.animation.Animation?) {}
        override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
        override fun onAnimationEnd(a: android.view.animation.Animation?) {
            if (gone) hide() else invisible()
        }
    })
    startAnimation(anim)
}

fun View.onClick(block: (View) -> Unit) {
    setOnClickListener(block)
}

// ── Context Extensions ─────────────────────────────────────────────────────────

fun Context.toast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(message: String, long: Boolean = false) {
    requireContext().toast(message, long)
}

// ── Time Extensions ────────────────────────────────────────────────────────────

fun Long.toTimeString(): String {
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    val millis = (this % 1000) / 10

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, millis)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, millis)
    }
}

fun Long.toSimpleTimeString(): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

// ── Uri / Media Extensions ─────────────────────────────────────────────────────

fun Uri.getVideoDurationMs(context: Context): Long {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, this)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        duration
    } catch (e: Exception) { 0L }
}

fun Uri.getVideoThumbnail(context: Context, timeMs: Long = 0): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, this)
        val bm = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bm
    } catch (e: Exception) { null }
}

fun Uri.getVideoSize(context: Context): Pair<Int, Int> {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, this)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
        retriever.release()
        Pair(w, h)
    } catch (e: Exception) { Pair(0, 0) }
}

// ── LiveData Extensions ────────────────────────────────────────────────────────

fun <T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: Observer<T>) {
    observe(owner, object : Observer<T> {
        override fun onChanged(value: T) {
            observer.onChanged(value)
            removeObserver(this)
        }
    })
}

// ── Number Extensions ──────────────────────────────────────────────────────────

fun Float.toSpeedLabel(): String = when {
    this == 1f -> "1x"
    this == 0.5f -> "0.5x"
    this == 0.25f -> "0.25x"
    this == 2f -> "2x"
    this == 4f -> "4x"
    else -> String.format(Locale.US, "%.2fx", this)
}

fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

fun Float.dpToPx(context: Context): Float {
    return this * context.resources.displayMetrics.density
}
