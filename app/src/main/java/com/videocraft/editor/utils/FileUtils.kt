package com.videocraft.editor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    /**
     * Convert a content URI to a real file path by copying to app cache.
     */
    fun uriToPath(context: Context, uri: Uri): String? {
        if (uri == Uri.EMPTY) return null
        if (uri.scheme == "file") return uri.path

        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var displayName = "temp_${System.currentTimeMillis()}"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) displayName = it.getString(nameIndex) ?: displayName
                }
            }
            val tempFile = File(context.cacheDir, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copy URI stream to a cache file and return the file.
     */
    suspend fun copyUriToCache(context: Context, uri: Uri, name: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                file
            } catch (e: Exception) { null }
        }

    /**
     * Save exported video to the device's Movies gallery.
     */
    suspend fun saveVideoToGallery(context: Context, sourceFile: File): Uri? =
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "VideoCraft_$timestamp.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoCraft")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "VideoCraft"
                )
                dir.mkdirs()
                val dest = File(dir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        }

    /**
     * Get all video files from device MediaStore.
     */
    suspend fun getDeviceVideos(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                videos.add(uri)
            }
        }
        videos
    }

    /**
     * Get all image files from device MediaStore.
     */
    suspend fun getDeviceImages(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        val images = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                images.add(uri)
            }
        }
        images
    }

    /**
     * Get all audio files from device MediaStore.
     */
    suspend fun getDeviceAudio(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        val audio = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                audio.add(uri)
            }
        }
        audio
    }

    /**
     * Create a temp output file for video export.
     */
    fun createTempVideoFile(context: Context, prefix: String = "output"): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.mp4")
    }

    /**
     * Get human-readable file size.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
